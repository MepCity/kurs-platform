package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.OrganizationListRateLimiter;
import org.mepcity.kursplatform.org.application.OrganizationPersistenceStateException;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.springframework.jdbc.datasource.DataSourceUtils;

/** Persistent actor quota; it runs in the request transaction so audit failure rolls it back too. */
public final class JdbcOrganizationListRateLimiter implements OrganizationListRateLimiter {
    private final DataSource dataSource;
    private final int limit;
    private final Duration window;

    public JdbcOrganizationListRateLimiter(DataSource dataSource, int limit, Duration window) {
        this.dataSource = dataSource;
        this.limit = limit;
        this.window = window;
    }

    @Override
    public void check(UUID actorUserId) {
        String sql = """
                INSERT INTO organization_list_rate_limits (actor_user_id, window_started_at, request_count)
                VALUES (?, date_trunc('second', transaction_timestamp()) -
                    mod(floor(extract(epoch FROM transaction_timestamp()))::bigint, ?)
                        * interval '1 second', 1)
                ON CONFLICT (actor_user_id, window_started_at) DO UPDATE
                    SET request_count = organization_list_rate_limits.request_count + 1
                RETURNING request_count, extract(epoch FROM (window_started_at + ? * interval '1 second' - transaction_timestamp()))::bigint
                """;
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, actorUserId);
            statement.setLong(2, window.toSeconds());
            statement.setLong(3, window.toSeconds());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("ORG_LIST kota sonucu yok");
                if (result.getInt(1) > limit) {
                    throw new RateLimitExceededException(Math.max(1, result.getLong(2)));
                }
            }
        } catch (RateLimitExceededException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("ORG_LIST kota kaydı yazılamadı", exception);
        }
    }
}
