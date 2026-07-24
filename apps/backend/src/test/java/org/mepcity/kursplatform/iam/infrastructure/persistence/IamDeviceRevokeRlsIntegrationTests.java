package org.mepcity.kursplatform.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real iam_runtime checks for the V21/V22 revoke authorization surface. */
class IamDeviceRevokeRlsIntegrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static final String PASSWORD = "iam-runtime-device-revoke-tests";
    private UUID actor;
    private UUID organization;
    private UUID otherUser;
    private UUID actorDevice;
    private UUID otherDevice;
    private UUID targetMembership;
    private UUID platformAdministratorId;

    @BeforeAll static void start() throws Exception {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().migrate();
        try (Connection admin = admin()) { admin.createStatement().execute("ALTER ROLE iam_runtime WITH PASSWORD '" + PASSWORD + "'"); }
    }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach void seed() throws Exception {
        actor = UUID.randomUUID(); organization = UUID.randomUUID();
        otherUser = UUID.randomUUID();
        actorDevice = UUID.randomUUID();
        otherDevice = UUID.randomUUID();
        targetMembership = UUID.randomUUID();
        platformAdministratorId = UUID.randomUUID();
        try (Connection admin = admin()) {
            admin.createStatement().execute("TRUNCATE organization_membership_permissions, organization_membership_roles, organization_memberships, people, organizations, users CASCADE");
            admin.createStatement().execute("INSERT INTO users(id,status) VALUES ('" + actor + "','ACTIVE'),('" + otherUser + "','ACTIVE')");
            admin.createStatement().execute("INSERT INTO organizations(id,name,default_timezone,status) VALUES ('" + organization + "','RLS test','Europe/Istanbul','ACTIVE')");
            UUID person = UUID.randomUUID(), membership = UUID.randomUUID(), role = UUID.randomUUID();
            admin.createStatement().execute("INSERT INTO people(id,organization_id,first_name,last_name,phone) VALUES ('" + person + "','" + organization + "','A','B','0')");
            admin.createStatement().execute("INSERT INTO organization_memberships(id,organization_id,user_id,person_id,status,granted_at) VALUES ('" + membership + "','" + organization + "','" + actor + "','" + person + "','ACTIVE',transaction_timestamp())");
            admin.createStatement().execute("INSERT INTO organization_membership_roles(id,organization_membership_id,organization_id,role,granted_at) VALUES ('" + role + "','" + membership + "','" + organization + "','ORG_ADMIN',transaction_timestamp())");
            UUID targetPerson = UUID.randomUUID();
            admin.createStatement().execute("INSERT INTO people(id,organization_id,first_name,last_name,phone) VALUES ('" + targetPerson + "','" + organization + "','T','U','1')");
            admin.createStatement().execute("INSERT INTO organization_memberships(id,organization_id,user_id,person_id,status,granted_at) VALUES ('" + targetMembership + "','" + organization + "','" + otherUser + "','" + targetPerson + "','ACTIVE',transaction_timestamp())");
            admin.createStatement().execute("INSERT INTO trusted_devices(id,user_id,device_identifier,platform) VALUES ('" + actorDevice + "','" + actor + "','" + UUID.randomUUID() + "','ANDROID'),('" + otherDevice + "','" + otherUser + "','" + UUID.randomUUID() + "','IOS')");
            admin.createStatement().execute("INSERT INTO platform_administrators(id,user_id,granted_by_user_id,granted_at) VALUES ('" + platformAdministratorId + "','" + actor + "','" + actor + "',transaction_timestamp())");
        }
    }

    @Test void securityDefinerRequiresExactRuntimeContext() throws Exception {
        assertThat(callAsRuntime(null, null, null, null, actor, organization)).isFalse();
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SESSION_REVOKE", actor, organization, actor, organization)).isTrue();
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SESSION_REVOKE", UUID.randomUUID(), organization, actor, organization)).isFalse();
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SESSION_REVOKE", actor, UUID.randomUUID(), actor, organization)).isFalse();
        assertThat(callAsRuntime("IAM_AUTH", "DEVICE_SESSION_REVOKE", actor, organization, actor, organization)).isFalse();
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SELF_REVOKE", actor, organization, actor, organization)).isFalse();
    }

    @Test void executeGrantIsLimitedToIamRuntime() throws Exception {
        try (Connection admin = admin()) {
            var rs = admin.createStatement().executeQuery("SELECT has_function_privilege('app_runtime','iam_device_session_revoke_authorized(uuid,uuid)','EXECUTE'), has_function_privilege('iam_runtime','iam_device_session_revoke_authorized(uuid,uuid)','EXECUTE')");
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse(); assertThat(rs.getBoolean(2)).isTrue();
            var grants = admin.createStatement().executeQuery("SELECT COUNT(*) FROM information_schema.routine_privileges WHERE routine_name='iam_device_session_revoke_authorized' AND grantee='PUBLIC'");
            grants.next(); assertThat(grants.getInt(1)).isZero();
        }
    }

    @Test
    void allIam006TablesForceRlsAndRuntimeHasNoBypassRole() throws Exception {
        String[] tables = {
                "trusted_devices", "refresh_token_families", "refresh_tokens",
                "organization_memberships", "organization_membership_roles",
                "organization_membership_permissions", "platform_administrators",
                "idempotency_keys", "audit_logs", "iam_device_rate_limits"
        };
        try (Connection owner = admin()) {
            for (String table : tables) {
                var flags = owner.createStatement().executeQuery(
                        "SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE oid='"
                                + table + "'::regclass");
                assertThat(flags.next()).isTrue();
                assertThat(flags.getBoolean(1)).as(table + " RLS").isTrue();
                assertThat(flags.getBoolean(2)).as(table + " FORCE RLS").isTrue();
            }
            var role = owner.createStatement().executeQuery(
                    "SELECT rolsuper, rolbypassrls FROM pg_roles WHERE rolname='iam_runtime'");
            assertThat(role.next()).isTrue();
            assertThat(role.getBoolean(1)).isFalse();
            assertThat(role.getBoolean(2)).isFalse();
            var immutableColumns = owner.createStatement().executeQuery(
                    "SELECT has_column_privilege('iam_runtime','trusted_devices','device_identifier','UPDATE'), "
                            + "has_column_privilege('iam_runtime','trusted_devices','user_id','UPDATE'), "
                            + "has_column_privilege('iam_runtime','trusted_devices','revoked_at','UPDATE')");
            immutableColumns.next();
            assertThat(immutableColumns.getBoolean(1)).isFalse();
            assertThat(immutableColumns.getBoolean(2)).isFalse();
            assertThat(immutableColumns.getBoolean(3)).isTrue();
        }
    }

    @Test
    void trustedDevicesAreBoundToExactActorTargetScopeAndOperation() throws Exception {
        assertThat(runtimeCount("IAM_AUTH", "DEVICE_LIST", actor, null, null,
                "SELECT count(*) FROM trusted_devices")).isEqualTo(1);
        assertThat(runtimeCount("IAM_AUTH", "DEVICE_LIST", otherUser, null, null,
                "SELECT count(*) FROM trusted_devices")).isEqualTo(1);
        assertThat(runtimeCount("IAM_AUTH", "SESSION_INFO", actor, null, actorDevice,
                "SELECT count(*) FROM trusted_devices")).isZero();
        assertThat(runtimeCount("GLOBAL", "PLATFORM_DEVICE_REVOKE", actor, otherUser, otherDevice,
                "SELECT count(*) FROM trusted_devices")).isEqualTo(1);
        assertThat(runtimeCount("GLOBAL", "PLATFORM_DEVICE_REVOKE", actor, otherUser, actorDevice,
                "SELECT count(*) FROM trusted_devices")).isZero();
        assertThatThrownBy(() -> runtimeCount(null, null, null, null, null,
                "UPDATE trusted_devices SET revoked_at=transaction_timestamp() WHERE id='"
                        + actorDevice + "' RETURNING 1")).isInstanceOf(Exception.class);
    }

    @Test
    void revokedRoleAndRevokedPlatformAdministratorFailClosed() throws Exception {
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SESSION_REVOKE", actor, organization, actor, organization))
                .isTrue();
        try (Connection owner = admin()) {
            owner.createStatement().execute(
                    "UPDATE organization_membership_roles SET revoked_at=transaction_timestamp() "
                            + "WHERE organization_id='" + organization + "' AND role='ORG_ADMIN'");
        }
        assertThat(callAsRuntime("ORGANIZATION", "DEVICE_SESSION_REVOKE", actor, organization, actor, organization))
                .isFalse();

        assertThat(runtimeSupportMembershipCount()).isEqualTo(1);
        try (Connection owner = admin()) {
            owner.createStatement().execute("UPDATE platform_administrators SET revoked_at=transaction_timestamp() "
                    + "WHERE id='" + platformAdministratorId + "'");
        }
        assertThat(runtimeSupportMembershipCount()).isZero();
    }

    @Test
    void rateLimitRowsAreIsolatedByActorScopeContextAndOperation() throws Exception {
        try (Connection runtime = runtime()) {
            runtime.setAutoCommit(false);
            set(runtime, "app.iam_operation_scope", "IAM_AUTH");
            set(runtime, "app.iam_operation_code", "DEVICE_LIST");
            set(runtime, "app.iam_actor_user_id", actor.toString());
            runtime.createStatement().execute(
                    "INSERT INTO iam_device_rate_limits(actor_user_id,scope_type,context_id,operation_code,"
                            + "window_started_at,request_count) VALUES ('" + actor
                            + "','IAM_AUTH','" + actor + "','DEVICE_LIST',date_trunc('minute',transaction_timestamp()),1)");
            runtime.commit();
        }
        assertThat(runtimeCount("IAM_AUTH", "DEVICE_LIST", actor, null, null,
                "SELECT count(*) FROM iam_device_rate_limits")).isEqualTo(1);
        assertThat(runtimeCount("IAM_AUTH", "DEVICE_SELF_REVOKE", actor, null, actorDevice,
                "SELECT count(*) FROM iam_device_rate_limits")).isZero();
        assertThat(runtimeCount("IAM_AUTH", "DEVICE_LIST", otherUser, null, null,
                "SELECT count(*) FROM iam_device_rate_limits")).isZero();
    }

    private long runtimeSupportMembershipCount() throws Exception {
        try (Connection runtime = runtime()) {
            runtime.setAutoCommit(false);
            set(runtime, "app.iam_operation_scope", "ORGANIZATION");
            set(runtime, "app.iam_operation_code", "DEVICE_SESSION_REVOKE");
            set(runtime, "app.iam_actor_user_id", actor.toString());
            set(runtime, "app.iam_target_organization_id", organization.toString());
            set(runtime, "app.iam_target_membership_id", targetMembership.toString());
            set(runtime, "app.iam_platform_admin_support_access", "true");
            var rows = runtime.createStatement().executeQuery(
                    "SELECT count(*) FROM organization_memberships WHERE id='" + targetMembership + "'");
            rows.next();
            long count = rows.getLong(1);
            runtime.rollback();
            return count;
        }
    }

    private long runtimeCount(
            String scope, String operation, UUID contextActor, UUID targetUser, UUID targetDevice,
            String sql) throws Exception {
        try (Connection runtime = runtime()) {
            runtime.setAutoCommit(false);
            if (scope != null) set(runtime, "app.iam_operation_scope", scope);
            if (operation != null) set(runtime, "app.iam_operation_code", operation);
            if (contextActor != null) set(runtime, "app.iam_actor_user_id", contextActor.toString());
            if (targetUser != null) set(runtime, "app.iam_target_user_id", targetUser.toString());
            if (targetDevice != null) set(runtime, "app.iam_target_device_id", targetDevice.toString());
            var rows = runtime.createStatement().executeQuery(sql);
            rows.next();
            long count = rows.getLong(1);
            runtime.rollback();
            return count;
        }
    }

    private boolean callAsRuntime(String scope, String operation, UUID contextActor, UUID contextOrganization, UUID argumentActor, UUID argumentOrganization) throws Exception {
        try (Connection runtime = runtime()) {
            runtime.setAutoCommit(false);
            if (scope != null) set(runtime, "app.iam_operation_scope", scope);
            if (operation != null) set(runtime, "app.iam_operation_code", operation);
            if (contextActor != null) set(runtime, "app.iam_actor_user_id", contextActor.toString());
            if (contextOrganization != null) set(runtime, "app.iam_target_organization_id", contextOrganization.toString());
            var rs = runtime.createStatement().executeQuery("SELECT iam_device_session_revoke_authorized('" + argumentActor + "','" + argumentOrganization + "')");
            rs.next(); boolean result = rs.getBoolean(1); runtime.rollback(); return result;
        }
    }

    private static void set(Connection connection, String key, String value) throws Exception {
        connection.createStatement().execute("SET LOCAL " + key + "='" + value + "'");
    }

    private static Connection runtime() throws Exception {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "iam_runtime", PASSWORD);
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }
}
