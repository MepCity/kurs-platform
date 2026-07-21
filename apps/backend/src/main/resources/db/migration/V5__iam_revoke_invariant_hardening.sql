-- IAM-004 Fix Round 2: the actor-scoped family/token revocation policies added in Round 1
-- (V4__iam_auth_scope_hardening.sql, for the disabled/revoked-provider branch of
-- PLATFORM_ADMIN_ACTIVATE/CONTEXT_ACTIVATE) matched a row regardless of its CURRENT revoked_at
-- value and only required the NEW value to be non-null — meaning an already-revoked row could be
-- matched and rewritten again with a different revoked_at, and the written value wasn't pinned to
-- transaction_timestamp(). Every other revoke policy in V1 (trusted_devices_update_self_revoke,
-- trusted_devices_update_platform_revoke, refresh_token_families_update_org, etc.) already follows
-- the correct "USING revoked_at IS NULL, WITH CHECK revoked_at = transaction_timestamp()" shape;
-- these two were the exception. Replaced here, not edited in place, since V4 already shipped.

DROP POLICY refresh_token_families_update_actor_revoke ON refresh_token_families;
CREATE POLICY refresh_token_families_update_actor_revoke ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND revoked_at IS NULL
) WITH CHECK (revoked_at = transaction_timestamp());

DROP POLICY refresh_tokens_update_actor_revoke ON refresh_tokens;
CREATE POLICY refresh_tokens_update_actor_revoke ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND revoked_at IS NULL
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = refresh_tokens.family_id
          AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
    )
) WITH CHECK (revoked_at = transaction_timestamp());
