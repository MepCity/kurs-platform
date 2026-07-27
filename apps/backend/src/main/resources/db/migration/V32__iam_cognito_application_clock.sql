-- IAM-009 due/lease state uses the application's injected UTC Clock as one authority.
-- Removing the database default prevents a future insert from silently mixing PostgreSQL wall
-- time with application-clock claim comparisons.
ALTER TABLE iam_cognito_security_events
    ALTER COLUMN next_attempt_at DROP DEFAULT;
