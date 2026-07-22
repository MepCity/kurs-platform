package org.mepcity.kursplatform.audit.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class AuditCoreMigrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();

        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @Test
    void catalogSeedsHaveExactContractedValuesAndClosedPayloads() throws Exception {
        Map<String, CatalogExpectation> expected = expectedCatalogs();

        try (var connection = connection()) {
            // 15 rows: AUDIT-001A's original 4 codes (unchanged) + ORG-003's additional, immutable
            // ORG_SETTING_CHANGED payload_schema_version=2 row (VERI_MODELI.md §13.0a) + IAM-004's
            // 6 new codes (V7__iam_audit_events.sql) + IAM-004 Fix Round 3's 4 further new codes
            // (V10__iam_audit_unknown_and_command_completed.sql), each a single row.
            assertThat(catalogCount(connection)).isEqualTo(19);
            assertThat(catalogCodes(connection)).containsExactlyElementsOf(expected.keySet());

            for (var entry : expected.entrySet()) {
                CatalogRow actual = catalog(connection, entry.getKey(), 1);
                CatalogExpectation expectation = entry.getValue();

                assertThat(actual.eventScope()).isEqualTo(expectation.eventScope());
                assertThat(actual.eventKind()).isEqualTo(expectation.eventKind());
                assertThat(actual.targetEntityType()).isEqualTo(expectation.targetEntityType());
                assertThat(actual.requiresTargetEntity()).isEqualTo(expectation.requiresTargetEntity());
                assertThat(actual.requiresClassScope()).isEqualTo(expectation.requiresClassScope());
                assertThat(actual.requiresOperationGroup()).isEqualTo(expectation.requiresOperationGroup());
                assertThat(actual.isUndoable()).isEqualTo(expectation.isUndoable());
                assertThat(jsonEquals(connection, actual.payloadSchema(), expectation.payloadSchema())).isTrue();
            }
        }
    }

    @Test
    void orgSettingChangedV1RowIsUnchangedAndV2RowAddsBrandFieldsAsSeparateImmutableRow() throws Exception {
        try (var connection = connection()) {
            // v1 (AUDIT-001A, physically defined in V2__audit_core.sql) must be byte-for-byte the
            // original seed -- ORG-003 never touches it, only adds a new row alongside it.
            CatalogRow v1 = catalog(connection, "ORG_SETTING_CHANGED", 1);
            assertThat(jsonEquals(connection, v1.payloadSchema(), """
                    {"oldValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"newValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                    """)).isTrue();

            // v2 (ORG-003's own V3 migration) = v1 fields + secondaryColor + brandColors, same
            // scope/kind/target/undoable shape, rejectUnknown still true.
            CatalogRow v2 = catalog(connection, "ORG_SETTING_CHANGED", 2);
            assertThat(v2.eventScope()).isEqualTo(v1.eventScope());
            assertThat(v2.eventKind()).isEqualTo(v1.eventKind());
            assertThat(v2.targetEntityType()).isEqualTo(v1.targetEntityType());
            assertThat(v2.requiresTargetEntity()).isEqualTo(v1.requiresTargetEntity());
            assertThat(v2.requiresClassScope()).isEqualTo(v1.requiresClassScope());
            assertThat(v2.requiresOperationGroup()).isEqualTo(v1.requiresOperationGroup());
            assertThat(v2.isUndoable()).isEqualTo(v1.isUndoable());
            assertThat(jsonEquals(connection, v2.payloadSchema(), """
                    {"oldValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","secondaryColor","logoAssetId","enabledModules","brandColors","attendanceStatuses","rowVersion"]},"newValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","secondaryColor","logoAssetId","enabledModules","brandColors","attendanceStatuses","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                    """)).isTrue();

            // Exactly two ORG_SETTING_CHANGED rows exist (v1 and v2), never a third/duplicate.
            try (var statement = connection.prepareStatement(
                    "SELECT count(*) FROM audit_action_catalog WHERE code = 'ORG_SETTING_CHANGED'");
                    var rows = statement.executeQuery()) {
                rows.next();
                assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }

    @Test
    void catalogTemporaryClassAndOperationGroupGatesRejectInvalidRows() throws Exception {
        try (var connection = connection()) {
            assertSqlState("23514", () -> insertCatalog(connection, "BAD_GROUP", false, true));
            assertSqlState("23514", () -> insertCatalog(connection, "BAD_CLASS", true, false));
        }
    }

    @Test
    void eachActionInsertsAndCatalogScopeKindTargetMismatchesFail() throws Exception {
        UUID actor = createUser();
        UUID organization = createOrganization(actor);

        try (var connection = connection()) {
            for (String action : List.of(
                    "ORG_CREATED", "ORG_STATUS_CHANGED", "ORG_SETTING_CHANGED", "PLATFORM_ADMIN_ORG_ACCESS")) {
                String eventKind = action.equals("PLATFORM_ADMIN_ORG_ACCESS") ? "ACCESS" : "DATA_MUTATION";
                insertAudit(
                        connection,
                        UUID.randomUUID(),
                        organization,
                        actor,
                        action,
                        eventKind,
                        "ORGANIZATION",
                        "ORGANIZATION",
                        false,
                        null,
                        null,
                        null,
                        null);
            }

            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), organization, actor, "ORG_CREATED",
                    "DATA_MUTATION", "ORGANIZATION", "GLOBAL", false, null, null, null, null));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), organization, actor, "ORG_CREATED",
                    "ACCESS", "ORGANIZATION", "ORGANIZATION", false, null, null, null, null));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), organization, actor, "ORG_CREATED",
                    "DATA_MUTATION", "ORGANIZATION", "USER", false, null, null, null, null));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), null, actor, "ORG_CREATED",
                    "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", false, null, null, null, null));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), organization, actor, "ORG_CREATED",
                    "DATA_MUTATION", "GLOBAL", "ORGANIZATION", false, null, null, null, null));
            assertSqlState("23514", () -> insertAudit(connection, UUID.randomUUID(), organization, actor,
                    "ORG_CREATED", "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", false, null,
                    UUID.randomUUID(), null, null));
            assertSqlState("23514", () -> insertAudit(connection, UUID.randomUUID(), organization, actor,
                    "ORG_CREATED", "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", false, null,
                    null, null, UUID.randomUUID()));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), organization, actor, "ORG_CREATED",
                    "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", false, "bad space", null, null, null));
        }
    }

    @Test
    void undoIsPinnedToSameOrganizationAndScopeAndIsUnique() throws Exception {
        UUID actor = createUser();
        UUID firstOrganization = createOrganization(actor);
        UUID secondOrganization = createOrganization(actor);
        UUID source = UUID.randomUUID();

        try (var connection = connection()) {
            insertAudit(connection, source, firstOrganization, actor, "ORG_SETTING_CHANGED", "DATA_MUTATION",
                    "ORGANIZATION", "ORGANIZATION", false, null, null, null, null);

            assertSqlState("23503", () -> insertAudit(connection, UUID.randomUUID(), secondOrganization, actor,
                    "ORG_SETTING_CHANGED", "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", true, null,
                    null, source, null));
            assertRejected(() -> insertAudit(connection, UUID.randomUUID(), firstOrganization, actor,
                    "ORG_SETTING_CHANGED", "DATA_MUTATION", "GLOBAL", "ORGANIZATION", true, null, null,
                    source, null));

            insertAudit(connection, UUID.randomUUID(), firstOrganization, actor, "ORG_SETTING_CHANGED",
                    "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", true, null, null, source, null);
            assertSqlState("23505", () -> insertAudit(connection, UUID.randomUUID(), firstOrganization, actor,
                    "ORG_SETTING_CHANGED", "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", true, null,
                    null, source, null));

            UUID selfId = UUID.randomUUID();
            assertSqlState("23514", () -> insertAudit(connection, selfId, firstOrganization, actor,
                    "ORG_SETTING_CHANGED", "DATA_MUTATION", "ORGANIZATION", "ORGANIZATION", true, null,
                    null, selfId, null));
        }
    }

    @Test
    void crossScopeUndoRejectsValidGlobalSourceThroughPinnedUndoForeignKeys() throws Exception {
        UUID actor = createUser();
        UUID organization = createOrganization(actor);

        try (var connection = connection()) {
            connection.setAutoCommit(false);
            try {
                insertGlobalCatalog(connection, "TEMP_GLOBAL_SOURCE");
                UUID globalSource = UUID.randomUUID();
                insertGlobalSourceAudit(connection, globalSource, actor, "TEMP_GLOBAL_SOURCE");

                assertSqlState("23503", () -> insertAudit(connection, UUID.randomUUID(),
                        organization, actor, "ORG_SETTING_CHANGED", "DATA_MUTATION", "ORGANIZATION",
                        "ORGANIZATION", true, null, null, globalSource, null));
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void rlsPoliciesGrantsAppendOnlyAndIndexDefinitionsAreExact() throws Exception {
        UUID actor = createUser();
        UUID organization = createOrganization(actor);
        UUID audit = UUID.randomUUID();

        try (var connection = connection()) {
            insertAudit(connection, audit, organization, actor, "ORG_CREATED", "DATA_MUTATION", "ORGANIZATION",
                    "ORGANIZATION", false, null, null, null, null);

            assertRejected(() -> updateAudit(connection, audit));
            assertRejected(() -> deleteAudit(connection, audit));
            assertRlsAndNoPolicies(connection);
            assertNoRuntimeOrPublicGrants(connection);
            assertIamRuntimeGrantIsInsertOnlyOnExpectedColumns(connection);
            assertRuntimeDmlDenied(connection, "app_runtime");
            assertIndex(connection, "audit_logs_organization_occurred_idx", "organization_id, occurred_at DESC", false,
                    null);
            assertIndex(connection, "audit_logs_organization_class_occurred_idx",
                    "organization_id, scope_class_id, occurred_at DESC", false, null);
            assertIndex(connection, "audit_logs_organization_target_idx",
                    "organization_id, target_entity_type, target_entity_id", false, null);
            assertIndex(connection, "audit_logs_operation_group_idx", "operation_group_id", false,
                    "WHERE (operation_group_id IS NOT NULL)");
            assertIndex(connection, "audit_logs_one_undo_per_source_idx", "undo_of_audit_log_id", true,
                    "WHERE is_undo");
        }
    }

    private static Map<String, CatalogExpectation> expectedCatalogs() {
        // LinkedHashMap in ascending-code order to match catalogCodes()'s `ORDER BY code` —
        // containsExactlyElementsOf compares this iteration order directly.
        Map<String, CatalogExpectation> expected = new LinkedHashMap<>();
        expected.put("CONTEXT_ACTIVATED", new CatalogExpectation(
                "ORGANIZATION", "ACCESS", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","refreshTokenFamilyId","organizationMembershipId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("IAM_ORG_PROVIDER_SESSION_REVOKED", new CatalogExpectation(
                "ORGANIZATION", "SECURITY", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["providerStatus","organizationMembershipId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("IAM_ORG_PROVIDER_STATUS_CHECK_BLOCKED", new CatalogExpectation(
                "ORGANIZATION", "SECURITY", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","providerStatus"],"enumValues":{"providerStatus":["UNKNOWN"]}},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("IAM_PROVIDER_SESSION_REVOKED", new CatalogExpectation(
                "GLOBAL", "SECURITY", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["providerStatus"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("IAM_PROVIDER_STATUS_CHECK_BLOCKED", new CatalogExpectation(
                "GLOBAL", "SECURITY", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","providerStatus"],"enumValues":{"providerStatus":["UNKNOWN"]}},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("ORG_CREATED", new CatalogExpectation(
                "ORGANIZATION", "DATA_MUTATION", "ORGANIZATION", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["status","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("ORG_SETTING_CHANGED", new CatalogExpectation(
                "ORGANIZATION", "DATA_MUTATION", "ORGANIZATION", true, false, false, true,
                """
                {"oldValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"newValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("ORG_STATUS_CHANGED", new CatalogExpectation(
                "ORGANIZATION", "DATA_MUTATION", "ORGANIZATION", true, false, false, false,
                """
                {"oldValue":{"allowed":["status","rowVersion"]},"newValue":{"allowed":["status","rowVersion"]},"eventMetadata":{"allowed":["revokedMembershipCount","revokedFamilyCount","revokedTokenCount","operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("PLATFORM_ADMIN_ACTIVATED", new CatalogExpectation(
                "GLOBAL", "ACCESS", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","refreshTokenFamilyId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("PLATFORM_ADMIN_ORG_ACCESS", new CatalogExpectation(
                "ORGANIZATION", "ACCESS", "ORGANIZATION", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","outcome"]},"reasonCodes":["FORBIDDEN"],"rejectUnknown":true}
                """));
        expected.put("PROVIDER_COMMAND_COMPLETED", new CatalogExpectation(
                "GLOBAL", "SECURITY", "PROVIDER_COMMAND", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("PROVIDER_COMMAND_EXHAUSTED", new CatalogExpectation(
                "GLOBAL", "SECURITY", "PROVIDER_COMMAND", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("PROVIDER_COMMAND_FAILED", new CatalogExpectation(
                "GLOBAL", "SECURITY", "PROVIDER_COMMAND", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        expected.put("PROVIDER_TOKEN_EXCHANGED", new CatalogExpectation(
                "GLOBAL", "ACCESS", "USER", true, false, false, false,
                """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","contextSelectionTokenId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}
                """));
        String refreshPayload = """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","refreshTokenFamilyId"]},"reasonCodes":[],"rejectUnknown":true}
                """;
        String refreshAuditPayload = """
                {"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","refreshTokenFamilyId","organizationMembershipId","trustedDeviceId"]},"reasonCodes":[],"rejectUnknown":true}
                """;
        expected.put("SESSION_LOGGED_OUT", new CatalogExpectation("GLOBAL", "ACCESS", "USER", true, false, false, false, refreshAuditPayload));
        expected.put("SESSION_REFRESHED", new CatalogExpectation("GLOBAL", "SECURITY", "USER", true, false, false, false, refreshAuditPayload));
        expected.put("SESSION_REFRESH_REPLAY_RECONCILED", new CatalogExpectation("GLOBAL", "SECURITY", "USER", true, false, false, false, refreshAuditPayload));
        expected.put("SESSION_REFRESH_REUSE_DETECTED", new CatalogExpectation("GLOBAL", "SECURITY", "USER", true, false, false, false, refreshAuditPayload));
        return expected;
    }

    private Connection connection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private UUID createUser() throws SQLException {
        UUID id = UUID.randomUUID();

        try (var connection = connection();
                var statement = connection.prepareStatement("INSERT INTO users(id,status) VALUES (?, 'ACTIVE')")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
        return id;
    }

    private UUID createOrganization(UUID actor) throws SQLException {
        UUID id = UUID.randomUUID();

        try (var connection = connection();
                var statement = connection.prepareStatement(
                        "INSERT INTO organizations(id,name,created_by_user_id,updated_by_user_id) VALUES (?,'audit',?,?)")) {
            statement.setObject(1, id);
            statement.setObject(2, actor);
            statement.setObject(3, actor);
            statement.executeUpdate();
        }
        return id;
    }

    private int catalogCount(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT count(*) FROM audit_action_catalog");
                var rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private List<String> catalogCodes(Connection connection) throws SQLException {
        // DISTINCT: ORG_SETTING_CHANGED now has two rows (payload_schema_version 1 and 2) but is
        // still one code; the 4-code closed set assertion is about codes, not (code, version) pairs.
        try (var statement = connection.prepareStatement("SELECT DISTINCT code FROM audit_action_catalog ORDER BY code");
                var rows = statement.executeQuery()) {
            var codes = new java.util.ArrayList<String>();
            while (rows.next()) {
                codes.add(rows.getString(1));
            }
            return codes;
        }
    }

    private CatalogRow catalog(Connection connection, String code, int payloadSchemaVersion) throws SQLException {
        String sql = "SELECT event_scope,event_kind,target_entity_type,requires_target_entity,requires_class_scope,"
                + "requires_operation_group,is_undoable,payload_schema FROM audit_action_catalog "
                + "WHERE code=? AND payload_schema_version=?";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setInt(2, payloadSchemaVersion);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                return new CatalogRow(
                        rows.getString(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getBoolean(4),
                        rows.getBoolean(5),
                        rows.getBoolean(6),
                        rows.getBoolean(7),
                        rows.getString(8));
            }
        }
    }

    private boolean jsonEquals(Connection connection, String actual, String expected) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT ?::jsonb = ?::jsonb")) {
            statement.setString(1, actual);
            statement.setString(2, expected);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getBoolean(1);
            }
        }
    }

    private void insertCatalog(Connection connection, String code, boolean classScope, boolean operationGroup)
            throws SQLException {
        String sql = "INSERT INTO audit_action_catalog(code,payload_schema_version,target_entity_type,event_scope,"
                + "event_kind,requires_target_entity,requires_class_scope,requires_operation_group,is_undoable,"
                + "payload_schema) VALUES(?,1,'X','ORGANIZATION','DATA_MUTATION',true,?,?,false,"
                + "'{\"oldValue\":{},\"newValue\":{},\"eventMetadata\":{},\"reasonCodes\":[],\"rejectUnknown\":true}')";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setBoolean(2, classScope);
            statement.setBoolean(3, operationGroup);
            statement.executeUpdate();
        }
    }

    private void insertGlobalCatalog(Connection connection, String code) throws SQLException {
        String sql = "INSERT INTO audit_action_catalog(code,payload_schema_version,target_entity_type,event_scope,"
                + "event_kind,requires_target_entity,requires_class_scope,requires_operation_group,is_undoable,"
                + "payload_schema) VALUES(?,1,'SYSTEM','GLOBAL','ACCESS',false,false,false,false,"
                + "'{\"oldValue\":{},\"newValue\":{},\"eventMetadata\":{},\"reasonCodes\":[],\"rejectUnknown\":true}')";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.executeUpdate();
        }
    }

    private void insertAudit(
            Connection connection,
            UUID id,
            UUID organizationId,
            UUID actorId,
            String action,
            String eventKind,
            String eventScope,
            String targetEntityType,
            boolean isUndo,
            String requestId,
            UUID scopeClassId,
            UUID undoOfAuditLogId,
            UUID operationGroupId)
            throws SQLException {
        String sql = "INSERT INTO audit_logs(id,organization_id,actor_user_id,request_id,scope_class_id,action_type,"
                + "payload_schema_version,event_scope,target_entity_type,event_kind,requires_target_entity,"
                + "requires_class_scope,requires_operation_group,target_entity_id,is_undo,undo_of_audit_log_id,"
                + "operation_group_id) VALUES(?,?,?,?,?,?,1,?::event_scope_enum,?,?::event_kind_enum,true,false,"
                + "false,?,?,?,?)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, organizationId);
            statement.setObject(3, actorId);
            statement.setString(4, requestId);
            statement.setObject(5, scopeClassId);
            statement.setString(6, action);
            statement.setString(7, eventScope);
            statement.setString(8, targetEntityType);
            statement.setString(9, eventKind);
            statement.setObject(10, organizationId);
            statement.setBoolean(11, isUndo);
            statement.setObject(12, undoOfAuditLogId);
            statement.setObject(13, operationGroupId);
            statement.executeUpdate();
        }
    }

    private void insertGlobalSourceAudit(Connection connection, UUID id, UUID actorId, String action) throws SQLException {
        String sql = "INSERT INTO audit_logs(id,actor_user_id,action_type,payload_schema_version,event_scope,"
                + "target_entity_type,event_kind,requires_target_entity,requires_class_scope,"
                + "requires_operation_group,is_undo) VALUES(?,?,?,1,'GLOBAL','SYSTEM','ACCESS',false,false,false,false)";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setObject(2, actorId);
            statement.setString(3, action);
            statement.executeUpdate();
        }
    }

    private void updateAudit(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE audit_logs SET request_id='x' WHERE id=?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private void deleteAudit(Connection connection, UUID id) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM audit_logs WHERE id=?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private void assertRlsAndNoPolicies(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                        "SELECT relrowsecurity,relforcerowsecurity FROM pg_class WHERE oid='audit_logs'::regclass");
                var rows = statement.executeQuery()) {
            rows.next();
            assertThat(rows.getBoolean(1)).isTrue();
            assertThat(rows.getBoolean(2)).isTrue();
        }
        // AUDIT-001A seeded the audit_logs table without INSERT policies; ORG-003 adds the narrow
        // org_runtime INSERT policies for the four ORG action types. Only org_runtime-targeted
        // INSERT policies are permitted; SELECT/UPDATE/DELETE policies remain absent.
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM pg_policies WHERE tablename='audit_logs' AND cmd = 'INSERT' AND roles = '{org_runtime}'");
                var rows = statement.executeQuery()) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(4);
        }
        // IAM-004 (V7) adds its own narrow INSERT policies for iam_runtime, same pattern as
        // org_runtime above — three policies: GLOBAL events, ORGANIZATION events, provider-command.
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM pg_policies WHERE tablename='audit_logs' AND cmd = 'INSERT' AND roles = '{iam_runtime}'");
                var rows = statement.executeQuery()) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(3);
        }
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM pg_policies WHERE tablename='audit_logs' AND cmd IN ('SELECT','UPDATE','DELETE')");
                var rows = statement.executeQuery()) {
            rows.next();
            assertThat(rows.getInt(1)).isZero();
        }
    }

    private void assertNoRuntimeOrPublicGrants(Connection connection) throws SQLException {
        // iam_runtime is deliberately excluded here: IAM-004 grants it a narrow, INSERT-only,
        // column-level privilege on audit_logs (V7__iam_audit_events.sql), mirroring org_runtime's
        // own pre-existing grant (V3) — assertIamRuntimeGrantIsInsertOnlyOnExpectedColumns verifies
        // that grant is exactly as narrow as intended. app_runtime and PUBLIC still get nothing.
        String sql = "SELECT count(*) FROM information_schema.role_table_grants WHERE table_name='audit_logs'"
                + " AND grantee IN ('PUBLIC','app_runtime') UNION ALL SELECT count(*)"
                + " FROM information_schema.column_privileges WHERE table_name='audit_logs'"
                + " AND grantee IN ('PUBLIC','app_runtime')";
        try (var statement = connection.prepareStatement(sql); var rows = statement.executeQuery()) {
            while (rows.next()) {
                assertThat(rows.getInt(1)).isZero();
            }
        }
    }

    private void assertIamRuntimeGrantIsInsertOnlyOnExpectedColumns(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT count(*) FROM information_schema.role_table_grants WHERE table_name='audit_logs'"
                        + " AND grantee='iam_runtime' AND privilege_type != 'INSERT'");
                var rows = statement.executeQuery()) {
            rows.next();
            assertThat(rows.getInt(1)).as("iam_runtime must have no non-INSERT privilege on audit_logs").isZero();
        }
        try (var statement = connection.prepareStatement(
                "SELECT column_name FROM information_schema.column_privileges WHERE table_name='audit_logs'"
                        + " AND grantee='iam_runtime' AND privilege_type='INSERT' ORDER BY column_name")) {
            try (var rows = statement.executeQuery()) {
                var columns = new java.util.ArrayList<String>();
                while (rows.next()) {
                    columns.add(rows.getString(1));
                }
                assertThat(columns).containsExactlyInAnyOrder(
                        "id", "organization_id", "actor_user_id", "request_id", "action_type",
                        "payload_schema_version", "event_scope", "target_entity_type", "event_kind",
                        "requires_target_entity", "requires_class_scope", "requires_operation_group",
                        "target_entity_id", "old_value", "new_value", "event_metadata", "reason_code",
                        "operation_group_id", "is_undo", "undo_of_audit_log_id");
            }
        }
        // SELECT/UPDATE/DELETE remain fully denied for iam_runtime — only the INSERT grant above
        // plus the RLS policies from V7 determine what it can actually write.
        connection.createStatement().execute("SET ROLE iam_runtime");
        try {
            assertPrivilegeDenied(() -> connection.createStatement().executeQuery("SELECT * FROM audit_logs"));
            assertPrivilegeDenied(() -> connection.createStatement().execute("UPDATE audit_logs SET request_id='x'"));
            assertPrivilegeDenied(() -> connection.createStatement().execute("DELETE FROM audit_logs"));
        } finally {
            connection.createStatement().execute("RESET ROLE");
        }
    }

    private void assertRuntimeDmlDenied(Connection connection, String role) throws SQLException {
        connection.createStatement().execute("SET ROLE " + role);
        try {
            assertPrivilegeDenied(() -> connection.createStatement().executeQuery("SELECT * FROM audit_logs"));
            assertPrivilegeDenied(() -> connection.createStatement().execute(
                    "INSERT INTO audit_logs(id,action_type) VALUES('" + UUID.randomUUID() + "','X')"));
            assertPrivilegeDenied(() -> connection.createStatement().execute("UPDATE audit_logs SET request_id='x'"));
            assertPrivilegeDenied(() -> connection.createStatement().execute("DELETE FROM audit_logs"));
        } finally {
            connection.createStatement().execute("RESET ROLE");
        }
    }

    private void assertIndex(Connection connection, String name, String columns, boolean unique, String predicate)
            throws SQLException {
        try (var statement = connection.prepareStatement(
                        "SELECT pg_get_indexdef(indexrelid) FROM pg_index WHERE indexrelid=?::regclass")) {
            statement.setString(1, name);
            try (var rows = statement.executeQuery()) {
                rows.next();
                String definition = rows.getString(1);
                assertThat(definition).contains(columns);
                if (unique) {
                    assertThat(definition).contains("UNIQUE");
                }
                if (predicate != null) {
                    assertThat(definition).contains(predicate);
                }
            }
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

    private static void assertRejected(SqlAction action) {
        assertThatThrownBy(action::run).isInstanceOf(SQLException.class);
    }

    private static void assertPrivilegeDenied(SqlAction action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("42501"));
    }

    private record CatalogExpectation(
            String eventScope,
            String eventKind,
            String targetEntityType,
            boolean requiresTargetEntity,
            boolean requiresClassScope,
            boolean requiresOperationGroup,
            boolean isUndoable,
            String payloadSchema) {}

    private record CatalogRow(
            String eventScope,
            String eventKind,
            String targetEntityType,
            boolean requiresTargetEntity,
            boolean requiresClassScope,
            boolean requiresOperationGroup,
            boolean isUndoable,
            String payloadSchema) {}

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }
}
