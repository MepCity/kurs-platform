package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** V25's least-privilege contract is exercised against PostgreSQL, never an in-memory substitute. */
class OrganizationListV25MigrationTests {

    @Test
    void createsForcedRlsActorScopedQuotaWithOnlyOrgRuntimeGrant() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration").load().migrate();
            try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = owner.createStatement();
                    var result = statement.executeQuery("SELECT relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname='organization_list_rate_limits'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isTrue();
                assertThat(result.getBoolean(2)).isTrue();
            }
            try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = owner.createStatement();
                    var result = statement.executeQuery("SELECT rolbypassrls, rolsuper FROM pg_roles WHERE rolname='org_runtime'")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getBoolean(1)).isFalse();
                assertThat(result.getBoolean(2)).isFalse();
            }
            try (Connection owner =
                            DriverManager.getConnection(
                                    postgres.getJdbcUrl(),
                                    postgres.getUsername(),
                                    postgres.getPassword());
                    var statement = owner.createStatement();
                    var result =
                            statement.executeQuery(
                                    """
                                    SELECT grantee, privilege_type
                                    FROM information_schema.role_table_grants
                                    WHERE table_schema='public'
                                      AND table_name='organization_list_rate_limits'
                                    ORDER BY grantee, privilege_type
                                    """)) {
                Set<String> grants = new java.util.HashSet<>();
                while (result.next()) {
                    grants.add(result.getString(1) + ":" + result.getString(2));
                }
                assertThat(grants)
                        .contains(
                                "org_runtime:SELECT",
                                "org_runtime:INSERT",
                                "org_runtime:UPDATE")
                        .noneMatch(
                                grant ->
                                        grant.startsWith("iam_runtime:")
                                                || grant.startsWith("app_runtime:")
                                                || grant.equals("org_runtime:DELETE"));
            }
            try (Connection owner =
                            DriverManager.getConnection(
                                    postgres.getJdbcUrl(),
                                    postgres.getUsername(),
                                    postgres.getPassword());
                    var statement = owner.createStatement();
                    var result =
                            statement.executeQuery(
                                    """
                                    SELECT policyname, cmd, roles::text, qual, with_check
                                    FROM pg_policies
                                    WHERE schemaname='public'
                                      AND tablename IN (
                                          'organization_list_rate_limits',
                                          'organization_memberships',
                                          'organization_membership_roles')
                                      AND policyname IN (
                                          'organization_list_rate_limits_org_runtime',
                                          'organization_memberships_select_org_list',
                                          'organization_membership_roles_select_org_list')
                                    ORDER BY policyname
                                    """)) {
                Set<String> policies = new java.util.HashSet<>();
                while (result.next()) {
                    String policy = result.getString("policyname");
                    policies.add(policy);
                    assertThat(result.getString("roles")).contains("org_runtime");
                    assertThat(result.getString("qual").toLowerCase(java.util.Locale.ROOT))
                            .contains("current_user")
                            .contains("app.iam_operation_code")
                            .contains("org_list")
                            .contains("app.iam_actor_user_id");
                }
                assertThat(policies)
                        .containsExactlyInAnyOrder(
                                "organization_list_rate_limits_org_runtime",
                                "organization_memberships_select_org_list",
                                "organization_membership_roles_select_org_list");
            }
        }
    }
}
