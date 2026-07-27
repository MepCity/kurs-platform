-- IAM-009 correction: canonical pending work is reclaimed from PostgreSQL after SQS ACK.
-- TERMINAL was never a production transition; remove it from the live status contract.
ALTER TABLE iam_cognito_security_events
    DROP CONSTRAINT IF EXISTS iam_cognito_security_events_check,
    DROP CONSTRAINT IF EXISTS iam_cognito_security_event_lease_shape_ck,
    ALTER COLUMN status DROP DEFAULT;
ALTER TABLE iam_cognito_security_events
    ALTER COLUMN status TYPE TEXT USING status::text;
DROP TYPE iam_cognito_security_event_status_enum;
CREATE TYPE iam_cognito_security_event_status_enum AS ENUM ('PENDING_MAPPING', 'COMPLETED');
ALTER TABLE iam_cognito_security_events
    ALTER COLUMN status TYPE iam_cognito_security_event_status_enum
    USING status::iam_cognito_security_event_status_enum,
    ALTER COLUMN status SET DEFAULT 'PENDING_MAPPING',
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT transaction_timestamp(),
    ADD CONSTRAINT iam_cognito_security_event_status_shape_ck CHECK (
        (status = 'COMPLETED' AND completed_at IS NOT NULL AND terminal_at IS NULL)
        OR (status = 'PENDING_MAPPING' AND completed_at IS NULL AND terminal_at IS NULL)),
    ADD CONSTRAINT iam_cognito_security_event_lease_shape_ck CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (status = 'PENDING_MAPPING'
            AND lease_owner IS NOT NULL
            AND lease_expires_at IS NOT NULL));

ALTER TABLE iam_cognito_security_events
    DROP CONSTRAINT iam_cognito_security_events_event_name_check,
    ADD CONSTRAINT iam_cognito_security_events_event_name_check CHECK (
        event_name IN (
            'AdminDisableUser',
            'AdminUserGlobalSignOut',
            'AdminResetUserPassword',
            'AdminSetUserPassword'));

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
        AND provider = 'COGNITO'
        AND user_pool_id = current_setting('app.iam_provider_pool_id', true))
    WITH CHECK (
        current_user = 'iam_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) IN (
            'COGNITO_SECURITY_EVENT_PROCESS',
            'COGNITO_RECONCILIATION_SWEEP')
        AND provider = 'COGNITO'
        AND user_pool_id = current_setting('app.iam_provider_pool_id', true));
