package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
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
        }
    }
}
