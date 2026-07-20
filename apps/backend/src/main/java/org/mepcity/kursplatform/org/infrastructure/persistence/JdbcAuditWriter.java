package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.AuditEvent;
import org.mepcity.kursplatform.org.application.AuditWriter;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * JDBC adapter for {@link AuditWriter}. Uses the caller's Spring transaction and the same
 * PostgreSQL RLS context established by the application service.
 *
 * <p>Only the columns granted to {@code org_runtime} by the V3 migration are written. The
 * {@code scope_class_id}, {@code occurred_at}, {@code ip_address} and {@code device_id} columns
 * are intentionally not set: {@code scope_class_id} stays {@code NULL} per the V2 catalog
 * {@code requires_class_scope=false} contract, {@code occurred_at} uses the table default
 * ({@code transaction_timestamp()}), and ORG V1 flows do not record IP/device context yet.
 *
 * <p>JSONB payloads are bound as text and cast with {@code ?::jsonb}. The text is produced by
 * {@link #serialize(AuditEvent.AuditPayload)} and {@link #serialize(AuditEvent.AuditMetadata)},
 * which switch on the sealed typed value objects directly. No arbitrary {@code Map} or
 * {@code toString()} of unknown objects is ever serialized, so the catalog payload shape cannot be
 * bypassed through the writer.
 */
public final class JdbcAuditWriter implements AuditWriter {

    private static final String INSERT_SQL = """
            INSERT INTO audit_logs (id, organization_id, actor_user_id, request_id, action_type,
                payload_schema_version, event_scope, target_entity_type, event_kind,
                requires_target_entity, requires_class_scope, requires_operation_group,
                target_entity_id, old_value, new_value, event_metadata, reason_code,
                operation_group_id, is_undo, undo_of_audit_log_id)
            VALUES (?, ?, ?, ?, ?, ?, ?::event_scope_enum, ?, ?::event_kind_enum,
                true, false, false, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?,
                NULL, false, NULL)
            """;

    private final DataSource dataSource;

    public JdbcAuditWriter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void write(AuditEvent event) {
        // Arrange: bind the caller's transactional connection and the prepared statement.
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            // Act: bind every granted column from the typed event.
            statement.setObject(1, event.id());
            statement.setObject(2, event.organizationId());
            statement.setObject(3, event.actorUserId());
            statement.setString(4, event.requestId());
            statement.setString(5, event.actionType());
            statement.setShort(6, event.payloadSchemaVersion());
            statement.setString(7, event.eventScope().name());
            statement.setString(8, event.targetEntityType());
            statement.setString(9, event.eventKind().name());
            statement.setObject(10, event.targetEntityId());
            statement.setString(11, serialize(event.oldValue()));
            statement.setString(12, serialize(event.newValue()));
            statement.setString(13, serialize(event.eventMetadata()));
            statement.setString(14, event.reasonCode());
            if (statement.executeUpdate() != 1) {
                throw new AuditWriteException("Audit satırı yazılamadı: beklenen etki 1 satır", null);
            }
        } catch (SQLException exception) {
            // Assert: propagate so the surrounding transaction rolls back atomically.
            throw new AuditWriteException("Audit satırı yazılamadı", exception);
        }
    }

    private static String serialize(AuditEvent.AuditPayload payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof AuditEvent.NullPayload) {
            return null;
        }
        if (payload instanceof AuditEvent.StatusPayload status) {
            return "{\"status\":\"" + status.status().name() + "\",\"rowVersion\":" + status.rowVersion() + "}";
        }
        if (payload instanceof AuditEvent.SettingPayload setting) {
            return serializeSetting(setting);
        }
        throw new AuditWriteException("Bilinmeyen payload tipi: " + payload.getClass().getName(), null);
    }

    /**
     * A field is emitted iff it is in {@code changedFields} — regardless of whether its value is
     * {@code null} or an empty list. This is what lets an untouched field (omitted entirely) be
     * told apart from a field explicitly cleared to {@code null} (emitted as JSON {@code null}) or
     * to an empty list (emitted as {@code []}).
     */
    private static String serializeSetting(AuditEvent.SettingPayload setting) {
        var builder = new StringBuilder("{");
        var changed = setting.changedFields();
        boolean first = true;
        if (changed.contains("name")) {
            first = appendStringField(builder, first, "name", setting.name());
        }
        if (changed.contains("shortName")) {
            first = appendStringField(builder, first, "shortName", setting.shortName());
        }
        if (changed.contains("defaultTimezone")) {
            first = appendStringField(builder, first, "defaultTimezone", setting.defaultTimezone());
        }
        if (changed.contains("primaryColor")) {
            first = appendStringField(builder, first, "primaryColor", setting.primaryColor());
        }
        if (changed.contains("secondaryColor")) {
            first = appendStringField(builder, first, "secondaryColor", setting.secondaryColor());
        }
        if (changed.contains("logoAssetId")) {
            first = appendUuidField(builder, first, "logoAssetId", setting.logoAssetId());
        }
        if (changed.contains("enabledModules")) {
            first = appendModuleStateListField(builder, first, "enabledModules", setting.enabledModules());
        }
        if (changed.contains("brandColors")) {
            first = appendBrandColorListField(builder, first, "brandColors", setting.brandColors());
        }
        if (changed.contains("attendanceStatuses")) {
            first = appendStringListField(builder, first, "attendanceStatuses", setting.attendanceStatuses());
        }
        builder.append(",\"rowVersion\":").append(setting.rowVersion()).append('}');
        return builder.toString();
    }

    private static String serialize(AuditEvent.AuditMetadata metadata) {
        if (metadata instanceof AuditEvent.CreatedMetadata created) {
            return "{\"operationCode\":\"" + created.operationCode().name() + "\"}";
        }
        if (metadata instanceof AuditEvent.SettingChangedMetadata setting) {
            return "{\"operationCode\":\"" + setting.operationCode().name() + "\"}";
        }
        if (metadata instanceof AuditEvent.StatusChangedMetadata status) {
            return "{\"operationCode\":\"" + status.operationCode().name()
                    + "\",\"revokedMembershipCount\":" + status.revokedMembershipCount()
                    + ",\"revokedFamilyCount\":" + status.revokedFamilyCount()
                    + ",\"revokedTokenCount\":" + status.revokedTokenCount() + "}";
        }
        if (metadata instanceof AuditEvent.AccessMetadata access) {
            return "{\"operationCode\":\"" + access.operationCode().name()
                    + "\",\"outcome\":\"" + access.outcome().name() + "\"}";
        }
        throw new AuditWriteException("Bilinmeyen metadata tipi: " + metadata.getClass().getName(), null);
    }

    /** Always emits the field, as an explicit JSON {@code null} when {@code value} is {@code null}. */
    private static boolean appendStringField(StringBuilder builder, boolean first, String field, String value) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(field).append("\":");
        builder.append(value == null ? "null" : "\"" + escape(value) + "\"");
        return false;
    }

    /** Always emits the field, as an explicit JSON {@code null} when {@code value} is {@code null}. */
    private static boolean appendUuidField(StringBuilder builder, boolean first, String field, UUID value) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(field).append("\":");
        builder.append(value == null ? "null" : "\"" + value + "\"");
        return false;
    }

    /** Always emits the field, as {@code []} when {@code values} is {@code null} or empty. */
    private static boolean appendStringListField(StringBuilder builder, boolean first, String field, List<String> values) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(field).append("\":[");
        boolean itemFirst = true;
        for (String value : values) {
            if (!itemFirst) {
                builder.append(',');
            }
            itemFirst = false;
            builder.append('"').append(escape(value)).append('"');
        }
        builder.append(']');
        return false;
    }

    /**
     * Always emits the field, as {@code []} when {@code values} is {@code null} or empty. Each
     * element is the full {@code organization_modules} snapshot triple {@code
     * {moduleCode,isEnabled,sortOrder}}, in the order the caller supplied (the caller is
     * contractually responsible for ascending {@code sortOrder} — see {@code ORG_MARKA_AYARLARI_
     * API_SOZLESMESI.md §2.8.2}).
     */
    private static boolean appendModuleStateListField(
            StringBuilder builder, boolean first, String field, List<AuditEvent.ModuleState> values) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(field).append("\":[");
        boolean itemFirst = true;
        for (AuditEvent.ModuleState value : values) {
            if (!itemFirst) {
                builder.append(',');
            }
            itemFirst = false;
            builder.append("{\"moduleCode\":\"").append(escape(value.moduleCode())).append("\",\"isEnabled\":")
                    .append(value.isEnabled()).append(",\"sortOrder\":").append(value.sortOrder()).append('}');
        }
        builder.append(']');
        return false;
    }

    /**
     * Always emits the field, as {@code []} when {@code values} is {@code null} or empty. Each
     * element is the full {@code organization_brand_colors} snapshot pair {@code
     * {colorHex,sortOrder}}, in the order the caller supplied.
     */
    private static boolean appendBrandColorListField(
            StringBuilder builder, boolean first, String field, List<AuditEvent.BrandColor> values) {
        if (!first) {
            builder.append(',');
        }
        builder.append('"').append(field).append("\":[");
        boolean itemFirst = true;
        for (AuditEvent.BrandColor value : values) {
            if (!itemFirst) {
                builder.append(',');
            }
            itemFirst = false;
            builder.append("{\"colorHex\":\"").append(escape(value.colorHex())).append("\",\"sortOrder\":")
                    .append(value.sortOrder()).append('}');
        }
        builder.append(']');
        return false;
    }

    private static String escape(String text) {
        var builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                default -> {
                    // Every other C0 control character must be escaped as a JSON unicode sequence; an
                    // unescaped literal control byte inside a JSON string is invalid and would make
                    // ?::jsonb reject the row.
                    if (ch < 0x20) {
                        builder.append(String.format("\\u%04x", (int) ch));
                    } else {
                        builder.append(ch);
                    }
                }
            }
        }
        return builder.toString();
    }
}
