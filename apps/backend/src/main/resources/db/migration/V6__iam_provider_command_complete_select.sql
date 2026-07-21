-- IAM-004 Fix Round 2: completeProviderCommandAtomically's UPDATE ... RETURNING requires the row
-- to also satisfy a SELECT policy (PostgreSQL applies the relevant SELECT policies in addition to
-- the UPDATE policy's USING clause whenever RETURNING is used) — this was never exercised under
-- real RLS before (Round 1's tests for this method ran as the migration-owner/superuser, which
-- bypasses RLS entirely). Under real iam_runtime enforcement, the GLOBAL lifecycle completion path
-- (USER_DISABLE/USER_LOGOUT/PASSWORD_RESET) had no SELECT policy that matched a CLAIMED row held
-- by the calling worker: iam_provider_commands_select_global requires target_user_id/identity
-- context this call doesn't set (that context belongs to create, not complete), and
-- iam_provider_commands_select_worker is scoped to the PROVIDER_COMMAND_CLAIM code, not the
-- completion codes. ipc_complete's own path (IAM_PROVISIONING/TEACHER_ACCOUNT_CREATE) is
-- unaffected — iam_provider_commands_select_provisioning already matches there.
--
-- RETURNING re-checks the SELECT policy against the POST-update row, not the pre-image — a
-- policy keyed only on "lease_owner = my worker id" (true before the UPDATE) is already false by
-- the time RETURNING evaluates it, since ipc_complete_global's WITH CHECK just cleared lease_owner
-- to NULL. The second branch below covers the terminal post-image instead. This does mean any row
-- already COMPLETED/FAILED for the same command_type is technically selectable while a worker
-- holds this GLOBAL+lifecycle-code scope open — an accepted, narrow residual (bounded to a system
-- worker's own active transaction scope, not any broader read path) rather than a granted table-
-- wide SELECT.
CREATE POLICY iam_provider_commands_select_complete_global ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    AND command_type::text = current_setting('app.iam_operation_code', true)
    AND (
        lease_owner = current_setting('app.iam_worker_id', true)
        OR (status IN ('COMPLETED', 'FAILED') AND lease_owner IS NULL)
    )
);
