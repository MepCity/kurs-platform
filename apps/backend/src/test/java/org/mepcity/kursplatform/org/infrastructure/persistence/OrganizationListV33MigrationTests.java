package org.mepcity.kursplatform.org.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

/** Regression for Supabase session-pooler custom-GUC reset semantics observed by ALPHA-003. */
class OrganizationListV33MigrationTests {

    private static final String RUNTIME_PASSWORD = "alpha-003-runtime-password";

    @Test
    void supabaseSessionPoolerResidualEmptyGucFailsAtV32AndV33FailsClosed() throws Exception {
        try (PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")) {
            postgres.start();
            migrate(postgres, "32");

            UUID actor = UUID.randomUUID();
            UUID organization = UUID.randomUUID();
            seed(postgres, actor, organization);

            try (Connection runtime = runtimeConnection(postgres)) {
                primeTransactionLocalOrganizationGuc(runtime, organization);
                assertThat(currentSetting(runtime, "app.organization_id")).isEmpty();
                assertThatThrownBy(() -> globalListCount(runtime, actor, "GLOBAL", actor.toString()))
                        .isInstanceOf(SQLException.class)
                        .extracting(error -> ((SQLException) error).getSQLState())
                        .isEqualTo("22P02");
            }

            migrate(postgres, null);

            try (Connection runtime = runtimeConnection(postgres)) {
                assertThat(queryString(runtime, "SELECT session_user")).isEqualTo("iam_runtime");
                primeTransactionLocalOrganizationGuc(runtime, organization);
                assertThat(currentSetting(runtime, "app.organization_id")).isEmpty();

                assertThat(globalListCount(runtime, actor, "GLOBAL", actor.toString())).isOne();
                assertThat(globalListCount(runtime, actor, "ORGANIZATION", actor.toString()))
                        .isZero();
                assertThat(
                                globalListCount(
                                        runtime,
                                        actor,
                                        "GLOBAL",
                                        UUID.randomUUID().toString()))
                        .isZero();
            }

            try (Connection owner = ownerConnection(postgres)) {
                assertThat(queryLong(owner,
                                "SELECT max(version::integer) FROM flyway_schema_history WHERE success"))
                        .isEqualTo(33);
                assertThat(queryBoolean(owner,
                                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname='iam_runtime'"))
                        .isFalse();
                assertThat(queryBoolean(owner,
                                "SELECT rolsuper OR rolbypassrls FROM pg_roles WHERE rolname='org_runtime'"))
                        .isFalse();
                assertThat(queryBoolean(owner,
                                """
                                SELECT EXISTS (
                                    SELECT 1
                                    FROM pg_auth_members membership
                                    JOIN pg_roles granted ON granted.oid=membership.roleid
                                    JOIN pg_roles member ON member.oid=membership.member
                                    WHERE granted.rolname='org_runtime'
                                      AND member.rolname='iam_runtime'
                                )
                                """))
                        .isTrue();
            }
        }
    }

    private static void migrate(PostgreSQLContainer<?> postgres, String target) {
        var configuration =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword())
                        .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static void seed(
            PostgreSQLContainer<?> postgres, UUID actor, UUID organization) throws SQLException {
        try (Connection owner = ownerConnection(postgres)) {
            owner.createStatement()
                    .execute("ALTER ROLE iam_runtime PASSWORD '" + RUNTIME_PASSWORD + "'");
            try (var user = owner.prepareStatement("INSERT INTO users(id,status) VALUES (?,'ACTIVE')")) {
                user.setObject(1, actor);
                user.executeUpdate();
            }
            try (var admin =
                    owner.prepareStatement(
                            """
                            INSERT INTO platform_administrators(id,user_id,granted_at)
                            VALUES (?,?,transaction_timestamp())
                            """)) {
                admin.setObject(1, UUID.randomUUID());
                admin.setObject(2, actor);
                admin.executeUpdate();
            }
            try (var value =
                    owner.prepareStatement(
                            """
                            INSERT INTO organizations(
                                id,name,status,default_timezone,row_version,
                                created_by_user_id,updated_by_user_id)
                            VALUES (?,'ALPHA-003 Sentetik','ACTIVE','Europe/Istanbul',1,?,?)
                            """)) {
                value.setObject(1, organization);
                value.setObject(2, actor);
                value.setObject(3, actor);
                value.executeUpdate();
            }
        }
    }

    private static void primeTransactionLocalOrganizationGuc(
            Connection runtime, UUID organization) throws SQLException {
        runtime.setAutoCommit(false);
        runtime.createStatement().execute("SET LOCAL ROLE org_runtime");
        setLocal(runtime, "app.organization_id", organization.toString());
        runtime.commit();
    }

    private static long globalListCount(
            Connection runtime, UUID actor, String scope, String actorGuc) throws SQLException {
        runtime.setAutoCommit(false);
        try {
            runtime.createStatement().execute("SET LOCAL ROLE org_runtime");
            setLocal(runtime, "app.iam_operation_scope", scope);
            setLocal(runtime, "app.iam_actor_user_id", actorGuc);
            setLocal(runtime, "app.iam_operation_code", "ORG_LIST");
            setLocal(runtime, "app.iam_platform_admin_support_access", "false");
            assertThat(queryString(runtime, "SELECT current_user")).isEqualTo("org_runtime");
            assertThat(queryString(runtime, "SELECT session_user")).isEqualTo("iam_runtime");
            long count = queryLong(runtime, "SELECT count(*) FROM organizations");
            runtime.rollback();
            return count;
        } catch (SQLException exception) {
            runtime.rollback();
            throw exception;
        }
    }

    private static void setLocal(Connection connection, String key, String value)
            throws SQLException {
        try (var statement = connection.prepareStatement("SELECT set_config(?,?,true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        }
    }

    private static String currentSetting(Connection connection, String key) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT current_setting(?,true)")) {
            statement.setString(1, key);
            try (var result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                return result.getString(1);
            }
        }
    }

    private static String queryString(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static long queryLong(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }

    private static boolean queryBoolean(Connection connection, String sql) throws SQLException {
        try (var result = connection.createStatement().executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private static Connection runtimeConnection(PostgreSQLContainer<?> postgres)
            throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), "iam_runtime", RUNTIME_PASSWORD);
    }

    private static Connection ownerConnection(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
