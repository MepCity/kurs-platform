-- IAM-006: device/session revoke is intentionally restricted to three operation codes.  These
-- policies replace the broad V1 ORGANIZATION predicates and retain FORCE RLS on every table.

INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
    requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('DEVICE_SELF_REVOKED', 1, 'USER', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","trustedDeviceId","revokedRefreshTokenFamilyCount"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('DEVICE_SESSION_REVOKED', 1, 'USER', 'ORGANIZATION', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","organizationMembershipId","revokedRefreshTokenFamilyCount"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PLATFORM_DEVICE_REVOKED', 1, 'USER', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","trustedDeviceId","revokedRefreshTokenFamilyCount"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PLATFORM_ADMIN_ORG_ACCESS', 1, 'USER', 'ORGANIZATION', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","organizationMembershipId"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb)
ON CONFLICT (code, payload_schema_version) DO NOTHING;

CREATE POLICY platform_administrators_select_device_support ON platform_administrators FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND user_id=current_setting('app.iam_actor_user_id',true)::uuid AND revoked_at IS NULL
);

DROP POLICY organization_memberships_select_org ON organization_memberships;
DROP POLICY organization_memberships_update_org ON organization_memberships;
CREATE POLICY organization_memberships_select_device_revoke ON organization_memberships FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_id=current_setting('app.iam_target_organization_id',true)::uuid
 AND (user_id=current_setting('app.iam_actor_user_id',true)::uuid OR id=current_setting('app.iam_target_membership_id',true)::uuid
      OR current_setting('app.iam_platform_admin_support_access',true)='true')
);
CREATE POLICY organization_memberships_update_device_revoke ON organization_memberships FOR UPDATE TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_id=current_setting('app.iam_target_organization_id',true)::uuid
 AND id=current_setting('app.iam_target_membership_id',true)::uuid
) WITH CHECK (session_generation > 0 AND reauthentication_required_after = transaction_timestamp());

DROP POLICY organization_membership_roles_select_org ON organization_membership_roles;
CREATE POLICY organization_membership_roles_select_device_revoke ON organization_membership_roles FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_id=current_setting('app.iam_target_organization_id',true)::uuid
 AND EXISTS (SELECT 1 FROM organization_memberships om WHERE om.id=organization_membership_id
     AND (om.user_id=current_setting('app.iam_actor_user_id',true)::uuid OR om.id=current_setting('app.iam_target_membership_id',true)::uuid
          OR current_setting('app.iam_platform_admin_support_access',true)='true'))
);
DROP POLICY organization_membership_permissions_select_org ON organization_membership_permissions;
CREATE POLICY organization_membership_permissions_select_device_revoke ON organization_membership_permissions FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_id=current_setting('app.iam_target_organization_id',true)::uuid
);

DROP POLICY refresh_token_families_select_org ON refresh_token_families;
DROP POLICY refresh_token_families_update_org ON refresh_token_families;
CREATE POLICY refresh_token_families_select_device_revoke ON refresh_token_families FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_membership_id=current_setting('app.iam_target_membership_id',true)::uuid
);
CREATE POLICY refresh_token_families_update_device_revoke ON refresh_token_families FOR UPDATE TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND organization_membership_id=current_setting('app.iam_target_membership_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (revoked_at=transaction_timestamp());

CREATE POLICY refresh_token_families_select_device_self_revoke ON refresh_token_families FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SELF_REVOKE'
 AND user_id=current_setting('app.iam_actor_user_id',true)::uuid
 AND trusted_device_id=current_setting('app.iam_target_device_id',true)::uuid
);
CREATE POLICY refresh_token_families_update_device_self_revoke ON refresh_token_families FOR UPDATE TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SELF_REVOKE'
 AND user_id=current_setting('app.iam_actor_user_id',true)::uuid
 AND trusted_device_id=current_setting('app.iam_target_device_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (revoked_at=transaction_timestamp());

DROP POLICY refresh_tokens_select_org ON refresh_tokens;
DROP POLICY refresh_tokens_update_org ON refresh_tokens;
CREATE POLICY refresh_tokens_select_device_revoke ON refresh_tokens FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id AND f.organization_membership_id=current_setting('app.iam_target_membership_id',true)::uuid)
);
CREATE POLICY refresh_tokens_update_device_revoke ON refresh_tokens FOR UPDATE TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='ORGANIZATION'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id AND f.organization_membership_id=current_setting('app.iam_target_membership_id',true)::uuid)
) WITH CHECK (revoked_at=transaction_timestamp());

CREATE POLICY refresh_tokens_select_device_self_revoke ON refresh_tokens FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SELF_REVOKE'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id AND f.user_id=current_setting('app.iam_actor_user_id',true)::uuid
            AND f.trusted_device_id=current_setting('app.iam_target_device_id',true)::uuid)
);
CREATE POLICY refresh_tokens_update_device_self_revoke ON refresh_tokens FOR UPDATE TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='DEVICE_SELF_REVOKE'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id AND f.user_id=current_setting('app.iam_actor_user_id',true)::uuid
            AND f.trusted_device_id=current_setting('app.iam_target_device_id',true)::uuid)
) WITH CHECK (revoked_at=transaction_timestamp());

CREATE POLICY audit_logs_insert_iam_device_revoke ON audit_logs FOR INSERT TO iam_runtime WITH CHECK (
 current_user='iam_runtime' AND action_type IN ('DEVICE_SELF_REVOKED','PLATFORM_DEVICE_REVOKED','DEVICE_SESSION_REVOKED','PLATFORM_ADMIN_ORG_ACCESS')
 AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid AND target_entity_type='USER' AND requires_target_entity
 AND NOT requires_class_scope AND scope_class_id IS NULL AND NOT requires_operation_group AND operation_group_id IS NULL AND NOT is_undo AND undo_of_audit_log_id IS NULL
 AND ((action_type='DEVICE_SELF_REVOKED' AND event_scope='GLOBAL' AND organization_id IS NULL AND target_entity_id=current_setting('app.iam_actor_user_id',true)::uuid
       AND current_setting('app.iam_operation_scope',true)='IAM_AUTH' AND current_setting('app.iam_operation_code',true)='DEVICE_SELF_REVOKE')
  OR (action_type='PLATFORM_DEVICE_REVOKED' AND event_scope='GLOBAL' AND organization_id IS NULL
       AND current_setting('app.iam_operation_scope',true)='GLOBAL' AND current_setting('app.iam_operation_code',true)='PLATFORM_DEVICE_REVOKE')
  OR (action_type IN ('DEVICE_SESSION_REVOKED','PLATFORM_ADMIN_ORG_ACCESS') AND event_scope='ORGANIZATION'
       AND organization_id=current_setting('app.iam_target_organization_id',true)::uuid
       AND current_setting('app.iam_operation_scope',true)='ORGANIZATION' AND current_setting('app.iam_operation_code',true)='DEVICE_SESSION_REVOKE'
       AND (action_type='DEVICE_SESSION_REVOKED' OR current_setting('app.iam_platform_admin_support_access',true)='true')))
);
