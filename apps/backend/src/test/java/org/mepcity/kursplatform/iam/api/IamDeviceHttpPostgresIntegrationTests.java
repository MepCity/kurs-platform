package org.mepcity.kursplatform.iam.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.mepcity.kursplatform.iam.application.DeviceSessionService;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.application.IamDeviceRateLimiter;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Full IAM-006 HTTP proof. Only credential-to-actor resolution is replaced; controller, service,
 * transaction executor, JDBC repository, audit, idempotency, rate limiter and snapshot serializer
 * are production beans, connected as iam_runtime to a real PostgreSQL instance.
 */
@ActiveProfiles("test")
@SpringBootTest(properties = "spring.flyway.enabled=false")
@AutoConfigureMockMvc
@Import(IamDeviceHttpPostgresIntegrationTests.TestCredentials.class)
@Execution(ExecutionMode.SAME_THREAD)
class IamDeviceHttpPostgresIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String RUNTIME_PASSWORD = "iam-device-http-runtime";
    private static final AtomicReference<Fixture> FIXTURE = new AtomicReference<>();
    private static volatile boolean migrated;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TokenHasher tokenHasher;
    @Autowired ApplicationContext applicationContext;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        startAndMigrate();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "iam_runtime");
        registry.add("spring.datasource.password", () -> RUNTIME_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @BeforeEach
    void seed() throws Exception {
        try (Connection owner = owner()) {
            owner.createStatement().execute(
                    "TRUNCATE iam_device_rate_limits, iam_auth_response_escrows, idempotency_keys, "
                            + "refresh_tokens, refresh_token_families, context_selection_tokens, trusted_devices, "
                            + "audit_logs, organization_membership_permissions, organization_membership_roles, "
                            + "organization_memberships, platform_administrators, user_identities, users, people, "
                            + "organizations RESTART IDENTITY CASCADE");
            Fixture f = Fixture.create();
            FIXTURE.set(f);
            insert(owner, "INSERT INTO users(id,status,reauthentication_required_after) VALUES "
                    + "(?,'ACTIVE',?),(?,'ACTIVE',?)",
                    f.actor, Timestamp.from(Instant.EPOCH), f.targetUser, Timestamp.from(Instant.EPOCH));
            insert(owner, "INSERT INTO organizations(id,name,default_timezone,status) VALUES "
                    + "(?,'IAM HTTP','Europe/Istanbul','ACTIVE'),(?,'Other','Europe/Istanbul','ACTIVE')",
                    f.organization, f.otherOrganization);
            UUID actorPerson = UUID.randomUUID(), targetPerson = UUID.randomUUID();
            insert(owner, "INSERT INTO people(id,organization_id,first_name,last_name,phone) VALUES "
                    + "(?,?,'Actor','User','1'),(?,?,'Target','User','2')",
                    actorPerson, f.organization, targetPerson, f.organization);
            insert(owner, "INSERT INTO organization_memberships"
                    + "(id,organization_id,user_id,person_id,status,session_generation,"
                    + "reauthentication_required_after,granted_at) VALUES "
                    + "(?,?,?,?,'ACTIVE',1,?,?),(?,?,?,?,'ACTIVE',1,?,?)",
                    f.actorMembership, f.organization, f.actor, actorPerson, Timestamp.from(Instant.EPOCH),
                    Timestamp.from(f.past), f.targetMembership, f.organization, f.targetUser, targetPerson,
                    Timestamp.from(Instant.EPOCH), Timestamp.from(f.past));
            insert(owner, "INSERT INTO organization_membership_roles"
                    + "(id,organization_membership_id,organization_id,role,granted_at) VALUES "
                    + "(?,?,?,'ORG_ADMIN',?)",
                    UUID.randomUUID(), f.actorMembership, f.organization, Timestamp.from(f.past));
            insert(owner, "INSERT INTO trusted_devices"
                    + "(id,user_id,device_identifier,device_name,platform,trusted_at,last_seen_at) VALUES "
                    + "(?,?,?,'Current','ANDROID',?,?),(?,?,?,'Other','IOS',?,?),"
                    + "(?,?,?,'Target','ANDROID',?,?)",
                    f.currentDevice, f.actor, UUID.randomUUID(), Timestamp.from(f.now.minusSeconds(10)),
                    Timestamp.from(f.now), f.otherDevice, f.actor, UUID.randomUUID(),
                    Timestamp.from(f.now.minusSeconds(20)), Timestamp.from(f.now), f.targetDevice,
                    f.targetUser, UUID.randomUUID(), Timestamp.from(f.now.minusSeconds(30)),
                    Timestamp.from(f.now));
            insert(owner, "INSERT INTO platform_administrators(id,user_id,granted_by_user_id,granted_at)"
                    + " VALUES (?,?,?,?)", UUID.randomUUID(), f.actor, f.actor, Timestamp.from(f.past));
            seedFamily(owner, f.actor, f.currentDevice, f.actorMembership, f.currentFamily,
                    tokenHasher.hash("own-token"), f);
            seedFamily(owner, f.targetUser, f.targetDevice, f.targetMembership, f.targetFamily,
                    "target-access-hash", f);
            owner.commit();
        }
    }

    @Test
    void listUsesCursorPaginationAndPreservesCurrentDeviceAndRequestId() throws Exception {
        assertProductionWiring();
        var first = mockMvc.perform(get("/api/v1/iam/devices")
                        .header("Authorization", "Bearer own-token")
                        .header("X-Request-Id", "iam-device-list-1").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "iam-device-list-1"))
                .andExpect(jsonPath("$.items[0].isCurrentDevice").value(true))
                .andExpect(jsonPath("$.page.hasNextPage").value(true))
                .andReturn();
        String cursor = json(first).path("page").path("nextCursor").textValue();
        mockMvc.perform(get("/api/v1/iam/devices").header("Authorization", "Bearer own-token")
                        .param("limit", "1").param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].isCurrentDevice").value(false))
                .andExpect(jsonPath("$.page.hasNextPage").value(false));
        mockMvc.perform(get("/api/v1/iam/devices").header("Authorization", "Bearer own-token")
                        .param("limit", "2").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_CURSOR"));
        mockMvc.perform(get("/api/v1/iam/devices").header("Authorization", "Bearer missing"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownRevokePersistsAndReplaysOriginalSnapshotWithoutSecondAuditOrRateClaim() throws Exception {
        Fixture f = FIXTURE.get();
        var first = mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.otherDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "own-revoke-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device.revokedAt").isNotEmpty())
                .andExpect(jsonPath("$.isCurrentDevice").value(false))
                .andExpect(jsonPath("$.revokedRefreshTokenFamilyCount").value(0))
                .andReturn();
        try (Connection owner = owner()) {
            insert(owner, "UPDATE trusted_devices SET device_name='Changed after response' WHERE id=?",
                    f.otherDevice);
            owner.commit();
        }
        var replay = mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.otherDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "own-revoke-key"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(replay)).isEqualTo(json(first));
        assertThat(count("audit_logs WHERE action_type='DEVICE_SELF_REVOKED'")).isEqualTo(1);
        assertThat(count("idempotency_keys WHERE operation_type='DEVICE_SELF_REVOKE'")).isEqualTo(1);
        assertThat(count("iam_device_rate_limits WHERE operation_code='DEVICE_SELF_REVOKE'")).isEqualTo(1);
        mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.currentDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "own-revoke-key"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_KEY_REUSED"));
        mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.otherDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "body-rejected").contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void organizationRevokeIsTenantBoundAndReplaysStoredMembershipSnapshot() throws Exception {
        Fixture f = FIXTURE.get();
        var first = mockMvc.perform(post(
                                "/api/v1/iam/organizations/{org}/memberships/{membership}/session-revoke",
                                f.organization, f.targetMembership)
                        .header("Authorization", "Bearer organization-token")
                        .header("Idempotency-Key", "membership-revoke-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionGeneration").value(2))
                .andExpect(jsonPath("$.revokedRefreshTokenFamilyCount").value(1))
                .andReturn();
        var replay = mockMvc.perform(post(
                                "/api/v1/iam/organizations/{org}/memberships/{membership}/session-revoke",
                                f.organization, f.targetMembership)
                        .header("Authorization", "Bearer organization-token")
                        .header("Idempotency-Key", "membership-revoke-key"))
                .andExpect(status().isOk()).andReturn();
        assertThat(json(replay)).isEqualTo(json(first));
        assertThat(count("audit_logs WHERE action_type='DEVICE_SESSION_REVOKED'")).isEqualTo(1);
        assertThat(count("refresh_token_families WHERE id='" + f.targetFamily + "' AND revoked_at IS NOT NULL"))
                .isEqualTo(1);
        mockMvc.perform(post(
                                "/api/v1/iam/organizations/{org}/memberships/{membership}/session-revoke",
                                f.otherOrganization, f.targetMembership)
                        .header("Authorization", "Bearer organization-token")
                        .header("Idempotency-Key", "cross-tenant"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                                "/api/v1/iam/organizations/{org}/memberships/{membership}/session-revoke",
                                f.organization, UUID.randomUUID())
                        .header("Authorization", "Bearer organization-token")
                        .header("Idempotency-Key", "missing-target"))
                .andExpect(status().isNotFound());
    }

    @Test
    void platformRevokeRequiresGlobalScopeAndRateLimitReturnsPositiveRetryAfter() throws Exception {
        Fixture f = FIXTURE.get();
        mockMvc.perform(post("/api/v1/iam/platform-admin/users/{user}/devices/{device}/revoke",
                                f.targetUser, f.targetDevice)
                        .header("Authorization", "Bearer organization-token")
                        .header("Idempotency-Key", "platform-forbidden"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/iam/platform-admin/users/{user}/devices/{device}/revoke",
                                f.targetUser, f.targetDevice)
                        .header("Authorization", "Bearer global-token")
                        .header("Idempotency-Key", "platform-success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.device.revokedAt").isNotEmpty())
                .andExpect(jsonPath("$.revokedRefreshTokenFamilyCount").value(1));
        assertThat(count("audit_logs WHERE action_type='PLATFORM_DEVICE_REVOKED'")).isEqualTo(1);

        try (Connection owner = owner()) {
            insert(owner, "INSERT INTO iam_device_rate_limits"
                    + "(actor_user_id,scope_type,context_id,operation_code,window_started_at,request_count)"
                    + " VALUES (?,'IAM_AUTH',?,'DEVICE_LIST',"
                    + "to_timestamp(floor(extract(epoch FROM clock_timestamp())/60)*60),30)",
                    f.actor, f.actor);
            owner.commit();
        }
        mockMvc.perform(get("/api/v1/iam/devices").header("Authorization", "Bearer own-token"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After",
                        org.hamcrest.Matchers.matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }

    @Test
    void concurrentSameAndDifferentKeysSerializeToOneMutationWithoutDeadlock() throws Exception {
        Fixture f = FIXTURE.get();
        var pool = Executors.newFixedThreadPool(2);
        try {
            CyclicBarrier sameStart = new CyclicBarrier(2);
            var same = List.of(
                    pool.submit(() -> revokeAfterBarrier(sameStart, f.otherDevice, "same-race-key")),
                    pool.submit(() -> revokeAfterBarrier(sameStart, f.otherDevice, "same-race-key")));
            var first = same.get(0).get(20, TimeUnit.SECONDS);
            var second = same.get(1).get(20, TimeUnit.SECONDS);
            assertThat(first.getResponse().getStatus()).isEqualTo(200);
            assertThat(second.getResponse().getStatus()).isEqualTo(200);
            assertThat(objectMapper.readTree(first.getResponse().getContentAsString()))
                    .isEqualTo(objectMapper.readTree(second.getResponse().getContentAsString()));
            assertThat(count("audit_logs WHERE action_type='DEVICE_SELF_REVOKED'")).isEqualTo(1);

            UUID freshDevice = UUID.randomUUID();
            try (Connection owner = owner()) {
                insert(owner, "INSERT INTO trusted_devices"
                        + "(id,user_id,device_identifier,device_name,platform,trusted_at,last_seen_at)"
                        + " VALUES (?,?,?,'Race','ANDROID',?,?)", freshDevice, f.actor,
                        UUID.randomUUID(), Timestamp.from(f.now.minusSeconds(40)),
                        Timestamp.from(f.now));
                owner.commit();
            }
            CyclicBarrier differentStart = new CyclicBarrier(2);
            var different = List.of(
                    pool.submit(() -> revokeAfterBarrier(differentStart, freshDevice, "different-race-a")),
                    pool.submit(() -> revokeAfterBarrier(differentStart, freshDevice, "different-race-b")));
            assertThat(different.get(0).get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(different.get(1).get(20, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(count("audit_logs WHERE action_type='DEVICE_SELF_REVOKED'")).isEqualTo(3);
            assertThat(count("trusted_devices WHERE id='" + freshDevice + "' AND revoked_at IS NOT NULL"))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void currentDeviceRevokeInvalidatesAuthenticationBeforeReplay() throws Exception {
        Fixture f = FIXTURE.get();
        mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.currentDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "current-device-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCurrentDevice").value(true))
                .andExpect(jsonPath("$.revokedRefreshTokenFamilyCount").value(1));
        mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", f.currentDevice)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", "current-device-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REVOKED"));
        assertThat(count("audit_logs WHERE action_type='DEVICE_SELF_REVOKED'")).isEqualTo(1);
    }

    private org.springframework.test.web.servlet.MvcResult revokeAfterBarrier(
            CyclicBarrier barrier, UUID device, String key) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/iam/devices/{id}/revoke", device)
                        .header("Authorization", "Bearer own-token")
                        .header("Idempotency-Key", key))
                .andReturn();
    }

    private void assertProductionWiring() {
        assertThat(applicationContext.getBean(IamAuthRepository.class))
                .isInstanceOf(JdbcIamAuthRepository.class);
        assertThat(applicationContext.getBeanNamesForType(DeviceSessionService.class))
                .containsExactly("deviceSessionService");
        assertThat(applicationContext.getBean(IamTransactionExecutor.class).getClass().getSimpleName())
                .isEqualTo("SpringIamTransactionExecutor");
        assertThat(applicationContext.getBean(IamDeviceRateLimiter.class).getClass().getSimpleName())
                .isEqualTo("JdbcIamDeviceRateLimiter");
    }

    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static void seedFamily(
            Connection owner, UUID user, UUID device, UUID membership, UUID family,
            String accessHash, Fixture f) throws Exception {
        insert(owner, "INSERT INTO refresh_token_families"
                + "(id,user_id,trusted_device_id,organization_membership_id,authenticated_at,"
                + "issued_at_session_generation,created_at) VALUES (?,?,?,?,?,?,?)",
                family, user, device, membership, Timestamp.from(f.past), 1, Timestamp.from(f.past));
        insert(owner, "INSERT INTO refresh_tokens"
                + "(id,family_id,token_hash,access_token_hash,access_expires_at,issued_at,expires_at)"
                + " VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(), family, "refresh-" + UUID.randomUUID(),
                accessHash, Timestamp.from(f.now.plusSeconds(3600)), Timestamp.from(f.past),
                Timestamp.from(f.now.plusSeconds(7200)));
    }

    private static void insert(Connection connection, String sql, Object... values) throws Exception {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    private static long count(String target) throws Exception {
        try (Connection owner = owner();
             var rows = owner.createStatement().executeQuery("SELECT count(*) FROM " + target)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static synchronized void startAndMigrate() {
        if (migrated) return;
        POSTGRES.start();
        Flyway.configure().dataSource(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().migrate();
        try (Connection owner = owner()) {
            owner.createStatement().execute(
                    "ALTER ROLE iam_runtime PASSWORD '" + RUNTIME_PASSWORD + "'");
            owner.commit();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
        migrated = true;
    }

    private static Connection owner() throws Exception {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    record Fixture(
            UUID actor, UUID targetUser, UUID organization, UUID otherOrganization,
            UUID actorMembership, UUID targetMembership, UUID currentDevice, UUID otherDevice,
            UUID targetDevice, UUID currentFamily, UUID targetFamily, Instant past, Instant now) {
        static Fixture create() {
            Instant now = Instant.now();
            return new Fixture(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    now.minusSeconds(300), now);
        }
    }

    @TestConfiguration
    static class TestCredentials {
        @Bean
        @Primary
        ActiveSessionResolver iam006CredentialResolver() {
            return credential -> {
                Fixture f = FIXTURE.get();
                if (f == null) throw new CredentialAuthenticationException("UNAUTHENTICATED");
                return switch (credential) {
                    case "own-token", "organization-token" ->
                            CredentialResolution.platformAccess(
                                    ActiveSession.organization(f.actor, f.organization));
                    case "global-token" ->
                            CredentialResolution.platformAccess(
                                    ActiveSession.globalPlatformAdmin(f.actor));
                    default -> throw new CredentialAuthenticationException("UNAUTHENTICATED");
                };
            };
        }
    }
}
