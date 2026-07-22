INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
 requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('SESSION_REFRESH_REPLAY_RECONCILED',1,'USER','GLOBAL','SECURITY',true,false,false,false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","refreshTokenFamilyId","organizationMembershipId","trustedDeviceId"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb)
ON CONFLICT (code,payload_schema_version) DO NOTHING;

UPDATE audit_action_catalog SET payload_schema =
'{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","refreshTokenFamilyId","organizationMembershipId","trustedDeviceId"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb
WHERE code='SESSION_LOGGED_OUT' AND payload_schema_version=1;

DROP POLICY audit_logs_insert_iam_global ON audit_logs;
CREATE POLICY audit_logs_insert_iam_global ON audit_logs FOR INSERT TO iam_runtime WITH CHECK (
 current_user='iam_runtime' AND event_scope='GLOBAL' AND organization_id IS NULL AND target_entity_type='USER'
 AND requires_target_entity AND NOT requires_class_scope AND scope_class_id IS NULL
 AND NOT requires_operation_group AND operation_group_id IS NULL AND NOT is_undo AND undo_of_audit_log_id IS NULL
 AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid AND target_entity_id=current_setting('app.iam_actor_user_id',true)::uuid
 AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND ((action_type IN ('SESSION_REFRESHED','SESSION_REFRESH_REUSE_DETECTED','SESSION_REFRESH_REPLAY_RECONCILED') AND current_setting('app.iam_operation_code',true)='SESSION_REFRESH')
 OR (action_type='SESSION_LOGGED_OUT' AND current_setting('app.iam_operation_code',true)='SESSION_LOGOUT')
 OR (action_type='PROVIDER_TOKEN_EXCHANGED' AND current_setting('app.iam_operation_code',true)='PROVIDER_TOKEN_EXCHANGE')
 OR (action_type='PLATFORM_ADMIN_ACTIVATED' AND current_setting('app.iam_operation_code',true)='PLATFORM_ADMIN_ACTIVATE')
 OR (action_type='IAM_PROVIDER_SESSION_REVOKED' AND current_setting('app.iam_operation_code',true) IN ('PLATFORM_ADMIN_ACTIVATE','CONTEXT_ACTIVATE'))
 OR (action_type='IAM_PROVIDER_STATUS_CHECK_BLOCKED' AND current_setting('app.iam_operation_code',true)='PLATFORM_ADMIN_ACTIVATE'))
);
