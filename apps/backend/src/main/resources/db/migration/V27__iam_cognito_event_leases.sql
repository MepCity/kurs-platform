ALTER TABLE iam_cognito_security_events
    ADD COLUMN lease_owner TEXT,
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN fencing_token BIGINT NOT NULL DEFAULT 0 CHECK (fencing_token >= 0),
    ADD COLUMN last_attempt_at TIMESTAMPTZ,
    ADD CONSTRAINT iam_cognito_security_event_lease_shape_ck CHECK (
        (lease_owner IS NULL AND lease_expires_at IS NULL)
        OR (status='PENDING_MAPPING' AND lease_owner IS NOT NULL AND lease_expires_at IS NOT NULL));

REVOKE UPDATE ON iam_cognito_security_events FROM iam_runtime;
GRANT UPDATE (status, attempt_count, completed_at, terminal_at, lease_owner,
              lease_expires_at, fencing_token, last_attempt_at)
    ON iam_cognito_security_events TO iam_runtime;
