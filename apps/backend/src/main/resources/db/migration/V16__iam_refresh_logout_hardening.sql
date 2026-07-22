-- IAM-005 repair: V14's broad UPDATE policies were permissive and OR-composed with V1.
-- Replace every refresh/logout mutation policy with exact family/actor/device/timestamp guards.
DROP POLICY IF EXISTS refresh_tokens_update_refresh_logout ON refresh_tokens;
DROP POLICY IF EXISTS refresh_token_families_update_refresh_logout ON refresh_token_families;
DROP POLICY IF EXISTS refresh_tokens_sr ON refresh_tokens;
DROP POLICY IF EXISTS refresh_tokens_sl ON refresh_tokens;
DROP POLICY IF EXISTS refresh_token_families_sl ON refresh_token_families;

CREATE POLICY refresh_tokens_refresh_rotate ON refresh_tokens FOR UPDATE USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='SESSION_REFRESH'
 AND family_id=current_setting('app.iam_current_family_id',true)::uuid
 AND used_at IS NULL AND revoked_at IS NULL
) WITH CHECK (family_id=current_setting('app.iam_current_family_id',true)::uuid
 AND used_at=transaction_timestamp() AND revoked_at IS NULL);

CREATE POLICY refresh_tokens_refresh_reuse_revoke ON refresh_tokens FOR UPDATE USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='SESSION_REFRESH'
 AND current_setting('app.iam_security_revoke_required',true)='true'
 AND family_id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (family_id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at=transaction_timestamp());

CREATE POLICY refresh_families_refresh_reuse_revoke ON refresh_token_families FOR UPDATE USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='SESSION_REFRESH'
 AND current_setting('app.iam_security_revoke_required',true)='true'
 AND id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at=transaction_timestamp());

CREATE POLICY refresh_tokens_logout_revoke ON refresh_tokens FOR UPDATE USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='SESSION_LOGOUT'
 AND family_id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (family_id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at=transaction_timestamp());

CREATE POLICY refresh_families_logout_revoke ON refresh_token_families FOR UPDATE USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='IAM_AUTH'
 AND current_setting('app.iam_operation_code',true)='SESSION_LOGOUT'
 AND id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at IS NULL
) WITH CHECK (id=current_setting('app.iam_current_family_id',true)::uuid AND revoked_at=transaction_timestamp());

UPDATE audit_action_catalog SET payload_schema =
'{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","refreshTokenFamilyId","organizationMembershipId","trustedDeviceId"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb
WHERE code IN ('SESSION_REFRESHED','SESSION_REFRESH_REUSE_DETECTED') AND payload_schema_version=1;
