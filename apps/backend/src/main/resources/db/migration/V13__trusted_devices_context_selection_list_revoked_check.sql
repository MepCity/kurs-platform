-- IAM-004 Fix Round 5: a trusted device revoked AFTER a context-selection token was issued could
-- still be used to read the organization/context summary list for the rest of that token's TTL.
-- Two gaps combined to allow this:
--
--   1. ContextSelectionService.resolveSummaries() resolved the context token, refreshed the
--      IAM_AUTH scope to reveal actor+device, and went straight to reading organization summaries
--      — never once reading the trusted_devices row itself, so TrustedDevice.isActive() was never
--      checked (application-level gap, fixed in this same round without a migration — see
--      ContextSelectionService.java).
--
--   2. trusted_devices_select_activation (V4, narrowed by V11) only admits
--      {PLATFORM_ADMIN_ACTIVATE, CONTEXT_ACTIVATE, SESSION_INFO} — CONTEXT_SELECTION_LIST was never
--      in that list, so even the application-level fix above would have found the device row
--      invisible under RLS and (correctly) treated it as "not found" — but there was no RLS-level
--      backstop for this operation code at all before this migration: a future code path reading
--      trusted_devices under CONTEXT_SELECTION_LIST scope had nothing here stopping it from seeing
--      a revoked row.
--
-- A dedicated, narrowly-named policy (not a further edit of the already-narrowed V11 policy, and
-- not a widening of its operation_code list) keeps CONTEXT_SELECTION_LIST's visibility rule
-- independently readable and independently revocable, matching the same actor+device+revoked_at
-- shape V11 established for the other three IAM_AUTH operation codes. Only ADDS a new permissive
-- SELECT policy on trusted_devices for iam_runtime — RLS OR's permissive policies together, and no
-- existing policy (org_runtime-owned or otherwise) checks app.iam_operation_code =
-- 'CONTEXT_SELECTION_LIST', so this cannot open visibility for any other operation code.
CREATE POLICY trusted_devices_select_context_list ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'CONTEXT_SELECTION_LIST'
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    AND revoked_at IS NULL
);
