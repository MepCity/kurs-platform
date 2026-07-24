package org.mepcity.kursplatform.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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

    @BeforeAll static void start() throws Exception {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().migrate();
        try (Connection admin = admin()) { admin.createStatement().execute("ALTER ROLE iam_runtime WITH PASSWORD '" + PASSWORD + "'"); }
    }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach void seed() throws Exception {
        actor = UUID.randomUUID(); organization = UUID.randomUUID();
        try (Connection admin = admin()) {
            admin.createStatement().execute("TRUNCATE organization_membership_permissions, organization_membership_roles, organization_memberships, people, organizations, users CASCADE");
            admin.createStatement().execute("INSERT INTO users(id,status) VALUES ('" + actor + "','ACTIVE')");
            admin.createStatement().execute("INSERT INTO organizations(id,name,default_timezone,status) VALUES ('" + organization + "','RLS test','Europe/Istanbul','ACTIVE')");
            UUID person = UUID.randomUUID(), membership = UUID.randomUUID(), role = UUID.randomUUID();
            admin.createStatement().execute("INSERT INTO people(id,organization_id,first_name,last_name,phone) VALUES ('" + person + "','" + organization + "','A','B','0')");
            admin.createStatement().execute("INSERT INTO organization_memberships(id,organization_id,user_id,person_id,status,granted_at) VALUES ('" + membership + "','" + organization + "','" + actor + "','" + person + "','ACTIVE',transaction_timestamp())");
            admin.createStatement().execute("INSERT INTO organization_membership_roles(id,organization_membership_id,organization_id,role,granted_at) VALUES ('" + role + "','" + membership + "','" + organization + "','ORG_ADMIN',transaction_timestamp())");
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

    private boolean callAsRuntime(String scope, String operation, UUID contextActor, UUID contextOrganization, UUID argumentActor, UUID argumentOrganization) throws Exception {
        try (Connection runtime = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "iam_runtime", PASSWORD)) {
            runtime.setAutoCommit(false);
            if (scope != null) runtime.createStatement().execute("SET LOCAL app.iam_operation_scope='" + scope + "'");
            if (operation != null) runtime.createStatement().execute("SET LOCAL app.iam_operation_code='" + operation + "'");
            if (contextActor != null) runtime.createStatement().execute("SET LOCAL app.iam_actor_user_id='" + contextActor + "'");
            if (contextOrganization != null) runtime.createStatement().execute("SET LOCAL app.iam_target_organization_id='" + contextOrganization + "'");
            var rs = runtime.createStatement().executeQuery("SELECT iam_device_session_revoke_authorized('" + argumentActor + "','" + argumentOrganization + "')");
            rs.next(); boolean result = rs.getBoolean(1); runtime.rollback(); return result;
        }
    }

    private static Connection admin() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }
}
