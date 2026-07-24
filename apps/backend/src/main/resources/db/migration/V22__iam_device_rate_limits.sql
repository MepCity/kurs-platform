CREATE TABLE iam_device_rate_limits (
    actor_user_id UUID NOT NULL REFERENCES users(id),
    scope_type TEXT NOT NULL CHECK (scope_type IN ('IAM_AUTH','ORGANIZATION','GLOBAL')),
    context_id UUID NOT NULL,
    operation_code TEXT NOT NULL CHECK (operation_code IN ('DEVICE_LIST','DEVICE_SELF_REVOKE','DEVICE_SESSION_REVOKE','PLATFORM_DEVICE_REVOKE')),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 1),
    PRIMARY KEY(actor_user_id, scope_type, context_id, operation_code, window_started_at)
);
ALTER TABLE iam_device_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_device_rate_limits FORCE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON iam_device_rate_limits TO iam_runtime;
CREATE POLICY iam_device_rate_limits_runtime ON iam_device_rate_limits FOR ALL TO iam_runtime USING (
    current_user='iam_runtime' AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid
    AND scope_type=current_setting('app.iam_operation_scope',true)
    AND operation_code=current_setting('app.iam_operation_code',true)
    AND context_id=CASE WHEN scope_type='ORGANIZATION' THEN current_setting('app.iam_target_organization_id',true)::uuid
                        ELSE current_setting('app.iam_actor_user_id',true)::uuid END
) WITH CHECK (
    current_user='iam_runtime' AND actor_user_id=current_setting('app.iam_actor_user_id',true)::uuid
    AND scope_type=current_setting('app.iam_operation_scope',true)
    AND operation_code=current_setting('app.iam_operation_code',true)
    AND context_id=CASE WHEN scope_type='ORGANIZATION' THEN current_setting('app.iam_target_organization_id',true)::uuid
                        ELSE current_setting('app.iam_actor_user_id',true)::uuid END
);
