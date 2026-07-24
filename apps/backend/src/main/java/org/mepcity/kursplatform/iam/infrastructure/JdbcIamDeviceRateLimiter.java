package org.mepcity.kursplatform.iam.infrastructure;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.iam.application.IamDeviceRateLimiter;
import org.mepcity.kursplatform.iam.application.IamRateLimitExceededException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OperationScope;
import org.springframework.jdbc.datasource.DataSourceUtils;

/** Uses PostgreSQL clock_timestamp and one conditional UPSERT; no JVM clock or in-memory state. */
final class JdbcIamDeviceRateLimiter implements IamDeviceRateLimiter {
    private static final int LIMIT = 30;
    private static final long WINDOW_SECONDS = 60;
    private final DataSource dataSource;
    JdbcIamDeviceRateLimiter(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public void consume(UUID actor, OperationScope scope, UUID context, OperationCode operation) {
        String claim = "INSERT INTO iam_device_rate_limits(actor_user_id,scope_type,context_id,operation_code,window_started_at,request_count) "
                + "SELECT ?,?,?,?,to_timestamp(floor(extract(epoch FROM clock_timestamp()) / ?) * ?),1 "
                + "ON CONFLICT(actor_user_id,scope_type,context_id,operation_code,window_started_at) DO UPDATE SET request_count=iam_device_rate_limits.request_count+1 "
                + "WHERE iam_device_rate_limits.request_count < ? RETURNING request_count";
        java.sql.Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(claim)) {
            statement.setObject(1, actor); statement.setString(2, scope.name()); statement.setObject(3, context); statement.setString(4, operation.name());
            statement.setLong(5, WINDOW_SECONDS); statement.setLong(6, WINDOW_SECONDS); statement.setInt(7, LIMIT);
            try (ResultSet result = statement.executeQuery()) { if (result.next()) return; }
        } catch (java.sql.SQLException e) { throw new IllegalStateException("IAM rate-limit kaydı yazılamadı", e); }
        finally { DataSourceUtils.releaseConnection(connection, dataSource); }
        throw new IamRateLimitExceededException(WINDOW_SECONDS);
    }
}
