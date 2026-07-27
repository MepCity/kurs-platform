-- IAM-009: canonical pending work is reclaimed from PostgreSQL after SQS ACK.
ALTER TABLE iam_cognito_security_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp();

GRANT UPDATE (next_attempt_at) ON iam_cognito_security_events TO iam_runtime;
CREATE INDEX iam_cognito_security_events_pending_idx
    ON iam_cognito_security_events (user_pool_id, next_attempt_at, event_time)
    WHERE status = 'PENDING_MAPPING';

CREATE POLICY user_identities_select_cognito_event
    ON user_identities FOR SELECT TO iam_runtime
    USING (
        current_user = 'iam_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) = 'COGNITO_SECURITY_EVENT_PROCESS'
        AND current_setting('app.iam_system_actor', true) = 'true'
        AND issuer = current_setting('app.iam_provider_issuer', true)
        AND subject = current_setting('app.iam_provider_subject', true));

CREATE TABLE iam_cognito_lag_alarm_state (
    provider TEXT NOT NULL CHECK (provider = 'COGNITO'),
    user_pool_id TEXT NOT NULL,
    checkpoint TEXT NOT NULL CHECK (checkpoint IN ('EVENT', 'RECONCILIATION')),
    severity TEXT NOT NULL CHECK (severity IN ('WARNING', 'CRITICAL')),
    last_emitted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, user_pool_id, checkpoint, severity)
);
ALTER TABLE iam_cognito_lag_alarm_state ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_cognito_lag_alarm_state FORCE ROW LEVEL SECURITY;
GRANT SELECT, INSERT ON iam_cognito_lag_alarm_state TO iam_runtime;
GRANT UPDATE (last_emitted_at) ON iam_cognito_lag_alarm_state TO iam_runtime;
CREATE POLICY iam_cognito_lag_alarm_runtime ON iam_cognito_lag_alarm_state
    FOR ALL TO iam_runtime
    USING (
        current_user = 'iam_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) IN (
            'COGNITO_SECURITY_EVENT_PROCESS',
            'COGNITO_RECONCILIATION_SWEEP')
        AND current_setting('app.iam_system_actor', true) = 'true'
        AND provider = 'COGNITO'
        AND user_pool_id = current_setting('app.iam_provider_pool_id', true))
    WITH CHECK (
        current_user = 'iam_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) IN (
            'COGNITO_SECURITY_EVENT_PROCESS',
            'COGNITO_RECONCILIATION_SWEEP')
        AND current_setting('app.iam_system_actor', true) = 'true'
        AND provider = 'COGNITO'
        AND user_pool_id = current_setting('app.iam_provider_pool_id', true));
