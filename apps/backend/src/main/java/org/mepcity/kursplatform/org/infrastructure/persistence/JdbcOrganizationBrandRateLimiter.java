package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.OrganizationBrandRateLimiter;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.mepcity.kursplatform.org.application.RateLimitStorageException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Atomically shared PostgreSQL quota for organization-brand mutations. */
public final class JdbcOrganizationBrandRateLimiter implements OrganizationBrandRateLimiter {
    private static final String CLAIM = """
            INSERT INTO organization_brand_rate_limits (actor_user_id, organization_id, operation_code, window_started_at, request_count)
            SELECT ?, ?, ?, to_timestamp(floor(extract(epoch FROM clock_timestamp()) / ?) * ?), 1
            ON CONFLICT (actor_user_id, organization_id, operation_code, window_started_at)
            DO UPDATE SET request_count = organization_brand_rate_limits.request_count + 1
              WHERE organization_brand_rate_limits.request_count < ?
            RETURNING request_count
            """;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final int limit;
    private final Duration window;

    public JdbcOrganizationBrandRateLimiter(DataSource dataSource, PlatformTransactionManager manager, int limit, Duration window) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(manager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.limit = limit;
        this.window = window;
    }

    @Override public void check(UUID actor, UUID organization, String operation) {
        try {
            Boolean allowed = transactions.execute(status -> claim(actor, organization, operation));
            if (!Boolean.TRUE.equals(allowed)) throw new RateLimitExceededException(Math.max(1, window.toSeconds()));
        } catch (RateLimitExceededException exception) { throw exception; }
        catch (RuntimeException exception) { throw new RateLimitStorageException(exception); }
    }

    private boolean claim(UUID actor, UUID organization, String operation) {
        try {
            var connection = DataSourceUtils.getConnection(dataSource);
            connection.createStatement().execute("SET LOCAL ROLE org_runtime");
            set(connection, "app.iam_operation_scope", "ORGANIZATION");
            set(connection, "app.iam_actor_user_id", actor.toString());
            set(connection, "app.organization_id", organization.toString());
            set(connection, "app.iam_operation_code", operation);
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException(exception);
        }
        try (PreparedStatement statement = DataSourceUtils.getConnection(dataSource).prepareStatement(CLAIM)) {
            statement.setObject(1, actor); statement.setObject(2, organization); statement.setString(3, operation);
            statement.setLong(4, window.toSeconds()); statement.setLong(5, window.toSeconds()); statement.setInt(6, limit);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        } catch (java.sql.SQLException exception) { throw new IllegalStateException(exception); }
    }

    private static void set(java.sql.Connection connection, String key, String value) throws java.sql.SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        }
    }
}
