package org.mepcity.kursplatform.iam.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.EscrowStatus;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.RefreshToken;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.domain.UserIdentity;
import org.mepcity.kursplatform.iam.domain.UserStatus;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuthRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IamAuthRepositoryIntegrationTests {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static Flyway flyway;
    static JdbcTemplate jdbcTemplate;
    static IamAuthRepository repository;
    static DataSource dataSource;
    static TransactionTemplate transactionTemplate;

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
        flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        dataSource = new SimpleDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbcTemplate = new JdbcTemplate(dataSource);
        repository = new JdbcIamAuthRepository(jdbcTemplate);
        jdbcTemplate.execute("ALTER ROLE iam_runtime WITH PASSWORD '" + POSTGRES.getPassword() + "'");
        // The provider-command CAS methods below each issue >1 statement that must share the same
        // physical connection (a SET LOCAL-equivalent set_config(...) followed by the CAS UPDATE);
        // SimpleDataSource hands out a brand new connection per call, so without an explicit
        // Spring-managed transaction each statement would land on a different connection and the
        // set_config would never reach the UPDATE. This mirrors how the real IamTransactionExecutor
        // binds a single connection per transaction in production.
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE TABLE iam_auth_response_escrows, idempotency_keys, refresh_tokens, refresh_token_families, context_selection_tokens, trusted_devices, iam_secret_deliveries, iam_provider_commands, audit_logs, organization_membership_permissions, organization_membership_roles, organization_memberships, platform_administrator_profiles, platform_administrators, user_identities, users, people, organizations RESTART IDENTITY CASCADE");
    }

    private UUID seedOrganization(String name) {
        UUID orgId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO organizations (id, name, status) VALUES (?, ?, 'ACTIVE')", orgId, name);
        return orgId;
    }

    private UUID seedUser(UserStatus status) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO users (id, status) VALUES (?, ?::user_status_enum)", userId, status.name());
        return userId;
    }

    private UUID seedIdentity(UUID userId, String issuer, String subject) {
        UUID identityId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO user_identities (id, user_id, issuer, subject) VALUES (?, ?, ?, ?)",
                identityId, userId, issuer, subject);
        return identityId;
    }

    private void backdateProviderCommandLease(UUID commandId, Instant leaseExpiresAt) {
        // trg_ipc_state() requires a freshly-claimed lease to be in the future (a claim always
        // grants forward-looking time); to exercise "the lease has since expired" branches we
        // need a CLAIMED row with a past lease_expires_at, which only arises from time actually
        // passing after a valid claim. Simulate that by disabling the trigger for a direct
        // backdate rather than sleeping the test.
        jdbcTemplate.execute("ALTER TABLE iam_provider_commands DISABLE TRIGGER trg_ipc_state");
        try {
            jdbcTemplate.update("UPDATE iam_provider_commands SET lease_expires_at = ? WHERE id = ?",
                    Timestamp.from(leaseExpiresAt), commandId);
        } finally {
            jdbcTemplate.execute("ALTER TABLE iam_provider_commands ENABLE TRIGGER trg_ipc_state");
        }
    }

    private UUID seedPlatformAdmin(UUID userId) {
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES (?, ?, ?)",
                adminId, userId, Timestamp.from(Instant.now()));
        return adminId;
    }

    private UUID seedTrustedDevice(UUID userId, UUID deviceIdentifier, DevicePlatform platform) {
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO trusted_devices (id, user_id, device_identifier, platform) VALUES (?, ?, ?, ?::device_platform_enum)",
                deviceId, userId, deviceIdentifier, platform.name());
        return deviceId;
    }

    private UUID seedContextSelectionToken(UUID userId, UUID deviceId, String tokenHash, Instant authTime, Instant expiresAt) {
        // issued_at has no relationship to authTime/expiresAt other than the CHECK constraint
        // (expires_at = issued_at + 5 minutes); derive issued_at from the caller's expiresAt so
        // the exact-equality constraint holds regardless of DB clock drift vs. Instant.now().
        UUID tokenId = UUID.randomUUID();
        Instant issuedAt = expiresAt.minusSeconds(300);
        jdbcTemplate.update("INSERT INTO context_selection_tokens (id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                tokenId, userId, deviceId, tokenHash, Timestamp.from(authTime), Timestamp.from(issuedAt), Timestamp.from(expiresAt));
        return tokenId;
    }

    @Nested
    class RlsContextTests {

        @Test
        void rlsDeniesAccessWithoutSessionVariables() throws SQLException {
            // FORCE ROW LEVEL SECURITY makes a non-matching policy set filter rows to zero,
            // not raise an error, for SELECT — RLS is a WHERE-clause-level filter, not a grant
            // check. The absence of any app.iam_* session variable means no policy's USING
            // clause can evaluate true, so the query succeeds but returns nothing.
            UUID userId = seedUser(UserStatus.ACTIVE);
            try (Connection conn = openIamRuntimeConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                    ps.setObject(1, userId);
                    var rs = ps.executeQuery();
                    assertThat(rs.next()).as("default-deny: no session vars set means no row visible").isFalse();
                }
                conn.rollback();
            }
        }

        @Test
        void authenticationScopeCannotAccessMutationTables() throws SQLException {
            UUID userId = seedUser(UserStatus.ACTIVE);
            try (Connection conn = openIamRuntimeConnection()) {
                conn.setAutoCommit(false);
                setLocal(conn, "app.iam_operation_scope", "AUTHENTICATION");
                setLocal(conn, "app.iam_provider_issuer", "test-issuer");
                setLocal(conn, "app.iam_provider_subject", "test-subject");
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM trusted_devices WHERE user_id = ?")) {
                    ps.setObject(1, userId);
                    var rs = ps.executeQuery();
                    assertThat(rs.next()).as("AUTHENTICATION scope has no trusted_devices policy").isFalse();
                }
                conn.rollback();
            }
        }

        @Test
        void setLocalDoesNotLeakBetweenTransactions() throws SQLException {
            try (Connection conn = openIamRuntimeConnection()) {
                conn.setAutoCommit(false);
                setLocal(conn, "app.iam_operation_scope", "IAM_AUTH");
                setLocal(conn, "app.iam_operation_code", "PROVIDER_TOKEN_EXCHANGE");
                setLocal(conn, "app.iam_actor_user_id", UUID.randomUUID().toString());
                conn.commit();

                conn.setAutoCommit(false);
                assertThatThrownBy(() -> {
                    try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM users WHERE id = ?")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.executeQuery();
                    }
                }).isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }
    }

    @Nested
    class ContextTokenAtomicityTests {

        @Test
        void consumeContextSelectionTokenReturnsTrueWhenAvailable() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier, DevicePlatform.ANDROID);
            UUID tokenId = seedContextSelectionToken(userId, deviceId, "hash-1",
                    Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

            boolean consumed = repository.consumeContextSelectionTokenIfAvailable(tokenId, Instant.now());

            assertThat(consumed).isTrue();
            Optional<ContextSelectionToken> token = repository.findContextSelectionTokenByHash("hash-1");
            assertThat(token).isPresent();
            assertThat(token.get().consumedAt()).isNotNull();
        }

        @Test
        void consumeContextSelectionTokenReturnsFalseWhenAlreadyConsumed() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier, DevicePlatform.ANDROID);
            UUID tokenId = seedContextSelectionToken(userId, deviceId, "hash-2",
                    Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

            repository.consumeContextSelectionTokenIfAvailable(tokenId, Instant.now());
            boolean secondConsume = repository.consumeContextSelectionTokenIfAvailable(tokenId, Instant.now().plusSeconds(1));

            assertThat(secondConsume).isFalse();
        }

        @Test
        void consumeContextSelectionTokenReturnsFalseWhenRevoked() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID deviceIdentifier = UUID.randomUUID();
            UUID deviceId = seedTrustedDevice(userId, deviceIdentifier, DevicePlatform.ANDROID);
            UUID tokenId = seedContextSelectionToken(userId, deviceId, "hash-3",
                    Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));

            repository.markContextSelectionTokenRevoked(tokenId, Instant.now());
            boolean consumed = repository.consumeContextSelectionTokenIfAvailable(tokenId, Instant.now());

            assertThat(consumed).isFalse();
        }
    }

    @Nested
    class IdempotencyTests {

        @Test
        void insertIdempotencyKeyOrFindExistingReturnsEmptyOnFirstInsert() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            IdempotencyKey key = new IdempotencyKey(
                    UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, userId,
                    "mutation-1", "PROVIDER_TOKEN_EXCHANGE", "fp-1",
                    IdempotencyStatus.COMPLETED, null, (short) 200, null,
                    null, "ref-1", null, null, null,
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(300),
                    Instant.now().plusSeconds(300));

            Optional<IdempotencyKey> existing = repository.insertIdempotencyKeyOrFindExisting(key);

            assertThat(existing).isEmpty();
        }

        @Test
        void insertIdempotencyKeyOrFindExistingReturnsExistingOnDuplicate() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            IdempotencyKey key1 = new IdempotencyKey(
                    UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, userId,
                    "mutation-2", "PROVIDER_TOKEN_EXCHANGE", "fp-2",
                    IdempotencyStatus.COMPLETED, null, (short) 200, null,
                    null, "ref-2", null, null, null,
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(300),
                    Instant.now().plusSeconds(300));
            repository.insertIdempotencyKeyOrFindExisting(key1);

            IdempotencyKey key2 = new IdempotencyKey(
                    UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, userId,
                    "mutation-2", "PROVIDER_TOKEN_EXCHANGE", "fp-2",
                    IdempotencyStatus.COMPLETED, null, (short) 200, null,
                    null, "ref-2", null, null, null,
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(300),
                    Instant.now().plusSeconds(300));
            Optional<IdempotencyKey> existing = repository.insertIdempotencyKeyOrFindExisting(key2);

            assertThat(existing).isPresent();
            assertThat(existing.get().id()).isEqualTo(key1.id());
        }

        @Test
        void findIdempotencyKeyRetrievesByUserAndMutationAndScope() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            IdempotencyKey key = new IdempotencyKey(
                    UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, userId,
                    "mutation-3", "PROVIDER_TOKEN_EXCHANGE", "fp-3",
                    IdempotencyStatus.COMPLETED, null, (short) 200, null,
                    null, "ref-3", null, null, null,
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(300),
                    Instant.now().plusSeconds(300));
            repository.insertIdempotencyKeyOrFindExisting(key);

            Optional<IdempotencyKey> found = repository.findIdempotencyKey(
                    userId, "mutation-3", IdempotencyScope.IAM_AUTH, OperationCode.PROVIDER_TOKEN_EXCHANGE);

            assertThat(found).isPresent();
            assertThat(found.get().clientMutationId()).isEqualTo("mutation-3");
        }
    }

    @Nested
    class ProviderCommandCasTests {

        @Test
        void claimProviderCommandAtomicallyTransitionsPendingToClaimed() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-1");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);

            Optional<ProviderCommand> claimed = transactionTemplate.execute(status ->
                    repository.claimProviderCommandAtomically(
                            command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));

            assertThat(claimed).isPresent();
            assertThat(claimed.get().status()).isEqualTo(ProviderCommandStatus.CLAIMED);
            assertThat(claimed.get().leaseOwner()).isEqualTo("worker-1");
            assertThat(claimed.get().fencingToken()).isEqualTo(1);
            assertThat(claimed.get().attemptCount()).isEqualTo(1);
        }

        @Test
        void claimProviderCommandReturnsEmptyWhenAlreadyClaimed() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-2");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-2", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            transactionTemplate.execute(status -> repository.claimProviderCommandAtomically(
                    command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));

            Optional<ProviderCommand> secondClaim = transactionTemplate.execute(status ->
                    repository.claimProviderCommandAtomically(
                            command.id(), "worker-2", Instant.now().plusSeconds(300), Instant.now()));

            assertThat(secondClaim).isEmpty();
        }

        @Test
        void completeProviderCommandAtomicallySucceedsWithCorrectWorkerAndFencing() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-3");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-3", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            Optional<ProviderCommand> claimed = transactionTemplate.execute(status ->
                    repository.claimProviderCommandAtomically(
                            command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));

            Optional<ProviderCommand> completed = transactionTemplate.execute(status ->
                    repository.completeProviderCommandAtomically(
                            command.id(), "worker-1", claimed.get().fencingToken(), true, null, Instant.now()));

            assertThat(completed).isPresent();
            assertThat(completed.get().status()).isEqualTo(ProviderCommandStatus.COMPLETED);
            assertThat(completed.get().leaseOwner()).isNull();
            assertThat(completed.get().completedAt()).isNotNull();
        }

        @Test
        void completeProviderCommandFailsWithWrongFencingToken() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-4");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-4", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            transactionTemplate.execute(status -> repository.claimProviderCommandAtomically(
                    command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));

            Optional<ProviderCommand> completed = transactionTemplate.execute(status ->
                    repository.completeProviderCommandAtomically(
                            command.id(), "worker-1", 999, true, null, Instant.now()));

            assertThat(completed).isEmpty();
        }

        @Test
        void completeProviderCommandFailsWithWrongWorker() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-5");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-5", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            transactionTemplate.execute(status -> repository.claimProviderCommandAtomically(
                    command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));

            Optional<ProviderCommand> completed = transactionTemplate.execute(status ->
                    repository.completeProviderCommandAtomically(
                            command.id(), "wrong-worker", 1, true, null, Instant.now()));

            assertThat(completed).isEmpty();
        }

        @Test
        void completeProviderCommandFailsWithExpiredLease() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-6");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-6", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            Optional<ProviderCommand> claimed = transactionTemplate.execute(status ->
                    repository.claimProviderCommandAtomically(
                            command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));
            backdateProviderCommandLease(command.id(), Instant.now().minusSeconds(1));

            Optional<ProviderCommand> completed = transactionTemplate.execute(status ->
                    repository.completeProviderCommandAtomically(
                            command.id(), "worker-1", claimed.get().fencingToken(), true, null, Instant.now()));

            assertThat(completed).isEmpty();
        }

        @Test
        void reclaimExpiredLeaseAtomicallySucceedsAfterExpiry() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID identityId = seedIdentity(userId, "issuer", "subject-7");
            ProviderCommand command = new ProviderCommand(
                    UUID.randomUUID(), "idem-7", "cognito", ProviderCommandType.USER_DISABLE,
                    null, identityId, null, null, "fp", null, null,
                    ProviderCommandStatus.PENDING, 0, Instant.now(), null, 0, null,
                    Instant.now(), null, null);
            repository.saveProviderCommand(command);
            transactionTemplate.execute(status -> repository.claimProviderCommandAtomically(
                    command.id(), "worker-1", Instant.now().plusSeconds(300), Instant.now()));
            backdateProviderCommandLease(command.id(), Instant.now().minusSeconds(1));

            Optional<ProviderCommand> reclaimed = transactionTemplate.execute(status ->
                    repository.reclaimExpiredLeaseAtomically(
                            command.id(), "worker-2", Instant.now().plusSeconds(300), Instant.now()));

            assertThat(reclaimed).isPresent();
            assertThat(reclaimed.get().leaseOwner()).isEqualTo("worker-2");
            assertThat(reclaimed.get().fencingToken()).isEqualTo(2);
        }
    }

    @Nested
    class DeviceAdvisoryLockTests {

        @Test
        void acquireDeviceAdvisoryLockDoesNotThrow() {
            UUID userId = seedUser(UserStatus.ACTIVE);
            UUID deviceIdentifier = UUID.randomUUID();

            jdbcTemplate.execute("SET LOCAL app.iam_operation_scope = 'IAM_AUTH'; SET LOCAL app.iam_operation_code = 'PROVIDER_TOKEN_EXCHANGE'; SET LOCAL app.iam_actor_user_id = '" + userId + "'; SET LOCAL app.iam_provider_device_identifier = '" + deviceIdentifier + "'");
            try {
                repository.acquireDeviceAdvisoryLock(userId, deviceIdentifier);
            } finally {
                jdbcTemplate.execute("RESET app.iam_operation_scope; RESET app.iam_operation_code; RESET app.iam_actor_user_id; RESET app.iam_provider_device_identifier");
            }
        }
    }

    @Nested
    class RuntimeRoleTests {

        @Test
        void runtimeRoleIsNotSuperuser() throws SQLException {
            try (Connection conn = openIamRuntimeConnection()) {
                var rs = conn.createStatement().executeQuery("SELECT rolsuper FROM pg_roles WHERE rolname = 'iam_runtime'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolsuper")).isFalse();
            }
        }

        @Test
        void runtimeRoleDoesNotHaveBypassrls() throws SQLException {
            try (Connection conn = DriverManager.getConnection(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT rolbypassrls FROM pg_roles WHERE rolname = 'iam_runtime'");
                rs.next();
                assertThat(rs.getBoolean("rolbypassrls")).isFalse();
            }
        }
    }

    private Connection openIamRuntimeConnection() throws SQLException {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), "iam_runtime", POSTGRES.getPassword());
    }

    private void setLocal(Connection conn, String key, String value) throws SQLException {
        String safeKey = key.replace("'", "''");
        String safeValue = value.replace("'", "''");
        conn.createStatement().execute("SET LOCAL " + safeKey + " = '" + safeValue + "'");
    }

    private static class SimpleDataSource implements DataSource {
        private final String url;
        private final String username;
        private final String password;

        SimpleDataSource(String url, String username, String password) {
            this.url = url;
            this.username = username;
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return DriverManager.getConnection(url, username, password);
        }

        @Override
        public java.io.PrintWriter getLogWriter() { return null; }
        @Override
        public void setLogWriter(java.io.PrintWriter out) {}
        @Override
        public void setLoginTimeout(int seconds) {}
        @Override
        public int getLoginTimeout() { return 0; }
        @Override
        public java.util.logging.Logger getParentLogger() { return null; }
        @Override
        public <T> T unwrap(Class<T> iface) { throw new UnsupportedOperationException(); }
        @Override
        public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
