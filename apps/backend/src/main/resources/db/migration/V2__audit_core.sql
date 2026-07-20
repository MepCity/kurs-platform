-- Görev: AUDIT-001A — sınıf kapsamı kapalı erken ortak audit çekirdeği.

CREATE TABLE audit_action_catalog (
    code TEXT NOT NULL,
    payload_schema_version SMALLINT NOT NULL,
    target_entity_type TEXT NOT NULL,
    event_scope event_scope_enum NOT NULL,
    event_kind event_kind_enum NOT NULL,
    requires_target_entity BOOLEAN NOT NULL,
    requires_class_scope BOOLEAN NOT NULL DEFAULT false CHECK (requires_class_scope = false),
    requires_operation_group BOOLEAN NOT NULL DEFAULT false CHECK (requires_operation_group = false),
    is_undoable BOOLEAN NOT NULL DEFAULT false,
    payload_schema JSONB NOT NULL,
    PRIMARY KEY (code, payload_schema_version),
    UNIQUE (code, payload_schema_version, event_scope, target_entity_type, event_kind,
        requires_target_entity, requires_class_scope, requires_operation_group),
    CHECK ((event_kind = 'DATA_MUTATION' AND requires_target_entity) OR event_kind <> 'DATA_MUTATION')
    ,CHECK (requires_operation_group = false OR requires_class_scope = true)
);

INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
    requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('ORG_CREATED', 1, 'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["status","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('ORG_STATUS_CHANGED', 1, 'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, false,
 '{"oldValue":{"allowed":["status","rowVersion"]},"newValue":{"allowed":["status","rowVersion"]},"eventMetadata":{"allowed":["revokedMembershipCount","revokedFamilyCount","revokedTokenCount","operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('ORG_SETTING_CHANGED', 1, 'ORGANIZATION', 'ORGANIZATION', 'DATA_MUTATION', true, false, false, true,
 '{"oldValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"newValue":{"allowed":["name","shortName","defaultTimezone","primaryColor","logoAssetId","enabledModules","attendanceStatuses","rowVersion"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PLATFORM_ADMIN_ORG_ACCESS', 1, 'ORGANIZATION', 'ORGANIZATION', 'ACCESS', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","outcome"]},"reasonCodes":["FORBIDDEN"],"rejectUnknown":true}'::jsonb);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    organization_id UUID REFERENCES organizations(id),
    scope_class_id UUID,
    actor_user_id UUID REFERENCES users(id),
    request_id TEXT,
    action_type TEXT NOT NULL,
    payload_schema_version SMALLINT NOT NULL,
    event_scope event_scope_enum NOT NULL,
    target_entity_type TEXT NOT NULL,
    event_kind event_kind_enum NOT NULL,
    requires_target_entity BOOLEAN NOT NULL,
    requires_class_scope BOOLEAN NOT NULL CHECK (requires_class_scope = false),
    requires_operation_group BOOLEAN NOT NULL CHECK (requires_operation_group = false),
    target_entity_id UUID,
    old_value JSONB,
    new_value JSONB,
    event_metadata JSONB,
    reason_code TEXT,
    operation_group_id UUID,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    ip_address TEXT,
    device_id UUID REFERENCES trusted_devices(id),
    is_undo BOOLEAN NOT NULL DEFAULT false,
    undo_of_audit_log_id UUID,
    UNIQUE (id, organization_id),
    UNIQUE (id, event_scope),
    FOREIGN KEY (action_type, payload_schema_version, event_scope, target_entity_type, event_kind,
        requires_target_entity, requires_class_scope, requires_operation_group)
        REFERENCES audit_action_catalog (code, payload_schema_version, event_scope, target_entity_type, event_kind,
            requires_target_entity, requires_class_scope, requires_operation_group),
    FOREIGN KEY (undo_of_audit_log_id, organization_id) REFERENCES audit_logs (id, organization_id),
    FOREIGN KEY (undo_of_audit_log_id, event_scope) REFERENCES audit_logs (id, event_scope),
    CHECK ((event_scope = 'GLOBAL' AND organization_id IS NULL) OR (event_scope = 'ORGANIZATION' AND organization_id IS NOT NULL)),
    CHECK (scope_class_id IS NULL),
    CHECK ((requires_target_entity OR is_undo) AND target_entity_id IS NOT NULL
        OR (NOT requires_target_entity AND NOT is_undo)),
    CHECK (request_id IS NULL OR (char_length(request_id) BETWEEN 1 AND 128 AND request_id ~ '^[A-Za-z0-9._:-]+$')),
    CHECK ((is_undo AND undo_of_audit_log_id IS NOT NULL) OR (NOT is_undo AND undo_of_audit_log_id IS NULL)),
    CHECK (undo_of_audit_log_id IS NULL OR undo_of_audit_log_id <> id),
    CHECK ((requires_class_scope AND scope_class_id IS NOT NULL) OR (NOT requires_class_scope AND scope_class_id IS NULL)),
    CHECK ((requires_operation_group AND operation_group_id IS NOT NULL) OR (NOT requires_operation_group AND operation_group_id IS NULL))
);

CREATE UNIQUE INDEX audit_logs_one_undo_per_source_idx ON audit_logs (undo_of_audit_log_id) WHERE is_undo;
CREATE INDEX audit_logs_organization_occurred_idx ON audit_logs (organization_id, occurred_at DESC);
CREATE INDEX audit_logs_organization_class_occurred_idx ON audit_logs (organization_id, scope_class_id, occurred_at DESC);
CREATE INDEX audit_logs_organization_target_idx ON audit_logs (organization_id, target_entity_type, target_entity_id);
CREATE INDEX audit_logs_operation_group_idx ON audit_logs (operation_group_id) WHERE operation_group_id IS NOT NULL;

ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs FORCE ROW LEVEL SECURITY;
REVOKE ALL ON audit_action_catalog, audit_logs FROM PUBLIC;

CREATE OR REPLACE FUNCTION audit_logs_append_only_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'audit_logs append-only'; END; $$;
CREATE TRIGGER audit_logs_no_update_delete BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_append_only_guard();
