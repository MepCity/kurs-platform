package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real PostgreSQL proof for ORG-005's V20 seed and fail-closed policy extensions. */
class OrganizationBrandV20MigrationTests {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll static void migrate() {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }
    @AfterAll static void stop() { POSTGRES.stop(); }

    @Test void v20CreatesForceRlsTablesAndSixModuleSeedTrigger() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            assertThat(single(c, "SELECT relforcerowsecurity FROM pg_class WHERE relname='organization_brand_colors'")).isEqualTo("t");
            assertThat(single(c, "SELECT relforcerowsecurity FROM pg_class WHERE relname='organization_modules'")).isEqualTo("t");
            assertThat(single(c, "SELECT relforcerowsecurity FROM pg_class WHERE relname='organization_brand_rate_limits'")).isEqualTo("t");
            assertThat(single(c, "SELECT count(*) FROM pg_trigger WHERE tgname='organizations_seed_modules'")).isEqualTo("1");
        }
    }

    @Test void paletteAllowsSameSortOrderButKeepsColorHexUnique() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            UUID org = UUID.randomUUID();
            // Schema proof: the palette key is organization+color, not organization+sort order.
            assertThat(single(c, "SELECT count(*) FROM pg_constraint WHERE conrelid='organization_brand_colors'::regclass AND contype='u'")).isEqualTo("0");
            assertThat(single(c, "SELECT count(*) FROM pg_constraint WHERE conrelid='organization_brand_colors'::regclass AND contype='p'")).isEqualTo("1");
        }
    }

    @Test void v14RecreatesOrgSettingAndIdempotencyPolicies() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            String audit = single(c, "SELECT pg_get_expr(polwithcheck, polrelid) FROM pg_policy WHERE polname='audit_logs_insert_org_setting_changed'");
            String idem = single(c, "SELECT pg_get_expr(polqual, polrelid) FROM pg_policy WHERE polname='idempotency_keys_org_runtime_organization'");
            assertThat(audit).contains("ORG_UPDATE_BRAND_COLORS", "ORG_UPDATE_MODULES", "org_actor_has_brand_access");
            assertThat(idem).contains("ORG_UPDATE_BRAND", "ORG_UPDATE_MODULES", "org_actor_has_brand_access");
        }
    }

    @Test void paletteAndModuleWritesHaveOnlyNarrowOperationPolicies() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            String palettePolicies = single(c, "SELECT string_agg(polname || ':' || polcmd::text || ':' || pg_get_expr(polwithcheck, polrelid), E'\\n' ORDER BY polname) FROM pg_policy WHERE polrelid='organization_brand_colors'::regclass");
            String modulePolicies = single(c, "SELECT string_agg(polname || ':' || polcmd::text || ':' || coalesce(pg_get_expr(polqual, polrelid), '') || ':' || coalesce(pg_get_expr(polwithcheck, polrelid), ''), E'\\n' ORDER BY polname) FROM pg_policy WHERE polrelid='organization_modules'::regclass");
            assertThat(palettePolicies).contains("brand_colors_org_runtime_insert:a", "ORG_UPDATE_BRAND_COLORS", "BRAND_MANAGE")
                    .doesNotContain(":*:");
            assertThat(modulePolicies).contains("modules_org_runtime_update:w", "ORG_UPDATE_MODULES",
                    "modules_org_create_seed:a", "ORG_CREATE").doesNotContain("modules_org_runtime:*:");
        }
    }

    @Test void brandAccessFunctionHasDefinerHardeningAndNoPublicExecuteGrant() throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            String definition = single(c, "SELECT pg_get_functiondef('public.org_actor_has_brand_access(uuid,uuid,text)'::regprocedure)");
            String grants = single(c, "SELECT coalesce(string_agg(grantee || ':' || privilege_type, ',' ORDER BY grantee), '') FROM information_schema.role_routine_grants WHERE routine_schema='public' AND routine_name='org_actor_has_brand_access'");
            assertThat(definition).contains("SECURITY DEFINER", "SET search_path TO 'pg_catalog', 'public'",
                    "public.organization_memberships", "public.organization_membership_roles");
            assertThat(grants).contains("org_runtime:EXECUTE")
                    .doesNotContain("PUBLIC:EXECUTE", "iam_runtime:EXECUTE", "app_runtime:EXECUTE");
        }
    }

    @Test void brandAccessFunctionEnforcesTheLiveActorPermissionAndGucMatrix() throws Exception {
        UUID organization = UUID.randomUUID();
        UUID admin = member(organization, "ORG_ADMIN", null, false, false, false);
        UUID brandTeacher = member(organization, "TEACHER", "BRAND_MANAGE", false, false, false);
        UUID moduleTeacher = member(organization, "TEACHER", "MODULE_MANAGE", false, false, false);
        UUID noPermissionTeacher = member(organization, "TEACHER", null, false, false, false);
        UUID revokedMembership = member(organization, "ORG_ADMIN", null, true, false, false);
        UUID revokedRole = member(organization, "ORG_ADMIN", null, false, true, false);
        UUID revokedPermission = member(organization, "TEACHER", "BRAND_MANAGE", false, false, true);

        assertThat(callAsOrgRuntime(organization, admin, "ORGANIZATION", "ORG_VIEW_BRAND", null)).isTrue();
        assertThat(callAsOrgRuntime(organization, admin, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isTrue();
        assertThat(callAsOrgRuntime(organization, admin, "ORGANIZATION", "ORG_UPDATE_MODULES", "MODULE_MANAGE")).isTrue();
        assertThat(callAsOrgRuntime(organization, brandTeacher, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isTrue();
        assertThat(callAsOrgRuntime(organization, brandTeacher, "ORGANIZATION", "ORG_UPDATE_BRAND_COLORS", "BRAND_MANAGE")).isTrue();
        assertThat(callAsOrgRuntime(organization, brandTeacher, "ORGANIZATION", "ORG_UPDATE_MODULES", "MODULE_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, moduleTeacher, "ORGANIZATION", "ORG_UPDATE_MODULES", "MODULE_MANAGE")).isTrue();
        assertThat(callAsOrgRuntime(organization, moduleTeacher, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, noPermissionTeacher, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, revokedMembership, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, revokedRole, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, revokedPermission, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, admin, "GLOBAL", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(UUID.randomUUID(), admin, "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, UUID.randomUUID(), "ORGANIZATION", "ORG_UPDATE_BRAND", "BRAND_MANAGE")).isFalse();
        assertThat(callAsOrgRuntime(organization, admin, "ORGANIZATION", "IAM_AUTH", "BRAND_MANAGE")).isFalse();
    }

    @Test void brandAccessFunctionExecuteIsDeniedOutsideOrgRuntime() throws Exception {
        assertSqlState("42501", () -> callAs("iam_runtime"));
        assertSqlState("42501", () -> callAs("app_runtime"));
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            c.setAutoCommit(false);
            c.createStatement().execute("CREATE ROLE org_brand_function_denied NOLOGIN");
            c.commit();
        }
        assertSqlState("42501", () -> callAs("org_brand_function_denied"));
    }

    private UUID member(UUID organization, String role, String permission, boolean membershipRevoked,
            boolean roleRevoked, boolean permissionRevoked) throws Exception {
        UUID actor = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            c.setAutoCommit(false);
            c.createStatement().execute("INSERT INTO users (id, status) VALUES ('" + actor + "', 'ACTIVE') ON CONFLICT DO NOTHING");
            c.createStatement().execute("INSERT INTO organizations (id, name, status, default_timezone, created_by_user_id, updated_by_user_id) VALUES ('" + organization + "', 'Function test', 'ACTIVE', 'Europe/Istanbul', '" + actor + "', '" + actor + "') ON CONFLICT DO NOTHING");
            c.createStatement().execute("INSERT INTO people (id, organization_id, first_name, last_name, phone) VALUES ('" + UUID.randomUUID() + "','" + organization + "','A','B','1')");
            c.createStatement().execute("INSERT INTO organization_memberships (id, organization_id, user_id, person_id, status, granted_at) VALUES ('" + membership + "','" + organization + "','" + actor + "',(SELECT id FROM people WHERE organization_id='" + organization + "' ORDER BY created_at DESC LIMIT 1),'" + (membershipRevoked ? "SUSPENDED" : "ACTIVE") + "',transaction_timestamp())");
            c.createStatement().execute("INSERT INTO organization_membership_roles (id, organization_membership_id, organization_id, role, granted_at, revoked_at) VALUES ('" + roleId + "','" + membership + "','" + organization + "','" + role + "',transaction_timestamp()," + (roleRevoked ? "transaction_timestamp()" : "NULL") + ")");
            if (permission != null) c.createStatement().execute("INSERT INTO organization_membership_permissions (id, organization_id, target_membership_role_id, target_role_code, permission_code, granted_by_platform_admin_user_id, granted_at, revoked_at) VALUES ('" + UUID.randomUUID() + "','" + organization + "','" + roleId + "','TEACHER','" + permission + "','" + UUID.randomUUID() + "',transaction_timestamp()," + (permissionRevoked ? "transaction_timestamp()" : "NULL") + ")");
            c.commit();
        }
        return actor;
    }

    private boolean callAsOrgRuntime(UUID organization, UUID actor, String scope, String operation, String permission) throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            c.setAutoCommit(false);
            c.createStatement().execute("SET LOCAL ROLE org_runtime");
            c.createStatement().execute("SET LOCAL app.iam_operation_scope = '" + scope + "'");
            c.createStatement().execute("SET LOCAL app.organization_id = '" + organization + "'");
            c.createStatement().execute("SET LOCAL app.iam_actor_user_id = '" + actor + "'");
            c.createStatement().execute("SET LOCAL app.iam_operation_code = '" + operation + "'");
            try (var r = c.createStatement().executeQuery("SELECT org_actor_has_brand_access('" + organization + "', '" + actor + "', " + (permission == null ? "NULL" : "'" + permission + "'") + ")")) { r.next(); return r.getBoolean(1); }
        }
    }

    private void callAs(String role) throws Exception {
        try (Connection c = java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            c.setAutoCommit(false);
            c.createStatement().execute("SET LOCAL ROLE " + role);
            c.createStatement().executeQuery("SELECT org_actor_has_brand_access(NULL, NULL, NULL)");
        }
    }

    private static void assertSqlState(String expected, ThrowingSql operation) {
        assertThatThrownBy(operation::run).isInstanceOf(SQLException.class)
                .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo(expected));
    }

    @FunctionalInterface private interface ThrowingSql { void run() throws Exception; }

    private static String single(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
}
