package org.mepcity.kursplatform.iam.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.application.IamAuditWriter;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IamAuditWriteException;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * JDBC adapter for {@link IamAuditWriter}. Uses {@link DataSourceUtils#getConnection}, so it binds
 * to the SAME connection/transaction {@link org.mepcity.kursplatform.iam.infrastructure.SpringIamTransactionExecutor}
 * and {@link JdbcIamAuthRepository} are already using — the audit INSERT commits or rolls back as
 * one unit with the rest of the mutation, never separately.
 */
public final class JdbcIamAuditWriter implements IamAuditWriter {

    private static final String INSERT_SQL = """
            INSERT INTO audit_logs (id, organization_id, actor_user_id, request_id, action_type,
                payload_schema_version, event_scope, target_entity_type, event_kind,
                requires_target_entity, requires_class_scope, requires_operation_group,
                target_entity_id, old_value, new_value, event_metadata, reason_code,
                operation_group_id, is_undo, undo_of_audit_log_id)
            VALUES (?, ?, ?, ?, ?, 1, ?::event_scope_enum, ?, ?::event_kind_enum,
                true, false, false, ?, NULL, ?::jsonb, ?::jsonb, NULL, NULL, false, NULL)
            """;

    private final DataSource dataSource;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JdbcIamAuditWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void write(IamAuditEvent event) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setObject(1, event.id());
            statement.setObject(2, event.organizationId());
            statement.setObject(3, event.actorUserId());
            statement.setString(4, event.requestId());
            statement.setString(5, event.actionType());
            statement.setString(6, event.eventScope().name());
            statement.setString(7, event.targetEntityType());
            statement.setString(8, event.eventKind().name());
            statement.setObject(9, event.targetEntityId());
            statement.setString(10, objectMapper.writeValueAsString(event.newValue()));
            statement.setString(11, objectMapper.writeValueAsString(event.eventMetadata()));
            if (statement.executeUpdate() != 1) {
                throw new IamAuditWriteException("Audit satırı yazılamadı: beklenen etki 1 satır");
            }
        } catch (SQLException | JsonProcessingException e) {
            throw new IamAuditWriteException("Audit satırı yazılamadı", e);
        }
    }
}
