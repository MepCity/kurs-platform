package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.application.OrganizationCreateRateLimiter;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real HTTP proof: Spring's runtime DataSource logs in as iam_runtime, then lifecycle SET LOCAL ROLEs. */
@ActiveProfiles("local-stub")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "KURS_PLATFORM_ENVIRONMENT=development",
        "KURS_PLATFORM_PUBLIC_API_BASE_URL=https://api-development.example.invalid",
        "KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
        "KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid",
        "KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url",
        "KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper",
        "KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF=development/platform/iam-secret-delivery-key",
        "KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF=development/platform/cognito-admin-role"
})
@AutoConfigureMockMvc
@Import(OrganizationHttpPostgresIntegrationTests.TestAuthConfiguration.class)
class OrganizationHttpPostgresIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final AtomicReference<UUID> ACTOR = new AtomicReference<>();
    private static volatile boolean migrated;

    @Autowired MockMvc mockMvc;
    @Autowired DataSource dataSource;
    @Autowired org.springframework.context.ApplicationContext applicationContext;

    @DynamicPropertySource
    static void runtimeDataSource(DynamicPropertyRegistry registry) {
        startAndMigrate();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "iam_runtime");
        registry.add("spring.datasource.password", () -> "http-runtime-password");
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void seedActivePlatformAdministrator() throws Exception {
        UUID actor = UUID.randomUUID();
        ACTOR.set(actor);
        try (Connection owner = ownerConnection()) {
            owner.createStatement().execute("INSERT INTO users (id, status) VALUES ('" + actor + "', 'ACTIVE')");
            owner.createStatement().execute("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES ('"
                    + UUID.randomUUID() + "', '" + actor + "', transaction_timestamp())");
            owner.commit();
        }
    }

    @Test
    void httpCreateReplayConflictAndAuthorizationUseTheRealIamRuntimeToOrgRuntimeChain() throws Exception {
        assertProductionCompositionRoot();
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT session_user, current_user")) {
            result.next();
            assertThat(result.getString(1)).isEqualTo("iam_runtime");
            assertThat(result.getString(2)).isEqualTo("iam_runtime");
        }

        String body = "{\"name\":\"HTTP Gerçek Zincir\",\"defaultTimezone\":\"Europe/Istanbul\"}";
        var firstResult = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer global")
                        .header("Idempotency-Key", "http-real-key").contentType("application/json").content(body))
                .andReturn();
        String first = firstResult.getResponse().getContentAsString();
        String firstLocation = firstResult.getResponse().getHeader("Location");
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
        assertThat(firstLocation).startsWith("/api/v1/organizations/");
        assertThat(ownerCount("organizations")).isEqualTo(1);
        assertThat(ownerCount("audit_logs WHERE action_type = 'ORG_CREATED'")).isEqualTo(1);
        assertThat(ownerCount("idempotency_keys WHERE scope_type = 'GLOBAL' AND operation_type = 'ORG_CREATE' AND status = 'COMPLETED'"))
                .isEqualTo(1);

        var replayResult = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer global")
                        .header("Idempotency-Key", "http-real-key").contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/organizations/")))
                .andReturn();
        String replay = replayResult.getResponse().getContentAsString();
        assertThat(replayResult.getResponse().getHeader("Location")).isEqualTo(firstLocation);
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(replay))
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(first));
        assertThat(ownerCount("organizations")).isEqualTo(1);
        assertThat(ownerCount("audit_logs WHERE action_type = 'ORG_CREATED'")).isEqualTo(1);

        var conflict = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer global")
                        .header("Idempotency-Key", "http-real-key").contentType("application/json")
                        .content("{\"name\":\"Farklı parmak izi\",\"defaultTimezone\":\"Europe/Istanbul\"}"))
                .andReturn();
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer organization")
                        .header("Idempotency-Key", "org-denied").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer context")
                        .header("Idempotency-Key", "context-denied").contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        assertThat(ownerCount("organizations")).isEqualTo(1);
        assertThat(ownerCount("audit_logs WHERE action_type = 'ORG_CREATED'")).isEqualTo(1);
    }

    private void assertProductionCompositionRoot() {
        assertThat(applicationContext.getBeanNamesForType(OrganizationController.class))
                .containsExactly("organizationController");
        assertThat(applicationContext.getBeanNamesForType(OrganizationCreationService.class))
                .containsExactly("organizationCreationService");
        assertThat(applicationContext.getBeanNamesForType(OrganizationLifecycleService.class))
                .containsExactly("organizationLifecycleService");
        assertThat(applicationContext.getBeanNamesForType(OrganizationRepository.class))
                .containsExactly("jdbcOrganizationRepository");
        assertThat(applicationContext.getBeanNamesForType(OrganizationCreateRateLimiter.class))
                .containsExactly("organizationCreateRateLimiter");
        assertThat(applicationContext.getBean(OrganizationRepository.class))
                .isInstanceOf(JdbcOrganizationRepository.class);
        assertThat(applicationContext.getBean(OrganizationController.class).getClass().getName())
                .doesNotContain("TestAuthConfiguration");
        assertThat(applicationContext.getBean(OrganizationCreationService.class).getClass().getName())
                .doesNotContain("TestAuthConfiguration");
        assertThat(applicationContext.getBean(OrganizationLifecycleService.class).getClass().getName())
                .doesNotContain("TestAuthConfiguration");
        assertThat(applicationContext.getBean(OrganizationCreateRateLimiter.class).getClass().getName())
                .doesNotContain("TestAuthConfiguration");
    }

    private static synchronized void startAndMigrate() {
        if (migrated) return;
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().migrate();
        try (Connection owner = ownerConnection()) {
            owner.createStatement().execute("ALTER ROLE iam_runtime PASSWORD 'http-runtime-password'");
            owner.commit();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
        migrated = true;
    }

    private static Connection ownerConnection() throws Exception {
        Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    private static long ownerCount(String target) throws Exception {
        try (Connection owner = ownerConnection(); var result = owner.createStatement().executeQuery("SELECT count(*) FROM " + target)) {
            result.next();
            return result.getLong(1);
        }
    }

    @TestConfiguration
    static class TestAuthConfiguration {
        @Bean @Primary
        ActiveSessionResolver httpIntegrationCredentialResolver() {
            return credential -> switch (credential) {
                case "global" -> CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(ACTOR.get()));
                case "organization" -> CredentialResolution.platformAccess(ActiveSession.organization(ACTOR.get(), UUID.randomUUID()));
                case "context" -> CredentialResolution.contextSelection();
                default -> throw new org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException("UNAUTHENTICATED");
            };
        }
    }
}
