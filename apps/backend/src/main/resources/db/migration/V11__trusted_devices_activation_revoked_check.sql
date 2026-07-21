-- IAM-004 Fix Round 4: a revoked trusted device could still activate a brand-new global or
-- organization session and could still resolve a live /sessions/me read, as long as the caller
-- held a context-selection token or refresh token issued before the revoke. Two independent gaps
-- combined to allow this:
--
--   1. SessionActivationService.performPlatformAdminMutation / performContextActivationMutation
--      and SessionInfoService.resolve all called findTrustedDeviceById without ever checking
--      TrustedDevice.isActive() (application-level gap, fixed in this same round without a
--      migration — see SessionActivationService.java / SessionInfoService.java).
--
--   2. trusted_devices_select_activation (V4__iam_auth_scope_hardening.sql), the RLS policy that
--      backs every one of those reads under IAM_AUTH/{PLATFORM_ADMIN_ACTIVATE, CONTEXT_ACTIVATE,
--      SESSION_INFO}, never filtered on revoked_at at all — a revoked device row was just as
--      visible as an active one. V4 is an already-applied migration (Flyway migrations are
--      immutable once run), so this narrows the SAME policy name via DROP + CREATE in a new
--      migration rather than editing V4 in place.
--
-- This is RLS-level defense-in-depth for the application-level fix: even if a future code path
-- forgets the TrustedDevice.isActive() check, a revoked device's row is now simply invisible to
-- every one of these three operation codes, so the read returns empty and every caller already
-- treats "device not found" as SESSION_REVOKED.
DROP POLICY trusted_devices_select_activation ON trusted_devices;

CREATE POLICY trusted_devices_select_activation ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND id = current_setting('app.iam_current_trusted_device_id', true)::uuid
    AND revoked_at IS NULL
);
