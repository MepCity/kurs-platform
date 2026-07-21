-- IAM-004 Fix Round 2: audit integration. IAM_GIRIS_OTURUM_API_SOZLESMESI.md §14 requires
-- provider-token-exchange, platform-admin-activate, context-activation, the disabled/revoked
-- fail-closed branch, and provider-command security outcomes to each produce an audit event in
-- the SAME transaction as the mutation they describe. Reuses the AUDIT-001A audit_logs/
-- audit_action_catalog core (V2) exactly as ORG-003 does — new catalog rows here, no change to
-- the append-only table or its existing rows.

INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
    requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('PROVIDER_TOKEN_EXCHANGED', 1, 'USER', 'GLOBAL', 'ACCESS', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","contextSelectionTokenId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PLATFORM_ADMIN_ACTIVATED', 1, 'USER', 'GLOBAL', 'ACCESS', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","refreshTokenFamilyId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('CONTEXT_ACTIVATED', 1, 'USER', 'ORGANIZATION', 'ACCESS', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["deviceIdentifier","refreshTokenFamilyId","organizationMembershipId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('IAM_PROVIDER_SESSION_REVOKED', 1, 'USER', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["providerStatus"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('IAM_ORG_PROVIDER_SESSION_REVOKED', 1, 'USER', 'ORGANIZATION', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["providerStatus","organizationMembershipId"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PROVIDER_COMMAND_COMPLETED', 1, 'PROVIDER_COMMAND', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb);

-- iam_runtime writes only the columns these events actually populate; scope_class_id/
-- operation_group_id/ip_address/device_id are never set by IAM's audit writer (requires_class_scope
-- and requires_operation_group are false for every code above, same as AUDIT-001A's v1 rows).
GRANT INSERT (id, organization_id, actor_user_id, request_id, action_type, payload_schema_version,
    event_scope, target_entity_type, event_kind, requires_target_entity, requires_class_scope,
    requires_operation_group, target_entity_id, old_value, new_value, event_metadata, reason_code,
    operation_group_id, is_undo, undo_of_audit_log_id) ON audit_logs TO iam_runtime;

-- GLOBAL events: organization_id IS NULL (app.iam_operation_scope='IAM_AUTH' or 'GLOBAL',
-- app.iam_actor_user_id already set by IamTransactionExecutor for every IAM_AUTH/GLOBAL call).
CREATE POLICY audit_logs_insert_iam_global ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND action_type IN ('PROVIDER_TOKEN_EXCHANGED', 'PLATFORM_ADMIN_ACTIVATED', 'IAM_PROVIDER_SESSION_REVOKED')
        AND event_scope = 'GLOBAL'
        AND organization_id IS NULL
        AND target_entity_type = 'USER'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND (
            (current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
                AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
                AND target_entity_id = current_setting('app.iam_actor_user_id', true)::uuid)
            OR (current_setting('app.iam_operation_scope', true) = 'GLOBAL'
                AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM')
        )
    );

CREATE POLICY audit_logs_insert_iam_organization ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND action_type IN ('CONTEXT_ACTIVATED', 'IAM_ORG_PROVIDER_SESSION_REVOKED')
        AND event_scope = 'ORGANIZATION'
        AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
        AND organization_id IS NOT NULL
        AND organization_id = current_setting('app.iam_target_organization_id', true)::uuid
        AND target_entity_type = 'USER'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND target_entity_id = current_setting('app.iam_actor_user_id', true)::uuid
    );

-- Provider-command completion: written from inside the GLOBAL/<lifecycle-code> completeCommand
-- transaction, target_entity_id is the command id itself (no single "actor" concept for a
-- worker-driven system command).
CREATE POLICY audit_logs_insert_provider_command ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND action_type = 'PROVIDER_COMMAND_COMPLETED'
        AND event_scope = 'GLOBAL'
        AND organization_id IS NULL
        AND target_entity_type = 'PROVIDER_COMMAND'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND actor_user_id IS NULL
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    );
