-- IAM-009: a consumer can revoke only the resolved target user, never an arbitrary tenant/user.
CREATE POLICY iam_cognito_event_families ON refresh_token_families FOR UPDATE TO iam_runtime
USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
 AND current_setting('app.iam_system_actor',true)='true'
 AND user_id=current_setting('app.iam_target_user_id',true)::uuid AND revoked_at IS NULL)
WITH CHECK (revoked_at IS NOT NULL);
CREATE POLICY iam_cognito_event_tokens ON refresh_tokens FOR UPDATE TO iam_runtime
USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS' AND revoked_at IS NULL
 AND current_setting('app.iam_system_actor',true)='true'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id AND f.user_id=current_setting('app.iam_target_user_id',true)::uuid))
WITH CHECK (revoked_at IS NOT NULL);
CREATE POLICY iam_cognito_event_user_barrier ON users FOR UPDATE TO iam_runtime
USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
 AND current_setting('app.iam_system_actor',true)='true'
 AND id=current_setting('app.iam_target_user_id',true)::uuid)
WITH CHECK (id=current_setting('app.iam_target_user_id',true)::uuid);
CREATE POLICY iam_cognito_event_audit ON audit_logs FOR INSERT TO iam_runtime WITH CHECK (
 current_user='iam_runtime' AND action_type='IAM_PROVIDER_SESSION_REVOKED' AND event_scope='GLOBAL'
 AND actor_user_id IS NULL AND target_entity_id=current_setting('app.iam_target_user_id',true)::uuid
 AND current_setting('app.iam_system_actor',true)='true'
 AND current_setting('app.iam_operation_scope',true)='GLOBAL' AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS');
