package org.mepcity.kursplatform.iam.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.application.ContextSelectionService;
import org.mepcity.kursplatform.iam.application.IamAuditWriter;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.ProviderCommandOutcome;
import org.mepcity.kursplatform.iam.application.ProviderCommandResult;
import org.mepcity.kursplatform.iam.application.ProviderCommandRetryPolicy;
import org.mepcity.kursplatform.iam.application.ProviderCommandScheduler;
import org.mepcity.kursplatform.iam.application.ProviderCommandService;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorker;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeResult;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeService;
import org.mepcity.kursplatform.iam.application.SessionActivationResult;
import org.mepcity.kursplatform.iam.application.SessionActivationService;
import org.mepcity.kursplatform.iam.application.SessionInfoResult;
import org.mepcity.kursplatform.iam.application.SessionInfoService;
import org.mepcity.kursplatform.iam.application.SessionRefreshService;
import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;
import org.mepcity.kursplatform.iam.infrastructure.AeadEscrowServiceImpl;
import org.mepcity.kursplatform.iam.infrastructure.HmacSha256TokenHasher;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuditWriter;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuthRepository;
import org.mepcity.kursplatform.iam.infrastructure.SpringIamTransactionExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises ProviderTokenExchangeService / SessionActivationService / ProviderCommandService
 * through the ACTUAL Spring wiring (SpringIamTransactionExecutor + DataSourceTransactionManager +
 * JdbcIamAuthRepository), authenticating as iam_runtime against a real Postgres, rather than
 * mocking IamAuthRepository. Mock-based unit tests (ProviderTokenExchangeServiceTests etc.) prove
 * the service logic branches; these prove the transaction actually commits/rolls back as one unit
 * and that concurrent requests are correctly serialized at the database level — neither of which
 * a mocked repository or a NoopTransactionExecutor can demonstrate.
 */
class IamTransactionAtomicityIntegrationTests {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final String RUNTIME_PASSWORD = "iam-runtime-test-password";

    static JdbcTemplate adminJdbcTemplate;
    static JdbcTemplate runtimeJdbcTemplate;
    static IamAuthRepository repository;
    static IamTransactionExecutor transactionExecutor;
    static HmacSha256TokenHasher tokenHasher;
    static AeadEscrowServiceImpl escrowService;
    static SecureRandom secureRandom;
    static Clock clock;
    static IamServiceSettings settings;
    static ProviderTokenExchangeService exchangeService;
    static SessionActivationService activationService;
    static SessionInfoService sessionInfoService;
    static ContextSelectionService contextSelectionService;
    static SessionRefreshService refreshService;
    static ProviderCommandService providerCommandService;
    static StubCognitoUserStatusChecker statusChecker;
    static IamAuditWriter auditWriter;

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();

        DriverManagerDataSource adminDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        adminJdbcTemplate = new JdbcTemplate(adminDataSource);
        adminJdbcTemplate.execute("ALTER ROLE iam_runtime WITH PASSWORD '" + RUNTIME_PASSWORD + "'");

        DriverManagerDataSource runtimeDataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "iam_runtime", RUNTIME_PASSWORD);
        runtimeJdbcTemplate = new JdbcTemplate(runtimeDataSource);
        repository = new JdbcIamAuthRepository(runtimeJdbcTemplate);
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(runtimeDataSource);
        transactionExecutor = new SpringIamTransactionExecutor(transactionManager, runtimeDataSource);

        tokenHasher = new HmacSha256TokenHasher("test-pepper-for-atomicity-tests-1234");
        escrowService = new AeadEscrowServiceImpl("test-escrow-secret-for-atomicity-1234");
        secureRandom = new SecureRandom();
        // IAM mutation policies deliberately stamp state with PostgreSQL's transaction clock.
        // Keep fixture-issued timestamps safely before that clock so millisecond scheduling cannot
        // create an impossible issued_at > revoked_at/used_at row during a fast Testcontainers run.
        clock = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(-1));
        settings = new FixedServiceSettings();
        statusChecker = new StubCognitoUserStatusChecker();
        auditWriter = new JdbcIamAuditWriter(runtimeDataSource);

        exchangeService = new ProviderTokenExchangeService(repository, IamTransactionAtomicityIntegrationTests::parseTestToken,
                tokenHasher, escrowService, secureRandom, clock, transactionExecutor, settings, auditWriter);
        providerCommandService = new ProviderCommandService(repository, clock, transactionExecutor, auditWriter,
                new ProviderCommandRetryPolicy(10, Duration.ofSeconds(10), Duration.ofMinutes(15), 0.25d, secureRandom));
        activationService = new SessionActivationService(repository, statusChecker, tokenHasher,
                escrowService, secureRandom, clock, transactionExecutor, settings, auditWriter,
                providerCommandService);
        sessionInfoService = new SessionInfoService(repository, tokenHasher, clock, transactionExecutor);
        contextSelectionService = new ContextSelectionService(repository, tokenHasher, clock, transactionExecutor);
        refreshService = new SessionRefreshService(repository, tokenHasher, secureRandom, clock, transactionExecutor,
                settings, escrowService, auditWriter);
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetState() {
        adminJdbcTemplate.execute("TRUNCATE TABLE iam_auth_response_escrows, idempotency_keys, refresh_tokens, " +
                "refresh_token_families, context_selection_tokens, trusted_devices, iam_secret_deliveries, " +
                "iam_provider_commands, audit_logs, organization_membership_permissions, organization_membership_roles, " +
                "organization_memberships, platform_administrator_profiles, platform_administrators, user_identities, " +
                "users, people, organizations RESTART IDENTITY CASCADE");
        statusChecker.reset();
    }

    private static CognitoTokenClaims parseTestToken(String token) {
        // format: subject|issuer|clientId|authEpochSeconds|expEpochSeconds
        String[] parts = token.split("\\|");
        return new CognitoTokenClaims(parts[1], parts[0], parts[2], "access",
                Instant.ofEpochSecond(Long.parseLong(parts[3])), Instant.ofEpochSecond(Long.parseLong(parts[4])),
                null, "fp-" + token.hashCode());
    }

    private static String buildToken(String subject, String issuer, String clientId, Instant authTime, Instant expiresAt) {
        return subject + "|" + issuer + "|" + clientId + "|" + authTime.getEpochSecond() + "|" + expiresAt.getEpochSecond();
    }

    private UUID seedUser() {
        UUID userId = UUID.randomUUID();
        adminJdbcTemplate.update("INSERT INTO users (id, status, reauthentication_required_after) VALUES (?, 'ACTIVE'::user_status_enum, ?)",
                userId, java.sql.Timestamp.from(Instant.EPOCH));
        return userId;
    }

    private void seedIdentity(UUID userId, String issuer, String subject) {
        adminJdbcTemplate.update("INSERT INTO user_identities (id, user_id, issuer, subject) VALUES (?, ?, ?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
                UUID.randomUUID(), userId, issuer, subject);
    }

    private UUID seedOrganization() {
        UUID orgId = UUID.randomUUID();
        adminJdbcTemplate.update("INSERT INTO organizations (id, name, status) VALUES (?, 'Test Org', 'ACTIVE')", orgId);
        return orgId;
    }

    private UUID seedActiveMembership(UUID orgId, UUID userId, String role) {
        return seedMembership(orgId, userId, role, "ACTIVE");
    }

    /**
     * organization_memberships.status is immutable post-insert — trg_org_membership_revoke()
     * (V1) rejects ANY update that changes status (or id/organization_id/user_id/person_id/
     * granted_by_user_id/granted_at), and separately requires session_generation to increment by
     * exactly 1 together with reauthentication_required_after = transaction_timestamp() on every
     * update it does allow. A non-ACTIVE membership row for a test fixture must therefore be
     * INSERTed directly with that status, never produced by UPDATE-after-insert.
     */
    private UUID seedMembership(UUID orgId, UUID userId, String role, String status) {
        UUID personId = UUID.randomUUID();
        adminJdbcTemplate.update(
                "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES (?, ?, 'Test', 'Person', '5550000000')",
                personId, orgId);
        UUID membershipId = UUID.randomUUID();
        adminJdbcTemplate.update(
                "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, session_generation, reauthentication_required_after, granted_at) " +
                        "VALUES (?, ?, ?, ?, ?::user_status_enum, 1, ?, ?)",
                membershipId, orgId, userId, personId, status,
                java.sql.Timestamp.from(Instant.EPOCH), java.sql.Timestamp.from(clock.instant()));
        adminJdbcTemplate.update(
                "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) " +
                        "VALUES (?, ?, ?, ?::membership_role_enum, ?)",
                UUID.randomUUID(), membershipId, orgId, role, java.sql.Timestamp.from(clock.instant()));
        return membershipId;
    }

    private void revokeTrustedDevice(UUID deviceId) {
        adminJdbcTemplate.update("UPDATE trusted_devices SET revoked_at = transaction_timestamp() WHERE id = ?", deviceId);
    }

    private UUID seedTrustedDevice(UUID userId, UUID deviceIdentifier) {
        UUID deviceId = UUID.randomUUID();
        adminJdbcTemplate.update("INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                "VALUES (?, ?, ?, 'ANDROID'::device_platform_enum)", deviceId, userId, deviceIdentifier);
        seedIdentity(userId, "test-issuer", "subject-" + userId);
        return deviceId;
    }

    @Nested
    class ProviderTokenExchangeAtomicity {

        @Test
        void exchangeCommitsTrustedDeviceContextTokenIdempotencyAndEscrowInOneTransaction() {
            UUID userId = seedUser();
            seedIdentity(userId, "test-issuer", "subject-1");
            UUID deviceIdentifier = UUID.randomUUID();
            Instant now = clock.instant();
            String token = buildToken("subject-1", "test-issuer", "client-1", now.minusSeconds(30), now.plusSeconds(3600));

            ProviderTokenExchangeResult result = exchangeService.exchange(token, deviceIdentifier,
                    DevicePlatform.ANDROID, "Pixel 9", "mutation-atomic-1");

            assertThat(result.contextSelectionToken()).isNotNull();
            assertThat(countRows("trusted_devices", userId)).isEqualTo(1);
            assertThat(countRows("context_selection_tokens", userId)).isEqualTo(1);
            assertThat(countRowsIdempotency(userId, "PROVIDER_TOKEN_EXCHANGE")).isEqualTo(1);
            assertThat(countRowsEscrow(userId)).isEqualTo(1);
        }

        @Test
        void exchangeRollsBackAllWritesWhenEscrowStepFails() {
            UUID userId = seedUser();
            seedIdentity(userId, "test-issuer", "subject-2");
            UUID deviceIdentifier = UUID.randomUUID();
            Instant now = clock.instant();
            String token = buildToken("subject-2", "test-issuer", "client-1", now.minusSeconds(30), now.plusSeconds(3600));

            AeadEscrowService throwingEscrow = new ThrowingEncryptEscrowService(escrowService);
            ProviderTokenExchangeService failingService = new ProviderTokenExchangeService(
                    repository, IamTransactionAtomicityIntegrationTests::parseTestToken, tokenHasher,
                    throwingEscrow, secureRandom, clock, transactionExecutor, settings, auditWriter);

            assertThatThrownBy(() -> failingService.exchange(token, deviceIdentifier,
                    DevicePlatform.ANDROID, "Pixel 9", "mutation-atomic-2"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(countRows("trusted_devices", userId)).as("device insert must roll back").isEqualTo(0);
            assertThat(countRows("context_selection_tokens", userId)).as("context token insert must roll back").isEqualTo(0);
            assertThat(countRowsIdempotency(userId, "PROVIDER_TOKEN_EXCHANGE")).as("idempotency key must roll back").isEqualTo(0);
        }

        @Test
        void concurrentExchangeWithSameIdempotencyKeyProducesSingleWinner() throws Exception {
            UUID userId = seedUser();
            seedIdentity(userId, "test-issuer", "subject-3");
            UUID deviceIdentifier = UUID.randomUUID();
            Instant now = clock.instant();
            String token = buildToken("subject-3", "test-issuer", "client-1", now.minusSeconds(30), now.plusSeconds(3600));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            try {
                List<Future<ProviderTokenExchangeResult>> futures = List.of(
                        pool.submit(() -> { startLatch.await(); return exchangeService.exchange(
                                token, deviceIdentifier, DevicePlatform.ANDROID, "Pixel 9", "mutation-concurrent-1"); }),
                        pool.submit(() -> { startLatch.await(); return exchangeService.exchange(
                                token, deviceIdentifier, DevicePlatform.ANDROID, "Pixel 9", "mutation-concurrent-1"); }));
                startLatch.countDown();

                ProviderTokenExchangeResult first = futures.get(0).get(30, TimeUnit.SECONDS);
                ProviderTokenExchangeResult second = futures.get(1).get(30, TimeUnit.SECONDS);

                assertThat(first.contextSelectionToken().hash()).isEqualTo(second.contextSelectionToken().hash());
                assertThat(countRows("trusted_devices", userId)).as("only one device row despite the race").isEqualTo(1);
                assertThat(countRows("context_selection_tokens", userId)).as("only one context token despite the race").isEqualTo(1);
                assertThat(countRowsIdempotency(userId, "PROVIDER_TOKEN_EXCHANGE")).isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }
    }

    @Nested
    class AuditIntegration {

        @Test
        void auditWriteFailureRollsBackTheWholeIamMutation() {
            UUID userId = seedUser();
            seedIdentity(userId, "test-issuer", "subject-audit-1");
            UUID deviceIdentifier = UUID.randomUUID();
            Instant now = clock.instant();
            String token = buildToken("subject-audit-1", "test-issuer", "client-1", now.minusSeconds(30), now.plusSeconds(3600));

            ProviderTokenExchangeService failingAuditService = new ProviderTokenExchangeService(
                    repository, IamTransactionAtomicityIntegrationTests::parseTestToken, tokenHasher,
                    escrowService, secureRandom, clock, transactionExecutor, settings, new ThrowingAuditWriter());

            assertThatThrownBy(() -> failingAuditService.exchange(token, deviceIdentifier,
                    DevicePlatform.ANDROID, "Pixel 9", "mutation-audit-failure-1"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(countRows("trusted_devices", userId)).as("device insert must roll back").isEqualTo(0);
            assertThat(countRows("context_selection_tokens", userId)).as("context token insert must roll back").isEqualTo(0);
            assertThat(countRowsIdempotency(userId, "PROVIDER_TOKEN_EXCHANGE")).as("idempotency key must roll back").isEqualTo(0);
            assertThat(countRowsEscrow(userId)).as("replay escrow must roll back").isEqualTo(0);
            assertThat(countAuditLogs(userId, "PROVIDER_TOKEN_EXCHANGED")).as("no partial audit row").isEqualTo(0);
        }

        @Test
        void replayingTheSameIdempotencyKeyProducesNoSecondAuditRecord() {
            UUID userId = seedUser();
            seedIdentity(userId, "test-issuer", "subject-audit-2");
            UUID deviceIdentifier = UUID.randomUUID();
            Instant now = clock.instant();
            String token = buildToken("subject-audit-2", "test-issuer", "client-1", now.minusSeconds(30), now.plusSeconds(3600));

            ProviderTokenExchangeResult first = exchangeService.exchange(token, deviceIdentifier,
                    DevicePlatform.ANDROID, "Pixel 9", "mutation-audit-replay-1");
            ProviderTokenExchangeResult second = exchangeService.exchange(token, deviceIdentifier,
                    DevicePlatform.ANDROID, "Pixel 9", "mutation-audit-replay-1");

            assertThat(first.contextSelectionToken().hash()).isEqualTo(second.contextSelectionToken().hash());
            assertThat(countAuditLogs(userId, "PROVIDER_TOKEN_EXCHANGED")).as("replay must not double-audit").isEqualTo(1);
        }
    }

    @Nested
    class SessionActivationAtomicity {

        private String seedContextToken(UUID userId, UUID deviceId, Instant authenticatedAt) {
            String tokenValue = "ctx-" + UUID.randomUUID();
            String tokenHash = tokenHasher.hash(tokenValue);
            Instant issuedAt = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens " +
                            "(id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, deviceId, tokenHash,
                    java.sql.Timestamp.from(authenticatedAt), java.sql.Timestamp.from(issuedAt),
                    java.sql.Timestamp.from(issuedAt.plusSeconds(300)));
            return tokenValue;
        }

        private void grantPlatformAdmin(UUID userId) {
            adminJdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, java.sql.Timestamp.from(clock.instant()));
        }

        @Test
        void activatePlatformAdminCommitsFamilyTokenIdempotencyAndEscrowAtomically() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            grantPlatformAdmin(userId);
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            SessionActivationResult result = activationService.activatePlatformAdmin(tokenValue, "mutation-activation-1");

            assertThat(result.session().accessToken()).isNotNull();
            assertThat(countTable("refresh_token_families", "user_id", userId)).isEqualTo(1);
            assertThat(countTable("refresh_tokens", null, null)).isEqualTo(1);
            assertThat(countRowsIdempotency(userId, "PLATFORM_ADMIN_ACTIVATE")).isEqualTo(1);
            assertThat(countRowsEscrow(userId)).isEqualTo(1);
            assertThat(isContextTokenConsumed(tokenValue)).isTrue();
        }

        @Test
        void activatePlatformAdminRollsBackTokenConsumptionAndFamilyWhenEscrowStepFails() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            grantPlatformAdmin(userId);
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            AeadEscrowService throwingEscrow = new ThrowingEncryptEscrowService(escrowService);
            SessionActivationService failingService = new SessionActivationService(
                    repository, statusChecker, tokenHasher, throwingEscrow, secureRandom, clock,
                    transactionExecutor, settings, auditWriter, providerCommandService);

            assertThatThrownBy(() -> failingService.activatePlatformAdmin(tokenValue, "mutation-activation-2"))
                    .isInstanceOf(RuntimeException.class);

            assertThat(countTable("refresh_token_families", "user_id", userId))
                    .as("family insert must roll back").isEqualTo(0);
            assertThat(countRowsIdempotency(userId, "PLATFORM_ADMIN_ACTIVATE")).isEqualTo(0);
            assertThat(isContextTokenConsumed(tokenValue))
                    .as("context token consumption must roll back with the rest of the mutation").isFalse();
        }

        @Test
        void concurrentActivationWithSameContextTokenProducesSingleFamily() throws Exception {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            grantPlatformAdmin(userId);
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            try {
                List<Future<SessionActivationResult>> futures = List.of(
                        pool.submit(() -> { startLatch.await();
                            return activationService.activatePlatformAdmin(tokenValue, "mutation-activation-concurrent"); }),
                        pool.submit(() -> { startLatch.await();
                            return activationService.activatePlatformAdmin(tokenValue, "mutation-activation-concurrent"); }));
                startLatch.countDown();

                SessionActivationResult first = futures.get(0).get(30, TimeUnit.SECONDS);
                SessionActivationResult second = futures.get(1).get(30, TimeUnit.SECONDS);

                assertThat(first.session().accessToken().hash()).isEqualTo(second.session().accessToken().hash());
                assertThat(countTable("refresh_token_families", "user_id", userId))
                        .as("only one family despite the concurrent activation race").isEqualTo(1);
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void platformAdminActivationFailsClosedForRevokedDevice() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            grantPlatformAdmin(userId);
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));
            revokeTrustedDevice(deviceId);

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-revoked-device-1"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");

            assertThat(isContextTokenConsumed(tokenValue))
                    .as("context token must not be consumed by a rejected activation").isFalse();
            assertThat(countTable("refresh_token_families", "user_id", userId))
                    .as("no new family for a revoked device").isEqualTo(0);
            assertThat(countTable("refresh_tokens", null, null))
                    .as("no new refresh/access token pair").isEqualTo(0);
            assertThat(countRowsIdempotency(userId, "PLATFORM_ADMIN_ACTIVATE"))
                    .as("no idempotency key committed for a rejected activation").isEqualTo(0);
            assertThat(countRowsEscrow(userId)).as("no replay escrow committed").isEqualTo(0);
            assertThat(countAuditLogs(userId, "PLATFORM_ADMIN_ACTIVATED"))
                    .as("no success audit for a rejected activation").isEqualTo(0);
        }

        @Test
        void contextActivationFailsClosedForRevokedDevice() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));
            revokeTrustedDevice(deviceId);

            assertThatThrownBy(() -> activationService.activateContext(tokenValue, membershipId, "mutation-revoked-device-2"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");

            assertThat(isContextTokenConsumed(tokenValue))
                    .as("context token must not be consumed by a rejected activation").isFalse();
            assertThat(countTable("refresh_token_families", "user_id", userId))
                    .as("no new family for a revoked device").isEqualTo(0);
            assertThat(countRowsIdempotency(userId, "CONTEXT_ACTIVATE"))
                    .as("no idempotency key committed for a rejected activation").isEqualTo(0);
            assertThat(countRowsEscrow(userId)).as("no replay escrow committed").isEqualTo(0);
            assertThat(countAuditLogs(userId, "CONTEXT_ACTIVATED"))
                    .as("no success audit for a rejected activation").isEqualTo(0);
        }

        @Test
        void replayOfACompletedActivationFailsClosedOnceDeviceIsRevokedAfterTheFact() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            grantPlatformAdmin(userId);
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            SessionActivationResult first = activationService.activatePlatformAdmin(tokenValue, "mutation-replay-then-revoke-1");
            assertThat(first.session().accessToken()).isNotNull();

            // The device is revoked strictly AFTER the activation already completed and escrowed a
            // replay result. A client retry with the SAME idempotency key must not resurrect that
            // escrowed session — the whole point of revoking a device is to kill every session tied
            // to it, replay included.
            revokeTrustedDevice(deviceId);

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-replay-then-revoke-1"))
                    .as("replaying an idempotency key for a since-revoked device must not return the escrowed session")
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void rawSqlActivationPolicyDoesNotExposeARevokedDevice() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier);
            revokeTrustedDevice(deviceId);

            boolean visible = transactionExecutor.executeInIamAuthScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.PLATFORM_ADMIN_ACTIVATE,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext
                            .actorAndDevice(userId, deviceId),
                    () -> repository.findTrustedDeviceById(userId, deviceId).isPresent());

            assertThat(visible)
                    .as("trusted_devices_select_activation must hide a revoked device row even under the exact matching actor+device scope")
                    .isFalse();
        }
    }

    @Nested
    class SessionRefreshAtomicity {
        private record ReplayFixture(UUID userId, UUID familyId, String originalRefresh, String mutationKey) {}

        private ReplayFixture prepareCompletedRefreshReplay(String suffix) {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            adminJdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, java.sql.Timestamp.from(clock.instant()));
            String context = "ctx-reconcile-" + suffix + "-" + UUID.randomUUID();
            Instant now = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens (id,user_id,trusted_device_id,token_hash,authenticated_at,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), userId, deviceId, tokenHasher.hash(context), java.sql.Timestamp.from(now.minusSeconds(5)),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(300)));
            String key = "refresh-reconcile-" + suffix;
            SessionActivationResult activation = activationService.activatePlatformAdmin(context, "activation-" + suffix);
            String originalRefresh = activation.session().refreshToken().value();
            refreshService.refresh(originalRefresh, key);
            UUID familyId = adminJdbcTemplate.queryForObject("SELECT id FROM refresh_token_families WHERE user_id = ?", UUID.class, userId);
            return new ReplayFixture(userId, familyId, originalRefresh, key);
        }

        private void assertReplayReconciledAndZeroized(ReplayFixture fixture) {
            assertThatThrownBy(() -> refreshService.refresh(fixture.originalRefresh(), fixture.mutationKey()))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("SESSION_REVOKED");
            assertThat(adminJdbcTemplate.queryForObject("SELECT revoked_at IS NOT NULL FROM refresh_token_families WHERE id = ?",
                    Boolean.class, fixture.familyId())).isTrue();
            assertThat(adminJdbcTemplate.queryForObject("SELECT count(*) FROM refresh_tokens WHERE family_id = ? AND revoked_at IS NOT NULL",
                    Integer.class, fixture.familyId())).isEqualTo(2);
            Map<String, Object> escrow = adminJdbcTemplate.queryForMap("SELECT status::text, ciphertext, aead_key_reference, aead_nonce, aad_context, deleted_at "
                            + "FROM iam_auth_response_escrows WHERE idempotency_key_id = (SELECT id FROM idempotency_keys WHERE user_id = ? AND client_mutation_id = ?)",
                    fixture.userId(), fixture.mutationKey());
            assertThat(escrow.get("status")).isEqualTo("REVOKED");
            assertThat(escrow.get("ciphertext")).isNull();
            assertThat(escrow.get("aead_key_reference")).isNull();
            assertThat(escrow.get("aead_nonce")).isNull();
            assertThat(escrow.get("aad_context")).isNull();
            assertThat(escrow.get("deleted_at")).isNotNull();
            assertThat(countAuditLogs(fixture.userId(), "SESSION_REFRESH_REPLAY_RECONCILED")).isEqualTo(1);
            assertThatThrownBy(() -> refreshService.refresh(fixture.originalRefresh(), fixture.mutationKey()))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("SESSION_REVOKED");
            assertThat(countAuditLogs(fixture.userId(), "SESSION_REFRESH_REPLAY_RECONCILED")).isEqualTo(1);
        }

        private SessionActivationResult activateOrganizationSession(UUID userId, UUID deviceId, UUID membershipId, String mutation) {
            String context = "ctx-logout-negative-" + UUID.randomUUID();
            Instant now = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens (id,user_id,trusted_device_id,token_hash,authenticated_at,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), userId, deviceId, tokenHasher.hash(context), java.sql.Timestamp.from(now.minusSeconds(5)),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(300)));
            return activationService.activateContext(context, membershipId, mutation);
        }

        @Test
        void refreshReplayAndReuseAreAtomicForGlobalFamily() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            adminJdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, java.sql.Timestamp.from(clock.instant()));
            String context = "ctx-refresh-" + UUID.randomUUID();
            Instant now = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens (id,user_id,trusted_device_id,token_hash,authenticated_at,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), userId, deviceId, tokenHasher.hash(context), java.sql.Timestamp.from(now.minusSeconds(5)),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(300)));
            SessionActivationResult activation = activationService.activatePlatformAdmin(context, "activation-refresh");
            var first = refreshService.refresh(activation.session().refreshToken().value(), "refresh-retry-1");
            var replay = refreshService.refresh(activation.session().refreshToken().value(), "refresh-retry-1");
            assertThat(replay.session().refreshToken().value()).isEqualTo(first.session().refreshToken().value());
            assertThat(countTable("refresh_tokens", null, null)).isEqualTo(2);
            assertThatThrownBy(() -> refreshService.refresh(activation.session().refreshToken().value(), "refresh-reuse-2"))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("SESSION_REVOKED");
            assertThat(adminJdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token_families WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
        }

        @Test
        void terminalReplayEscrowCommitsOneReconciliationBeforeReturningFailClosed() {
            ReplayFixture fixture = prepareCompletedRefreshReplay("expired");
            // Keep the escrow READY and secret-bearing; only its TTL has elapsed.  The service,
            // not fixture SQL, must perform the terminal transition and zeroization.
            adminJdbcTemplate.update("UPDATE iam_auth_response_escrows SET created_at = transaction_timestamp() - interval '11 minutes', "
                            + "expires_at = transaction_timestamp() - interval '1 minute' "
                            + "WHERE idempotency_key_id = (SELECT id FROM idempotency_keys WHERE user_id = ? AND client_mutation_id = ?)",
                    fixture.userId(), fixture.mutationKey());
            assertReplayReconciledAndZeroized(fixture);
        }

        @Test
        void decryptFailureOnReadyEscrowIsZeroizedByTheService() {
            ReplayFixture fixture = prepareCompletedRefreshReplay("decrypt");
            adminJdbcTemplate.update("UPDATE iam_auth_response_escrows SET aad_context = 'tampered-aad' "
                            + "WHERE idempotency_key_id = (SELECT id FROM idempotency_keys WHERE user_id = ? AND client_mutation_id = ?)",
                    fixture.userId(), fixture.mutationKey());
            assertReplayReconciledAndZeroized(fixture);
        }

        @Test
        void alreadyRevokedFamilyStillZeroizesReadyEscrowAndAuditsOnce() {
            ReplayFixture fixture = prepareCompletedRefreshReplay("pre-revoked");
            adminJdbcTemplate.update("UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ?", fixture.familyId());
            assertReplayReconciledAndZeroized(fixture);
        }

        @Test
        void logoutReplayIsACommittedNoOpAndAuditsDeviceMembershipAndRequestId() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String context = "ctx-logout-" + UUID.randomUUID();
            Instant now = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens (id,user_id,trusted_device_id,token_hash,authenticated_at,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                    UUID.randomUUID(), userId, deviceId, tokenHasher.hash(context), java.sql.Timestamp.from(now.minusSeconds(5)),
                    java.sql.Timestamp.from(now), java.sql.Timestamp.from(now.plusSeconds(300)));
            SessionActivationResult activation = activationService.activateContext(context, membershipId, "activation-logout");
            String access = activation.session().accessToken().value();
            String refresh = activation.session().refreshToken().value();
            MDC.put("requestId", "request-logout-proof");
            try {
                refreshService.logout(access, refresh, "logout-replay");
                int auditsAfterFirst = countAuditLogs(userId, "SESSION_LOGGED_OUT");
                refreshService.logout(access, refresh, "logout-replay");
                assertThat(countAuditLogs(userId, "SESSION_LOGGED_OUT")).isEqualTo(auditsAfterFirst).isEqualTo(1);
            } finally {
                MDC.remove("requestId");
            }
            assertThat(adminJdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token_families WHERE user_id = ? AND revoked_at IS NOT NULL",
                    Integer.class, userId)).isEqualTo(1);
            Map<String, Object> audit = adminJdbcTemplate.queryForMap("SELECT request_id, event_metadata FROM audit_logs WHERE actor_user_id = ? AND action_type = 'SESSION_LOGGED_OUT'",
                    userId);
            assertThat(audit.get("request_id")).isEqualTo("request-logout-proof");
            assertThat(audit.get("event_metadata").toString()).contains(deviceId.toString(), membershipId.toString());
        }

        @Test
        void logoutSameKeyWithDifferentFingerprintIsRejectedWithoutSecondMutation() {
            UUID user = seedUser(); UUID org = seedOrganization(); UUID membership = seedActiveMembership(org, user, "TEACHER");
            SessionActivationResult first = activateOrganizationSession(user, seedTrustedDevice(user, UUID.randomUUID()), membership, "a1");
            SessionActivationResult second = activateOrganizationSession(user, seedTrustedDevice(user, UUID.randomUUID()), membership, "a2");
            refreshService.logout(first.session().accessToken().value(), first.session().refreshToken().value(), "same-key");
            assertThatThrownBy(() -> refreshService.logout(second.session().accessToken().value(), second.session().refreshToken().value(), "same-key"))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("IDEMPOTENCY_KEY_REUSED");
            assertThat(countAuditLogs(user, "SESSION_LOGGED_OUT")).isEqualTo(1);
            assertThat(countRowsIdempotency(user, "SESSION_LOGOUT")).isEqualTo(1);
            assertThat(adminJdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token_families WHERE user_id=? AND revoked_at IS NULL", Integer.class, user)).isEqualTo(1);
        }

        @Test
        void logoutDifferentKeyOnTerminalFamilyIsRejectedWithoutSecondAudit() {
            UUID user = seedUser(); UUID org = seedOrganization(); UUID membership = seedActiveMembership(org, user, "TEACHER");
            SessionActivationResult session = activateOrganizationSession(user, seedTrustedDevice(user, UUID.randomUUID()), membership, "terminal");
            refreshService.logout(session.session().accessToken().value(), session.session().refreshToken().value(), "first-key");
            assertThatThrownBy(() -> refreshService.logout(session.session().accessToken().value(), session.session().refreshToken().value(), "second-key"))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("SESSION_REVOKED");
            assertThat(countAuditLogs(user, "SESSION_LOGGED_OUT")).isEqualTo(1);
            assertThat(countRowsIdempotency(user, "SESSION_LOGOUT")).isEqualTo(1);
        }

        @Test
        void logoutRejectsCrossActorAccessRefreshPairWithoutAnyPersistence() {
            UUID org = seedOrganization(); UUID firstUser = seedUser(); UUID secondUser = seedUser();
            SessionActivationResult first = activateOrganizationSession(firstUser, seedTrustedDevice(firstUser, UUID.randomUUID()), seedActiveMembership(org, firstUser, "TEACHER"), "cross-a");
            SessionActivationResult second = activateOrganizationSession(secondUser, seedTrustedDevice(secondUser, UUID.randomUUID()), seedActiveMembership(org, secondUser, "TEACHER"), "cross-b");
            assertThatThrownBy(() -> refreshService.logout(second.session().accessToken().value(), first.session().refreshToken().value(), "cross-key"))
                    .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("UNAUTHENTICATED");
            assertThat(countAuditLogs(firstUser, "SESSION_LOGGED_OUT")).isZero(); assertThat(countRowsIdempotency(firstUser, "SESSION_LOGOUT")).isZero();
            assertThat(adminJdbcTemplate.queryForObject("SELECT count(*) FROM refresh_token_families WHERE revoked_at IS NOT NULL", Integer.class)).isZero();
        }
    }

    @Nested
    class SessionInfoAtomicity {

        private String seedSession(UUID userId, UUID deviceId, UUID membershipId, Integer issuedAtSessionGeneration,
                                   Instant authenticatedAt) {
            UUID familyId = UUID.randomUUID();
            adminJdbcTemplate.update(
                    "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, organization_membership_id, authenticated_at, issued_at_session_generation, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    familyId, userId, deviceId, membershipId,
                    java.sql.Timestamp.from(authenticatedAt), issuedAtSessionGeneration,
                    java.sql.Timestamp.from(clock.instant()));
            String accessTokenValue = "acc-" + UUID.randomUUID();
            String refreshTokenValue = "ref-" + UUID.randomUUID();
            adminJdbcTemplate.update(
                    "INSERT INTO refresh_tokens (id, family_id, token_hash, access_token_hash, access_expires_at, issued_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), familyId, tokenHasher.hash(refreshTokenValue), tokenHasher.hash(accessTokenValue),
                    java.sql.Timestamp.from(clock.instant().plusSeconds(600)), java.sql.Timestamp.from(clock.instant()),
                    java.sql.Timestamp.from(clock.instant().plusSeconds(2_592_000)));
            return accessTokenValue;
        }

        private void grantPlatformAdmin(UUID userId) {
            adminJdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, java.sql.Timestamp.from(clock.instant()));
        }

        @Test
        void validOrganizationSessionResolves() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));

            SessionInfoResult result = sessionInfoService.resolveSession(accessToken);

            assertThat(result.organizationMembership()).isNotNull();
            assertThat(result.organizationMembership().id()).isEqualTo(membershipId);
            assertThat(result.platformAdministrator()).isNull();
        }

        @Test
        void revokedDeviceFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));
            revokeTrustedDevice(deviceId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void userReauthenticationThresholdRaisedFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            Instant authenticatedAt = clock.instant().minusSeconds(10);
            String accessToken = seedSession(userId, deviceId, membershipId, 1, authenticatedAt);
            // Raise the user's reauth threshold to strictly after the session's authenticatedAt.
            adminJdbcTemplate.update("UPDATE users SET reauthentication_required_after = ? WHERE id = ?",
                    java.sql.Timestamp.from(clock.instant().plusSeconds(3600)), userId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void inactiveMembershipFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            // status is immutable post-insert (trg_org_membership_revoke) — seed SUSPENDED directly.
            UUID membershipId = seedMembership(orgId, userId, "TEACHER", "SUSPENDED");
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void suspendedOrganizationFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));
            adminJdbcTemplate.update("UPDATE organizations SET status = 'SUSPENDED' WHERE id = ?", orgId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void noActiveRolesFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));
            adminJdbcTemplate.update(
                    "UPDATE organization_membership_roles SET revoked_at = transaction_timestamp() WHERE organization_membership_id = ?",
                    membershipId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void sessionGenerationMismatchFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            // Session was issued at generation 1, but the membership has since been bumped — e.g. by
            // OrganizationLifecycleService's own device-wipe/session-revoke barrier update, which
            // increments session_generation and raises reauthentication_required_after together
            // (trg_org_membership_revoke requires both fields to change atomically on every update).
            String accessToken = seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));
            adminJdbcTemplate.update(
                    "UPDATE organization_memberships SET session_generation = session_generation + 1, " +
                            "reauthentication_required_after = transaction_timestamp() WHERE id = ?", membershipId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void membershipReauthenticationThresholdRaisedFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            // trg_org_membership_revoke couples session_generation and reauthentication_required_after
            // on every update, so isolating "only the reauth threshold changed" from "generation
            // mismatch" means seeding the session AT the post-bump generation — the generation check
            // then passes and only the reauth-threshold check can fail.
            adminJdbcTemplate.update(
                    "UPDATE organization_memberships SET session_generation = session_generation + 1, " +
                            "reauthentication_required_after = transaction_timestamp() WHERE id = ?", membershipId);
            int bumpedGeneration = adminJdbcTemplate.queryForObject(
                    "SELECT session_generation FROM organization_memberships WHERE id = ?", Integer.class, membershipId);
            Instant reauthThreshold = adminJdbcTemplate.queryForObject(
                    "SELECT reauthentication_required_after FROM organization_memberships WHERE id = ?", Instant.class, membershipId);
            String accessToken = seedSession(userId, deviceId, membershipId, bumpedGeneration,
                    reauthThreshold.minusSeconds(60));

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void validGlobalPlatformAdminSessionResolves() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            grantPlatformAdmin(userId);
            String accessToken = seedSession(userId, deviceId, null, null, clock.instant().minusSeconds(10));

            SessionInfoResult result = sessionInfoService.resolveSession(accessToken);

            assertThat(result.platformAdministrator()).isNotNull();
            assertThat(result.platformAdministrator().status()).isEqualTo("ACTIVE");
            assertThat(result.organizationMembership()).isNull();
        }

        @Test
        void revokedPlatformAdministratorFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            grantPlatformAdmin(userId);
            String accessToken = seedSession(userId, deviceId, null, null, clock.instant().minusSeconds(10));
            adminJdbcTemplate.update(
                    "UPDATE platform_administrators SET revoked_at = transaction_timestamp() WHERE user_id = ?", userId);

            assertThatThrownBy(() -> sessionInfoService.resolveSession(accessToken))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void anotherActorsDeviceFamilyAndOrganizationAreNotVisibleUnderRls() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            seedSession(userId, deviceId, membershipId, 1, clock.instant().minusSeconds(10));

            UUID otherUserId = seedUser();
            UUID otherDeviceId = seedTrustedDevice(otherUserId, UUID.randomUUID());
            UUID otherOrgId = seedOrganization();
            UUID otherMembershipId = seedActiveMembership(otherOrgId, otherUserId, "TEACHER");
            String otherAccessToken = seedSession(otherUserId, otherDeviceId, otherMembershipId, 1, clock.instant().minusSeconds(10));

            // Resolving actor B's own session must only ever see actor B's own device/membership —
            // never actor A's rows, even though both exist in the same database at the same time.
            SessionInfoResult result = sessionInfoService.resolveSession(otherAccessToken);

            assertThat(result.device().id()).isNotEqualTo(deviceId);
            assertThat(result.organizationMembership().id()).isEqualTo(otherMembershipId);
            assertThat(result.organizationMembership().id()).isNotEqualTo(membershipId);
        }
    }

    @Nested
    class ContextSelectionListAtomicity {

        private String seedContextToken(UUID userId, UUID deviceId, Instant authenticatedAt) {
            String tokenValue = "ctx-" + UUID.randomUUID();
            String tokenHash = tokenHasher.hash(tokenValue);
            Instant issuedAt = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens " +
                            "(id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, deviceId, tokenHash,
                    java.sql.Timestamp.from(authenticatedAt), java.sql.Timestamp.from(issuedAt),
                    java.sql.Timestamp.from(issuedAt.plusSeconds(300)));
            return tokenValue;
        }

        @Test
        void activeDeviceListsOnlyTheActorsOwnSummaries() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            UUID membershipId = seedActiveMembership(orgId, userId, "TEACHER");
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            UUID otherUserId = seedUser();
            UUID otherDeviceId = seedTrustedDevice(otherUserId, UUID.randomUUID());
            UUID otherOrgId = seedOrganization();
            seedActiveMembership(otherOrgId, otherUserId, "TEACHER");

            List<ContextSelectionSummary> summaries = contextSelectionService.listContextSelections(tokenValue);

            assertThat(summaries).hasSize(1);
            assertThat(summaries.get(0).id()).isEqualTo(membershipId);
        }

        @Test
        void deviceRevokedAfterTokenIssuanceFailsClosed() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            UUID orgId = seedOrganization();
            seedActiveMembership(orgId, userId, "TEACHER");
            String tokenValue = seedContextToken(userId, deviceId, clock.instant().minusSeconds(10));

            // The context token was issued while the device was still active; revoking it AFTER
            // issuance must still close the list read for the token's remaining TTL.
            revokeTrustedDevice(deviceId);

            assertThatThrownBy(() -> contextSelectionService.listContextSelections(tokenValue))
                    .as("a revoked device must fail closed before any organization summary is queried")
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");
        }

        @Test
        void rawSqlContextSelectionListPolicyDoesNotExposeARevokedDevice() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());
            revokeTrustedDevice(deviceId);

            boolean visible = transactionExecutor.executeInIamAuthScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.CONTEXT_SELECTION_LIST,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext
                            .actorAndDevice(userId, deviceId),
                    () -> repository.findTrustedDeviceById(userId, deviceId).isPresent());

            assertThat(visible)
                    .as("trusted_devices_select_context_list must hide a revoked device row even under the exact matching actor+device scope")
                    .isFalse();
        }

        @Test
        void anotherActorsDeviceIsNotVisibleUnderContextSelectionListScope() {
            UUID userId = seedUser();
            UUID deviceId = seedTrustedDevice(userId, UUID.randomUUID());

            UUID otherUserId = seedUser();
            UUID otherDeviceId = seedTrustedDevice(otherUserId, UUID.randomUUID());

            boolean crossActorVisible = transactionExecutor.executeInIamAuthScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.CONTEXT_SELECTION_LIST,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext
                            .actorAndDevice(userId, otherDeviceId),
                    () -> repository.findTrustedDeviceById(userId, otherDeviceId).isPresent());
            assertThat(crossActorVisible)
                    .as("a device belonging to a different actor must not be visible even if the id is guessed correctly")
                    .isFalse();

            boolean wrongCurrentDeviceGuc = transactionExecutor.executeInIamAuthScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.CONTEXT_SELECTION_LIST,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext
                            .actorAndDevice(userId, otherDeviceId),
                    () -> repository.findTrustedDeviceById(userId, deviceId).isPresent());
            assertThat(wrongCurrentDeviceGuc)
                    .as("app.iam_current_trusted_device_id must match the exact device id being read, not just the actor")
                    .isFalse();
        }
    }

    @Nested
    class ProviderRevocationInvariants {

        private UUID seedTrustedDevice(UUID userId, UUID deviceIdentifier, String subject) {
            UUID deviceId = UUID.randomUUID();
            adminJdbcTemplate.update("INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                    "VALUES (?, ?, ?, 'ANDROID'::device_platform_enum)", deviceId, userId, deviceIdentifier);
            adminJdbcTemplate.update("INSERT INTO user_identities (id, user_id, issuer, subject) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), userId, "test-issuer", subject);
            return deviceId;
        }

        private String seedContextToken(UUID userId, UUID deviceId, Instant authenticatedAt) {
            String tokenValue = "ctx-" + UUID.randomUUID();
            String tokenHash = tokenHasher.hash(tokenValue);
            Instant issuedAt = clock.instant();
            adminJdbcTemplate.update("INSERT INTO context_selection_tokens " +
                            "(id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    UUID.randomUUID(), userId, deviceId, tokenHash,
                    java.sql.Timestamp.from(authenticatedAt), java.sql.Timestamp.from(issuedAt),
                    java.sql.Timestamp.from(issuedAt.plusSeconds(300)));
            return tokenValue;
        }

        private void grantPlatformAdmin(UUID userId) {
            adminJdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                    UUID.randomUUID(), userId, java.sql.Timestamp.from(clock.instant()));
        }

        private UUID seedActiveFamily(UUID userId, UUID trustedDeviceId) {
            UUID familyId = UUID.randomUUID();
            adminJdbcTemplate.update("INSERT INTO refresh_token_families " +
                            "(id, user_id, trusted_device_id, authenticated_at, created_at) VALUES (?, ?, ?, ?, ?)",
                    familyId, userId, trustedDeviceId,
                    java.sql.Timestamp.from(clock.instant().minusSeconds(3600)),
                    java.sql.Timestamp.from(clock.instant().minusSeconds(3600)));
            return familyId;
        }

        private boolean isFamilyRevoked(UUID familyId) {
            Boolean revoked = adminJdbcTemplate.queryForObject(
                    "SELECT revoked_at IS NOT NULL FROM refresh_token_families WHERE id = ?",
                    Boolean.class, familyId);
            return Boolean.TRUE.equals(revoked);
        }

        @Test
        void disabledProviderRevokesOnlyTargetActorFamiliesNotOtherUsers() {
            UUID userA = seedUser();
            UUID deviceIdentifierA = UUID.randomUUID();
            UUID trustedDeviceA = seedTrustedDevice(userA, deviceIdentifierA, "subject-disabled-a");
            grantPlatformAdmin(userA);
            UUID familyA = seedActiveFamily(userA, trustedDeviceA);

            UUID userB = seedUser();
            UUID deviceIdentifierB = UUID.randomUUID();
            UUID trustedDeviceB = seedTrustedDevice(userB, deviceIdentifierB, "subject-other-b");
            grantPlatformAdmin(userB);
            UUID familyB = seedActiveFamily(userB, trustedDeviceB);

            statusChecker.setStatus("subject-disabled-a", ProviderUserStatus.DISABLED);
            String tokenValue = seedContextToken(userA, trustedDeviceA, clock.instant().minusSeconds(10));

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-revoke-1"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");

            assertThat(isFamilyRevoked(familyA)).as("target actor's own family must be revoked").isTrue();
            assertThat(isFamilyRevoked(familyB)).as("another user's family must not be touched").isFalse();
        }

        @Test
        void disabledProviderEnqueuesRealUserDisableProviderCommand() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID trustedDeviceId = seedTrustedDevice(userId, deviceIdentifier, "subject-enqueue-disable");
            grantPlatformAdmin(userId);
            seedActiveFamily(userId, trustedDeviceId);
            statusChecker.setStatus("subject-enqueue-disable", ProviderUserStatus.DISABLED);
            String tokenValue = seedContextToken(userId, trustedDeviceId, clock.instant().minusSeconds(10));

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-enqueue-disable-1"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");

            Integer pendingUserDisableCount = adminJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM iam_provider_commands WHERE command_type = 'USER_DISABLE' " +
                            "AND status = 'PENDING' AND target_identity_id IN " +
                            "(SELECT id FROM user_identities WHERE user_id = ? AND subject = 'subject-enqueue-disable')",
                    Integer.class, userId);
            assertThat(pendingUserDisableCount)
                    .as("a real USER_DISABLE provider command must be enqueued for the scheduler to pick up")
                    .isEqualTo(1);
        }

        @Test
        void revokedProviderEnqueuesRealUserLogoutProviderCommand() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID trustedDeviceId = seedTrustedDevice(userId, deviceIdentifier, "subject-enqueue-logout");
            grantPlatformAdmin(userId);
            seedActiveFamily(userId, trustedDeviceId);
            statusChecker.setStatus("subject-enqueue-logout", ProviderUserStatus.REVOKED);
            String tokenValue = seedContextToken(userId, trustedDeviceId, clock.instant().minusSeconds(10));

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-enqueue-logout-1"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("SESSION_REVOKED");

            Integer pendingUserLogoutCount = adminJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM iam_provider_commands WHERE command_type = 'USER_LOGOUT' " +
                            "AND status = 'PENDING' AND target_identity_id IN " +
                            "(SELECT id FROM user_identities WHERE user_id = ? AND subject = 'subject-enqueue-logout')",
                    Integer.class, userId);
            assertThat(pendingUserLogoutCount)
                    .as("a real USER_LOGOUT provider command must be enqueued for the scheduler to pick up")
                    .isEqualTo(1);
        }

        @Test
        void unknownProviderStatusBlocksActivationWithoutAnyMutationAndAuditsExactlyOnce() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID trustedDeviceId = seedTrustedDevice(userId, deviceIdentifier, "subject-unknown-status");
            grantPlatformAdmin(userId);
            statusChecker.setStatus("subject-unknown-status", ProviderUserStatus.UNKNOWN);
            String tokenValue = seedContextToken(userId, trustedDeviceId, clock.instant().minusSeconds(10));

            assertThatThrownBy(() -> activationService.activatePlatformAdmin(tokenValue, "mutation-unknown-status-1"))
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("PROVIDER_UNAVAILABLE");

            assertThat(countTable("refresh_token_families", "user_id", userId))
                    .as("no session may be established on an indeterminate provider verdict").isEqualTo(0);
            assertThat(isContextTokenConsumed(tokenValue))
                    .as("the context token must remain usable for a legitimate retry once the provider recovers")
                    .isFalse();
            assertThat(countAuditLogs(userId, "IAM_PROVIDER_STATUS_CHECK_BLOCKED"))
                    .as("the blocked security event must still be visible").isEqualTo(1);
        }

        @Test
        void alreadyRevokedFamilyIsNotVisibleToTheActorRevokePolicyAnymore() {
            UUID userId = seedUser();
            UUID deviceIdentifier = UUID.randomUUID();
            UUID trustedDeviceId = seedTrustedDevice(userId, deviceIdentifier, "subject-prerevoked");
            UUID familyId = seedActiveFamily(userId, trustedDeviceId);
            adminJdbcTemplate.update(
                    "UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ?", familyId);
            Instant firstRevokedAt = adminJdbcTemplate.queryForObject(
                    "SELECT revoked_at FROM refresh_token_families WHERE id = ?", Instant.class, familyId);

            // Exercise the exact RLS policy this round hardened: an actor-scoped revoke attempt
            // against an already-revoked row (revoked_at IS NOT NULL) must affect zero rows,
            // proving refresh_token_families_update_actor_revoke's USING clause excludes it. Runs
            // through the real transactionExecutor so SET LOCAL and the UPDATE share one actual
            // transaction (a bare ConnectionCallback would autocommit each statement separately
            // and lose the SET LOCAL before the UPDATE ever sees it).
            int affected = transactionExecutor.executeInIamAuthScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.PLATFORM_ADMIN_ACTIVATE,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext.actorOnly(userId),
                    () -> {
                        var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(
                                ((org.springframework.jdbc.core.JdbcTemplate) runtimeJdbcTemplate).getDataSource());
                        try (var stmt = connection.prepareStatement(
                                "UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ?")) {
                            stmt.setObject(1, familyId);
                            return stmt.executeUpdate();
                        } catch (java.sql.SQLException e) {
                            throw new RuntimeException(e);
                        } finally {
                            org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection,
                                    ((org.springframework.jdbc.core.JdbcTemplate) runtimeJdbcTemplate).getDataSource());
                        }
                    });
            assertThat(affected).as("already-revoked row must not be re-writable").isEqualTo(0);

            Instant afterAttempt = adminJdbcTemplate.queryForObject(
                    "SELECT revoked_at FROM refresh_token_families WHERE id = ?", Instant.class, familyId);
            assertThat(afterAttempt).isEqualTo(firstRevokedAt);
        }
    }

    @Nested
    class ProviderCommandClaimRace {

        // Round 2: ProviderCommandService now runs through the real IamTransactionExecutor and
        // the real iam_runtime role, not the migration-owner connection — these tests exercise
        // the actual RLS-gated GLOBAL/PROVIDER_COMMAND_CLAIM and per-type-completion policies,
        // not just the CAS SQL in isolation.
        private record IdentityRef(UUID userId, UUID identityId) {}

        private IdentityRef seedIdentityForCommand(String subject) {
            UUID userId = seedUser();
            UUID identityId = UUID.randomUUID();
            adminJdbcTemplate.update("INSERT INTO user_identities (id, user_id, issuer, subject) VALUES (?, ?, ?, ?)",
                    identityId, userId, "issuer", subject);
            return new IdentityRef(userId, identityId);
        }

        @Test
        void concurrentClaimHasExactlyOneWinner() throws Exception {
            IdentityRef identity = seedIdentityForCommand("subject-claim-race");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-claim-race-1");
            assertThat(created.status()).isEqualTo(ProviderCommandStatus.PENDING);

            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            AtomicReference<Exception> workerAError = new AtomicReference<>();
            AtomicReference<Exception> workerBError = new AtomicReference<>();
            try {
                Future<ProviderCommandResult> workerA = pool.submit(() -> {
                    startLatch.await();
                    try {
                        return providerCommandService.claimCommand(created.id(), "worker-A");
                    } catch (Exception e) {
                        workerAError.set(e);
                        return null;
                    }
                });
                Future<ProviderCommandResult> workerB = pool.submit(() -> {
                    startLatch.await();
                    try {
                        return providerCommandService.claimCommand(created.id(), "worker-B");
                    } catch (Exception e) {
                        workerBError.set(e);
                        return null;
                    }
                });
                startLatch.countDown();
                ProviderCommandResult resultA = workerA.get(30, TimeUnit.SECONDS);
                ProviderCommandResult resultB = workerB.get(30, TimeUnit.SECONDS);

                long successes = java.util.stream.Stream.of(resultA, resultB).filter(r -> r != null).count();
                long failures = java.util.stream.Stream.of(workerAError.get(), workerBError.get()).filter(e -> e != null).count();

                assertThat(successes).as("exactly one worker must win the claim").isEqualTo(1);
                assertThat(failures).as("exactly one worker must lose with STATE_CONFLICT").isEqualTo(1);
                Exception loserError = workerAError.get() != null ? workerAError.get() : workerBError.get();
                assertThat(loserError).isInstanceOf(IamException.class);
                assertThat(((IamException) loserError).errorCode()).isEqualTo("STATE_CONFLICT");
            } finally {
                pool.shutdownNow();
            }
        }

        @Test
        void workerProcessOneRunsTheRealCreateClaimProviderCallCompleteChainUnderRealRls() {
            IdentityRef identity = seedIdentityForCommand("subject-worker-chain");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-worker-chain-1");

            AtomicReference<String> capturedSubject = new AtomicReference<>();
            ProviderCommandWorker worker = new ProviderCommandWorker(providerCommandService, repository,
                    transactionExecutor, (commandType, subject, issuer) -> {
                        capturedSubject.set(subject);
                        return ProviderCommandOutcome.ofSuccess();
                    });

            ProviderCommandResult completed = worker.processOne(created.id(), "worker-real-chain");

            assertThat(completed.status()).isEqualTo(ProviderCommandStatus.COMPLETED);
            assertThat(capturedSubject.get()).as("worker adapter must see the resolved provider subject")
                    .isEqualTo("subject-worker-chain");
            Integer auditCount = adminJdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action_type = 'PROVIDER_COMMAND_COMPLETED' " +
                            "AND target_entity_id = ?", Integer.class, created.id());
            assertThat(auditCount).as("real provider-command completion must be audited").isEqualTo(1);
        }

        @Test
        void createClaimCompleteEndToEndChainSucceedsUnderRealRls() {
            IdentityRef identity = seedIdentityForCommand("subject-e2e-chain");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-e2e-1");

            ProviderCommandResult claimed = providerCommandService.claimCommand(created.id(), "worker-e2e");
            assertThat(claimed.status()).isEqualTo(ProviderCommandStatus.CLAIMED);

            ProviderCommandResult completed = providerCommandService.completeCommand(
                    claimed.id(), claimed.commandType(), claimed.targetUserId(),
                    claimed.organizationId(), "worker-e2e", claimed.fencingToken(), true, null);

            assertThat(completed.status()).isEqualTo(ProviderCommandStatus.COMPLETED);
        }

        @Test
        void staleFencingTokenCannotCompleteAfterReclaim() {
            IdentityRef identity = seedIdentityForCommand("subject-stale-fencing");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-stale-1");
            ProviderCommandResult firstClaim = providerCommandService.claimCommand(created.id(), "worker-1");
            // Simulate the first worker's lease already expiring, then a second worker reclaiming.
            adminJdbcTemplate.update(
                    "ALTER TABLE iam_provider_commands DISABLE TRIGGER trg_ipc_state");
            try {
                adminJdbcTemplate.update("UPDATE iam_provider_commands SET lease_expires_at = ? WHERE id = ?",
                        java.sql.Timestamp.from(clock.instant().minusSeconds(1)), created.id());
            } finally {
                adminJdbcTemplate.update("ALTER TABLE iam_provider_commands ENABLE TRIGGER trg_ipc_state");
            }
            providerCommandService.claimCommand(created.id(), "worker-2");

            assertThatThrownBy(() -> providerCommandService.completeCommand(
                    created.id(), ProviderCommandType.USER_DISABLE, null, null,
                    "worker-1", firstClaim.fencingToken(), true, null))
                    .as("the original worker's stale fencing token must not complete the reclaimed command")
                    .isInstanceOf(IamException.class)
                    .extracting("errorCode").isEqualTo("STATE_CONFLICT");
        }

        @Test
        void claimWithoutAnyTransactionScopeIsRejectedByRls() {
            IdentityRef identity = seedIdentityForCommand("subject-no-context");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-no-context-1");

            // Bypass the executor entirely: a bare runtimeJdbcTemplate call has no
            // app.iam_operation_scope/app.iam_worker_id/app.iam_fencing_token set at all, so RLS
            // must reject the claim by default-deny rather than exposing the row.
            int affected = runtimeJdbcTemplate.update(
                    "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = attempt_count + 1, " +
                            "fencing_token = fencing_token + 1, lease_owner = 'rogue-worker', " +
                            "lease_expires_at = ?, next_attempt_at = ? WHERE id = ? AND status = 'PENDING'",
                    java.sql.Timestamp.from(clock.instant().plusSeconds(300)),
                    java.sql.Timestamp.from(clock.instant()), created.id());

            assertThat(affected).as("claim without any RLS context must affect zero rows").isEqualTo(0);
        }

        @Test
        void commandForOneIdentityIsNotVisibleUnderAnotherIdentitysScope() {
            IdentityRef identityA = seedIdentityForCommand("subject-cross-a");
            IdentityRef identityB = seedIdentityForCommand("subject-cross-b");
            ProviderCommandResult createdForA = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identityA.identityId(), identityA.userId(),
                    null, "lookup-hash-a", "payload-fp-a", null, null, "idem-cross-1");

            // Reading command A's row under B's GLOBAL/USER_DISABLE target-identity scope must
            // return nothing — iam_provider_commands_select_global keys visibility on
            // app.iam_target_user_id matching via the row's own target_identity_id join, so a
            // request scoped to a different identity/user never sees this row.
            boolean visibleUnderB = transactionExecutor.executeInGlobalScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.USER_DISABLE,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext
                            .actorOnly(null).withTargetIdentity(identityB.identityId(), identityB.userId()),
                    () -> repository.findProviderCommandById(createdForA.id()).isPresent());

            assertThat(visibleUnderB).as("command scoped to identity A must not leak under identity B's scope").isFalse();
        }

        @Test
        void sameTypeSiblingTerminalCommandIsNotVisibleUnderAnotherCommandsCompleteScope() {
            // V9 residual fix: iam_provider_commands_select_complete_global (V6) originally let a
            // worker holding GLOBAL/<lifecycle-code> see EVERY terminal row of that command_type; V9
            // narrowed it to require id = app.iam_target_provider_command_id. Neither identity here
            // matches the other (so iam_provider_commands_select_global cannot be the one making a
            // row visible) — only the complete-scope policy is in play.
            IdentityRef identityA = seedIdentityForCommand("subject-sibling-a");
            IdentityRef identityB = seedIdentityForCommand("subject-sibling-b");
            ProviderCommandResult createdA = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identityA.identityId(), identityA.userId(),
                    null, "lookup-hash-a", "payload-fp-a", null, null, "idem-sibling-a-1");
            ProviderCommandResult createdB = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identityB.identityId(), identityB.userId(),
                    null, "lookup-hash-b", "payload-fp-b", null, null, "idem-sibling-b-1");

            ProviderCommandResult claimedA = providerCommandService.claimCommand(createdA.id(), "worker-sibling-a");
            providerCommandService.completeCommand(claimedA.id(), claimedA.commandType(), null, null,
                    "worker-sibling-a", claimedA.fencingToken(), true, null);
            ProviderCommandResult claimedB = providerCommandService.claimCommand(createdB.id(), "worker-sibling-b");
            providerCommandService.completeCommand(claimedB.id(), claimedB.commandType(), null, null,
                    "worker-sibling-b", claimedB.fencingToken(), true, null);

            boolean[] visibility = transactionExecutor.executeInGlobalScope(
                    org.mepcity.kursplatform.iam.domain.OperationCode.USER_DISABLE,
                    org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext.actorOnly(null),
                    () -> {
                        runtimeJdbcTemplate.queryForObject(
                                "SELECT set_config('app.iam_target_provider_command_id', ?, true)",
                                Object.class, createdA.id().toString());
                        boolean aVisible = repository.findProviderCommandById(createdA.id()).isPresent();
                        boolean bVisible = repository.findProviderCommandById(createdB.id()).isPresent();
                        return new boolean[]{aVisible, bVisible};
                    });

            assertThat(visibility[0])
                    .as("the command matching app.iam_target_provider_command_id must remain visible")
                    .isTrue();
            assertThat(visibility[1])
                    .as("a sibling terminal command of the SAME command_type must not leak via the complete-scope SELECT policy")
                    .isFalse();
        }

        @Test
        void realSchedulerFindsPendingCommandAndCompletesUserDisableEndToEnd() {
            IdentityRef identity = seedIdentityForCommand("subject-scheduler-e2e");
            ProviderCommandResult created = providerCommandService.createCommand(
                    ProviderCommandType.USER_DISABLE, null, identity.identityId(), identity.userId(),
                    null, "lookup-hash", "payload-fp", null, null, "idem-scheduler-e2e-1");
            assertThat(created.status()).isEqualTo(ProviderCommandStatus.PENDING);

            ProviderCommandWorker realWorker = new ProviderCommandWorker(providerCommandService, repository,
                    transactionExecutor, (commandType, subject, issuer) -> ProviderCommandOutcome.ofSuccess());
            ProviderCommandScheduler scheduler = new ProviderCommandScheduler(
                    realWorker, repository, transactionExecutor, settings, clock, secureRandom);

            int processed = scheduler.pollOnce();

            assertThat(processed).as("the real scheduler must find and process the due PENDING command").isEqualTo(1);
            ProviderCommandStatus finalStatus = adminJdbcTemplate.queryForObject(
                    "SELECT status::text FROM iam_provider_commands WHERE id = ?",
                    (rs, rowNum) -> ProviderCommandStatus.valueOf(rs.getString(1)), created.id());
            assertThat(finalStatus).isEqualTo(ProviderCommandStatus.COMPLETED);
        }
    }

    private int countRows(String table, UUID userId) {
        Integer count = adminJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countTable(String table, String userColumn, UUID userId) {
        if (userColumn == null) {
            Integer count = adminJdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
            return count == null ? 0 : count;
        }
        Integer count = adminJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + userColumn + " = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countRowsIdempotency(UUID userId, String operationType) {
        Integer count = adminJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM idempotency_keys WHERE user_id = ? AND operation_type = ?",
                Integer.class, userId, operationType);
        return count == null ? 0 : count;
    }

    private int countRowsEscrow(UUID userId) {
        Integer count = adminJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM iam_auth_response_escrows WHERE actor_user_id = ?", Integer.class, userId);
        return count == null ? 0 : count;
    }

    private int countAuditLogs(UUID actorUserId, String actionType) {
        Integer count = adminJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE actor_user_id = ? AND action_type = ?",
                Integer.class, actorUserId, actionType);
        return count == null ? 0 : count;
    }

    private boolean isContextTokenConsumed(String tokenValue) {
        String hash = tokenHasher.hash(tokenValue);
        Boolean consumed = adminJdbcTemplate.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM context_selection_tokens WHERE token_hash = ?",
                Boolean.class, hash);
        return Boolean.TRUE.equals(consumed);
    }

    private static class FixedServiceSettings implements IamServiceSettings {
        @Override public Duration accessTokenTtl() { return Duration.ofMinutes(10); }
        @Override public Duration refreshTokenTtl() { return Duration.ofDays(30); }
        @Override public Duration contextSelectionTokenTtl() { return Duration.ofMinutes(5); }
        @Override public Duration activationEscrowTtl() { return Duration.ofMinutes(5); }
        @Override public Duration idempotencyRetention() { return Duration.ofDays(30); }
        @Override public boolean providerCommandWorkerEnabled() { return false; }
        @Override public Duration providerCommandPollInterval() { return Duration.ofSeconds(60); }
        @Override public int providerCommandBatchLimit() { return 20; }
        @Override public Duration providerCommandLeaseTtl() { return Duration.ofMinutes(5); }
        @Override public int providerCommandMaxAttempts() { return 10; }
        @Override public Duration providerCommandBackoffBase() { return Duration.ofSeconds(10); }
        @Override public Duration providerCommandBackoffMax() { return Duration.ofMinutes(15); }
        @Override public double providerCommandJitter() { return 0.25d; }
    }

    private static class StubCognitoUserStatusChecker implements CognitoUserStatusChecker {
        private final Map<String, ProviderUserStatus> overrides = new ConcurrentHashMap<>();

        void reset() {
            overrides.clear();
        }

        void setStatus(String subject, ProviderUserStatus status) {
            overrides.put(subject, status);
        }

        @Override
        public ProviderUserStatus checkCanonicalStatus(UUID userIdentifier, String issuer, String subject) {
            if (subject == null) {
                return ProviderUserStatus.ACTIVE;
            }
            return overrides.getOrDefault(subject, ProviderUserStatus.ACTIVE);
        }
    }

    /** Simulates an audit-write failure: every write() throws, as JdbcIamAuditWriter does on a real INSERT error. */
    private static class ThrowingAuditWriter implements IamAuditWriter {
        @Override
        public void write(org.mepcity.kursplatform.iam.domain.IamAuditEvent event) {
            throw new org.mepcity.kursplatform.iam.domain.IamAuditWriteException("simulated audit write failure");
        }
    }

    /** Delegates decrypt() but always throws on encrypt(), to force a mid-mutation failure. */
    private static class ThrowingEncryptEscrowService implements AeadEscrowService {
        private final AeadEscrowService delegate;

        ThrowingEncryptEscrowService(AeadEscrowService delegate) {
            this.delegate = delegate;
        }

        @Override
        public EncryptedEscrow encrypt(UUID actorUserId, String operationType, UUID deviceIdentifier,
                                        String tokenFingerprint, EscrowPayload payload) {
            throw new RuntimeException("simulated escrow encryption failure");
        }

        @Override
        public EscrowPayload decrypt(AuthReplayEscrow escrow) {
            return delegate.decrypt(escrow);
        }
    }
}
