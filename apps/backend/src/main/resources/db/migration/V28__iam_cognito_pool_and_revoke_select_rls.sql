DROP POLICY iam_cognito_security_events_runtime ON iam_cognito_security_events;
CREATE POLICY iam_cognito_security_events_runtime ON iam_cognito_security_events FOR ALL TO iam_runtime
    USING (current_user='iam_runtime'
        AND current_setting('app.iam_operation_scope',true)='GLOBAL'
        AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
        AND provider='COGNITO'
        AND user_pool_id=current_setting('app.iam_provider_pool_id',true))
    WITH CHECK (current_user='iam_runtime'
        AND current_setting('app.iam_operation_scope',true)='GLOBAL'
        AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
        AND provider='COGNITO'
        AND user_pool_id=current_setting('app.iam_provider_pool_id',true));

CREATE POLICY iam_cognito_event_families_select ON refresh_token_families FOR SELECT TO iam_runtime
USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
 AND user_id=current_setting('app.iam_target_user_id',true)::uuid);
CREATE POLICY iam_cognito_event_tokens_select ON refresh_tokens FOR SELECT TO iam_runtime
USING (current_user='iam_runtime' AND current_setting('app.iam_operation_scope',true)='GLOBAL'
 AND current_setting('app.iam_operation_code',true)='COGNITO_SECURITY_EVENT_PROCESS'
 AND EXISTS (SELECT 1 FROM refresh_token_families f WHERE f.id=family_id
   AND f.user_id=current_setting('app.iam_target_user_id',true)::uuid));
