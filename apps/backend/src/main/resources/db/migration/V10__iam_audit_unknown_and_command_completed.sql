-- IAM-004 Fix Round 3: audit integration completion. Adds the audit events IAM_GIRIS_OTURUM_API_SOZLESMESI.md
-- §14 requires that V7 did not yet cover, and closes the audit-allow-list residual V7 left open.
--
-- (1) UNKNOWN provider-status outcomes: §14 line 876 lists "provider belirsizliği" as a
-- security-meaningful failure that must be audited. SessionActivationService's UNKNOWN branch
-- refuses to open a session and throws PROVIDER_UNAVAILABLE, but V7 had no action_type for it, so
-- no audit row was ever written. Add IAM_PROVIDER_STATUS_CHECK_BLOCKED (GLOBAL/SECURITY) and
-- IAM_ORG_PROVIDER_STATUS_CHECK_BLOCKED (ORGANIZATION/SECURITY) so both activation paths record
-- the security event — written in a SEPARATE transaction from the (rolled-back) main mutation, so
-- the audit survives even though no session/family/token was created.
--
-- (2) Provider-command terminal failures: V7 had PROVIDER_COMMAND_COMPLETED (success). A
-- non-retryable failure or a retryable failure that exhausted the retry budget now needs its own
-- terminal audit so operators can see WHY a disable/logout permanently failed, not just that it
-- succeeded. Add PROVIDER_COMMAND_FAILED (non-retryable) and PROVIDER_COMMAND_EXHAUSTED (retried
-- out). Per §12.3, retry attempts themselves are NOT audited — only the terminal outcome.
--
-- (3) V7 audit_logs_insert_iam_global residual: the policy's second branch admitted
-- PROVIDER_TOKEN_EXCHANGED / PLATFORM_ADMIN_ACTIVATED / IAM_PROVIDER_SESSION_REVOKED under
-- GLOBAL + PROVIDER_COMMAND_CLAIM scope — a coupling that let a worker holding the claim scope
-- write an actor-bound access event it should never be able to produce. Remove that branch
-- entirely; the IAM_AUTH-scoped branch already covers every legitimate producer of those events.
--
-- (4) Strict action_type × operation_code coupling: split the IAM_AUTH/GLOBAL audit insert policy
-- so each action_type can only be written under the operation_code that legitimately produces it.
-- A PLATFORM_ADMIN_ACTIVATE flow can no longer write a PROVIDER_TOKEN_EXCHANGED row, etc.

INSERT INTO audit_action_catalog (code, payload_schema_version, target_entity_type, event_scope, event_kind,
    requires_target_entity, requires_class_scope, requires_operation_group, is_undoable, payload_schema) VALUES
('IAM_PROVIDER_STATUS_CHECK_BLOCKED', 1, 'USER', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","providerStatus"],"enumValues":{"providerStatus":["UNKNOWN"]}},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('IAM_ORG_PROVIDER_STATUS_CHECK_BLOCKED', 1, 'USER', 'ORGANIZATION', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":[],"requiredNull":true},"eventMetadata":{"allowed":["operationCode","providerStatus"],"enumValues":{"providerStatus":["UNKNOWN"]}},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PROVIDER_COMMAND_FAILED', 1, 'PROVIDER_COMMAND', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb),
('PROVIDER_COMMAND_EXHAUSTED', 1, 'PROVIDER_COMMAND', 'GLOBAL', 'SECURITY', true, false, false, false,
 '{"oldValue":{"allowed":[],"requiredNull":true},"newValue":{"allowed":["success","safeErrorCode"]},"eventMetadata":{"allowed":["operationCode"]},"reasonCodes":[],"rejectUnknown":true}'::jsonb)
ON CONFLICT (code, payload_schema_version) DO NOTHING;

-- (3) + (4) Replace audit_logs_insert_iam_global: drop the PROVIDER_COMMAND_CLAIM branch entirely
-- (no worker path legitimately writes these actor-bound access events) and split the IAM_AUTH
-- branch by action_type × operation_code so a PLATFORM_ADMIN_ACTIVATE flow cannot emit a
-- PROVIDER_TOKEN_EXCHANGED row, etc. Each action_type is admitted only under the operation_code
-- that legitimately produces it.
DROP POLICY audit_logs_insert_iam_global ON audit_logs;

CREATE POLICY audit_logs_insert_iam_global ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND event_scope = 'GLOBAL'
        AND organization_id IS NULL
        AND target_entity_type = 'USER'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND target_entity_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
        AND (
            (action_type = 'PROVIDER_TOKEN_EXCHANGED'
                AND current_setting('app.iam_operation_code', true) = 'PROVIDER_TOKEN_EXCHANGE')
            OR (action_type = 'PLATFORM_ADMIN_ACTIVATED'
                AND current_setting('app.iam_operation_code', true) = 'PLATFORM_ADMIN_ACTIVATE')
            OR (action_type = 'IAM_PROVIDER_SESSION_REVOKED'
                AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE'))
            OR (action_type = 'IAM_PROVIDER_STATUS_CHECK_BLOCKED'
                AND current_setting('app.iam_operation_code', true) = 'PLATFORM_ADMIN_ACTIVATE')
        )
    );

-- Organization-scoped: add the ORG-side UNKNOWN blocked audit alongside CONTEXT_ACTIVATED and
-- IAM_ORG_PROVIDER_SESSION_REVOKED, with the same strict action_type × operation_code coupling.
DROP POLICY audit_logs_insert_iam_organization ON audit_logs;

CREATE POLICY audit_logs_insert_iam_organization ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND event_scope = 'ORGANIZATION'
        AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
        AND organization_id IS NOT NULL
        AND organization_id = current_setting('app.iam_target_organization_id', true)::uuid
        AND target_entity_type = 'USER'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND actor_user_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND target_entity_id = current_setting('app.iam_actor_user_id', true)::uuid
        AND (
            (action_type = 'CONTEXT_ACTIVATED'
                AND current_setting('app.iam_operation_code', true) = 'CONTEXT_ACTIVATE')
            OR (action_type = 'IAM_ORG_PROVIDER_SESSION_REVOKED'
                AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE'))
            OR (action_type = 'IAM_ORG_PROVIDER_STATUS_CHECK_BLOCKED'
                AND current_setting('app.iam_operation_code', true) = 'CONTEXT_ACTIVATE')
        )
    );

-- Provider-command terminal outcomes: widen the existing action_type allow-list to cover the new
-- FAILED / EXHAUSTED codes. No operation_code coupling change — these still require
-- GLOBAL + USER_DISABLE/USER_LOGOUT/PASSWORD_RESET, exactly as V7 established for COMPLETED.
-- (No DROP needed: the existing policy admits action_type = 'PROVIDER_COMMAND_COMPLETED' only.
-- Replace it with the widened version.)
DROP POLICY audit_logs_insert_provider_command ON audit_logs;

CREATE POLICY audit_logs_insert_provider_command ON audit_logs FOR INSERT TO iam_runtime
    WITH CHECK (
        current_user = 'iam_runtime'
        AND action_type IN ('PROVIDER_COMMAND_COMPLETED', 'PROVIDER_COMMAND_FAILED', 'PROVIDER_COMMAND_EXHAUSTED')
        AND event_scope = 'GLOBAL'
        AND organization_id IS NULL
        AND target_entity_type = 'PROVIDER_COMMAND'
        AND requires_target_entity
        AND NOT requires_class_scope AND scope_class_id IS NULL
        AND NOT requires_operation_group AND operation_group_id IS NULL
        AND NOT is_undo AND undo_of_audit_log_id IS NULL
        AND target_entity_id IS NOT NULL
        AND actor_user_id IS NULL
        AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
        AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    );
