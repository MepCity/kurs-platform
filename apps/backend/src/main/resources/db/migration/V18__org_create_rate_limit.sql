-- ORG-004: global platform-admin create quota. The key contains only actor UUID and operation code.
CREATE TABLE organization_create_rate_limits (
    actor_user_id UUID NOT NULL REFERENCES users(id),
    operation_code TEXT NOT NULL CHECK (operation_code = 'ORG_CREATE'),
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count >= 1),
    PRIMARY KEY (actor_user_id, operation_code, window_started_at)
);
CREATE INDEX organization_create_rate_limits_expiry_idx
    ON organization_create_rate_limits (window_started_at);
GRANT SELECT, INSERT, UPDATE ON organization_create_rate_limits TO iam_runtime;
