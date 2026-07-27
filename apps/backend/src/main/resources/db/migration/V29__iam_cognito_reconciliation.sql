CREATE TABLE iam_cognito_reconciliation_targets (
    identity_id UUID PRIMARY KEY REFERENCES user_identities(id),
    user_id UUID NOT NULL REFERENCES users(id),
    issuer TEXT NOT NULL,
    subject TEXT NOT NULL,
    user_pool_id TEXT NOT NULL,
    next_check_at TIMESTAMPTZ NOT NULL,
    last_checked_at TIMESTAMPTZ,
    last_provider_status TEXT CHECK (last_provider_status IN ('ACTIVE','DISABLED','REVOKED','UNKNOWN')),
    lease_owner TEXT,
    lease_expires_at TIMESTAMPTZ,
    fencing_token BIGINT NOT NULL DEFAULT 0 CHECK (fencing_token>=0),
    CHECK ((lease_owner IS NULL)=(lease_expires_at IS NULL)),
    UNIQUE (identity_id,user_id)
);
ALTER TABLE iam_cognito_reconciliation_targets ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_cognito_reconciliation_targets FORCE ROW LEVEL SECURITY;
GRANT SELECT,INSERT ON iam_cognito_reconciliation_targets TO iam_runtime;
GRANT UPDATE (next_check_at,last_checked_at,last_provider_status,lease_owner,lease_expires_at,fencing_token)
 ON iam_cognito_reconciliation_targets TO iam_runtime;
CREATE POLICY iam_cognito_reconciliation_runtime ON iam_cognito_reconciliation_targets FOR ALL TO iam_runtime
 USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
   AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP'
   AND user_pool_id=current_setting('app.iam_provider_pool_id',true))
 WITH CHECK (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
   AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP'
   AND user_pool_id=current_setting('app.iam_provider_pool_id',true));

CREATE POLICY user_identities_select_cognito_sweep ON user_identities FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP');
CREATE POLICY refresh_families_select_cognito_sweep ON refresh_token_families FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP');
CREATE POLICY refresh_families_update_cognito_sweep ON refresh_token_families FOR UPDATE TO iam_runtime
 USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
  AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP'
  AND user_id=current_setting('app.iam_target_user_id',true)::uuid AND revoked_at IS NULL)
 WITH CHECK (revoked_at IS NOT NULL);
CREATE POLICY refresh_tokens_select_cognito_sweep ON refresh_tokens FOR SELECT TO iam_runtime USING (
 current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id
  AND f.user_id=current_setting('app.iam_target_user_id',true)::uuid));
CREATE POLICY refresh_tokens_update_cognito_sweep ON refresh_tokens FOR UPDATE TO iam_runtime
 USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
  AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP' AND revoked_at IS NULL
  AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id
   AND f.user_id=current_setting('app.iam_target_user_id',true)::uuid))
 WITH CHECK (revoked_at IS NOT NULL);
CREATE POLICY users_update_cognito_sweep ON users FOR UPDATE TO iam_runtime
 USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
  AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP'
  AND id=current_setting('app.iam_target_user_id',true)::uuid)
 WITH CHECK (id=current_setting('app.iam_target_user_id',true)::uuid);
CREATE POLICY audit_insert_cognito_sweep ON audit_logs FOR INSERT TO iam_runtime WITH CHECK (
 current_user='iam_runtime' AND action_type='IAM_PROVIDER_SESSION_REVOKED' AND event_scope='GLOBAL'
 AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid
 AND target_entity_id=current_setting('app.iam_target_user_id',true)::uuid
 AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_RECONCILIATION_SWEEP');
