-- IAM-009: CloudTrail/EventBridge/SQS delivery is at-least-once and non-authoritative.
-- Keep only the canonical event identity and safe routing fields; raw payload/credentials never persist.
CREATE TYPE iam_cognito_security_event_status_enum AS ENUM ('PENDING_MAPPING', 'COMPLETED', 'TERMINAL');

CREATE TABLE iam_cognito_security_events (
    provider TEXT NOT NULL CHECK (provider = 'COGNITO'),
    user_pool_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    event_name TEXT NOT NULL CHECK (event_name IN ('AdminDisableUser', 'AdminUserGlobalSignOut', 'RevokeToken', 'AdminResetUserPassword', 'AdminSetUserPassword')),
    subject TEXT NOT NULL,
    event_time TIMESTAMPTZ NOT NULL,
    status iam_cognito_security_event_status_enum NOT NULL DEFAULT 'PENDING_MAPPING',
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    completed_at TIMESTAMPTZ,
    terminal_at TIMESTAMPTZ,
    PRIMARY KEY (provider, user_pool_id, event_id),
    CHECK ((status = 'COMPLETED' AND completed_at IS NOT NULL AND terminal_at IS NULL)
        OR (status = 'TERMINAL' AND terminal_at IS NOT NULL AND completed_at IS NULL)
        OR (status = 'PENDING_MAPPING' AND completed_at IS NULL AND terminal_at IS NULL))
);
ALTER TABLE iam_cognito_security_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam_cognito_security_events FORCE ROW LEVEL SECURITY;
GRANT SELECT, INSERT, UPDATE ON iam_cognito_security_events TO iam_runtime;
CREATE POLICY iam_cognito_security_events_runtime ON iam_cognito_security_events FOR ALL TO iam_runtime
    USING (current_user = 'iam_runtime' AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) = 'COGNITO_SECURITY_EVENT_PROCESS')
    WITH CHECK (provider = 'COGNITO' AND current_user = 'iam_runtime'
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) = 'COGNITO_SECURITY_EVENT_PROCESS');
