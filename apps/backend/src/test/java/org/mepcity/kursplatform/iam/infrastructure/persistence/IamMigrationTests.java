package org.mepcity.kursplatform.iam.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IamMigrationTests {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
        var flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
    }

    @AfterAll
    static void stopContainer() {
        POSTGRES.stop();
    }

    private Connection newConnection() throws SQLException {
        var conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        conn.setAutoCommit(false);
        return conn;
    }

    private void execWithDetail(Connection conn, String sql) throws SQLException {
        try {
            conn.createStatement().execute(sql);
        } catch (SQLException e) {
            String detail = "[SQL-EXEC] SQL: " + sql + " | SQLState: " + e.getSQLState() + " | Message: " + e.getMessage();
            SQLException next = e.getNextException();
            if (next != null) {
                detail += " | NextSQLState: " + next.getSQLState() + " | NextMessage: " + next.getMessage();
            }
            throw new SQLException(detail, e.getSQLState(), e.getErrorCode(), e);
        }
    }

    private int execUpdateWithDetail(Connection conn, String sql) throws SQLException {
        try {
            return conn.createStatement().executeUpdate(sql);
        } catch (SQLException e) {
            String detail = "[SQL-UPD] SQL: " + sql + " | SQLState: " + e.getSQLState() + " | Message: " + e.getMessage();
            SQLException next = e.getNextException();
            if (next != null) {
                detail += " | NextSQLState: " + next.getSQLState() + " | NextMessage: " + next.getMessage();
            }
            throw new SQLException(detail, e.getSQLState(), e.getErrorCode(), e);
        }
    }

    @Nested
    class TableExistence {
        @Test
        void createsAllExpectedTables() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename NOT LIKE 'flyway_%' ORDER BY tablename");
                Set<String> tables = new HashSet<>();
                while (rs.next()) tables.add(rs.getString(1));
                conn.commit();
                assertThat(tables).contains(
                        "organizations", "users", "user_identities", "people",
                        "platform_administrators", "platform_administrator_profiles",
                        "organization_memberships", "organization_membership_roles",
                        "organization_membership_permissions",
                        "permission_categories", "permission_catalog",
                        "trusted_devices", "context_selection_tokens",
                        "refresh_token_families", "refresh_tokens",
                        "iam_provider_commands", "iam_secret_deliveries",
                        "iam_event_cursors", "iam_event_deduplications",
                        "idempotency_keys", "iam_auth_response_escrows");
            }
        }
    }

    @Nested
    class EnumTypes {
        @Test
        void createsAllExpectedEnums() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT t.typname FROM pg_type t JOIN pg_enum e ON t.oid = e.enumtypid GROUP BY t.typname ORDER BY t.typname");
                Set<String> enums = new HashSet<>();
                while (rs.next()) enums.add(rs.getString(1));
                conn.commit();
                assertThat(enums).contains(
                        "user_status_enum", "membership_role_enum", "device_platform_enum",
                        "provider_command_type_enum", "provider_command_status_enum",
                        "secret_delivery_status_enum", "event_source_enum",
                        "idempotency_scope_enum", "idempotency_status_enum",
                        "escrow_status_enum", "event_scope_enum", "event_kind_enum");
            }
        }

        @Test
        void eventSourceEnumHasAdminAndUserValues() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT enum_range(NULL::event_source_enum)");
                rs.next();
                conn.commit();
                assertThat(rs.getString(1)).contains("ADMIN_EVENTS", "USER_EVENTS");
            }
        }
    }

    @Nested
    class SeedData {
        @Test
        void permissionCategoriesSeeded() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT code FROM permission_categories ORDER BY code");
                List<String> codes = new ArrayList<>();
                while (rs.next()) codes.add(rs.getString(1));
                conn.commit();
                assertThat(codes).containsExactly(
                        "ATTENDANCE_REPORTING", "CLASS_STUDENT_GUARDIAN",
                        "ORG_SETTINGS", "PROGRAM", "STAFF_MANAGEMENT");
            }
        }

        @Test
        void permissionCatalogHas19Entries() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM permission_catalog");
                rs.next();
                conn.commit();
                assertThat(rs.getInt(1)).isEqualTo(19);
            }
        }
    }

    @Nested
    class Roles {
        @Test
        void iamRuntimeRoleIsNotSuperuserOrBypassRls() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT rolsuper, rolcreatedb, rolcreaterole, rolinherit, rolbypassrls FROM pg_roles WHERE rolname = 'iam_runtime'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("rolsuper")).isFalse();
                assertThat(rs.getBoolean("rolcreatedb")).isFalse();
                assertThat(rs.getBoolean("rolcreaterole")).isFalse();
                assertThat(rs.getBoolean("rolbypassrls")).isFalse();
                conn.commit();
            }
        }

        @Test
        void appRuntimeHasNoDirectTableGrants() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT COUNT(*) FROM information_schema.table_privileges WHERE grantee = 'app_runtime' AND table_schema = 'public'");
                rs.next();
                conn.commit();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }

        @Test
        void iamRuntimeHasGrantsForIamTables() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT table_name FROM information_schema.table_privileges WHERE grantee = 'iam_runtime' AND table_schema = 'public' AND privilege_type = 'SELECT' GROUP BY table_name ORDER BY table_name");
                Set<String> granted = new HashSet<>();
                while (rs.next()) granted.add(rs.getString(1));
                conn.commit();
                assertThat(granted).contains(
                        "users", "user_identities", "trusted_devices",
                        "refresh_token_families", "refresh_tokens");
            }
        }
    }

    @Nested
    class RlsEnforcement {
        @Test
        void trustedDevicesHasForceRls() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT relname, relforcerowsecurity FROM pg_class WHERE relname = 'trusted_devices'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean("relforcerowsecurity")).isTrue();
                conn.commit();
            }
        }

        @Test
        void allExpectedTablesHaveRlsEnabled() throws Exception {
            List<String> expected = List.of(
                    "users", "user_identities", "platform_administrators", "platform_administrator_profiles",
                    "trusted_devices", "organization_memberships", "organization_membership_roles",
                    "organization_membership_permissions", "context_selection_tokens",
                    "refresh_token_families", "refresh_tokens", "idempotency_keys",
                    "iam_auth_response_escrows", "iam_provider_commands", "iam_secret_deliveries",
                    "people");
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT relname FROM pg_class WHERE relname = ANY(ARRAY[" +
                                "'users','user_identities','platform_administrators','platform_administrator_profiles'," +
                                "'trusted_devices','organization_memberships','organization_membership_roles'," +
                                "'organization_membership_permissions','context_selection_tokens'," +
                                "'refresh_token_families','refresh_tokens','idempotency_keys'," +
                                "'iam_auth_response_escrows','iam_provider_commands','iam_secret_deliveries','people'" +
                                "]) AND relrowsecurity = true ORDER BY relname");
                Set<String> rls = new HashSet<>();
                while (rs.next()) rls.add(rs.getString(1));
                conn.commit();
                assertThat(rls).containsAll(expected);
            }
        }

        @Test
        void allExpectedTablesHaveForceRls() throws Exception {
            List<String> expected = List.of(
                    "users", "user_identities", "platform_administrators", "platform_administrator_profiles",
                    "trusted_devices", "organization_memberships", "organization_membership_roles",
                    "organization_membership_permissions", "context_selection_tokens",
                    "refresh_token_families", "refresh_tokens", "idempotency_keys",
                    "iam_auth_response_escrows", "iam_provider_commands", "iam_secret_deliveries",
                    "people");
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT relname FROM pg_class WHERE relname = ANY(ARRAY[" +
                                "'users','user_identities','platform_administrators','platform_administrator_profiles'," +
                                "'trusted_devices','organization_memberships','organization_membership_roles'," +
                                "'organization_membership_permissions','context_selection_tokens'," +
                                "'refresh_token_families','refresh_tokens','idempotency_keys'," +
                                "'iam_auth_response_escrows','iam_provider_commands','iam_secret_deliveries','people'" +
                                "]) AND relforcerowsecurity = true ORDER BY relname");
                Set<String> forced = new HashSet<>();
                while (rs.next()) forced.add(rs.getString(1));
                conn.commit();
                assertThat(forced).containsAll(expected);
            }
        }

        @Test
        void trustedDevicesHasAllSixNamedPolicies() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT policyname FROM pg_policies WHERE tablename = 'trusted_devices' ORDER BY policyname");
                Set<String> policies = new HashSet<>();
                while (rs.next()) policies.add(rs.getString(1));
                conn.commit();
                assertThat(policies).contains(
                        "trusted_devices_select_self",
                        "trusted_devices_select_provider_exchange",
                        "trusted_devices_select_platform_revoke",
                        "trusted_devices_insert_provider_exchange",
                        "trusted_devices_update_self_revoke",
                        "trusted_devices_update_platform_revoke");
            }
        }

        @Test
        void onlyRevokedAtColumnIsUpdatable() throws Exception {
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT column_name FROM information_schema.column_privileges " +
                                "WHERE grantee = 'iam_runtime' AND table_name = 'trusted_devices' AND privilege_type = 'UPDATE'");
                Set<String> cols = new HashSet<>();
                while (rs.next()) cols.add(rs.getString(1));
                conn.commit();
                assertThat(cols).containsExactly("revoked_at");
            }
        }

        @Test
        void columnLevelGrantBlocksPlatformUpdateAndAllowsRevokedAtUpdate() throws Exception {
            UUID uid = UUID.randomUUID();
            UUID devId = UUID.randomUUID();
            UUID devUid = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn,
                        "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn,
                        "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                                "VALUES ('" + devId + "', '" + uid + "', '" + devUid + "', 'ANDROID')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_AUTH'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_TOKEN_EXCHANGE'");
                execWithDetail(conn, "SET LOCAL app.iam_provider_device_identifier = '" + devUid + "'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + uid + "'");
                execWithDetail(conn, "SAVEPOINT sp1");
                assertThatCode(() ->
                        execWithDetail(conn,
                                "UPDATE trusted_devices SET platform = 'IOS' WHERE id = '" + devId + "'"))
                        .isInstanceOf(SQLException.class);
                execWithDetail(conn, "ROLLBACK TO SAVEPOINT sp1");
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_AUTH'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'DEVICE_SELF_REVOKE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_device_id = '" + devId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + uid + "'");
                execWithDetail(conn,
                        "UPDATE trusted_devices SET revoked_at = transaction_timestamp() WHERE id = '" + devId + "'");
                var rs = conn.createStatement().executeQuery(
                        "SELECT revoked_at FROM trusted_devices WHERE id = '" + devId + "'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getTimestamp("revoked_at")).isNotNull();
                conn.commit();
            }
        }
    }

    @Nested
    class ConstraintViolations {
        @Test
        void rejectsInvalidUserStatus() throws Exception {
            try (var conn = newConnection()) {
                assertThatCode(() ->
                        execWithDetail(conn,
                                "INSERT INTO users (id, status) VALUES ('" + UUID.randomUUID() + "', 'INVALID')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void rejectsInvalidMembershipRole() throws Exception {
            try (var conn = newConnection()) {
                UUID orgId = UUID.randomUUID();
                UUID uid = UUID.randomUUID();
                UUID pid = UUID.randomUUID();
                UUID mid = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) " +
                        "VALUES ('" + pid + "', '" + orgId + "', 'a', 'b', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) " +
                        "VALUES ('" + mid + "', '" + orgId + "', '" + uid + "', '" + pid + "', 'ACTIVE', now())");
                execWithDetail(conn, "SAVEPOINT sp");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) " +
                                "VALUES ('" + UUID.randomUUID() + "', '" + mid + "', '" + orgId + "', 'INVALID_ROLE', now())"))
                        .isInstanceOf(SQLException.class);
                execWithDetail(conn, "ROLLBACK TO SAVEPOINT sp");
                conn.commit();
            }
        }

        @Test
        void contextTokenEnforcesFiveMinuteTtl() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                UUID devId = UUID.randomUUID();
                UUID devUid = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devId + "', '" + uid + "', '" + devUid + "', 'ANDROID')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO context_selection_tokens (id, user_id, trusted_device_id, token_hash, authenticated_at, " +
                                "issued_at, expires_at) VALUES ('" + UUID.randomUUID() + "', '" + uid + "', '" + devId + "', '" +
                                UUID.randomUUID() + "', now(), now(), now() + INTERVAL '10 minutes')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void refreshTokensEnforceTimeOrdering() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                UUID devId = UUID.randomUUID();
                UUID devUid = UUID.randomUUID();
                UUID familyId = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devId + "', '" + uid + "', '" + devUid + "', 'ANDROID')");
                execWithDetail(conn, "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, authenticated_at) " +
                        "VALUES ('" + familyId + "', '" + uid + "', '" + devId + "', now())");
                Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
                Instant future = Instant.now().plus(1, ChronoUnit.HOURS);
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO refresh_tokens (id, family_id, token_hash, access_token_hash, access_expires_at, " +
                                "issued_at, used_at, expires_at) VALUES ('" + UUID.randomUUID() + "', '" + familyId + "', '" +
                                UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + future + "', '" +
                                future + "', '" + past + "', '" + future + "')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void refreshTokenFamilyRequiresSessionGenerationWithMembership() throws Exception {
            try (var conn = newConnection()) {
                UUID orgId = UUID.randomUUID();
                UUID uid = UUID.randomUUID();
                UUID pid = UUID.randomUUID();
                UUID membershipId = UUID.randomUUID();
                UUID devId = UUID.randomUUID();
                UUID devUid = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) " +
                        "VALUES ('" + pid + "', '" + orgId + "', 'a', 'b', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) " +
                        "VALUES ('" + membershipId + "', '" + orgId + "', '" + uid + "', '" + pid + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devId + "', '" + uid + "', '" + devUid + "', 'ANDROID')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, organization_membership_id, authenticated_at) " +
                                "VALUES ('" + UUID.randomUUID() + "', '" + uid + "', '" + devId + "', '" + membershipId + "', now())"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void idempotencyKeysRejectsGlobalWithOrganizationId() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                UUID orgId = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, " +
                                "operation_type, request_fingerprint, status, lease_owner, lease_generation, lease_expires_at, key_retention_expires_at) " +
                                "VALUES ('" + UUID.randomUUID() + "', 'GLOBAL', '" + orgId + "', '" + uid + "', 'key1', 'OP', 'fp', 'PENDING', 'l1', 1, " +
                                "now() + INTERVAL '1 hour', now() + INTERVAL '2 hour')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void idempotencyKeysRejectsIamAuthWithOrganizationId() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                UUID orgId = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, " +
                                "operation_type, request_fingerprint, status, lease_owner, lease_generation, lease_expires_at, key_retention_expires_at) " +
                                "VALUES ('" + UUID.randomUUID() + "', 'IAM_AUTH', '" + orgId + "', '" + uid + "', 'key2', 'OP', 'fp', 'PENDING', 'l1', 1, " +
                                "now() + INTERVAL '1 hour', now() + INTERVAL '2 hour')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void idempotencyKeysRejectsOrganizationWithNullOrganizationId() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, " +
                                "operation_type, request_fingerprint, status, lease_owner, lease_generation, lease_expires_at, key_retention_expires_at) " +
                                "VALUES ('" + UUID.randomUUID() + "', 'ORGANIZATION', NULL, '" + uid + "', 'key3', 'OP', 'fp', 'PENDING', 'l1', 1, " +
                                "now() + INTERVAL '1 hour', now() + INTERVAL '2 hour')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void escrowRejectsWrongOperationTtl() throws Exception {
            try (var conn = newConnection()) {
                UUID uid = UUID.randomUUID();
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO idempotency_keys (id, scope_type, user_id, client_mutation_id, operation_type, " +
                        "request_fingerprint, status, lease_owner, lease_generation, lease_expires_at, key_retention_expires_at) " +
                        "VALUES ('" + UUID.randomUUID() + "', 'IAM_AUTH', '" + uid + "', 'key1', 'SESSION_REFRESH', 'fp', 'PENDING', 'l1', 1, " +
                        "now() + INTERVAL '1 hour', now() + INTERVAL '2 hour')");
                assertThatCode(() ->
                        execWithDetail(conn, "INSERT INTO iam_auth_response_escrows (id, idempotency_key_id, actor_user_id, operation_type, " +
                                "device_identifier, token_fingerprint, status, ciphertext, aead_key_reference, aead_nonce, aad_context, " +
                                "expires_at, result_refresh_token_family_id, result_refresh_token_id) VALUES ('" +
                                UUID.randomUUID() + "', (SELECT id FROM idempotency_keys WHERE client_mutation_id = 'key1'), '" +
                                uid + "', 'PROVIDER_TOKEN_EXCHANGE', '" + UUID.randomUUID() + "', 'fp', 'READY', " +
                                "'\\x01'::bytea, 'kr', '\\x01'::bytea, 'ctx', now() + INTERVAL '5 minutes', '" +
                                UUID.randomUUID() + "', '" + UUID.randomUUID() + "')"))
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }
    }

    @Nested
    class CrossActorAccessDenial {
        @Test
        void actorCannotSeeOtherUsersTrustedDevicesUnderRls() throws Exception {
            UUID userA = UUID.randomUUID();
            UUID userB = UUID.randomUUID();
            UUID devAId = UUID.randomUUID();
            UUID devAUid = UUID.randomUUID();
            UUID devBId = UUID.randomUUID();
            UUID devBUid = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + userA + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + userB + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devAId + "', '" + userA + "', '" + devAUid + "', 'ANDROID')");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devBId + "', '" + userB + "', '" + devBUid + "', 'ANDROID')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_AUTH'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'DEVICE_LIST'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + userA + "'");
                var rs = conn.createStatement().executeQuery("SELECT id FROM trusted_devices");
                Set<String> visible = new HashSet<>();
                while (rs.next()) visible.add(rs.getString(1));
                assertThat(visible).contains(devAId.toString());
                assertThat(visible).doesNotContain(devBId.toString());
                conn.commit();
            }
        }

        @Test
        void wrongScopeReturnsEmptyTrustedDevices() throws Exception {
            UUID uid = UUID.randomUUID();
            UUID devId = UUID.randomUUID();
            UUID devUid = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                        "VALUES ('" + devId + "', '" + uid + "', '" + devUid + "', 'ANDROID')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'DEVICE_LIST'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + uid + "'");
                var rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM trusted_devices");
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(0);
                conn.commit();
            }
        }
    }

    @Nested
    class SessionRefreshLogoutFamilyBinding {
        @Test
        void sessionRefreshSeesOnlyTargetFamilyOnSameDevice() throws Exception {
            UUID uid = UUID.randomUUID();
            UUID deviceId = UUID.randomUUID();
            UUID deviceUid = UUID.randomUUID();
            UUID familyA = UUID.randomUUID();
            UUID familyB = UUID.randomUUID();
            UUID tokenA = UUID.randomUUID();
            UUID tokenB = UUID.randomUUID();
            Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
            try (var conn = newConnection()) {
                execWithDetail(conn,
                        "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn,
                        "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                                "VALUES ('" + deviceId + "', '" + uid + "', '" + deviceUid + "', 'ANDROID')");
                execWithDetail(conn,
                        "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, authenticated_at) VALUES " +
                                "('" + familyA + "', '" + uid + "', '" + deviceId + "', now()), " +
                                "('" + familyB + "', '" + uid + "', '" + deviceId + "', now())");
                execWithDetail(conn,
                        "INSERT INTO refresh_tokens (id, family_id, token_hash, access_token_hash, access_expires_at, expires_at) VALUES " +
                                "('" + tokenA + "', '" + familyA + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + future + "', '" + future + "'), " +
                                "('" + tokenB + "', '" + familyB + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + future + "', '" + future + "')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn,
                        "SET LOCAL app.iam_operation_scope = 'IAM_AUTH'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_operation_code = 'SESSION_REFRESH'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_actor_user_id = '" + uid + "'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_current_trusted_device_id = '" + deviceId + "'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_current_family_id = '" + familyA + "'");
                var rs = conn.createStatement().executeQuery(
                        "SELECT id FROM refresh_tokens");
                Set<String> visible = new HashSet<>();
                while (rs.next()) {
                    visible.add(rs.getString(1));
                }
                conn.commit();
                assertThat(visible)
                        .as("familyA token must be visible")
                        .contains(tokenA.toString());
                assertThat(visible)
                        .as("familyB token must NOT be visible")
                        .doesNotContain(tokenB.toString());
            }
        }

        @Test
        void sessionLogoutCannotSeeOrUpdateOtherFamilyOnSameDevice() throws Exception {
            UUID uid = UUID.randomUUID();
            UUID deviceId = UUID.randomUUID();
            UUID deviceUid = UUID.randomUUID();
            UUID familyA = UUID.randomUUID();
            UUID familyB = UUID.randomUUID();
            UUID tokenA = UUID.randomUUID();
            UUID tokenB = UUID.randomUUID();
            Instant future = Instant.now().plus(30, ChronoUnit.DAYS);
            try (var conn = newConnection()) {
                execWithDetail(conn,
                        "INSERT INTO users (id, status) VALUES ('" + uid + "', 'ACTIVE')");
                execWithDetail(conn,
                        "INSERT INTO trusted_devices (id, user_id, device_identifier, platform) " +
                                "VALUES ('" + deviceId + "', '" + uid + "', '" + deviceUid + "', 'ANDROID')");
                execWithDetail(conn,
                        "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, authenticated_at) VALUES " +
                                "('" + familyA + "', '" + uid + "', '" + deviceId + "', now()), " +
                                "('" + familyB + "', '" + uid + "', '" + deviceId + "', now())");
                execWithDetail(conn,
                        "INSERT INTO refresh_tokens (id, family_id, token_hash, access_token_hash, access_expires_at, expires_at) VALUES " +
                                "('" + tokenA + "', '" + familyA + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + future + "', '" + future + "'), " +
                                "('" + tokenB + "', '" + familyB + "', '" + UUID.randomUUID() + "', '" + UUID.randomUUID() + "', '" + future + "', '" + future + "')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn,
                        "SET LOCAL app.iam_operation_scope = 'IAM_AUTH'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_operation_code = 'SESSION_LOGOUT'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_actor_user_id = '" + uid + "'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_current_trusted_device_id = '" + deviceId + "'");
                execWithDetail(conn,
                        "SET LOCAL app.iam_current_family_id = '" + familyA + "'");
                var rs = conn.createStatement().executeQuery(
                        "SELECT id FROM refresh_tokens");
                Set<String> visible = new HashSet<>();
                while (rs.next()) {
                    visible.add(rs.getString(1));
                }
                assertThat(visible)
                        .as("familyA must be visible for SESSION_LOGOUT")
                        .contains(tokenA.toString());
                assertThat(visible)
                        .as("familyB must NOT be visible")
                        .doesNotContain(tokenB.toString());
                int updated = execUpdateWithDetail(conn,
                        "UPDATE refresh_tokens SET revoked_at = now() WHERE id = '" + tokenA + "'");
                assertThat(updated).as("familyA revoke must succeed").isEqualTo(1);
                int blocked = execUpdateWithDetail(conn,
                        "UPDATE refresh_tokens SET revoked_at = now() WHERE id = '" + tokenB + "'");
                assertThat(blocked).as("familyB revoke must be blocked").isEqualTo(0);
                conn.commit();
            }
        }
    }

    @Nested
    class PlatformAdministratorPlatformDeviceRevokeVisibility {

        @Test
        void platformDeviceRevokeSeesOnlyTheCallingActiveAdministrator() throws Exception {
            UUID actorId = UUID.randomUUID();
            UUID otherAdminId = UUID.randomUUID();
            UUID revokedAdminId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE'), ('" + otherAdminId + "', 'ACTIVE'), ('" + revokedAdminId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO platform_administrators (id, user_id, granted_at, revoked_at) VALUES ('" + UUID.randomUUID() + "', '" + actorId + "', now(), NULL), ('" + UUID.randomUUID() + "', '" + otherAdminId + "', now(), NULL), ('" + UUID.randomUUID() + "', '" + revokedAdminId + "', now() - INTERVAL '1 day', now())");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PLATFORM_DEVICE_REVOKE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                var rs = conn.createStatement().executeQuery("SELECT user_id FROM platform_administrators ORDER BY user_id");
                Set<String> visible = new HashSet<>();
                while (rs.next()) visible.add(rs.getString(1));
                assertThat(visible).as("PLATFORM_DEVICE_REVOKE must expose only the active caller")
                        .containsExactly(actorId.toString());
                conn.commit();
            }

            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PLATFORM_DEVICE_REVOKE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + revokedAdminId + "'");
                var rs = conn.createStatement().executeQuery("SELECT user_id FROM platform_administrators");
                assertThat(rs.next()).as("revoked administrator must not be visible").isFalse();
                conn.commit();
            }
        }

        @Test
        void organizationAndOtherGlobalOperationsCannotSeePlatformAdministrators() throws Exception {
            UUID actorId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO platform_administrators (id, user_id, granted_at) VALUES ('" + UUID.randomUUID() + "', '" + actorId + "', now())");
                conn.commit();
            }
            for (String operationCode : List.of("ORG_CREATE", "ORG_LIST", "PROVIDER_COMMAND_CLAIM", "USER_DISABLE")) {
                try (var conn = newConnection()) {
                    execWithDetail(conn, "SET ROLE iam_runtime");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_code = '" + operationCode + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                    var rs = conn.createStatement().executeQuery("SELECT user_id FROM platform_administrators");
                    assertThat(rs.next()).as(operationCode + " must not open platform-admin visibility").isFalse();
                    conn.commit();
                }
            }
        }
    }

    @Nested
    class SecretDeliveryAuthorization {

        @Test
        void orgAdminWithActiveMembershipCanEscrowAndFinalize() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k1', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int inserted = execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')");
                assertThat(inserted).as("ORG_ADMIN INSERT must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'READY', ready_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'ESCROWED'");
                assertThat(updated).as("FINALIZE ESCROWED->READY must succeed").isEqualTo(1);
                var rs = conn.createStatement().executeQuery("SELECT id FROM iam_secret_deliveries WHERE status = 'READY'");
                assertThat(rs.next()).as("FINALIZE SELECT must see READY delivery").isTrue();
                updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'READY'");
                assertThat(updated).as("FINALIZE READY->CONSUMED must succeed").isEqualTo(1);
                conn.commit();
            }
        }

        @Test
        void teacherWithPermissionCanEscrowAndFinalize() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID teacherRoleId = UUID.randomUUID();
            UUID permId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + teacherRoleId + "', '" + membershipId + "', '" + orgId + "', 'TEACHER', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_permissions (id, organization_id, target_membership_role_id, permission_code, granted_by_platform_admin_user_id, granted_at) VALUES ('" + permId + "', '" + orgId + "', '" + teacherRoleId + "', 'TEACHER_ACCOUNT_MANAGE', '" + actorId + "', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k2', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int inserted = execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')");
                assertThat(inserted).as("TEACHER with permission INSERT must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'READY', ready_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'ESCROWED'");
                assertThat(updated).as("FINALIZE ESCROWED->READY must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'READY'");
                assertThat(updated).as("TEACHER with permission READY->CONSUMED must succeed").isEqualTo(1);
                conn.commit();
            }
        }

        @Test
        void revokedRoleOrPermissionDeniesAccess() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at, revoked_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now(), now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k3', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')"))
                        .as("revoked role must deny INSERT")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void revokedTeacherAccountManagePermissionDenied() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID teacherRoleId = UUID.randomUUID();
            UUID permId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + teacherRoleId + "', '" + membershipId + "', '" + orgId + "', 'TEACHER', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_permissions (id, organization_id, target_membership_role_id, permission_code, granted_by_platform_admin_user_id, granted_at, revoked_at) VALUES ('" + permId + "', '" + orgId + "', '" + teacherRoleId + "', 'TEACHER_ACCOUNT_MANAGE', '" + actorId + "', now(), now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k2b', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')"))
                        .as("revoked TEACHER_ACCOUNT_MANAGE permission must deny INSERT")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void inactiveMembershipDeniesAccess() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'SUSPENDED', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k4', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')"))
                        .as("SUSPENDED membership must deny INSERT")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void crossOrgOrActorOrCommandDenied() throws Exception {
            UUID orgA = UUID.randomUUID();
            UUID orgB = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgA + "', 'OrgA')");
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgB + "', 'OrgB')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgA + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgA + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgA + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k5', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgA + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgB + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')"))
                        .as("wrong organization must deny INSERT")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
        }

        @Test
        void createOperationCannotConsume() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k6', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'READY', ready_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'ESCROWED'");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'READY'");
                assertThat(updated).as("TEACHER_ACCOUNT_CREATE must NOT consume READY delivery").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT status FROM iam_secret_deliveries WHERE id = '" + deliveryId + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).as("status must remain READY").isEqualTo("READY");
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void finalizeAuthorizedReadyToConsumed() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k7', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'READY', ready_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'ESCROWED'");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                var rs = conn.createStatement().executeQuery("SELECT id FROM iam_secret_deliveries WHERE id = '" + deliveryId + "' AND status = 'READY'");
                assertThat(rs.next()).as("FINALIZE must see READY delivery").isTrue();
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'READY'");
                assertThat(updated).as("FINALIZE READY->CONSUMED must succeed").isEqualTo(1);
                conn.commit();
            }
        }

        @Test
        void terminalDeliveryCannotBeReConsumed() throws Exception {
            UUID orgId = UUID.randomUUID();
            UUID actorId = UUID.randomUUID();
            UUID targetId = UUID.randomUUID();
            UUID personId = UUID.randomUUID();
            UUID membershipId = UUID.randomUUID();
            UUID roleId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID deliveryId = UUID.randomUUID();
            UUID expiredCommandId = UUID.randomUUID();
            UUID expiredDeliveryId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + actorId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + personId + "', '" + orgId + "', 'A', 'B', '555')");
                execWithDetail(conn, "INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membershipId + "', '" + orgId + "', '" + actorId + "', '" + personId + "', 'ACTIVE', now())");
                execWithDetail(conn, "INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at) VALUES ('" + roleId + "', '" + membershipId + "', '" + orgId + "', 'ORG_ADMIN', now())");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'k8', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + expiredCommandId + "', 'k8-expired', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp-expired', 'hash-expired', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + deliveryId + "', '" + cmdId + "', '" + actorId + "', '\\x02'::bytea, 'key2', now() + INTERVAL '10 minutes')");
                execUpdateWithDetail(conn,
                        "INSERT INTO iam_secret_deliveries (id, provider_command_id, recipient_actor_user_id, encrypted_secret, payload_key_id, expires_at) VALUES ('" + expiredDeliveryId + "', '" + expiredCommandId + "', '" + actorId + "', '\\x03'::bytea, 'key3', now() + INTERVAL '10 minutes')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'READY', ready_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'ESCROWED'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "' AND status = 'READY'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'EXPIRED' WHERE id = '" + expiredDeliveryId + "' AND status = 'ESCROWED'");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_FINALIZE'");
                execWithDetail(conn, "SET LOCAL app.iam_actor_user_id = '" + actorId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                var terminalRead = conn.createStatement().executeQuery(
                        "SELECT status, encrypted_secret, payload_key_id FROM iam_secret_deliveries WHERE id = '" + deliveryId + "'");
                assertThat(terminalRead.next()).as("terminal row remains visible for state handling").isTrue();
                assertThat(terminalRead.getString("status")).isEqualTo("CONSUMED");
                assertThat(terminalRead.getBytes("encrypted_secret")).as("lost response must not permit secret recovery").isNull();
                assertThat(terminalRead.getString("payload_key_id")).as("terminal row must not retain the key reference").isNull();
                var expiredRead = conn.createStatement().executeQuery(
                        "SELECT status, encrypted_secret, payload_key_id FROM iam_secret_deliveries WHERE id = '" + expiredDeliveryId + "'");
                assertThat(expiredRead.next()).as("expired terminal row remains visible for state handling").isTrue();
                assertThat(expiredRead.getString("status")).isEqualTo("EXPIRED");
                assertThat(expiredRead.getBytes("encrypted_secret")).isNull();
                assertThat(expiredRead.getString("payload_key_id")).isNull();
                int reConsume = execUpdateWithDetail(conn,
                        "UPDATE iam_secret_deliveries SET status = 'CONSUMED', consumed_at = transaction_timestamp() WHERE id = '" + deliveryId + "'");
                assertThat(reConsume).as("terminal CONSUMED delivery must return 0 rows via RLS").isEqualTo(0);
                conn.commit();
            }
            try (var verifyConn = newConnection()) {
                var rs = verifyConn.createStatement().executeQuery(
                        "SELECT status, consumed_at, ready_at, encrypted_secret, payload_key_id FROM iam_secret_deliveries WHERE id = '" + deliveryId + "'");
                assertThat(rs.next()).as("delivery row must still exist").isTrue();
                assertThat(rs.getString("status")).as("status must remain CONSUMED").isEqualTo("CONSUMED");
                assertThat(rs.getTimestamp("consumed_at")).as("consumed_at must not change").isNotNull();
                assertThat(rs.getTimestamp("ready_at")).as("ready_at must not change").isNotNull();
                assertThat(rs.getBytes("encrypted_secret")).isNull();
                assertThat(rs.getString("payload_key_id")).isNull();
                var expiredRs = verifyConn.createStatement().executeQuery(
                        "SELECT status, encrypted_secret, payload_key_id FROM iam_secret_deliveries WHERE id = '" + expiredDeliveryId + "'");
                assertThat(expiredRs.next()).isTrue();
                assertThat(expiredRs.getString("status")).isEqualTo("EXPIRED");
                assertThat(expiredRs.getBytes("encrypted_secret")).isNull();
                assertThat(expiredRs.getString("payload_key_id")).isNull();
                verifyConn.commit();
            }
        }
    }

    @Nested
    class ProviderCommandStateMachine {

        @Test
        void successfulProviderCompletion() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'w0', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int claimed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = 1, fencing_token = 1, lease_owner = 'worker-A', lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'PENDING'");
                assertThat(claimed).as("claim must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int completed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + cmdId + "' AND status = 'CLAIMED'");
                assertThat(completed).as("completion must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                var rs = conn.createStatement().executeQuery(
                        "SELECT status, completed_at, lease_owner FROM iam_provider_commands WHERE id = '" + cmdId + "'");
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("COMPLETED");
                assertThat(rs.getTimestamp("completed_at")).isNotNull();
                assertThat(rs.getString("lease_owner")).isNull();
                conn.commit();
            }
        }

        @Test
        void wrongWorkerCannotComplete() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'w1', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int claimed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = 1, fencing_token = 1, lease_owner = 'worker-A', lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'PENDING'");
                assertThat(claimed).isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-B'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + cmdId + "' AND status = 'CLAIMED'");
                assertThat(updated).as("wrong worker must see 0 rows").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT status FROM iam_provider_commands WHERE id = '" + cmdId + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).as("status must remain CLAIMED").isEqualTo("CLAIMED");
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void staleFencingTokenRejected() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id) VALUES ('" + cmdId + "', 'w2', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = 1, fencing_token = 1, lease_owner = 'worker-A', lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'PENDING'");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '99'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + cmdId + "' AND status = 'CLAIMED'");
                assertThat(updated).as("stale fencing token must see 0 rows").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT status FROM iam_provider_commands WHERE id = '" + cmdId + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).as("status must remain CLAIMED").isEqualTo("CLAIMED");
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void expiredLeaseAllowsRenewButNotComplete() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            UUID cmdId2 = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id, status, attempt_count, fencing_token, lease_owner, lease_expires_at) VALUES ('" + cmdId + "', 'w3', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1', 'CLAIMED', 1, 1, 'worker-A', now() - INTERVAL '1 minute')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id, status, attempt_count, fencing_token, lease_owner, lease_expires_at) VALUES ('" + cmdId2 + "', 'w3b', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp2', 'hash2', '\\x01'::bytea, 'key1', 'CLAIMED', 1, 1, 'worker-A', now() - INTERVAL '1 minute')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-B'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '2'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET fencing_token = 2, lease_owner = 'worker-A', lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'CLAIMED' AND lease_expires_at < transaction_timestamp()"))
                        .as("renew must require the context worker to own the renewed lease")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '2'");
                int renewed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET fencing_token = 2, lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'CLAIMED' AND lease_expires_at < transaction_timestamp()");
                assertThat(renewed).as("expired lease renew must succeed").isEqualTo(1);
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + cmdId2 + "' AND status = 'CLAIMED'");
                assertThat(updated).as("expired lease must block completion").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT status FROM iam_provider_commands WHERE id = '" + cmdId2 + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).as("status must remain CLAIMED").isEqualTo("CLAIMED");
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void nonExpiredLeaseBlocksRenew() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id, status, attempt_count, fencing_token, lease_owner, lease_expires_at) VALUES ('" + cmdId + "', 'w4', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1', 'CLAIMED', 1, 1, 'worker-A', now() + INTERVAL '5 minutes')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '2'");
                int renewed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET fencing_token = 2, lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'CLAIMED' AND lease_expires_at < transaction_timestamp()");
                assertThat(renewed).as("non-expired lease renew must see 0 rows").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT fencing_token FROM iam_provider_commands WHERE id = '" + cmdId + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("fencing_token")).as("fencing_token must not change").isEqualTo(1);
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void terminalCommandImmutable() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            UUID cmdId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'PROVISIONING')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, organization_id, payload_fingerprint, username_lookup_hash, encrypted_command_payload, payload_key_id, status, attempt_count, fencing_token, completed_at) VALUES ('" + cmdId + "', 'w5', 'cognito', 'TEACHER_ACCOUNT_CREATE', '" + targetId + "', '" + orgId + "', 'fp', 'hash', '\\x01'::bytea, 'key1', 'COMPLETED', 1, 1, now())");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'IAM_PROVISIONING'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'TEACHER_ACCOUNT_CREATE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.organization_id = '" + orgId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int updated = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'FAILED', completed_at = now() WHERE id = '" + cmdId + "'");
                assertThat(updated).as("terminal command must see 0 rows").isEqualTo(0);
                try (var verifyConn = newConnection()) {
                    var rs = verifyConn.createStatement().executeQuery(
                            "SELECT status FROM iam_provider_commands WHERE id = '" + cmdId + "'");
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).as("status must remain COMPLETED").isEqualTo("COMPLETED");
                    verifyConn.commit();
                }
                conn.commit();
            }
        }

        @Test
        void globalProviderCommandIdentityVisibilityIsLimitedToAllowedCommands() throws Exception {
            UUID targetId = UUID.randomUUID();
            UUID orgId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO organizations (id, name) VALUES ('" + orgId + "', 'Test')");
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + targetId + "', 'ACTIVE')");
                conn.commit();
            }
            for (String commandType : List.of("USER_DISABLE", "USER_LOGOUT", "PASSWORD_RESET")) {
                UUID identityId = UUID.randomUUID();
                UUID cmdId = UUID.randomUUID();
                try (var conn = newConnection()) {
                    execWithDetail(conn, "INSERT INTO user_identities (id, user_id, issuer, subject) VALUES ('" + identityId + "', '" + targetId + "', 'cognito-" + commandType + "', '" + commandType + "')");
                    conn.commit();
                }
                try (var conn = newConnection()) {
                    execWithDetail(conn, "SET ROLE iam_runtime");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_code = '" + commandType + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_identity_id = '" + identityId + "'");
                    int inserted = execUpdateWithDetail(conn,
                            "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_identity_id, payload_fingerprint) VALUES ('" + cmdId + "', 'g-" + commandType + "', 'cognito', '" + commandType + "', '" + identityId + "', 'fp')");
                    assertThat(inserted).as(commandType + " insert must see only its target identity").isEqualTo(1);
                    conn.commit();
                }
                try (var conn = newConnection()) {
                    execWithDetail(conn, "SET ROLE iam_runtime");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_code = '" + commandType + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_identity_id = '" + UUID.randomUUID() + "'");
                    assertThatCode(() -> execUpdateWithDetail(conn,
                            "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_identity_id, payload_fingerprint) VALUES ('" + UUID.randomUUID() + "', 'bad-" + commandType + "', 'cognito', '" + commandType + "', '" + identityId + "', 'fp')"))
                            .as(commandType + " must reject a different target identity context")
                            .isInstanceOf(SQLException.class);
                    conn.rollback();
                }
                try (var conn = newConnection()) {
                    execWithDetail(conn, "SET ROLE iam_runtime");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'PROVIDER_COMMAND_CLAIM'");
                    execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                    execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                    int claimed = execUpdateWithDetail(conn,
                            "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = 1, fencing_token = 1, lease_owner = 'worker-A', lease_expires_at = now() + INTERVAL '5 minutes' WHERE id = '" + cmdId + "' AND status = 'PENDING'");
                    assertThat(claimed).isEqualTo(1);
                    conn.commit();
                }
                try (var conn = newConnection()) {
                    execWithDetail(conn, "SET ROLE iam_runtime");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                    execWithDetail(conn, "SET LOCAL app.iam_operation_code = '" + commandType + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_target_identity_id = '" + identityId + "'");
                    execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                    execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                    int completed = execUpdateWithDetail(conn,
                            "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + cmdId + "' AND status = 'CLAIMED'");
                    assertThat(completed).as("global " + commandType + " completion must succeed").isEqualTo(1);
                    conn.commit();
                }
            }
            UUID foreignUserId = UUID.randomUUID();
            UUID foreignIdentityId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO users (id, status) VALUES ('" + foreignUserId + "', 'ACTIVE')");
                execWithDetail(conn, "INSERT INTO user_identities (id, user_id, issuer, subject) VALUES ('" + foreignIdentityId + "', '" + foreignUserId + "', 'cognito-foreign', 'foreign')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'USER_DISABLE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_identity_id, payload_fingerprint) VALUES ('" + UUID.randomUUID() + "', 'missing-target-identity', 'cognito', 'USER_DISABLE', '" + foreignIdentityId + "', 'fp')"))
                        .as("missing app.iam_target_identity_id must fail closed")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'USER_DISABLE'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_identity_id = '" + foreignIdentityId + "'");
                assertThatCode(() -> execUpdateWithDetail(conn,
                        "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_identity_id, payload_fingerprint) VALUES ('" + UUID.randomUUID() + "', 'foreign-target-identity', 'cognito', 'USER_DISABLE', '" + foreignIdentityId + "', 'fp')"))
                        .as("identity belonging to a different user must fail closed")
                        .isInstanceOf(SQLException.class);
                conn.rollback();
            }
            UUID expiredIdentityId = UUID.randomUUID();
            UUID expiredCommandId = UUID.randomUUID();
            try (var conn = newConnection()) {
                execWithDetail(conn, "INSERT INTO user_identities (id, user_id, issuer, subject) VALUES ('" + expiredIdentityId + "', '" + targetId + "', 'cognito-expired', 'expired-global')");
                execWithDetail(conn, "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_identity_id, payload_fingerprint, status, attempt_count, fencing_token, lease_owner, lease_expires_at) VALUES ('" + expiredCommandId + "', 'expired-global', 'cognito', 'USER_LOGOUT', '" + expiredIdentityId + "', 'fp', 'CLAIMED', 1, 1, 'worker-A', now() - INTERVAL '1 minute')");
                conn.commit();
            }
            try (var conn = newConnection()) {
                execWithDetail(conn, "SET ROLE iam_runtime");
                execWithDetail(conn, "SET LOCAL app.iam_operation_scope = 'GLOBAL'");
                execWithDetail(conn, "SET LOCAL app.iam_operation_code = 'USER_LOGOUT'");
                execWithDetail(conn, "SET LOCAL app.iam_target_user_id = '" + targetId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_target_identity_id = '" + expiredIdentityId + "'");
                execWithDetail(conn, "SET LOCAL app.iam_worker_id = 'worker-A'");
                execWithDetail(conn, "SET LOCAL app.iam_fencing_token = '1'");
                int completed = execUpdateWithDetail(conn,
                        "UPDATE iam_provider_commands SET status = 'COMPLETED', completed_at = now(), lease_owner = NULL, lease_expires_at = NULL WHERE id = '" + expiredCommandId + "' AND status = 'CLAIMED'");
                assertThat(completed).as("expired global lease must block completion").isEqualTo(0);
                conn.commit();
            }
        }
    }
}
