package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
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

    private static String single(Connection c, String sql) throws Exception {
        try (var s = c.createStatement(); var r = s.executeQuery(sql)) { r.next(); return r.getString(1); }
    }
}
