package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.org.application.OrganizationCreateRateLimiter;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.application.OrganizationBrandService;
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
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(OrderAnnotation.class)
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

    @Test @Order(1)
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

    @Test @Order(2)
    void httpBrandUsesTheProductionControllerServiceRepositoryAuditAndIdempotencyChain() throws Exception {
        assertThat(applicationContext.getBean(OrganizationBrandService.class).getClass().getName())
                .doesNotContain("TestAuthConfiguration");
        var created = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer global")
                        .header("Idempotency-Key", "brand-http-create").contentType("application/json")
                        .content("{\"name\":\"Marka HTTP\",\"defaultTimezone\":\"Europe/Istanbul\"}"))
                .andExpect(status().isCreated()).andReturn();
        UUID organizationId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).path("id").textValue());

        mockMvc.perform(get("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("X-Request-Id", "brand-request-42"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"1\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.primaryColor").value("#2E7D32"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.secondaryColor").value("#E65100"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.logo").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.colors").doesNotExist());
        assertThat(ownerCount("audit_logs WHERE action_type = 'PLATFORM_ADMIN_ORG_ACCESS' AND request_id = 'brand-request-42'"))
                .isEqualTo(1);

        String body = "{\"primaryColor\":\"#1565C0\"}";
        var first = mockMvc.perform(patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("Idempotency-Key", "brand-http-replay")
                        .header("If-Match-Row-Version", "1").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\"")).andReturn();
        var replay = mockMvc.perform(patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("Idempotency-Key", "brand-http-replay")
                        .header("If-Match-Row-Version", "1").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(replay.getResponse().getContentAsString()))
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString()));
        assertThat(ownerCount("audit_logs WHERE action_type = 'ORG_SETTING_CHANGED' AND organization_id = '" + organizationId + "'"))
                .isEqualTo(1);
    }

    @Test @Order(3)
    void sameKeyWithDifferentRowVersionIsRejectedForBrandPaletteAndModulesWithoutSideEffects() throws Exception {
        UUID organizationId = createOrganization("Fingerprint çatışması");

        assertDifferentRowVersionConflict(organizationId, rowVersion -> patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("If-Match-Row-Version", rowVersion).content("{\"primaryColor\":\"#1565C0\"}"),
                "ORG_UPDATE_BRAND", 1);
        assertDifferentRowVersionConflict(organizationId, rowVersion -> put("/api/v1/organizations/{id}/brand-colors", organizationId)
                        .header("If-Match-Row-Version", rowVersion)
                        .content("{\"items\":[{\"colorHex\":\"#1565C0\",\"sortOrder\":0}]}"),
                "ORG_UPDATE_BRAND_COLORS", 2);
        assertDifferentRowVersionConflict(organizationId, rowVersion -> patch("/api/v1/organizations/{id}/modules", organizationId)
                        .header("If-Match-Row-Version", rowVersion)
                        .content("{\"items\":[{\"moduleCode\":\"ATT\",\"isEnabled\":false}]}"),
                "ORG_UPDATE_MODULES", 3);
    }

    @Test @Order(4)
    void sameFingerprintReplayReturnsTheOriginalBrandSnapshotAfterTheSourceChanges() throws Exception {
        UUID organizationId = createOrganization("Snapshot replay");
        String firstKey = "brand-original-snapshot";
        var first = mockMvc.perform(patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("Idempotency-Key", firstKey)
                        .header("If-Match-Row-Version", "1").contentType("application/json")
                        .content("{\"primaryColor\":\"#1565C0\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\"")).andReturn();
        mockMvc.perform(patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("Idempotency-Key", "brand-source-change")
                        .header("If-Match-Row-Version", "2").contentType("application/json")
                        .content("{\"primaryColor\":\"#6A1B9A\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"3\""));
        var replay = mockMvc.perform(patch("/api/v1/organizations/{id}/brand", organizationId)
                        .header("Authorization", "Bearer global").header("Idempotency-Key", firstKey)
                        .header("If-Match-Row-Version", "1").contentType("application/json")
                        .content("{\"primaryColor\":\"#1565C0\"}"))
                .andExpect(status().isOk()).andExpect(header().string("ETag", "\"2\"")).andReturn();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().readTree(replay.getResponse().getContentAsString()))
                .isEqualTo(new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getResponse().getContentAsString()));
    }

    @Test @Order(5)
    void plainTextWritesReturnTheSafeInvalidRequestEnvelopeWithoutPersistentSideEffects() throws Exception {
        UUID organizationId = createOrganization("Yanlış içerik türü");
        long auditBefore = ownerCount("audit_logs WHERE organization_id = '" + organizationId + "'");
        long rateBefore = ownerCount("organization_brand_rate_limits WHERE organization_id = '" + organizationId + "'");
        long completedBefore = ownerCount("idempotency_keys WHERE organization_id = '" + organizationId + "' AND status = 'COMPLETED'");

        assertPlainTextRejected(patch("/api/v1/organizations/{id}/brand", organizationId), "plain-brand-42");
        assertPlainTextRejected(put("/api/v1/organizations/{id}/brand-colors", organizationId), "plain-palette-42");
        assertPlainTextRejected(patch("/api/v1/organizations/{id}/modules", organizationId), "plain-modules-42");

        assertThat(ownerCount("audit_logs WHERE organization_id = '" + organizationId + "'")).isEqualTo(auditBefore);
        assertThat(ownerCount("organization_brand_rate_limits WHERE organization_id = '" + organizationId + "'")).isEqualTo(rateBefore);
        assertThat(ownerCount("idempotency_keys WHERE organization_id = '" + organizationId + "' AND status = 'COMPLETED'"))
                .isEqualTo(completedBefore);
    }

    private UUID createOrganization(String name) throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID actor = ACTOR.get();
        try (Connection owner = ownerConnection(); var organization = owner.prepareStatement("""
                INSERT INTO organizations (id, name, status, default_timezone, created_by_user_id, updated_by_user_id)
                VALUES (?, ?, 'ACTIVE', 'Europe/Istanbul', ?, ?)
                """);
                var modules = owner.prepareStatement("""
                INSERT INTO organization_modules (organization_id, module_code, is_enabled, sort_order, updated_by_user_id)
                VALUES (?, ?, true, ?, ?)
                ON CONFLICT (organization_id, module_code) DO NOTHING
                """)) {
            organization.setObject(1, organizationId);
            organization.setString(2, name);
            organization.setObject(3, actor);
            organization.setObject(4, actor);
            organization.executeUpdate();
            String[] moduleCodes = {"ATT", "PROGRAM", "CONTENT", "PROGRESS", "EXPORT", "AUDIT"};
            for (int index = 0; index < moduleCodes.length; index++) {
                modules.setObject(1, organizationId);
                modules.setString(2, moduleCodes[index]);
                modules.setInt(3, index);
                modules.setObject(4, actor);
                modules.addBatch();
            }
            modules.executeBatch();
            owner.commit();
        }
        return organizationId;
    }

    private void assertDifferentRowVersionConflict(UUID organizationId,
            Function<String, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder> requestForVersion,
            String operation, int firstRowVersion) throws Exception {
        String key = "fingerprint-" + operation.toLowerCase();
        mockMvc.perform(requestForVersion.apply(Integer.toString(firstRowVersion)).header("Authorization", "Bearer global")
                        .header("Idempotency-Key", key).contentType("application/json"))
                .andExpect(status().isOk());
        long auditAfterFirst = ownerCount("audit_logs WHERE action_type = 'ORG_SETTING_CHANGED' AND organization_id = '" + organizationId + "'");
        long rateAfterFirst = ownerCount("organization_brand_rate_limits WHERE organization_id = '" + organizationId + "'");
        long completedAfterFirst = ownerCount("idempotency_keys WHERE organization_id = '" + organizationId + "' AND operation_type = '" + operation + "' AND status = 'COMPLETED'");

        mockMvc.perform(requestForVersion.apply(Integer.toString(firstRowVersion + 1)).header("Authorization", "Bearer global")
                        .header("Idempotency-Key", key).contentType("application/json"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code")
                        .value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(ownerCount("audit_logs WHERE action_type = 'ORG_SETTING_CHANGED' AND organization_id = '" + organizationId + "'"))
                .isEqualTo(auditAfterFirst);
        assertThat(ownerCount("organization_brand_rate_limits WHERE organization_id = '" + organizationId + "'"))
                .isEqualTo(rateAfterFirst);
        assertThat(ownerCount("idempotency_keys WHERE organization_id = '" + organizationId + "' AND operation_type = '" + operation + "' AND status = 'COMPLETED'"))
                .isEqualTo(completedAfterFirst);
    }

    private void assertPlainTextRejected(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String requestId) throws Exception {
        var result = mockMvc.perform(request.header("Authorization", "Bearer global").header("Idempotency-Key", requestId)
                        .header("If-Match-Row-Version", "1").header("X-Request-Id", requestId)
                        .contentType("text/plain").content("not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.code").value("INVALID_REQUEST"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error.requestId").value(requestId))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception", "SQLException", "stack", "org.postgresql");
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
