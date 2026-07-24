package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.org.infrastructure.persistence.JacksonOrganizationResultSerializer;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationCreateRateLimiter;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.org.application.AuditEvent;
import org.mepcity.kursplatform.org.application.AuditWriter;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.IdempotencyKeyReusedException;
import org.mepcity.kursplatform.org.application.IdempotencyOutcome;
import org.mepcity.kursplatform.org.application.IdempotencyRecorder;
import org.mepcity.kursplatform.org.application.LifecycleRequest;
import org.mepcity.kursplatform.org.application.LifecycleResult;
import org.mepcity.kursplatform.org.application.OrganizationConflictException;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.application.OrganizationNotVisibleException;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real PostgreSQL proof for ORG-003 grants, RLS, atomic rollback and idempotency behaviour. */
class OrganizationMigrationTests {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource dataSource;
    static PlatformTransactionManager transactionManager;
    static final JacksonOrganizationResultSerializer RESULT_SERIALIZER =
            new JacksonOrganizationResultSerializer(new ObjectMapper().findAndRegisterModules());

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
        var flyway = Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        transactionManager = new DataSourceTransactionManager(dataSource);
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    // --------------------------------------------------------------------------
    // Migration order, grants and role shape
    // --------------------------------------------------------------------------

    @Test
    void migrationOrderAppliesV1IamThenV2AuditCoreThenV3Org() throws Exception {
        try (var connection = openConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT version FROM flyway_schema_history WHERE success AND type = 'SQL' ORDER BY installed_rank");
                    ResultSet result = statement.executeQuery()) {
                result.next();
                assertThat(result.getString(1)).isEqualTo("1");
                result.next();
                assertThat(result.getString(1)).isEqualTo("2");
                result.next();
                assertThat(result.getString(1)).isEqualTo("3");
            }
        }
    }

    @Test
    void persistentCreateRateLimitIsActorScopedAtomicAndSharedAcrossInstances() throws Exception {
        var first = new JdbcOrganizationCreateRateLimiter(dataSource, transactionManager,
                2, java.time.Duration.ofMinutes(1));
        var second = new JdbcOrganizationCreateRateLimiter(dataSource, transactionManager,
                2, java.time.Duration.ofMinutes(1));
        UUID actorOne = activeAdmin();
        UUID actorTwo = activeAdmin();
        first.check(actorOne);
        second.check(actorOne);
        assertThatThrownBy(() -> first.check(actorOne)).isInstanceOf(RateLimitExceededException.class)
                .satisfies(error -> assertThat(((RateLimitExceededException) error).retryAfterSeconds()).isPositive());
        first.check(actorTwo);
    }

    @Test
    void iamRuntimeLoginCanOnlyUseOrgPrivilegesAfterExplicitSetRole() throws Exception {
        UUID actor = activeAdmin();
        try (Connection owner = openConnection()) {
            owner.createStatement().execute("ALTER ROLE iam_runtime PASSWORD 'test-runtime-password'");
            owner.commit();
        }
        try (Connection runtime = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "iam_runtime", "test-runtime-password")) {
            assertSqlState("42501", () -> runtime.createStatement().execute(
                    "INSERT INTO organizations (id, name, status, default_timezone, row_version, created_by_user_id, updated_by_user_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'Doğrudan red', 'ACTIVE', 'Europe/Istanbul', 1, '"
                            + actor + "', '" + actor + "')"));
            runtime.setAutoCommit(false);
            runtime.createStatement().execute("SET LOCAL ROLE org_runtime");
            try (ResultSet role = runtime.createStatement().executeQuery("SELECT current_user, session_user")) {
                role.next();
                assertThat(role.getString(1)).isEqualTo("org_runtime");
                assertThat(role.getString(2)).isEqualTo("iam_runtime");
            }
            runtime.rollback();
        }
        var runtimeDataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "iam_runtime", "test-runtime-password");
        var runtimeManager = new DataSourceTransactionManager(runtimeDataSource);
        var runtimeService = new OrganizationLifecycleService(new JdbcOrganizationRepository(runtimeDataSource), runtimeDataSource,
                runtimeManager, new JdbcAuditWriter(runtimeDataSource), new JdbcIdempotencyRecorder(runtimeDataSource),
                RESULT_SERIALIZER, new JdbcOrganizationCreateRateLimiter(runtimeDataSource, runtimeManager, 100, java.time.Duration.ofMinutes(1)));
        LifecycleResult result = runtimeService.create(new LifecycleRequest(actor, UUID.randomUUID(), 1, "runtime-role-key",
                "runtime-role-fingerprint", "runtime-role-request", "runtime-role-worker", Instant.now().plusSeconds(60),
                Instant.now().plusSeconds(300)), "iam runtime zinciri", null, "Europe/Istanbul");
        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);
    }

    @Test
    void completedReplayBypassesDbQuotaButNewKeyIsRateLimited() throws Exception {
        UUID actor = activeAdmin();
        var limiter = new JdbcOrganizationCreateRateLimiter(dataSource, transactionManager, 1, java.time.Duration.ofMinutes(1));
        var service = new OrganizationLifecycleService(new JdbcOrganizationRepository(dataSource), dataSource, transactionManager,
                new JdbcAuditWriter(dataSource), new JdbcIdempotencyRecorder(dataSource), RESULT_SERIALIZER, limiter);
        LifecycleRequest first = new LifecycleRequest(actor, UUID.randomUUID(), 1, "replay-rate-key", "same-fingerprint",
                "request-1", "worker-1", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
        LifecycleResult initial = service.create(first, "Replay kotası", null, "Europe/Istanbul");
        LifecycleResult replay = service.create(first, "Replay kotası", null, "Europe/Istanbul");
        assertThat(initial).isInstanceOf(LifecycleResult.Committed.class);
        assertThat(replay).isInstanceOf(LifecycleResult.Replayed.class);
        LifecycleRequest another = new LifecycleRequest(actor, UUID.randomUUID(), 1, "new-rate-key", "different-fingerprint",
                "request-2", "worker-2", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> service.create(another, "Yeni kota", null, "Europe/Istanbul"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void orgRuntimeRoleIsNotOwnerSuperuserOrBypassAndPrimaryColorColumnExists() throws Exception {
        try (var connection = openConnection()) {
            var color = connection.createStatement().executeQuery(
                    "SELECT column_name FROM information_schema.columns WHERE table_name = 'organizations' "
                            + "AND column_name = 'primary_color'");
            assertThat(color.next()).isTrue();
            var role = connection.createStatement().executeQuery(
                    "SELECT rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolbypassrls FROM pg_roles WHERE rolname = 'org_runtime'");
            assertThat(role.next()).isTrue();
            assertThat(role.getBoolean("rolsuper")).isFalse();
            assertThat(role.getBoolean("rolcreatedb")).isFalse();
            assertThat(role.getBoolean("rolcreaterole")).isFalse();
            assertThat(role.getBoolean("rolinherit")).isFalse();
            assertThat(role.getBoolean("rolbypassrls")).isFalse();
        }
    }

    @Test
    void auditLogsColumnLevelInsertGrantIsExactAndNoTableWidePrivilege() throws Exception {
        try (var connection = openConnection()) {
            var columns = new java.util.TreeSet<String>();
            try (var result = connection.createStatement().executeQuery(
                    "SELECT column_name FROM information_schema.column_privileges "
                            + "WHERE grantee = 'org_runtime' AND table_name = 'audit_logs' AND privilege_type = 'INSERT'")) {
                while (result.next()) {
                    columns.add(result.getString(1));
                }
            }
            assertThat(columns).containsExactlyInAnyOrder(
                    "actor_user_id", "action_type", "event_kind", "event_metadata", "event_scope",
                    "id", "is_undo", "new_value", "old_value", "operation_group_id",
                    "organization_id", "payload_schema_version", "reason_code", "request_id",
                    "requires_class_scope", "requires_operation_group", "requires_target_entity",
                    "target_entity_id", "target_entity_type", "undo_of_audit_log_id");
            assertThat(columns).doesNotContain("scope_class_id", "occurred_at", "ip_address", "device_id");
            try (var result = connection.createStatement().executeQuery(
                    "SELECT count(*) FROM information_schema.role_table_grants "
                            + "WHERE grantee = 'org_runtime' AND table_name = 'audit_logs' "
                            + "AND privilege_type IN ('INSERT','UPDATE','DELETE','SELECT')")) {
                result.next();
                assertThat(result.getLong(1)).isZero();
            }
        }
    }

    @Test
    void orgRuntimeCannotAuditUpdateDeleteOrTableWideInsert() throws Exception {
        UUID actor = user();
        UUID org = organization("Audit deny", actor, null, "ACTIVE");
        // Table-wide INSERT (all columns) is denied even with a valid RLS context: org_runtime only has
        // column-level INSERT. Each assertion uses its own connection so an aborted statement does not
        // poison the following one (25P02 in_failed_sql_transaction).
        assertSqlStateWithFreshConnection(actor, org, "42501", "INSERT INTO audit_logs (id, organization_id, "
                + "actor_user_id, action_type, payload_schema_version, event_scope, target_entity_type, event_kind, "
                + "requires_target_entity, requires_class_scope, requires_operation_group, target_entity_id, is_undo) "
                + "VALUES ('" + UUID.randomUUID() + "', '" + org + "', '" + actor + "', 'ORG_STATUS_CHANGED', 1, "
                + "'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, '" + org + "', false)");
        assertSqlStateWithFreshConnection(actor, org, "42501", "UPDATE audit_logs SET request_id = 'x'");
        assertSqlStateWithFreshConnection(actor, org, "42501", "DELETE FROM audit_logs");
    }

    private void assertSqlStateWithFreshConnection(UUID actor, UUID org, String expectedSqlState, String sql)
            throws Exception {
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_SUSPEND'");
            assertSqlState(expectedSqlState, () -> connection.createStatement().execute(sql));
        }
    }

    @Test
    void scopeClassIdRuntimeInsertIsRejectedWithInsufficientPrivilegeNotCheckViolation() throws Exception {
        UUID actor = activeAdmin();
        UUID org = organization("Scope class", actor, null, "ACTIVE");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_SUSPEND'");
            // scope_class_id is not in the INSERT grant list -> 42501 insufficient_privilege (not 23514).
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, scope_class_id, is_undo) VALUES ('"
                            + UUID.randomUUID() + "', '" + org + "', '" + actor + "', 'ORG_STATUS_CHANGED', 1, "
                            + "'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, '" + org
                            + "', '" + UUID.randomUUID() + "', false)"));
        }
    }

    @Test
    void appRuntimeCannotInsertAuditLogs() throws Exception {
        UUID actor = user();
        UUID org = organization("App runtime", actor, null, "ACTIVE");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE app_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, is_undo) VALUES ('"
                            + UUID.randomUUID() + "', '" + org + "', '" + actor + "', 'ORG_CREATED', 1, "
                            + "'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, '" + org + "', false)"));
        }
    }

    @Test
    void auditLogsAppendOnlyGuardRejectsUpdateAndDelete() throws Exception {
        UUID actor = user();
        UUID org = organization("Append only", actor, null, "ACTIVE");
        UUID audit = insertAuditAsOwner(actor, org, "ORG_CREATED", "DATA_MUTATION");
        try (var connection = openConnection()) {
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(
                    "UPDATE audit_logs SET request_id = 'x' WHERE id = '" + audit + "'"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> connection.createStatement().executeUpdate(
                    "DELETE FROM audit_logs WHERE id = '" + audit + "'"))
                    .isInstanceOf(SQLException.class);
        }
    }

    // --------------------------------------------------------------------------
    // platform_administrators canonical ORGANIZATION-scope allow-list (14 operation codes:
    // ORG-001's 5 + ORG-002's 9). This is the single policy ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md
    // §4.1a defines; ORG-002 does not get a separate allow-list.
    // --------------------------------------------------------------------------

    private static final List<String> CANONICAL_ORGANIZATION_SCOPE_OPERATION_CODES = List.of(
            "ORG_DETAIL", "ORG_UPDATE_IDENTITY", "ORG_SUSPEND", "ORG_ACTIVATE", "ORG_ARCHIVE",
            "ORG_VIEW_BRAND", "ORG_UPDATE_BRAND", "ORG_VIEW_BRAND_COLORS", "ORG_UPDATE_BRAND_COLORS",
            "ORG_VIEW_MODULES", "ORG_UPDATE_MODULES", "ORG_UPLOAD_LOGO", "ORG_REMOVE_LOGO", "ORG_VIEW_LOGO");

    // Deliberately NOT in ascending-sortOrder/alphabetical order -- the setter must canonicalize
    // regardless of caller order (never trust caller ordering for the persisted JSON).
    private static final List<AuditEvent.ModuleState> FULL_MODULE_SNAPSHOT_SCRAMBLED = List.of(
            new AuditEvent.ModuleState("AUDIT", true, 5),
            new AuditEvent.ModuleState("EXPORT", true, 4),
            new AuditEvent.ModuleState("PROGRESS", true, 3),
            new AuditEvent.ModuleState("PROGRAM", true, 2),
            new AuditEvent.ModuleState("CONTENT", true, 1),
            new AuditEvent.ModuleState("ATT", true, 0));
    private static final List<AuditEvent.ModuleState> FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED = List.of(
            new AuditEvent.ModuleState("ATT", true, 0),
            new AuditEvent.ModuleState("CONTENT", true, 1),
            new AuditEvent.ModuleState("PROGRAM", false, 2),
            new AuditEvent.ModuleState("PROGRESS", true, 3),
            new AuditEvent.ModuleState("EXPORT", true, 4),
            new AuditEvent.ModuleState("AUDIT", true, 5));

    @Test
    void platformAdministratorsOrganizationScopeSelectAllowsAllFourteenCanonicalOperationCodes() throws Exception {
        assertThat(CANONICAL_ORGANIZATION_SCOPE_OPERATION_CODES).hasSize(14);
        UUID admin = activeAdmin();
        for (String operationCode : CANONICAL_ORGANIZATION_SCOPE_OPERATION_CODES) {
            try (var connection = openConnection()) {
                connection.createStatement().execute("SET ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operationCode + "'");
                assertThat(count(connection, "SELECT count(*) FROM platform_administrators WHERE user_id = '" + admin + "'"))
                        .as("operation code %s", operationCode).isEqualTo(1);
            }
        }
    }

    @Test
    void platformAdministratorsOrganizationScopeSelectHidesOtherAdminRevokedAdminAndOutOfListOperationCode() throws Exception {
        UUID admin = activeAdmin();
        UUID otherAdmin = activeAdmin();
        UUID revoked = revokedAdmin();

        // Another active admin's row must never be visible under the acting admin's own context,
        // even for an in-allow-list operation code.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_VIEW_BRAND'");
            assertThat(count(connection, "SELECT count(*) FROM platform_administrators WHERE user_id = '" + otherAdmin + "'"))
                    .isZero();
        }
        // A revoked admin acting as themselves sees zero rows, fail-closed.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + revoked + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_UPDATE_MODULES'");
            assertThat(count(connection, "SELECT count(*) FROM platform_administrators")).isZero();
        }
        // An operation code outside the 14-code allow-list sees zero rows even for a valid active
        // admin -- includes the sibling GLOBAL-scope codes (wrong scope) and an invented code.
        for (String outOfList : List.of("ORG_CREATE", "ORG_LIST", "ORG_DELETE", "NOT_A_REAL_CODE")) {
            try (var connection = openConnection()) {
                connection.createStatement().execute("SET ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + outOfList + "'");
                assertThat(count(connection, "SELECT count(*) FROM platform_administrators"))
                        .as("operation code %s", outOfList).isZero();
            }
        }
    }

    @Test
    void fakeSupportFlagWithoutVerifiedAdminNeverOpensTargetOrganizationAccess() throws Exception {
        UUID actor = user(); // not a platform administrator at all
        UUID org = organization("Fake support flag", actor, null, "ACTIVE");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            // The flag is set FIRST, out of the real order (the real flow only sets it AFTER a
            // successful platform_administrators SELECT) -- proves the flag alone never fabricates
            // access: the organizations RLS policy independently re-verifies an active admin row.
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_DETAIL'");
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + org + "'")).isZero();
        }
    }

    // --------------------------------------------------------------------------
    // Positive audit RLS — every ORG operation code writes its matching audit row
    // --------------------------------------------------------------------------

    @Test
    void positiveAuditRlsForAllOrgOperationCodes() throws Exception {
        UUID admin = activeAdmin();
        UUID created = organization("Pos create", admin, null, "ACTIVE");
        // GLOBAL CREATE: ORG_CREATED, organization_id = target_entity_id = new org; app.organization_id unset.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'GLOBAL'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_CREATE'");
            insertAudit(connection, UUID.randomUUID(), created, admin, "ORG_CREATED", "DATA_MUTATION", created,
                    "ORG_CREATE", null, null);
            connection.commit();
        }
        // GLOBAL LIST: PLATFORM_ADMIN_ORG_ACCESS, one per viewed org; organization_id = target_entity_id.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'GLOBAL'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_LIST'");
            insertAccessAudit(connection, UUID.randomUUID(), created, admin, "ALLOWED", null);
            connection.commit();
        }
        // ORGANIZATION DETAIL + lifecycle: PLATFORM_ADMIN_ORG_ACCESS + matching status/setting rows.
        for (String operation : List.of("ORG_DETAIL", "ORG_UPDATE_IDENTITY", "ORG_SUSPEND", "ORG_ACTIVATE", "ORG_ARCHIVE")) {
            try (var connection = openConnection()) {
                connection.createStatement().execute("SET ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + created + "'");
                connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operation + "'");
                insertAccessAudit(connection, UUID.randomUUID(), created, admin, "ALLOWED", null);
                if (!operation.equals("ORG_DETAIL")) {
                    String action = operation.equals("ORG_UPDATE_IDENTITY") ? "ORG_SETTING_CHANGED" : "ORG_STATUS_CHANGED";
                    String oldStatus = null;
                    String newStatus = null;
                    if (action.equals("ORG_STATUS_CHANGED")) {
                        oldStatus = switch (operation) {
                            case "ORG_SUSPEND", "ORG_ARCHIVE" -> "ACTIVE";
                            case "ORG_ACTIVATE" -> "SUSPENDED";
                            default -> throw new IllegalStateException("Unexpected operation: " + operation);
                        };
                        newStatus = switch (operation) {
                            case "ORG_SUSPEND" -> "SUSPENDED";
                            case "ORG_ACTIVATE" -> "ACTIVE";
                            case "ORG_ARCHIVE" -> "ARCHIVED";
                            default -> throw new IllegalStateException("Unexpected operation: " + operation);
                        };
                    }
                    insertAudit(connection, UUID.randomUUID(), created, admin, action, "DATA_MUTATION", created,
                            operation, oldStatus, newStatus);
                }
                connection.commit();
            }
        }
    }

    @Test
    void orgActorPatchWritesOrgSettingChangedWithoutSupportFlag() throws Exception {
        UUID org = organization("Org actor patch", user(), null, "ACTIVE");
        // A real, active ORG_ADMIN membership -- the org-actor audit-write branch now requires
        // org_actor_has_identity_update_access to hold, not merely "no support flag, no admin".
        UUID actor = orgAdminActor(org);
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_UPDATE_IDENTITY'");
            insertAudit(connection, UUID.randomUUID(), org, actor, "ORG_SETTING_CHANGED", "DATA_MUTATION", org,
                    "ORG_UPDATE_IDENTITY", null, null);
            connection.commit();
        }
    }

    // --------------------------------------------------------------------------
    // Negative audit RLS — fail-closed on every boundary violation
    // --------------------------------------------------------------------------

    @Test
    void negativeAuditRlsRejectsAllBoundaryViolations() throws Exception {
        UUID admin = activeAdmin();
        UUID other = user();
        UUID org = organization("Negative", admin, null, "ACTIVE");
        UUID otherOrg = organization("Other negative", admin, null, "ACTIVE");

        assertAuditRejected(admin, org, "GLOBAL", "ORG_LIST", null, "ORG_CREATED", "DATA_MUTATION", org);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_SUSPEND", true, "ORG_SETTING_CHANGED", "DATA_MUTATION", org);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_SUSPEND", true, "ORG_STATUS_CHANGED", "DATA_MUTATION", org, "GLOBAL");
        assertAuditRejected(other, org, "ORGANIZATION", "ORG_SUSPEND", true, "ORG_STATUS_CHANGED", "DATA_MUTATION", org);
        UUID revoked = revokedAdmin();
        assertAuditRejected(revoked, org, "ORGANIZATION", "ORG_SUSPEND", true, "ORG_STATUS_CHANGED", "DATA_MUTATION", org);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_SUSPEND", false, "ORG_STATUS_CHANGED", "DATA_MUTATION", org);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_SUSPEND", "client-provided", "ORG_STATUS_CHANGED", "DATA_MUTATION", org);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_SUSPEND", true, "ORG_STATUS_CHANGED", "DATA_MUTATION", otherOrg);
        assertAuditRejected(admin, org, "ORGANIZATION", "ORG_UPDATE_IDENTITY", true, "ORG_SETTING_CHANGED", "DATA_MUTATION", org, "ORGANIZATION", true, false);
    }

    // --------------------------------------------------------------------------
    // DB-level defense-in-depth — event_metadata operationCode binding, ORG_STATUS_CHANGED
    // transition compatibility and PLATFORM_ADMIN_ORG_ACCESS outcome/reasonCode correlation are all
    // enforced directly in the audit_logs RLS policies (V3), not just in Java. A row with a
    // perfectly valid transaction context but an internally inconsistent event_metadata/old_value/
    // new_value/reason_code must still be rejected with 42501.
    // --------------------------------------------------------------------------

    @Test
    void orgCreatedAndSettingChangedRejectMetadataOperationCodeMismatch() throws Exception {
        UUID admin = activeAdmin();
        UUID orgActor = user();
        UUID org = organization("Created/setting metadata mismatch", admin, null, "ACTIVE");

        // ORG_CREATED: context says ORG_CREATE, event_metadata claims ORG_LIST.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'GLOBAL'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_CREATE'");
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, event_metadata, is_undo) VALUES ('"
                            + UUID.randomUUID() + "','" + org + "','" + admin + "','ORG_CREATED',1,"
                            + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                            + "'{\"operationCode\":\"ORG_LIST\"}'::jsonb,false)"));
        }

        // ORG_SETTING_CHANGED (org-actor path, no admin/support flag): context says
        // ORG_UPDATE_IDENTITY, event_metadata claims ORG_SUSPEND.
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + orgActor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_UPDATE_IDENTITY'");
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, event_metadata, is_undo) VALUES ('"
                            + UUID.randomUUID() + "','" + org + "','" + orgActor + "','ORG_SETTING_CHANGED',1,"
                            + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                            + "'{\"operationCode\":\"ORG_SUSPEND\"}'::jsonb,false)"));
        }
    }

    @Test
    void platformAdminOrgAccessRejectsOperationCodeMismatchAndOutcomeReasonCodeMismatch() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Access db-level guard", admin, null, "ACTIVE");

        // Metadata operationCode not matching the transaction context's operation code.
        assertAccessInsertRejected(admin, org, "ORG_DETAIL", "ORG_SUSPEND", "ALLOWED", null);
        // ALLOWED outcome with a non-null reason code.
        assertAccessInsertRejected(admin, org, "ORG_DETAIL", "ORG_DETAIL", "ALLOWED", "FORBIDDEN");
        // FORBIDDEN outcome with a null reason code.
        assertAccessInsertRejected(admin, org, "ORG_DETAIL", "ORG_DETAIL", "FORBIDDEN", null);
        // FORBIDDEN outcome with a reason code other than the closed FORBIDDEN value.
        assertAccessInsertRejected(admin, org, "ORG_DETAIL", "ORG_DETAIL", "FORBIDDEN", "OTHER");
    }

    /**
     * Sets up an otherwise-valid ORGANIZATION/ORG_DETAIL platform-admin-support-access transaction
     * context and attempts a {@code PLATFORM_ADMIN_ORG_ACCESS} INSERT whose {@code
     * event_metadata.operationCode}/{@code outcome}/{@code reason_code} combination is deliberately
     * inconsistent; asserts PostgreSQL rejects it with {@code 42501}.
     */
    private void assertAccessInsertRejected(UUID admin, UUID org, String contextOperationCode,
            String metadataOperationCode, String outcome, String reasonCode) throws Exception {
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + contextOperationCode + "'");
            String reason = reasonCode == null ? "NULL" : "'" + reasonCode + "'";
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, event_metadata, reason_code, is_undo) VALUES ('"
                            + UUID.randomUUID() + "','" + org + "','" + admin + "','PLATFORM_ADMIN_ORG_ACCESS',1,"
                            + "'ORGANIZATION','ORGANIZATION','ACCESS',true,false,false,'" + org + "',"
                            + "'{\"operationCode\":\"" + metadataOperationCode + "\",\"outcome\":\"" + outcome + "\"}'::jsonb,"
                            + reason + ",false)"));
        }
    }

    @Test
    void orgStatusChangedRejectsMetadataOperationCodeMismatchEvenWithValidContextAndTransition() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Status metadata mismatch", admin, null, "ACTIVE");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_SUSPEND'");
            // Context operation code and the ACTIVE->SUSPENDED transition are both valid for
            // ORG_SUSPEND, but event_metadata itself claims ORG_ARCHIVE -- must still be rejected.
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, old_value, new_value, event_metadata, is_undo) "
                            + "VALUES ('" + UUID.randomUUID() + "','" + org + "','" + admin + "','ORG_STATUS_CHANGED',1,"
                            + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                            + "'{\"status\":\"ACTIVE\",\"rowVersion\":1}'::jsonb,"
                            + "'{\"status\":\"SUSPENDED\",\"rowVersion\":2}'::jsonb,"
                            + "'{\"operationCode\":\"ORG_ARCHIVE\",\"revokedMembershipCount\":0,"
                            + "\"revokedFamilyCount\":0,\"revokedTokenCount\":0}'::jsonb,false)"));
        }
    }

    @Test
    void orgStatusChangedRejectsTransitionsIncompatibleWithOperationCode() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Status transition mismatch", admin, null, "ACTIVE");
        // Each row is individually well-formed JSON but is not a transition its operation code may
        // produce: ORG_SUSPEND only ACTIVE->SUSPENDED, ORG_ACTIVATE only SUSPENDED->ACTIVE,
        // ORG_ARCHIVE only ACTIVE|SUSPENDED->ARCHIVED.
        record BadTransition(String operationCode, String oldStatus, String newStatus) {}
        var badTransitions = List.of(
                new BadTransition("ORG_SUSPEND", "SUSPENDED", "SUSPENDED"),
                new BadTransition("ORG_SUSPEND", "ARCHIVED", "SUSPENDED"),
                new BadTransition("ORG_SUSPEND", "ACTIVE", "ARCHIVED"),
                new BadTransition("ORG_ACTIVATE", "ACTIVE", "ACTIVE"),
                new BadTransition("ORG_ACTIVATE", "ARCHIVED", "ACTIVE"),
                new BadTransition("ORG_ARCHIVE", "ARCHIVED", "ARCHIVED"),
                new BadTransition("ORG_ARCHIVE", "ACTIVE", "SUSPENDED"));
        for (BadTransition bad : badTransitions) {
            try (var connection = openConnection()) {
                connection.createStatement().execute("SET ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
                connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
                connection.createStatement().execute(
                        "SET LOCAL app.iam_operation_code = '" + bad.operationCode() + "'");
                assertSqlState("42501", () -> connection.createStatement().execute(
                        "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, "
                                + "payload_schema_version, event_scope, target_entity_type, event_kind, "
                                + "requires_target_entity, requires_class_scope, requires_operation_group, "
                                + "target_entity_id, old_value, new_value, event_metadata, is_undo) VALUES ('"
                                + UUID.randomUUID() + "','" + org + "','" + admin + "','ORG_STATUS_CHANGED',1,"
                                + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                                + "'{\"status\":\"" + bad.oldStatus() + "\",\"rowVersion\":1}'::jsonb,"
                                + "'{\"status\":\"" + bad.newStatus() + "\",\"rowVersion\":2}'::jsonb,"
                                + "'{\"operationCode\":\"" + bad.operationCode() + "\",\"revokedMembershipCount\":0,"
                                + "\"revokedFamilyCount\":0,\"revokedTokenCount\":0}'::jsonb,false)"));
            }
        }
    }

    // --------------------------------------------------------------------------
    // Atomicity — SUSPEND / ARCHIVE / ACTIVATE full chain rollback on audit failure
    // --------------------------------------------------------------------------

    @Test
    void createCommitsActiveOrganizationAuditAndGlobalIdempotency() throws Exception {
        UUID admin = activeAdmin();
        UUID organizationId = UUID.randomUUID();
        var request = lifecycleRequest(admin, organizationId, 1, "create-ok");

        LifecycleResult result = serviceWithRealWriter().create(request, "Yeni Kurum", "Yeni", "Europe/Istanbul");

        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);
        Organization created = ((LifecycleResult.Committed) result).organization();
        assertThat(created.id()).isEqualTo(organizationId);
        assertThat(created.status()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(created.rowVersion()).isEqualTo(1);
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + organizationId
                    + "' AND status = 'ACTIVE' AND row_version = 1")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + organizationId
                    + "' AND action_type = 'ORG_CREATED'")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'create-ok'"
                    + " AND scope_type = 'GLOBAL' AND organization_id IS NULL AND status = 'COMPLETED'"
                    + " AND terminal_http_status = 201")).isEqualTo(1);
        }
    }

    @Test
    void createReplayDoesNotCreateSecondOrganizationOrAudit() throws Exception {
        UUID admin = activeAdmin();
        UUID organizationId = UUID.randomUUID();
        var request = lifecycleRequest(admin, organizationId, 1, "create-replay");
        var service = serviceWithRealWriter();

        assertThat(service.create(request, "Tek Kurum", null, "Europe/Istanbul"))
                .isInstanceOf(LifecycleResult.Committed.class);
        assertThat(service.create(request, "Tek Kurum", null, "Europe/Istanbul"))
                .isInstanceOf(LifecycleResult.Replayed.class);
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + organizationId + "'"))
                    .isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + organizationId
                    + "' AND action_type = 'ORG_CREATED'")).isEqualTo(1);
        }
    }

    @Test
    void createRollsBackOrganizationAndClaimWhenAuditWriteFails() throws Exception {
        UUID admin = activeAdmin();
        UUID organizationId = UUID.randomUUID();
        assertThatThrownBy(() -> serviceWithRlsCorruptingWriter().create(
                lifecycleRequest(admin, organizationId, 1, "create-rollback"), "Hatalı", null, "Europe/Istanbul"))
                .isInstanceOf(RuntimeException.class);
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + organizationId + "'"))
                    .isZero();
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + organizationId + "'"))
                    .isZero();
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'create-rollback'"))
                    .isZero();
        }
    }

    @Test
    void suspendAtomicallyWritesStatusBarrierFamilyTokenAuditAndIdempotency() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Suspend ok", admin, null, "ACTIVE");
        MembershipChain chain = createMembershipChain(org);

        var service = serviceWithRealWriter();
        LifecycleResult result = service.suspend(lifecycleRequest(admin, org, 1, "suspend-ok"));

        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);
        assertThat(((LifecycleResult.Committed) result).organization().status()).isEqualTo(OrganizationStatus.SUSPENDED);
        try (var connection = openConnection()) {
            assertThat(sessionGeneration(connection, chain.membershipId())).isEqualTo(2);
            assertThat(count(connection, "SELECT count(*) FROM refresh_token_families WHERE id = '" + chain.familyId() + "' AND revoked_at IS NULL")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM refresh_tokens WHERE id = '" + chain.tokenId() + "' AND revoked_at IS NULL")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org + "' AND action_type = 'ORG_STATUS_CHANGED'")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'suspend-ok' AND status = 'COMPLETED'")).isEqualTo(1);
        }
    }

    @Test
    void suspendRollsBackEntireChainWhenRealJdbcAuditWriterViolatesRls() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Suspend rollback", admin, null, "ACTIVE");
        MembershipChain chain = createMembershipChain(org);

        var service = serviceWithRlsCorruptingWriter();
        var request = lifecycleRequest(admin, org, 1, "suspend-rollback");

        assertThatThrownBy(() -> service.suspend(request)).isInstanceOf(RuntimeException.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + org + "' AND status = 'ACTIVE' AND row_version = 1")).isEqualTo(1);
            assertThat(sessionGeneration(connection, chain.membershipId())).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM refresh_token_families WHERE id = '" + chain.familyId() + "' AND revoked_at IS NULL")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM refresh_tokens WHERE id = '" + chain.tokenId() + "' AND revoked_at IS NULL")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org + "'")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'suspend-rollback'")).isZero();
        }
    }

    @Test
    void archiveAtomicallyRollsBackOnRealJdbcAuditWriterRlsViolation() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Archive rollback", admin, null, "ACTIVE");
        MembershipChain chain = createMembershipChain(org);

        var service = serviceWithRlsCorruptingWriter();
        var request = lifecycleRequest(admin, org, 1, "archive-rollback");

        assertThatThrownBy(() -> service.archive(request)).isInstanceOf(RuntimeException.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + org + "' AND status = 'ACTIVE' AND row_version = 1")).isEqualTo(1);
            assertThat(sessionGeneration(connection, chain.membershipId())).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM refresh_token_families WHERE id = '" + chain.familyId() + "' AND revoked_at IS NULL")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org + "'")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'archive-rollback'")).isZero();
        }
    }

    @Test
    void activateCommitsRowVersionBumpWithoutRevivingOldSessionsAndRollsBackOnAuditFailure() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Activate", admin, null, "ACTIVE");
        MembershipChain chain = createMembershipChain(org);
        var service = serviceWithRealWriter();

        LifecycleResult suspended = service.suspend(lifecycleRequest(admin, org, 1, "activate-prep-suspend"));
        assertThat(suspended).isInstanceOf(LifecycleResult.Committed.class);
        int suspendedRowVersion = ((LifecycleResult.Committed) suspended).organization().rowVersion();

        LifecycleResult activated = service.activate(lifecycleRequest(admin, org, suspendedRowVersion, "activate-ok"));
        assertThat(activated).isInstanceOf(LifecycleResult.Committed.class);
        Organization active = ((LifecycleResult.Committed) activated).organization();
        assertThat(active.status()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(active.rowVersion()).isEqualTo(suspendedRowVersion + 1);
        try (var connection = openConnection()) {
            assertThat(sessionGeneration(connection, chain.membershipId())).isEqualTo(2);
            assertThat(count(connection, "SELECT count(*) FROM refresh_tokens WHERE id = '" + chain.tokenId() + "' AND revoked_at IS NULL")).isZero();
        }

        UUID suspendedOrg = organization("Activate rollback", admin, null, "SUSPENDED");
        var rollbackService = serviceWithRlsCorruptingWriter();
        assertThatThrownBy(() -> rollbackService.activate(lifecycleRequest(admin, suspendedOrg, 1, "activate-rollback")))
                .isInstanceOf(RuntimeException.class);
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + suspendedOrg + "' AND status = 'SUSPENDED' AND row_version = 1")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + suspendedOrg + "' AND action_type = 'ORG_STATUS_CHANGED'")).isZero();
        }
    }

    @Test
    void staleRowVersionSecondLifecycleCommandIsRejected() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Stale", admin, null, "ACTIVE");
        var service = serviceWithRealWriter();
        service.suspend(lifecycleRequest(admin, org, 1, "stale-suspend-1"));
        assertThatThrownBy(() -> service.suspend(lifecycleRequest(admin, org, 1, "stale-suspend-2")))
                .isInstanceOf(OrganizationConflictException.class);
    }

    // --------------------------------------------------------------------------
    // updateIdentity (ORG_UPDATE_IDENTITY) — real org-actor and platform-admin authorization,
    // end-to-end through OrganizationLifecycleService
    // --------------------------------------------------------------------------

    @Test
    void updateIdentityCommitsForOrgAdminActorAndWritesOnlyChangedFields() throws Exception {
        UUID org = organization("Old Name", user(), null, "ACTIVE");
        UUID actor = orgAdminActor(org);
        var service = serviceWithRealWriter();

        LifecycleResult result = service.updateIdentity(
                lifecycleRequest(actor, org, 1, "identity-org-admin"), "New Name", null, "Europe/Istanbul");
        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);
        Organization updated = ((LifecycleResult.Committed) result).organization();
        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.rowVersion()).isEqualTo(2);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org
                    + "' AND action_type = 'ORG_SETTING_CHANGED'")).isEqualTo(1);
            // No PLATFORM_ADMIN_ORG_ACCESS audit for a plain org-actor.
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org
                    + "' AND action_type = 'PLATFORM_ADMIN_ORG_ACCESS'")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = "
                    + "'identity-org-admin' AND status = 'COMPLETED'")).isEqualTo(1);
        }
    }

    @Test
    void updateIdentityCommitsForPlatformAdminAndWritesBothAuditRows() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Old Name", admin, null, "ACTIVE");
        var service = serviceWithRealWriter();

        LifecycleResult result = service.updateIdentity(
                lifecycleRequest(admin, org, 1, "identity-platform-admin"), "New Name", "NN", "Europe/Istanbul");
        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org
                    + "' AND action_type = 'ORG_SETTING_CHANGED'")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org
                    + "' AND action_type = 'PLATFORM_ADMIN_ORG_ACCESS'")).isEqualTo(1);
        }
    }

    @Test
    void updateIdentityRejectsActorWithoutMembershipOrAdminStatus() throws Exception {
        UUID org = organization("Old Name", user(), null, "ACTIVE");
        UUID actor = user(); // no membership, no admin row
        var service = serviceWithRealWriter();
        assertThatThrownBy(() -> service.updateIdentity(
                lifecycleRequest(actor, org, 1, "identity-forbidden"), "New Name", null, "Europe/Istanbul"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void updateIdentityRejectsOrgActorWhenOrganizationSuspended() throws Exception {
        UUID org = organization("Old Name", user(), null, "SUSPENDED");
        UUID actor = orgAdminActor(org);
        var service = serviceWithRealWriter();
        // organizations_select_organization's org-actor branch only makes ACTIVE rows visible at
        // all (ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md §2.6: "org-scoped access denied entirely
        // for SUSPENDED/ARCHIVED"); the row is invisible, not merely conflicting.
        assertThatThrownBy(() -> service.updateIdentity(
                lifecycleRequest(actor, org, 1, "identity-suspended-actor"), "New Name", null, "Europe/Istanbul"))
                .isInstanceOf(OrganizationNotVisibleException.class);
    }

    @Test
    void updateIdentityAllowsPlatformAdminWhenOrganizationSuspended() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Old Name", admin, null, "SUSPENDED");
        var service = serviceWithRealWriter();
        LifecycleResult result = service.updateIdentity(
                lifecycleRequest(admin, org, 1, "identity-suspended-admin"), "New Name", null, "Europe/Istanbul");
        assertThat(result).isInstanceOf(LifecycleResult.Committed.class);
    }

    @Test
    void updateIdentityRejectsArchivedOrganizationForBothActorTypes() throws Exception {
        UUID admin = activeAdmin();
        UUID archivedOrg = organization("Old Name", admin, null, "ARCHIVED");
        var service = serviceWithRealWriter();

        // Platform admin CAN lock-read an ARCHIVED row: organizations_update_identity's admin branch
        // keeps the ARCHIVED restriction only in WITH CHECK, not USING -- so SELECT ... FOR UPDATE
        // (which PostgreSQL gates on the UPDATE policy's USING clause too) succeeds, and the service
        // itself throws OrganizationConflictException explicitly, before ever attempting the
        // mutation. WITH CHECK still independently blocks the real UPDATE at the RLS layer (defense
        // in depth) since identity update never changes status, so NEW.status stays 'ARCHIVED'.
        assertThatThrownBy(() -> service.updateIdentity(
                lifecycleRequest(admin, archivedOrg, 1, "identity-archived-admin"), "New Name", null, "Europe/Istanbul"))
                .isInstanceOf(OrganizationConflictException.class);

        // Whole transaction rolled back: no idempotency claim, no audit row, no mutation survived.
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = "
                    + "'identity-archived-admin'")).isZero();
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + archivedOrg + "'"))
                    .isZero();
            assertThat(count(connection, "SELECT count(*) FROM organizations WHERE id = '" + archivedOrg
                    + "' AND name = 'Old Name' AND status = 'ARCHIVED' AND row_version = 1")).isEqualTo(1);
        }

        // An org actor cannot even see an ARCHIVED row at all (org-actor SELECT branch requires
        // ACTIVE) -- not-visible, not merely conflicting. Unaffected by the admin-branch change.
        UUID orgActorArchived = organization("Old Name", user(), null, "ARCHIVED");
        UUID orgActor = orgAdminActor(orgActorArchived);
        assertThatThrownBy(() -> service.updateIdentity(
                lifecycleRequest(orgActor, orgActorArchived, 1, "identity-archived-actor"), "New Name", null, "Europe/Istanbul"))
                .isInstanceOf(OrganizationNotVisibleException.class);
    }

    @Test
    void organizationsUpdateIdentityRlsLetsPlatformAdminLockArchivedRowButRejectsTheActualUpdate() throws Exception {
        // Direct-SQL proof, independent of the Java service: the row is lockable (USING has no
        // ARCHIVED restriction for the admin branch)...
        UUID admin = activeAdmin();
        UUID org = organization("Old Name", admin, null, "ARCHIVED");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_UPDATE_IDENTITY'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            try (var statement = connection.prepareStatement("SELECT id FROM organizations WHERE id = ? FOR UPDATE")) {
                statement.setObject(1, org);
                try (var result = statement.executeQuery()) {
                    assertThat(result.next()).as("admin must be able to lock-read the ARCHIVED row").isTrue();
                }
            }
            // ...but the real UPDATE is independently rejected by WITH CHECK (status <> 'ARCHIVED')
            // even though this statement never touches the status column: NEW.status stays
            // 'ARCHIVED', so WITH CHECK fails regardless of what the SET clause changes.
            assertSqlState("42501", () -> connection.createStatement().executeUpdate(
                    "UPDATE organizations SET name = 'Should Not Apply' WHERE id = '" + org + "'"));
        }
    }

    // --------------------------------------------------------------------------
    // Idempotency — replay vs clash vs claim
    // --------------------------------------------------------------------------

    @Test
    void sameKeySameFingerprintReplaysWithoutSecondMutationOrAudit() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Replay", admin, null, "ACTIVE");
        createMembershipChain(org);
        var service = serviceWithRealWriter();
        var request = lifecycleRequest(admin, org, 1, "replay-key");

        LifecycleResult first = service.suspend(request);
        assertThat(first).isInstanceOf(LifecycleResult.Committed.class);
        int rowVersionAfterFirst = ((LifecycleResult.Committed) first).organization().rowVersion();

        LifecycleResult second = service.suspend(request);
        assertThat(second).isInstanceOf(LifecycleResult.Replayed.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT row_version FROM organizations WHERE id = '" + org + "'")).isEqualTo(rowVersionAfterFirst);
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org + "' AND action_type = 'ORG_STATUS_CHANGED'")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'replay-key'")).isEqualTo(1);
        }
    }

    @Test
    void sameKeyDifferentFingerprintClashesFailClosedWithoutMutation() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Clash", admin, null, "ACTIVE");
        createMembershipChain(org);
        var service = serviceWithRealWriter();
        service.suspend(lifecycleRequest(admin, org, 1, "clash-key"));

        // Same clientMutationId but a deliberately different fingerprint (different rowVersion echo)
        // must clash fail-closed without running a second mutation or writing a second audit row.
        var secondRequest = new LifecycleRequest(admin, org, 2, "clash-key",
                "different-fingerprint-clash-key", "request-clash-key-2", "test-worker",
                Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> service.suspend(secondRequest)).isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void revokedAdminCannotReplayPriorSuccessfulMutation() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Revoked replay", admin, null, "ACTIVE");
        createMembershipChain(org);
        var service = serviceWithRealWriter();
        service.suspend(lifecycleRequest(admin, org, 1, "revoked-replay-key"));

        try (var connection = openConnection()) {
            connection.createStatement().executeUpdate(
                    "UPDATE platform_administrators SET revoked_at = transaction_timestamp() WHERE user_id = '" + admin + "'");
            connection.commit();
        }
        assertThatThrownBy(() -> service.suspend(lifecycleRequest(admin, org, 2, "revoked-replay-key")))
                .isInstanceOf(RuntimeException.class);
    }

    // --------------------------------------------------------------------------
    // AuditEvent typed factory validation — closed surface, no factory bypass
    // --------------------------------------------------------------------------

    @Test
    void auditEventFactoryRejectsInvalidOperationCodes() {
        var factory = new AuditEvent.Factory("request-id");
        // ORG_CREATED requires operation code ORG_CREATE; any other code is rejected.
        assertThatThrownBy(() -> factory.orgCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", 1)).isInstanceOf(IllegalArgumentException.class);
        // Unknown operation code string is rejected.
        assertThatThrownBy(() -> factory.orgCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DELETE", 1)).isInstanceOf(IllegalArgumentException.class);
        // ORG_STATUS_CHANGED requires ORG_SUSPEND/ACTIVATE/ARCHIVE.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_CREATE", "ACTIVE", "SUSPENDED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // ORG_SETTING_CHANGED requires ORG_UPDATE_IDENTITY.
        assertThatThrownBy(() -> factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", new AuditEvent.SettingChange(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditEventFactoryRejectsInvalidStatusOutcomeReasonCodeAndRanges() {
        var factory = new AuditEvent.Factory("request-id");
        // Invalid status enum for ORG_STATUS_CHANGED.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", "UNKNOWN", "SUSPENDED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // rowVersion below 1 is rejected.
        assertThatThrownBy(() -> factory.orgCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_CREATE", 0)).isInstanceOf(IllegalArgumentException.class);
        // Negative revoked count is rejected.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", "ACTIVE", "SUSPENDED", 1, 2, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // Invalid access outcome is rejected.
        assertThatThrownBy(() -> factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "MAYBE", null)).isInstanceOf(IllegalArgumentException.class);
        // Invalid reason code is rejected.
        assertThatThrownBy(() -> factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "FORBIDDEN", "NOT_A_REASON")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditEventFactoryRejectsSettingChangeWithNoChangedField() {
        var factory = new AuditEvent.Factory("request-id");
        // Zero setter calls on SettingChange must not silently produce an all-null payload.
        assertThatThrownBy(() -> factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", new AuditEvent.SettingChange(), 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // --------------------------------------------------------------------------
    // enabledModules / brandColors snapshot validation and canonicalization (fail-fast, no DB)
    // --------------------------------------------------------------------------

    @Test
    void settingChangeRejectsIncompleteModuleSnapshot() {
        // Only 5 of the 6 fixed module codes -- ATT is missing.
        var incomplete = List.of(
                new AuditEvent.ModuleState("CONTENT", true, 0), new AuditEvent.ModuleState("PROGRAM", true, 1),
                new AuditEvent.ModuleState("PROGRESS", true, 2), new AuditEvent.ModuleState("EXPORT", true, 3),
                new AuditEvent.ModuleState("AUDIT", true, 4));
        assertThatThrownBy(() -> new AuditEvent.SettingChange().enabledModules(FULL_MODULE_SNAPSHOT_SCRAMBLED, incomplete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingChangeRejectsDuplicateModuleCode() {
        var duplicated = new java.util.ArrayList<>(FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        duplicated.remove(duplicated.size() - 1); // drop AUDIT
        duplicated.add(new AuditEvent.ModuleState("ATT", false, 5)); // duplicate ATT instead
        assertThatThrownBy(() -> new AuditEvent.SettingChange().enabledModules(FULL_MODULE_SNAPSHOT_SCRAMBLED, duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingChangeRejectsUnknownModuleCode() {
        var withUnknown = new java.util.ArrayList<>(FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        withUnknown.remove(withUnknown.size() - 1); // drop AUDIT
        withUnknown.add(new AuditEvent.ModuleState("NOTIFY", true, 5)); // NOTIFY is not manageable in V1
        assertThatThrownBy(() -> new AuditEvent.SettingChange().enabledModules(FULL_MODULE_SNAPSHOT_SCRAMBLED, withUnknown))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moduleStateRejectsSortOrderOutOfRange() {
        assertThatThrownBy(() -> new AuditEvent.ModuleState("ATT", true, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent.ModuleState("ATT", true, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new AuditEvent.ModuleState("ATT", true, 999).sortOrder()).isEqualTo(999);
    }

    @Test
    void settingChangeCanonicalizesModuleSnapshotRegardlessOfCallerOrder() {
        var change = new AuditEvent.SettingChange().enabledModules(
                FULL_MODULE_SNAPSHOT_SCRAMBLED, FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        var factory = new AuditEvent.Factory("request-id");
        var event = factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", change, 2);
        var oldModules = ((AuditEvent.SettingPayload) event.oldValue()).enabledModules();
        assertThat(oldModules).extracting(AuditEvent.ModuleState::moduleCode)
                .containsExactly("ATT", "CONTENT", "PROGRAM", "PROGRESS", "EXPORT", "AUDIT");
        assertThat(oldModules).extracting(AuditEvent.ModuleState::sortOrder).containsExactly(0, 1, 2, 3, 4, 5);
    }

    @Test
    void settingChangeRejectsBrandColorsExceedingMaximumOfTwenty() {
        var tooMany = new java.util.ArrayList<AuditEvent.BrandColor>();
        for (int i = 0; i < 21; i++) {
            tooMany.add(new AuditEvent.BrandColor(String.format("#%06X", i), i));
        }
        assertThatThrownBy(() -> new AuditEvent.SettingChange().brandColors(List.of(), tooMany))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingChangeAcceptsExactlyTwentyBrandColors() {
        var exactlyTwenty = new java.util.ArrayList<AuditEvent.BrandColor>();
        for (int i = 0; i < 20; i++) {
            exactlyTwenty.add(new AuditEvent.BrandColor(String.format("#%06X", i), i));
        }
        var change = new AuditEvent.SettingChange().brandColors(List.of(), exactlyTwenty);
        var factory = new AuditEvent.Factory("request-id");
        // Building the event (which internally requires hasChange()) must not throw.
        var event = factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_BRAND_COLORS", change, 2);
        assertThat(((AuditEvent.SettingPayload) event.newValue()).brandColors()).hasSize(20);
    }

    @Test
    void settingChangeRejectsDuplicateBrandColorHex() {
        var duplicated = List.of(new AuditEvent.BrandColor("#FFC107", 0), new AuditEvent.BrandColor("#FFC107", 1));
        assertThatThrownBy(() -> new AuditEvent.SettingChange().brandColors(List.of(), duplicated))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void brandColorRejectsSortOrderOutOfRangeAndInvalidHex() {
        assertThatThrownBy(() -> new AuditEvent.BrandColor("#FFC107", -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent.BrandColor("#FFC107", 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent.BrandColor("FFC107", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AuditEvent.BrandColor("#FFC10", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void settingChangeCanonicalizesBrandColorsRegardlessOfCallerOrder() {
        // Deliberately reverse-sorted, plus a sortOrder tie broken alphabetically by colorHex.
        var reversed = List.of(
                new AuditEvent.BrandColor("#FF5722", 1), new AuditEvent.BrandColor("#0000FF", 0),
                new AuditEvent.BrandColor("#AAAAAA", 0));
        var change = new AuditEvent.SettingChange().brandColors(List.of(), reversed);
        var factory = new AuditEvent.Factory("request-id");
        var event = factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_BRAND_COLORS", change, 2);
        var newColors = ((AuditEvent.SettingPayload) event.newValue()).brandColors();
        assertThat(newColors).extracting(AuditEvent.BrandColor::colorHex)
                .containsExactly("#0000FF", "#AAAAAA", "#FF5722");
    }

    @Test
    void auditEventFactoryBuildsValidTypedEventsAndSettingChangeRequiresAChange() {
        var factory = new AuditEvent.Factory("request-id");
        var access = factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "FORBIDDEN", "FORBIDDEN");
        assertThat(access.actionType()).isEqualTo("PLATFORM_ADMIN_ORG_ACCESS");
        assertThat(access.oldValue()).isInstanceOf(AuditEvent.NullPayload.class);
        assertThat(access.newValue()).isInstanceOf(AuditEvent.NullPayload.class);
        assertThat(access.eventMetadata()).isInstanceOf(AuditEvent.AccessMetadata.class);

        var created = factory.orgCreated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_CREATE", 1);
        assertThat(created.oldValue()).isInstanceOf(AuditEvent.NullPayload.class);
        assertThat(created.newValue()).isInstanceOf(AuditEvent.StatusPayload.class);
        assertThat(((AuditEvent.StatusPayload) created.newValue()).status()).isEqualTo(AuditEvent.OrgStatus.ACTIVE);

        // SettingChange requires at least a changed field; unchanged values are rejected.
        assertThatThrownBy(() -> new AuditEvent.SettingChange().name("same", "same"))
                .isInstanceOf(IllegalArgumentException.class);
        var change = new AuditEvent.SettingChange().name("Old", "New");
        var setting = factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", change, 3);
        assertThat(setting.newValue()).isInstanceOf(AuditEvent.SettingPayload.class);
        assertThat(((AuditEvent.SettingPayload) setting.newValue()).name()).isEqualTo("New");
        // oldValue must carry the real prior value, never the new value written twice.
        assertThat(setting.oldValue()).isInstanceOf(AuditEvent.SettingPayload.class);
        assertThat(((AuditEvent.SettingPayload) setting.oldValue()).name()).isEqualTo("Old");
    }

    @Test
    void auditEventFactoryRejectsInvalidStatusTransitions() {
        var factory = new AuditEvent.Factory("request-id");
        // ORG_SUSPEND is only valid ACTIVE -> SUSPENDED.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", "SUSPENDED", "SUSPENDED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", "ARCHIVED", "SUSPENDED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // ORG_ACTIVATE is only valid SUSPENDED -> ACTIVE.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ACTIVATE", "ACTIVE", "ACTIVE", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ACTIVATE", "ARCHIVED", "ACTIVE", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // ORG_ARCHIVE is only valid from ACTIVE or SUSPENDED -> ARCHIVED.
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ARCHIVE", "ARCHIVED", "ARCHIVED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ARCHIVE", "ACTIVE", "SUSPENDED", 1, 2, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        // The matching transitions must all succeed.
        assertThat(factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_SUSPEND", "ACTIVE", "SUSPENDED", 1, 2, 0, 0, 0)).isNotNull();
        assertThat(factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ACTIVATE", "SUSPENDED", "ACTIVE", 1, 2, 0, 0, 0)).isNotNull();
        assertThat(factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ARCHIVE", "ACTIVE", "ARCHIVED", 1, 2, 0, 0, 0)).isNotNull();
        assertThat(factory.orgStatusChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_ARCHIVE", "SUSPENDED", "ARCHIVED", 1, 2, 0, 0, 0)).isNotNull();
    }

    @Test
    void auditEventFactoryEnforcesAccessOutcomeReasonCodeCorrelation() {
        var factory = new AuditEvent.Factory("request-id");
        // ALLOWED must never carry a reason code.
        assertThatThrownBy(() -> factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "ALLOWED", "FORBIDDEN"))
                .isInstanceOf(IllegalArgumentException.class);
        // FORBIDDEN must always carry reason code FORBIDDEN, not a null reason.
        assertThatThrownBy(() -> factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "FORBIDDEN", null))
                .isInstanceOf(IllegalArgumentException.class);
        // The matching combinations must succeed.
        var allowed = factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "ALLOWED", null);
        assertThat(((AuditEvent.AccessMetadata) allowed.eventMetadata()).outcome()).isEqualTo(AuditEvent.AccessOutcome.ALLOWED);
        assertThat(allowed.reasonCode()).isNull();
        var forbidden = factory.platformAdminOrgAccess(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_DETAIL", "FORBIDDEN", "FORBIDDEN");
        assertThat(forbidden.reasonCode()).isEqualTo("FORBIDDEN");
    }

    @Test
    void auditSettingChangedV1PersistsDistinctOldAndNewValuesInPostgres() throws Exception {
        UUID org = organization("Setting change proof", user(), null, "ACTIVE");
        // A real, active ORG_ADMIN membership -- the org-actor branch requires
        // org_actor_has_identity_update_access to hold.
        UUID actor = orgAdminActor(org);
        UUID auditId = UUID.randomUUID();
        UUID oldLogoId = UUID.randomUUID();

        // A multi-field change: name, logoAssetId and the full enabledModules snapshot all move in
        // the same event. This exercises old/new separation across fields at once, plus an explicit
        // null clear (logoAssetId) and canonicalization of a deliberately scrambled module snapshot
        // order. v1 only (no secondaryColor/brandColors) -- proves the pre-existing ORG-001 identity
        // path still works.
        var change = new AuditEvent.SettingChange()
                .name("Old Name", "New Name")
                .logoAssetId(oldLogoId, null)
                .enabledModules(FULL_MODULE_SNAPSHOT_SCRAMBLED, FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        var event = new AuditEvent.Factory("request-setting-proof")
                .orgSettingChanged(auditId, org, actor, org, "ORG_UPDATE_IDENTITY", change, 3);
        assertThat(event.payloadSchemaVersion()).isEqualTo((short) 1);

        writeAuditAsOrgRuntime(actor, org, "ORG_UPDATE_IDENTITY", event);

        try (var connection = openConnection()) {
            assertThat(jsonbInt(connection, auditId, "payload_schema_version")).isEqualTo(1);

            // name: real old value on one side, real new value on the other -- never New -> New.
            assertThat(jsonbText(connection, auditId, "old_value", "name")).isEqualTo("Old Name");
            assertThat(jsonbText(connection, auditId, "new_value", "name")).isEqualTo("New Name");

            // logoAssetId: old carries the real UUID; new is an explicit JSON null (key present).
            assertThat(jsonbText(connection, auditId, "old_value", "logoAssetId")).isEqualTo(oldLogoId.toString());
            assertThat(jsonbHasKey(connection, auditId, "new_value", "logoAssetId")).isTrue();
            assertThat(jsonbText(connection, auditId, "new_value", "logoAssetId")).isNull();

            // enabledModules: full 6-code snapshot on both sides, persisted in canonical ascending
            // sortOrder regardless of the deliberately scrambled input order; only PROGRAM's
            // isEnabled differs between old and new.
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "enabledModules", "moduleCode"))
                    .containsExactly("ATT", "CONTENT", "PROGRAM", "PROGRESS", "EXPORT", "AUDIT");
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "enabledModules", "sortOrder"))
                    .containsExactly("0", "1", "2", "3", "4", "5");
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "enabledModules", "isEnabled"))
                    .containsOnly("true");
            assertThat(jsonbArrayObjectField(connection, auditId, "new_value", "enabledModules", "moduleCode"))
                    .containsExactly("ATT", "CONTENT", "PROGRAM", "PROGRESS", "EXPORT", "AUDIT");
            assertThat(jsonbArrayObjectField(connection, auditId, "new_value", "enabledModules", "isEnabled"))
                    .containsExactly("true", "true", "false", "true", "true", "true");

            // Untouched fields, including the v2-only ones, must be entirely absent from both sides.
            for (String field : List.of("shortName", "defaultTimezone", "primaryColor", "secondaryColor",
                    "brandColors", "attendanceStatuses")) {
                assertThat(jsonbHasKey(connection, auditId, "old_value", field)).as("old_value.%s", field).isFalse();
                assertThat(jsonbHasKey(connection, auditId, "new_value", field)).as("new_value.%s", field).isFalse();
            }
        }
    }

    @Test
    void auditEventFactoryRejectsV2OnlyFieldsInV1SettingChanged() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange().secondaryColor("#2E7D32", "#1565C0");
        assertThatThrownBy(() -> factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", change, 2))
                .isInstanceOf(IllegalArgumentException.class);

        var brandChange = new AuditEvent.SettingChange()
                .brandColors(List.of(), List.of(new AuditEvent.BrandColor("#FFC107", 0)));
        assertThatThrownBy(() -> factory.orgSettingChanged(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", brandChange, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditEventFactoryRejectsWrongOperationCodeForSettingChangedV2() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange().secondaryColor("#2E7D32", "#1565C0");
        // ORG_UPDATE_IDENTITY (v1's own operation code) must not be accepted by the v2 factory.
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_IDENTITY", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
        // An unrelated valid-but-wrong code (e.g. a read-only view code) must also be rejected.
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_VIEW_BRAND", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Each ORG-002 write operation is tested SEPARATELY (never combined in one SettingChange) --
    // the operation-code x changedFields matrix (item 4) requires ORG_UPDATE_BRAND to touch only
    // primaryColor/secondaryColor, ORG_UPDATE_BRAND_COLORS only brandColors, ORG_UPDATE_MODULES only
    // enabledModules, ORG_UPLOAD_LOGO/ORG_REMOVE_LOGO only logoAssetId. All 5 write codes have no
    // org-actor audit-write branch (default-deny, see V3__...); the actor here must be an active
    // platform administrator with the support-access flag server-set.

    @Test
    void auditSettingChangedV2PersistsSecondaryColorViaOrgUpdateBrandInPostgres() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Brand color v2 proof", admin, null, "ACTIVE");
        UUID auditId = UUID.randomUUID();

        var change = new AuditEvent.SettingChange().secondaryColor("#E65100", "#00796B");
        var event = new AuditEvent.Factory("request-brand-v2-proof")
                .orgSettingChangedV2(auditId, org, admin, org, "ORG_UPDATE_BRAND", change, 5);
        assertThat(event.payloadSchemaVersion()).isEqualTo((short) 2);

        writeAuditAsPlatformAdmin(admin, org, "ORG_UPDATE_BRAND", event);

        try (var connection = openConnection()) {
            assertThat(jsonbInt(connection, auditId, "payload_schema_version")).isEqualTo(2);
            assertThat(jsonbText(connection, auditId, "old_value", "secondaryColor")).isEqualTo("#E65100");
            assertThat(jsonbText(connection, auditId, "new_value", "secondaryColor")).isEqualTo("#00796B");
            for (String field : List.of("name", "shortName", "defaultTimezone", "primaryColor", "logoAssetId",
                    "enabledModules", "brandColors", "attendanceStatuses")) {
                assertThat(jsonbHasKey(connection, auditId, "old_value", field)).as("old_value.%s", field).isFalse();
                assertThat(jsonbHasKey(connection, auditId, "new_value", field)).as("new_value.%s", field).isFalse();
            }
        }
    }

    @Test
    void auditSettingChangedV2RejectsBrandColorsUnderOrgUpdateBrandOperationCode() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange()
                .brandColors(List.of(), List.of(new AuditEvent.BrandColor("#FFC107", 0)));
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_BRAND", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditSettingChangedV2PersistsBrandColorsSnapshotViaOrgUpdateBrandColorsInPostgres() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Palette v2 proof", admin, null, "ACTIVE");
        UUID auditId = UUID.randomUUID();

        // Deliberately reverse-sorted input -- must persist in canonical ascending-sortOrder form.
        var change = new AuditEvent.SettingChange().brandColors(
                List.of(new AuditEvent.BrandColor("#FF5722", 1), new AuditEvent.BrandColor("#FFC107", 0)),
                List.of());
        var event = new AuditEvent.Factory("request-palette-v2-proof")
                .orgSettingChangedV2(auditId, org, admin, org, "ORG_UPDATE_BRAND_COLORS", change, 5);
        assertThat(event.payloadSchemaVersion()).isEqualTo((short) 2);

        writeAuditAsPlatformAdmin(admin, org, "ORG_UPDATE_BRAND_COLORS", event);

        try (var connection = openConnection()) {
            // old carries the real, canonically-ordered palette; new is an explicit empty array
            // (key present), never omitted.
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "brandColors", "colorHex"))
                    .containsExactly("#FFC107", "#FF5722");
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "brandColors", "sortOrder"))
                    .containsExactly("0", "1");
            assertThat(jsonbHasKey(connection, auditId, "new_value", "brandColors")).isTrue();
            assertThat(jsonbArrayObjectField(connection, auditId, "new_value", "brandColors", "colorHex")).isEmpty();
            for (String field : List.of("name", "shortName", "defaultTimezone", "primaryColor", "secondaryColor",
                    "logoAssetId", "enabledModules", "attendanceStatuses")) {
                assertThat(jsonbHasKey(connection, auditId, "old_value", field)).as("old_value.%s", field).isFalse();
                assertThat(jsonbHasKey(connection, auditId, "new_value", field)).as("new_value.%s", field).isFalse();
            }
        }
    }

    @Test
    void auditSettingChangedV2RejectsEnabledModulesUnderOrgUpdateBrandColorsOperationCode() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange().enabledModules(
                FULL_MODULE_SNAPSHOT_SCRAMBLED, FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_BRAND_COLORS", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditSettingChangedV2PersistsModuleSnapshotViaOrgUpdateModulesInPostgres() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Modules v2 proof", admin, null, "ACTIVE");
        UUID auditId = UUID.randomUUID();

        var change = new AuditEvent.SettingChange().enabledModules(
                FULL_MODULE_SNAPSHOT_SCRAMBLED, FULL_MODULE_SNAPSHOT_WITH_PROGRAM_DISABLED);
        var event = new AuditEvent.Factory("request-modules-v2-proof")
                .orgSettingChangedV2(auditId, org, admin, org, "ORG_UPDATE_MODULES", change, 5);
        assertThat(event.payloadSchemaVersion()).isEqualTo((short) 2);

        writeAuditAsPlatformAdmin(admin, org, "ORG_UPDATE_MODULES", event);

        try (var connection = openConnection()) {
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "enabledModules", "moduleCode"))
                    .containsExactly("ATT", "CONTENT", "PROGRAM", "PROGRESS", "EXPORT", "AUDIT");
            assertThat(jsonbArrayObjectField(connection, auditId, "old_value", "enabledModules", "sortOrder"))
                    .containsExactly("0", "1", "2", "3", "4", "5");
            assertThat(jsonbArrayObjectField(connection, auditId, "new_value", "enabledModules", "isEnabled"))
                    .containsExactly("true", "true", "false", "true", "true", "true");
            for (String field : List.of("name", "shortName", "defaultTimezone", "primaryColor", "secondaryColor",
                    "logoAssetId", "brandColors", "attendanceStatuses")) {
                assertThat(jsonbHasKey(connection, auditId, "old_value", field)).as("old_value.%s", field).isFalse();
                assertThat(jsonbHasKey(connection, auditId, "new_value", field)).as("new_value.%s", field).isFalse();
            }
        }
    }

    @Test
    void auditSettingChangedV2RejectsSecondaryColorUnderOrgUpdateModulesOperationCode() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange().secondaryColor("#E65100", "#00796B");
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPDATE_MODULES", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditSettingChangedV2PersistsLogoAssetIdViaUploadAndRemoveLogoInPostgres() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Logo v2 proof", admin, null, "ACTIVE");
        UUID oldLogoId = UUID.randomUUID();
        UUID newLogoId = UUID.randomUUID();

        // ORG_UPLOAD_LOGO: null (or a prior asset) -> a new asset id.
        UUID uploadAuditId = UUID.randomUUID();
        var uploadChange = new AuditEvent.SettingChange().logoAssetId(null, newLogoId);
        var uploadEvent = new AuditEvent.Factory("request-logo-upload-proof")
                .orgSettingChangedV2(uploadAuditId, org, admin, org, "ORG_UPLOAD_LOGO", uploadChange, 5);
        writeAuditAsPlatformAdmin(admin, org, "ORG_UPLOAD_LOGO", uploadEvent);

        // ORG_REMOVE_LOGO: an existing asset id -> explicit null clear.
        UUID removeAuditId = UUID.randomUUID();
        var removeChange = new AuditEvent.SettingChange().logoAssetId(oldLogoId, null);
        var removeEvent = new AuditEvent.Factory("request-logo-remove-proof")
                .orgSettingChangedV2(removeAuditId, org, admin, org, "ORG_REMOVE_LOGO", removeChange, 6);
        writeAuditAsPlatformAdmin(admin, org, "ORG_REMOVE_LOGO", removeEvent);

        try (var connection = openConnection()) {
            assertThat(jsonbHasKey(connection, uploadAuditId, "old_value", "logoAssetId")).isTrue();
            assertThat(jsonbText(connection, uploadAuditId, "old_value", "logoAssetId")).isNull();
            assertThat(jsonbText(connection, uploadAuditId, "new_value", "logoAssetId")).isEqualTo(newLogoId.toString());

            assertThat(jsonbText(connection, removeAuditId, "old_value", "logoAssetId")).isEqualTo(oldLogoId.toString());
            assertThat(jsonbHasKey(connection, removeAuditId, "new_value", "logoAssetId")).isTrue();
            assertThat(jsonbText(connection, removeAuditId, "new_value", "logoAssetId")).isNull();
        }
    }

    @Test
    void auditSettingChangedV2RejectsNonLogoFieldsUnderUploadAndRemoveLogoOperationCodes() {
        var factory = new AuditEvent.Factory("request-id");
        var nameChange = new AuditEvent.SettingChange().name("Old", "New");
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_UPLOAD_LOGO", nameChange, 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_REMOVE_LOGO", nameChange, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditSettingChangedV2RejectsOrgViewLogoOperationCodeEntirely() {
        var factory = new AuditEvent.Factory("request-id");
        var change = new AuditEvent.SettingChange().logoAssetId(null, UUID.randomUUID());
        // ORG_VIEW_LOGO is read-only and must never be able to produce ORG_SETTING_CHANGED.
        assertThatThrownBy(() -> factory.orgSettingChangedV2(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "ORG_VIEW_LOGO", change, 2))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void auditLogsInsertRejectsOrgViewLogoOperationCodeForOrgSettingChangedInPostgres() throws Exception {
        // Defense-in-depth: even if a caller somehow built an ORG_SETTING_CHANGED row tagged with
        // event_metadata.operationCode = ORG_VIEW_LOGO, the DB policy itself must reject it --
        // ORG_VIEW_LOGO is not in the operation_code allow-list for this action type at all.
        UUID admin = activeAdmin();
        UUID org = organization("View logo rejected", admin, null, "ACTIVE");
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'ORG_VIEW_LOGO'");
            assertSqlState("42501", () -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, old_value, new_value, event_metadata, is_undo) "
                            + "VALUES ('" + UUID.randomUUID() + "','" + org + "','" + admin + "','ORG_SETTING_CHANGED',2,"
                            + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                            + "NULL,'{\"logoAssetId\":\"" + UUID.randomUUID() + "\"}'::jsonb,"
                            + "'{\"operationCode\":\"ORG_VIEW_LOGO\"}'::jsonb,false)"));
        }
    }

    @Test
    void auditLogsInsertRejectsOrgActorPathForOrg002WriteOperationCodesInPostgres() throws Exception {
        // ORG-005 adds its own active-membership authorization paths for brand and modules;
        // file operations remain platform-admin-only until their separate contract lands.
        UUID org = organization("Org-actor default-deny", user(), null, "ACTIVE");
        UUID actor = orgAdminActor(org);
        for (String operationCode : List.of("ORG_UPLOAD_LOGO", "ORG_REMOVE_LOGO")) {
            try (var connection = openConnection()) {
                connection.createStatement().execute("SET ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operationCode + "'");
                assertSqlState("42501", () -> connection.createStatement().execute(
                        "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                                + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                                + "requires_operation_group, target_entity_id, old_value, new_value, event_metadata, is_undo) "
                                + "VALUES ('" + UUID.randomUUID() + "','" + org + "','" + actor + "','ORG_SETTING_CHANGED',2,"
                                + "'ORGANIZATION','ORGANIZATION','DATA_MUTATION',true,false,false,'" + org + "',"
                                + "NULL,'{\"primaryColor\":\"#123456\"}'::jsonb,"
                                + "'{\"operationCode\":\"" + operationCode + "\"}'::jsonb,false)"));
            }
        }
    }

    private void writeAuditAsOrgRuntime(UUID actor, UUID organizationId, String operationCode, AuditEvent event) {
        transactions().execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                connection.createStatement().execute("SET LOCAL ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + organizationId + "'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operationCode + "'");
            } catch (SQLException exception) {
                throw new RuntimeException("RLS context kurulamadı", exception);
            }
            new JdbcAuditWriter(dataSource).write(event);
            return null;
        });
    }

    /**
     * ORG-002's 5 write codes (ORG_UPDATE_BRAND/BRAND_COLORS/MODULES/UPLOAD_LOGO/REMOVE_LOGO) have
     * no org-actor audit-write branch (default-deny, see {@code V3__...}); {@code actor} must be an
     * active platform administrator and the support-access flag must be server-set.
     */
    private void writeAuditAsPlatformAdmin(UUID admin, UUID organizationId, String operationCode, AuditEvent event) {
        transactions().execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            try {
                connection.createStatement().execute("SET LOCAL ROLE org_runtime");
                connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
                connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + admin + "'");
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + organizationId + "'");
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operationCode + "'");
                connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            } catch (SQLException exception) {
                throw new RuntimeException("RLS context kurulamadı", exception);
            }
            new JdbcAuditWriter(dataSource).write(event);
            return null;
        });
    }

    private static String jsonbText(Connection connection, UUID auditId, String column, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + "->>'" + field + "' FROM audit_logs WHERE id = ?")) {
            statement.setObject(1, auditId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private static boolean jsonbHasKey(Connection connection, UUID auditId, String column, String field) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT jsonb_exists(" + column + ", ?) FROM audit_logs WHERE id = ?")) {
            statement.setString(1, field);
            statement.setObject(2, auditId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    private static int jsonbInt(Connection connection, UUID auditId, String column) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT " + column + " FROM audit_logs WHERE id = ?")) {
            statement.setObject(1, auditId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    /**
     * Extracts one sub-field's text value from each element of a JSON array column, in array
     * order ({@code WITH ORDINALITY}) -- e.g. every {@code moduleCode} in {@code enabledModules},
     * in the order the array was written, so structural-snapshot ordering can be asserted directly.
     */
    private static List<String> jsonbArrayObjectField(
            Connection connection, UUID auditId, String column, String arrayField, String subField)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT t.elem ->> '" + subField + "' FROM audit_logs, "
                        + "jsonb_array_elements(" + column + "->'" + arrayField + "') WITH ORDINALITY AS t(elem, ord) "
                        + "WHERE audit_logs.id = ? ORDER BY t.ord")) {
            statement.setObject(1, auditId);
            try (ResultSet result = statement.executeQuery()) {
                var values = new java.util.ArrayList<String>();
                while (result.next()) {
                    values.add(result.getString(1));
                }
                return values;
            }
        }
    }

    @Test
    void auditEventHasNoPublicCanonicalConstructorOrMapBasedFactory() {
        // The AuditEvent surface exposes only accessors, typed value objects and the Factory. There
        // is no public constructor and no Map/JSON entry point; reflection-free callers cannot build
        // an event with arbitrary payloads.
        var publicCtors = new java.util.ArrayList<java.lang.reflect.Constructor<?>>();
        for (var ctor : AuditEvent.class.getDeclaredConstructors()) {
            if (java.lang.reflect.Modifier.isPublic(ctor.getModifiers())) {
                publicCtors.add(ctor);
            }
        }
        assertThat(publicCtors).isEmpty();
        // AuditPayload / AuditMetadata are sealed interfaces; only the permitted records implement them.
        assertThat(AuditEvent.AuditPayload.class.isSealed()).isTrue();
        assertThat(AuditEvent.AuditMetadata.class.isSealed()).isTrue();
        assertThat(AuditEvent.AuditPayload.class.getPermittedSubclasses()).hasSize(3);
        assertThat(AuditEvent.AuditMetadata.class.getPermittedSubclasses()).hasSize(4);
    }

    // --------------------------------------------------------------------------
    // Concurrent idempotency — real PostgreSQL race behaviour
    // --------------------------------------------------------------------------

    @Test
    void twoConcurrentSameKeySameFingerprintProducersDoNotDuplicateMutationOrThrow500() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Concurrent same", admin, null, "ACTIVE");
        MembershipChain chain = createMembershipChain(org);

        var claimHeld = new java.util.concurrent.CountDownLatch(1);
        var releaseFirst = new java.util.concurrent.CountDownLatch(1);

        // The first producer's AuditWriter is the last write before markCompleted/commit, so signalling
        // from it proves the claim, org row lock and mutation have already happened inside a still-open
        // transaction, then blocks until released.
        AuditWriter barrierWriter = event -> {
            claimHeld.countDown();
            awaitQuietly(releaseFirst);
            new JdbcAuditWriter(dataSource).write(event);
        };
        var first = new OrganizationLifecycleService(new JdbcOrganizationRepository(dataSource), dataSource,
                transactionManager, barrierWriter, new JdbcIdempotencyRecorder(dataSource), RESULT_SERIALIZER, actor -> { });

        // The second producer's IdempotencyRecorder captures its own backend pid on the transactional
        // connection immediately before delegating to the real recorder (which is where it blocks) --
        // this is what lets the main thread find and watch the exact right backend in pg_stat_activity.
        var secondPid = new java.util.concurrent.CompletableFuture<Integer>();
        var second = new OrganizationLifecycleService(new JdbcOrganizationRepository(dataSource), dataSource,
                transactionManager, new JdbcAuditWriter(dataSource), pidCapturingRecorder(secondPid), RESULT_SERIALIZER, actor -> { });
        var request = lifecycleRequest(admin, org, 1, "concurrent-same-key");

        var firstResult = java.util.concurrent.CompletableFuture.supplyAsync(() -> first.suspend(request));
        assertThat(claimHeld.await(5, java.util.concurrent.TimeUnit.SECONDS))
                .as("first producer must claim and mutate before the race starts")
                .isTrue();

        // The second producer necessarily races against the first's still-uncommitted claim: its own
        // INSERT ON CONFLICT DO NOTHING conflicts with the uncommitted row and blocks on
        // SELECT ... FOR UPDATE until the first transaction commits or rolls back. Run it on its own
        // thread so the main thread can release the first producer only once proven blocked.
        var secondResult = new java.util.concurrent.atomic.AtomicReference<LifecycleResult>();
        var secondException = new java.util.concurrent.atomic.AtomicReference<Exception>();
        var secondFuture = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                secondResult.set(second.suspend(request));
            } catch (Exception exception) {
                secondException.set(exception);
            }
        });

        // Hard DB proof, not timing assumption: block releasing the first producer until
        // pg_stat_activity shows the second producer's backend genuinely parked on a Lock wait. A
        // timeout here (pid never arrives, or the backend never reaches Lock wait) fails the test
        // outright -- it is never swallowed as "good enough".
        int pid = secondPid.get(5, java.util.concurrent.TimeUnit.SECONDS);
        awaitBackendBlockedOnLock(pid, 5000);

        releaseFirst.countDown();

        LifecycleResult committed = firstResult.join();
        secondFuture.join();
        assertThat(committed).isInstanceOf(LifecycleResult.Committed.class);

        // The second producer was proven parked on the row lock before the first committed, so by the
        // time it unblocks the first has unconditionally already committed: the outcome must be
        // exactly a terminal Replay. Pending is not acceptable in this proven ordering, and any
        // exception (IdempotencyRecordException, a unique-constraint violation,
        // OrganizationConflictException, or anything else) fails this test outright.
        Exception failure = secondException.get();
        assertThat(failure)
                .as("second producer must not throw once the first has committed; got %s", failure)
                .isNull();
        assertThat(secondResult.get()).isInstanceOf(LifecycleResult.Replayed.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM audit_logs WHERE organization_id = '" + org + "' AND action_type = 'ORG_STATUS_CHANGED'")).isEqualTo(1);
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE client_mutation_id = 'concurrent-same-key'")).isEqualTo(1);
            assertThat(sessionGeneration(connection, chain.membershipId())).isEqualTo(2);
        }
    }

    @Test
    void sameKeyDifferentFingerprintClashesStablyUnderConcurrency() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Concurrent clash", admin, null, "ACTIVE");
        createMembershipChain(org);
        var first = new OrganizationLifecycleService(new JdbcOrganizationRepository(dataSource), dataSource,
                transactionManager, new JdbcAuditWriter(dataSource), new JdbcIdempotencyRecorder(dataSource), RESULT_SERIALIZER, actor -> { });
        var second = new OrganizationLifecycleService(new JdbcOrganizationRepository(dataSource), dataSource,
                transactionManager, new JdbcAuditWriter(dataSource), new JdbcIdempotencyRecorder(dataSource), RESULT_SERIALIZER, actor -> { });

        var firstRequest = lifecycleRequest(admin, org, 1, "concurrent-clash-key");
        var firstFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> first.suspend(firstRequest));
        firstFuture.join();

        var secondRequest = new LifecycleRequest(admin, org, 2, "concurrent-clash-key",
                "different-fingerprint", "request-clash", "test-worker",
                Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
        assertThatThrownBy(() -> second.suspend(secondRequest))
                .isInstanceOf(IdempotencyKeyReusedException.class);
    }

    @Test
    void expiredPendingLeaseIsReclaimedWithGreaterGeneration() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Lease reclaim", admin, null, "ACTIVE");
        createMembershipChain(org);
        var recorder = new JdbcIdempotencyRecorder(dataSource);

        // Seed an expired PENDING claim directly so the re-claim path can be exercised deterministically.
        UUID claimId = seedPendingClaim(admin, org, "lease-reclaim-key", "fingerprint-lease-reclaim-key",
                "ORG_SUSPEND", Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
        long generationBefore;
        try (var connection = openConnection()) {
            generationBefore = readLeaseGeneration(connection, claimId);
        }

        // resolveOrClaim with the same key + fingerprint should re-claim the expired lease and return
        // Claimed with a strictly greater lease_generation.
        IdempotencyOutcome outcome = transactions().execute(status -> recorder.resolveOrClaim(
                "ORGANIZATION", org, admin, "lease-reclaim-key", "ORG_SUSPEND", "fingerprint-lease-reclaim-key",
                "reclaim-worker", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300)));
        assertThat(outcome).isInstanceOf(IdempotencyOutcome.Claimed.class);
        var claimed = (IdempotencyOutcome.Claimed) outcome;
        assertThat(claimed.leaseGeneration()).isEqualTo(generationBefore + 1);
        assertThat(claimed.leaseOwner()).isEqualTo("reclaim-worker");
    }

    @Test
    void staleOwnerCannotMarkCompletedAfterLeaseExpiresDuringSameTransaction() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Lease expiry mid-tx", admin, null, "ACTIVE");
        var recorder = new JdbcIdempotencyRecorder(dataSource);

        // Seed a PENDING claim whose lease has not expired yet.
        Instant leaseExpiresAt = Instant.now().plusMillis(400);
        UUID claimId = seedPendingClaim(admin, org, "mid-tx-expiry-key", "fingerprint-mid-tx-expiry",
                "ORG_SUSPEND", leaseExpiresAt, Instant.now().plusSeconds(300));

        // The enclosing transaction starts here (freezing transaction_timestamp()) while the lease is
        // still valid. Real wall-clock time then advances past lease_expires_at before markCompleted
        // runs, inside the very same still-open transaction. Gating on clock_timestamp() (not
        // transaction_timestamp()) is what makes this stale owner fail instead of wrongly completing.
        assertThatThrownBy(() -> transactions().execute(status -> {
            try {
                Thread.sleep(600);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            recorder.markCompleted(claimId, "seed-worker", 1L, org, (short) 200, Instant.now().plusSeconds(300));
            return null;
        })).isInstanceOf(RuntimeException.class);

        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE id = '"
                    + claimId + "' AND status = 'PENDING'")).isEqualTo(1);
        }
    }

    @Test
    void concurrentDifferentFingerprintWhilePendingClashesInsteadOfWaitingOnLease() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Concurrent pending clash", admin, null, "ACTIVE");
        var recorder = new JdbcIdempotencyRecorder(dataSource);
        var key = "concurrent-pending-clash-key";

        var firstClaimed = new java.util.concurrent.CountDownLatch(1);
        var releaseFirstTransaction = new java.util.concurrent.CountDownLatch(1);

        // First transaction claims the key with fingerprint A and holds the transaction open
        // (uncommitted) so the row lock/uncommitted insert is still in place when the second
        // transaction races in.
        var firstOutcome = java.util.concurrent.CompletableFuture.supplyAsync(() -> transactions().execute(status -> {
            IdempotencyOutcome outcome = recorder.resolveOrClaim("ORGANIZATION", org, admin, key, "ORG_SUSPEND",
                    "fingerprint-A", "first-worker", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
            firstClaimed.countDown();
            awaitQuietly(releaseFirstTransaction);
            return outcome;
        }));
        assertThat(firstClaimed.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

        // Second transaction reuses the same key with a DIFFERENT fingerprint while the first is
        // still PENDING (not yet committed). It must block on the uncommitted row and, once the
        // first commits, see the fingerprint mismatch and clash immediately — it must never return
        // Pending for a lease that belongs to an unrelated request body.
        var secondOutcome = java.util.concurrent.CompletableFuture.supplyAsync(() -> transactions().execute(status ->
                recorder.resolveOrClaim("ORGANIZATION", org, admin, key, "ORG_SUSPEND",
                        "fingerprint-B", "second-worker", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300))));
        Thread.sleep(200);
        releaseFirstTransaction.countDown();

        assertThat(firstOutcome.join()).isInstanceOf(IdempotencyOutcome.Claimed.class);
        assertThat(secondOutcome.join()).isInstanceOf(IdempotencyOutcome.Clash.class);
    }

    private static void awaitQuietly(java.util.concurrent.CountDownLatch latch) {
        try {
            latch.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Wraps the real {@link JdbcIdempotencyRecorder} so that, on the very first {@code
     * resolveOrClaim} call, it reads {@code pg_backend_pid()} on its own transactional connection and
     * completes {@code pid} with it -- <em>before</em> delegating to the real recorder, which is
     * where this connection will actually block on the conflicting claim. This is what lets a test
     * find and watch the correct backend in {@code pg_stat_activity} instead of guessing from timing.
     */
    private IdempotencyRecorder pidCapturingRecorder(java.util.concurrent.CompletableFuture<Integer> pid) {
        IdempotencyRecorder delegate = new JdbcIdempotencyRecorder(dataSource);
        return new IdempotencyRecorder() {
            @Override
            public IdempotencyOutcome resolveOrClaim(String scopeType, UUID organizationId, UUID actorUserId,
                    String clientMutationId, String operationType, String requestFingerprint, String leaseOwner,
                    Instant leaseExpiresAt, Instant keyRetentionExpiresAt) {
                try {
                    Connection connection = DataSourceUtils.getConnection(dataSource);
                    try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                            ResultSet result = statement.executeQuery()) {
                        result.next();
                        pid.complete(result.getInt(1));
                    }
                } catch (SQLException exception) {
                    pid.completeExceptionally(exception);
                }
                return delegate.resolveOrClaim(scopeType, organizationId, actorUserId, clientMutationId,
                        operationType, requestFingerprint, leaseOwner, leaseExpiresAt, keyRetentionExpiresAt);
            }

            @Override
            public void markCompleted(UUID idempotencyKeyId, String leaseOwner, long leaseGeneration,
                    UUID resultEntityId, short terminalHttpStatus, Instant keyRetentionExpiresAt) {
                delegate.markCompleted(idempotencyKeyId, leaseOwner, leaseGeneration, resultEntityId,
                        terminalHttpStatus, keyRetentionExpiresAt);
            }
        };
    }

    /**
     * Polls {@code pg_stat_activity} on a fresh connection until {@code pid}'s {@code
     * wait_event_type} is {@code Lock} -- hard proof the backend is genuinely parked waiting for a
     * row/transaction lock, not merely "probably blocked by now". Throws (failing the calling test)
     * if the deadline passes without observing that state; a timeout here is a real failure, never
     * silently tolerated.
     */
    private void awaitBackendBlockedOnLock(int pid, long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        try (var connection = openConnection()) {
            while (System.currentTimeMillis() < deadline) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT wait_event_type, wait_event, state FROM pg_stat_activity WHERE pid = ?")) {
                    statement.setInt(1, pid);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next() && "Lock".equals(result.getString("wait_event_type"))) {
                            return;
                        }
                    }
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Backend pid " + pid + " never reached a Lock wait state in pg_stat_activity "
                + "within " + timeoutMillis + "ms -- cannot prove it was genuinely blocked on the conflicting claim.");
    }

    @Test
    void staleOwnerOrGenerationCannotMarkCompleted() throws Exception {
        UUID admin = activeAdmin();
        UUID org = organization("Stale owner", admin, null, "ACTIVE");
        createMembershipChain(org);
        var recorder = new JdbcIdempotencyRecorder(dataSource);

        UUID claimId = seedPendingClaim(admin, org, "stale-owner-key", "fingerprint-stale-owner",
                "ORG_SUSPEND", Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));

        // markCompleted with the wrong owner must fail; the record stays PENDING.
        assertThatThrownBy(() -> transactions().execute(status -> {
            recorder.markCompleted(claimId, "wrong-owner", 1L, org, (short) 200, Instant.now().plusSeconds(300));
            return null;
        })).isInstanceOf(RuntimeException.class);
        try (var connection = openConnection()) {
            assertThat(count(connection, "SELECT count(*) FROM idempotency_keys WHERE id = '" + claimId + "' AND status = 'PENDING'")).isEqualTo(1);
        }

        // markCompleted with a stale generation must also fail.
        assertThatThrownBy(() -> transactions().execute(status -> {
            recorder.markCompleted(claimId, "stale-worker", 99L, org, (short) 200, Instant.now().plusSeconds(300));
            return null;
        })).isInstanceOf(RuntimeException.class);
    }

    // --------------------------------------------------------------------------
    // Helpers
    // --------------------------------------------------------------------------

    private org.springframework.transaction.support.TransactionTemplate transactions() {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    private long readLeaseGeneration(Connection connection, UUID claimId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT lease_generation FROM idempotency_keys WHERE id = ?")) {
            statement.setObject(1, claimId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private UUID seedPendingClaim(UUID actor, UUID org, String clientMutationId, String fingerprint,
            String operationType, Instant leaseExpiresAt, Instant keyRetentionExpiresAt) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = 'ORGANIZATION'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operationType + "'");
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, "
                            + "operation_type, request_fingerprint, status, lease_owner, lease_generation, "
                            + "lease_expires_at, key_retention_expires_at) VALUES (?, 'ORGANIZATION', ?, ?, ?, ?, ?, "
                            + "'PENDING', ?, ?, ?, ?)")) {
                statement.setObject(1, id);
                statement.setObject(2, org);
                statement.setObject(3, actor);
                statement.setString(4, clientMutationId);
                statement.setString(5, operationType);
                statement.setString(6, fingerprint);
                statement.setString(7, "seed-worker");
                statement.setLong(8, 1L);
                statement.setTimestamp(9, java.sql.Timestamp.from(leaseExpiresAt));
                statement.setTimestamp(10, java.sql.Timestamp.from(keyRetentionExpiresAt));
                statement.executeUpdate();
            }
            connection.commit();
        }
        return id;
    }

    private Connection openConnection() throws SQLException {
        var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    private UUID user() throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO users (id, status) VALUES ('" + id + "', 'ACTIVE')");
            connection.commit();
        }
        return id;
    }

    private UUID activeAdmin() throws Exception {
        UUID id = user();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO platform_administrators (id, user_id, granted_at) VALUES ('"
                    + UUID.randomUUID() + "', '" + id + "', transaction_timestamp())");
            connection.commit();
        }
        return id;
    }

    private UUID revokedAdmin() throws Exception {
        UUID id = activeAdmin();
        try (var connection = openConnection()) {
            connection.createStatement().execute("UPDATE platform_administrators SET revoked_at = transaction_timestamp() WHERE user_id = '" + id + "'");
            connection.commit();
        }
        return id;
    }

    private UUID organization(String name, UUID actor, String color, String status) throws Exception {
        UUID id = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO organizations (id, name, primary_color, status, created_by_user_id, updated_by_user_id) VALUES ('"
                    + id + "', '" + name + "', " + (color == null ? "NULL" : "'" + color + "'") + ", '" + status + "', '" + actor + "', '" + actor + "')");
            connection.commit();
        }
        return id;
    }

    private record MembershipChain(UUID membershipId, UUID familyId, UUID tokenId) {}

    private MembershipChain createMembershipChain(UUID organizationId) throws Exception {
        UUID memberUser = user();
        UUID membership = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        UUID token = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + person + "','" + organizationId + "','A','B','1')");
            connection.createStatement().execute("INSERT INTO organization_memberships (id, organization_id, user_id, person_id, granted_at) VALUES ('" + membership + "','" + organizationId + "','" + memberUser + "','" + person + "',transaction_timestamp())");
            connection.createStatement().execute("INSERT INTO trusted_devices (id,user_id,device_identifier,platform) VALUES ('" + device + "','" + memberUser + "','" + UUID.randomUUID() + "','IOS')");
            connection.createStatement().execute("INSERT INTO refresh_token_families (id,user_id,trusted_device_id,organization_membership_id,authenticated_at,issued_at_session_generation) VALUES ('" + family + "','" + memberUser + "','" + device + "','" + membership + "',transaction_timestamp(),1)");
            connection.createStatement().execute("INSERT INTO refresh_tokens (id,family_id,token_hash,access_token_hash,access_expires_at,expires_at) VALUES ('" + token + "','" + family + "','t" + token + "','a" + token + "',transaction_timestamp()+interval '1 hour',transaction_timestamp()+interval '1 day')");
            connection.commit();
        }
        return new MembershipChain(membership, family, token);
    }

    /**
     * Creates a user with an {@code organization_memberships} row plus (optionally) an {@code
     * organization_membership_roles} row and (optionally) an {@code organization_membership_
     * permissions} row, exercising every combination the {@code org_actor_has_identity_update_access}
     * live-authorization checks care about: membership status, role revocation, permission
     * revocation and target permission code.
     */
    private UUID membershipActor(UUID organizationId, String membershipStatus, String role, boolean roleRevoked,
            String permissionCode, boolean permissionRevoked) throws Exception {
        UUID actor = user();
        UUID membership = UUID.randomUUID();
        UUID person = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO people (id, organization_id, first_name, last_name, phone) "
                    + "VALUES ('" + person + "','" + organizationId + "','A','B','1')");
            connection.createStatement().execute("INSERT INTO organization_memberships (id, organization_id, user_id, "
                    + "person_id, status, granted_at) VALUES ('" + membership + "','" + organizationId + "','" + actor
                    + "','" + person + "','" + membershipStatus + "',transaction_timestamp())");
            if (role != null) {
                UUID roleId = UUID.randomUUID();
                String revokedClause = roleRevoked ? "transaction_timestamp()" : "NULL";
                connection.createStatement().execute("INSERT INTO organization_membership_roles (id, "
                        + "organization_membership_id, organization_id, role, granted_at, revoked_at) VALUES ('"
                        + roleId + "','" + membership + "','" + organizationId + "','" + role
                        + "',transaction_timestamp()," + revokedClause + ")");
                if (permissionCode != null) {
                    String permRevokedClause = permissionRevoked ? "transaction_timestamp()" : "NULL";
                    connection.createStatement().execute("INSERT INTO organization_membership_permissions (id, "
                            + "organization_id, target_membership_role_id, target_role_code, permission_code, "
                            + "granted_by_platform_admin_user_id, granted_at, revoked_at) VALUES ('" + UUID.randomUUID()
                            + "','" + organizationId + "','" + roleId + "','TEACHER','" + permissionCode + "','"
                            + UUID.randomUUID() + "',transaction_timestamp()," + permRevokedClause + ")");
                }
            }
            connection.commit();
        }
        return actor;
    }

    private UUID orgAdminActor(UUID organizationId) throws Exception {
        return membershipActor(organizationId, "ACTIVE", "ORG_ADMIN", false, null, false);
    }

    private boolean orgActorHasIdentityUpdateAccess(UUID organizationId, UUID actor) throws Exception {
        try (var connection = openConnection();
                var statement = connection.prepareStatement("SELECT org_actor_has_identity_update_access(?, ?)")) {
            statement.setObject(1, organizationId);
            statement.setObject(2, actor);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getBoolean(1);
            }
        }
    }

    // --------------------------------------------------------------------------
    // org_actor_has_identity_update_access — every branch of the live authorization rule
    // --------------------------------------------------------------------------

    @Test
    void orgActorHasIdentityUpdateAccessGrantsOrgAdminWithoutAnyPermissionRow() throws Exception {
        UUID org = organization("Org admin access", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "ORG_ADMIN", false, null, false);
        // ORG_ADMIN never needs a organization_membership_permissions row at all.
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isTrue();
    }

    @Test
    void orgActorHasIdentityUpdateAccessGrantsTeacherWithActiveBrandManagePermission() throws Exception {
        UUID org = organization("Teacher access", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "TEACHER", false, "BRAND_MANAGE", false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isTrue();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesTeacherWithoutAnyPermission() throws Exception {
        UUID org = organization("Teacher no permission", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "TEACHER", false, null, false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesTeacherWithWrongPermissionCode() throws Exception {
        UUID org = organization("Teacher wrong permission", user(), null, "ACTIVE");
        // MODULE_MANAGE, not BRAND_MANAGE -- identity update needs BRAND_MANAGE specifically.
        UUID actor = membershipActor(org, "ACTIVE", "TEACHER", false, "MODULE_MANAGE", false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesRevokedOrgAdminRole() throws Exception {
        UUID org = organization("Revoked org admin", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "ORG_ADMIN", true, null, false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesRevokedTeacherRole() throws Exception {
        UUID org = organization("Revoked teacher role", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "TEACHER", true, "BRAND_MANAGE", false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesRevokedBrandManagePermission() throws Exception {
        UUID org = organization("Revoked permission", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "ACTIVE", "TEACHER", false, "BRAND_MANAGE", true);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesInactiveMembership() throws Exception {
        UUID org = organization("Inactive membership", user(), null, "ACTIVE");
        // status stays PROVISIONING (never activated).
        UUID actor = membershipActor(org, "PROVISIONING", "ORG_ADMIN", false, null, false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesSuspendedMembership() throws Exception {
        UUID org = organization("Suspended membership", user(), null, "ACTIVE");
        UUID actor = membershipActor(org, "SUSPENDED", "ORG_ADMIN", false, null, false);
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesMembershipInAnotherOrganization() throws Exception {
        UUID ownOrg = organization("Own org", user(), null, "ACTIVE");
        UUID otherOrg = organization("Other org", user(), null, "ACTIVE");
        UUID actor = membershipActor(ownOrg, "ACTIVE", "ORG_ADMIN", false, null, false);
        // The actor is a real ORG_ADMIN, but only in ownOrg, not otherOrg.
        assertThat(orgActorHasIdentityUpdateAccess(otherOrg, actor)).isFalse();
    }

    @Test
    void orgActorHasIdentityUpdateAccessDeniesNonMemberEntirely() throws Exception {
        UUID org = organization("No membership at all", user(), null, "ACTIVE");
        UUID actor = user();
        assertThat(orgActorHasIdentityUpdateAccess(org, actor)).isFalse();
    }

    private LifecycleRequest lifecycleRequest(UUID actor, UUID organizationId, int rowVersion, String clientMutationId) {
        return new LifecycleRequest(actor, organizationId, rowVersion, clientMutationId,
                "fingerprint-" + clientMutationId, "request-" + clientMutationId, "test-worker",
                Instant.now().plusSeconds(60), Instant.now().plusSeconds(300));
    }

    private OrganizationLifecycleService serviceWithRealWriter() {
        var repository = new JdbcOrganizationRepository(dataSource);
        var auditWriter = new JdbcAuditWriter(dataSource);
        var idempotencyRecorder = new JdbcIdempotencyRecorder(dataSource);
        return new OrganizationLifecycleService(repository, dataSource, transactionManager, auditWriter, idempotencyRecorder, RESULT_SERIALIZER, actor -> { });
    }

    /**
     * Builds a service whose {@link AuditWriter} wraps the real {@link JdbcAuditWriter} but corrupts
     * the RLS context (operation code mismatch) immediately before the INSERT, forcing a real
     * PostgreSQL {@code WITH CHECK} violation from the audit RLS policy instead of a mocked exception.
     * The real {@link JdbcAuditWriter} still performs the INSERT; the real database rejects the row.
     */
    private OrganizationLifecycleService serviceWithRlsCorruptingWriter() {
        var repository = new JdbcOrganizationRepository(dataSource);
        AuditWriter corrupting = event -> {
            try {
                var connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
                // Corrupt the operation code so the audit RLS policy's action_type/operation_code guard
                // fails. This is a real WITH CHECK violation raised by PostgreSQL, not a mock exception.
                connection.createStatement().execute("SET LOCAL app.iam_operation_code = 'CORRUPTED_FOR_TEST'");
            } catch (SQLException exception) {
                throw new AuditWriteException("Test bağlamı bozulamadı", exception);
            }
            new JdbcAuditWriter(dataSource).write(event);
        };
        var idempotencyRecorder = new JdbcIdempotencyRecorder(dataSource);
        return new OrganizationLifecycleService(repository, dataSource, transactionManager, corrupting, idempotencyRecorder, RESULT_SERIALIZER, actor -> { });
    }

    private UUID insertAuditAsOwner(UUID actor, UUID org, String action, String eventKind) throws Exception {
        UUID audit = UUID.randomUUID();
        try (var connection = openConnection()) {
            connection.createStatement().execute("INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, "
                    + "payload_schema_version, event_scope, target_entity_type, event_kind, requires_target_entity, "
                    + "requires_class_scope, requires_operation_group, target_entity_id, is_undo) VALUES ('"
                    + audit + "','" + org + "','" + actor + "','" + action + "',1,'ORGANIZATION','ORGANIZATION','"
                    + eventKind + "',true,false,false,'" + org + "',false)");
            connection.commit();
        }
        return audit;
    }

    /**
     * {@code event_metadata->>'operationCode'} must equal the RLS-checked
     * {@code app.iam_operation_code}; for {@code ORG_STATUS_CHANGED}, {@code old_value}/{@code
     * new_value} status must also form one of the transitions the DB policy allows for {@code
     * operationCode}. {@code oldStatus}/{@code newStatus} are {@code null} for actions the DB
     * doesn't transition-check (ORG_CREATED, ORG_SETTING_CHANGED).
     */
    private void insertAudit(Connection connection, UUID id, UUID org, UUID actor, String action, String eventKind,
            UUID target, String operationCode, String oldStatus, String newStatus) throws SQLException {
        String metadata = oldStatus != null
                ? "{\"operationCode\":\"" + operationCode + "\",\"revokedMembershipCount\":0,"
                        + "\"revokedFamilyCount\":0,\"revokedTokenCount\":0}"
                : "{\"operationCode\":\"" + operationCode + "\"}";
        String oldValue = oldStatus != null ? "'{\"status\":\"" + oldStatus + "\",\"rowVersion\":1}'::jsonb" : "NULL";
        String newValue = newStatus != null ? "'{\"status\":\"" + newStatus + "\",\"rowVersion\":2}'::jsonb" : "NULL";
        connection.createStatement().execute("INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, "
                + "payload_schema_version, event_scope, target_entity_type, event_kind, requires_target_entity, "
                + "requires_class_scope, requires_operation_group, target_entity_id, old_value, new_value, "
                + "event_metadata, is_undo) VALUES ('"
                + id + "','" + org + "','" + actor + "','" + action + "',1,'ORGANIZATION','ORGANIZATION','"
                + eventKind + "',true,false,false,'" + target + "'," + oldValue + "," + newValue + ",'"
                + metadata + "'::jsonb,false)");
    }

    private void insertAccessAudit(Connection connection, UUID id, UUID org, UUID actor, String outcome, String reasonCode)
            throws SQLException {
        var metadata = "{\"operationCode\":\"" + currentOperationCode(connection) + "\",\"outcome\":\"" + outcome + "\"}";
        var reason = reasonCode == null ? "NULL" : "'" + reasonCode + "'";
        connection.createStatement().execute("INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, "
                + "payload_schema_version, event_scope, target_entity_type, event_kind, requires_target_entity, "
                + "requires_class_scope, requires_operation_group, target_entity_id, event_metadata, reason_code, is_undo) "
                + "VALUES ('" + id + "','" + org + "','" + actor + "','PLATFORM_ADMIN_ORG_ACCESS',1,'ORGANIZATION',"
                + "'ORGANIZATION','ACCESS',true,false,false,'" + org + "','" + metadata + "'::jsonb," + reason + ",false)");
    }

    private static String currentOperationCode(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT current_setting('app.iam_operation_code', true)")) {
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getString(1);
            }
        }
    }

    private long sessionGeneration(Connection connection, UUID membershipId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT session_generation FROM organization_memberships WHERE id = ?")) {
            statement.setObject(1, membershipId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    private void assertAuditRejected(UUID actor, UUID org, String scope, String operation, Object support,
            String action, String eventKind, UUID target) throws Exception {
        assertAuditRejected(actor, org, scope, operation, support, action, eventKind, target, "ORGANIZATION", false, false);
    }

    private void assertAuditRejected(UUID actor, UUID org, String scope, String operation, Object support,
            String action, String eventKind, UUID target, String eventScope) throws Exception {
        assertAuditRejected(actor, org, scope, operation, support, action, eventKind, target, eventScope, false, false);
    }

    private void assertAuditRejected(UUID actor, UUID org, String scope, String operation, Object support,
            String action, String eventKind, UUID target, String eventScope, boolean isUndo,
            boolean globalWithOrganizationId) throws Exception {
        try (var connection = openConnection()) {
            connection.createStatement().execute("SET ROLE org_runtime");
            connection.createStatement().execute("SET LOCAL app.iam_operation_scope = '" + scope + "'");
            connection.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            if (!scope.equals("GLOBAL") || globalWithOrganizationId) {
                connection.createStatement().execute("SET LOCAL app.organization_id = '" + org + "'");
            }
            connection.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operation + "'");
            if (support instanceof Boolean b && b) {
                connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = 'true'");
            } else if (support instanceof String s) {
                connection.createStatement().execute("SET LOCAL app.iam_platform_admin_support_access = '" + s + "'");
            }
            assertThatThrownBy(() -> connection.createStatement().execute(
                    "INSERT INTO audit_logs (id, organization_id, actor_user_id, action_type, payload_schema_version, "
                            + "event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope, "
                            + "requires_operation_group, target_entity_id, is_undo) VALUES ('"
                            + UUID.randomUUID() + "','" + org + "','" + actor + "','" + action + "',1,'" + eventScope
                            + "','ORGANIZATION','" + eventKind + "',true,false,false,'" + target + "'," + isUndo + ")"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static SQLException assertSqlState(String expectedSqlState, SqlAction action) {
        try {
            action.run();
        } catch (SQLException error) {
            assertThat(error.getSQLState()).isEqualTo(expectedSqlState);
            return error;
        }
        throw new AssertionError("Expected SQLSTATE " + expectedSqlState);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
