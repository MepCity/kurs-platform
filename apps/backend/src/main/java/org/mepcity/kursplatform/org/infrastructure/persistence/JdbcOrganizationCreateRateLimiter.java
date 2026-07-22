package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.OrganizationCreateRateLimiter;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.mepcity.kursplatform.org.application.RateLimitStorageException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL UPSERT quota shared by every backend instance; no process-local state is used. */
public final class JdbcOrganizationCreateRateLimiter implements OrganizationCreateRateLimiter {
    private static final String OPERATION = "ORG_CREATE";
    private static final String CLAIM = """
            INSERT INTO organization_create_rate_limits (actor_user_id, operation_code, window_started_at, request_count)
            SELECT ?, ?, to_timestamp(floor(extract(epoch FROM clock_timestamp()) / ?) * ?), 1
            ON CONFLICT (actor_user_id, operation_code, window_started_at)
            DO UPDATE SET request_count = organization_create_rate_limits.request_count + 1
              WHERE organization_create_rate_limits.request_count < ?
            RETURNING request_count
            """;
    private static final String RETRY_AFTER = """
            SELECT GREATEST(1, CEIL(EXTRACT(EPOCH FROM
                (window_started_at + (? * interval '1 second') - clock_timestamp()))))::bigint
            FROM organization_create_rate_limits
            WHERE actor_user_id = ? AND operation_code = ?
              AND window_started_at = to_timestamp(floor(extract(epoch FROM clock_timestamp()) / ?) * ?)
            """;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final int limit;
    private final Duration window;

    public JdbcOrganizationCreateRateLimiter(DataSource dataSource, PlatformTransactionManager transactionManager,
                                             int limit, Duration window) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.limit = limit;
        this.window = window;
    }

    @Override public void check(UUID actorUserId) {
        try {
            Long retryAfter = transactions.execute(status -> claim(actorUserId));
            if (retryAfter != null) throw new RateLimitExceededException(retryAfter);
        } catch (RateLimitExceededException exception) { throw exception; }
        catch (RuntimeException exception) { throw new RateLimitStorageException(exception); }
    }

    private Long claim(UUID actorUserId) {
        try (PreparedStatement statement = DataSourceUtils.getConnection(dataSource).prepareStatement(CLAIM)) {
            statement.setObject(1, actorUserId);
            statement.setString(2, OPERATION);
            statement.setLong(3, window.toSeconds());
            statement.setLong(4, window.toSeconds());
            statement.setInt(5, limit);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) return null;
            }
            try (PreparedStatement retry = DataSourceUtils.getConnection(dataSource).prepareStatement(RETRY_AFTER)) {
                retry.setLong(1, window.toSeconds());
                retry.setObject(2, actorUserId);
                retry.setString(3, OPERATION);
                retry.setLong(4, window.toSeconds());
                retry.setLong(5, window.toSeconds());
                try (ResultSet result = retry.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Rate limit slot unavailable");
                    return result.getLong(1);
                }
            }
        } catch (java.sql.SQLException exception) { throw new IllegalStateException(exception); }
    }
}
