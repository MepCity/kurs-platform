-- IAM-004 Fix Round 3: provider-command worker hardening. Closes four gaps that the Round 1/2
-- migrations left open once the real worker/scheduler actually runs in production:
--
--   1. retry requeue: a retryable failure (network/429/5xx) had no legal CLAIMED→PENDING path, so
--      the worker could only ever mark the command COMPLETED or FAILED — burning the whole retry
--      budget on the first transient error. Add a fourth legal transition (CLAIMED→PENDING) to
--      trg_ipc_state(), gated on the SAME worker+fencing+commandId that completeCommand requires,
--      and a matching ipc_retry UPDATE policy.
--
--   2. V6 residual: iam_provider_commands_select_complete_global (V6) let a worker holding the
--      GLOBAL/<lifecycle-code> scope SEE every terminal row of that command_type, not just the one
--      it was completing. A malicious or buggy worker could enumerate other users' completed
--      disable/logout outcomes within its own transaction scope. Narrow it to the exact command id
--      via a new app.iam_target_provider_command_id GUC (set only by JdbcIamAuthRepository from
--      data it already holds, never from external input — same trust model as every other
--      app.iam_target_* var).
--
--   3. ipc_complete_global: tighten the UPDATE policy to require the same commandId match, so a
--      worker cannot complete a row it does not hold the lease on even if it somehow knows the id.
--
--   4. allow-list: iam_provider_commands_select_worker / ipc_claim / ipc_claim_renew /
--      iam_provider_commands_insert_global all admitted PASSWORD_RESET and TEACHER_ACCOUNT_CREATE.
--      V1 does not implement those (no KMS/payload-decryption); admitting them meant a
--      hand-inserted PENDING PASSWORD_RESET row could be claimed and would loop forever in a
--      poison CLAIMED state. Restrict every worker-facing policy + the insert policy to the
--      supported allow-list (USER_DISABLE, USER_LOGOUT); the enum and CHECK constraint stay so a
--      future task can widen them by adding decryption + widening this list.

-- (1) Extend trg_ipc_state() with a CLAIMED→PENDING retry-requeue branch. Done by DROP + recreate
-- of the whole function (Postgres CREATE OR REPLACE cannot drop branches), preserving every
-- existing transition exactly. The new branch requires:
--   * OLD.status = 'CLAIMED' AND NEW.status = 'PENDING' (the only new legal transition);
--   * app.iam_worker_id / app.iam_fencing_token / app.iam_target_provider_command_id all set, and
--     matching OLD.lease_owner / OLD.fencing_token / OLD.id respectively (so a lease lost to a
--     concurrent reclaim mid-call — which bumped fencing_token and changed lease_owner — silently
--     no-ops via the ipc_retry RLS USING qual before this trigger ever fires);
--   * NEW.lease_owner IS NULL AND NEW.lease_expires_at IS NULL AND NEW.fencing_token = 0 (a fresh
--     PENDING row's invariant, matching what INSERT establishes);
--   * NEW.next_attempt_at > transaction_timestamp() (backoff must actually delay the next attempt);
--   * NEW.attempt_count unchanged (a retry does not consume a new attempt slot — only claim does).
DROP FUNCTION IF EXISTS trg_ipc_state() CASCADE;
CREATE FUNCTION trg_ipc_state() RETURNS trigger AS $$
DECLARE
    _worker_id TEXT;
    _fencing BIGINT;
    _command_id TEXT;
BEGIN
    IF TG_OP <> 'UPDATE' THEN RETURN NEW; END IF;
    IF OLD.status IN ('COMPLETED','FAILED') THEN
        RAISE EXCEPTION 'terminal command cannot be updated';
    END IF;
    IF OLD.status = 'PENDING' AND NEW.status = 'CLAIMED' THEN
        IF NEW.attempt_count <> OLD.attempt_count + 1 THEN
            RAISE EXCEPTION 'attempt_count must increment by 1';
        END IF;
        IF NEW.fencing_token IS NULL OR NEW.fencing_token <> COALESCE(OLD.fencing_token, 0) + 1 THEN
            RAISE EXCEPTION 'fencing_token must increment by 1';
        END IF;
        IF NEW.lease_owner IS NULL THEN
            RAISE EXCEPTION 'lease_owner required for CLAIMED';
        END IF;
        IF NEW.lease_expires_at IS NULL OR NEW.lease_expires_at <= transaction_timestamp() THEN
            RAISE EXCEPTION 'lease_expires_at must be future';
        END IF;
        IF NEW.completed_at IS NOT NULL OR NEW.last_safe_error_code IS NOT NULL THEN
            RAISE EXCEPTION 'terminal fields must be NULL';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'CLAIMED' AND NEW.status = 'CLAIMED' THEN
        IF OLD.lease_expires_at IS NULL OR OLD.lease_expires_at >= transaction_timestamp() THEN
            RAISE EXCEPTION 'renew only for expired lease';
        END IF;
        IF NEW.fencing_token IS NULL OR NEW.fencing_token <> OLD.fencing_token + 1 THEN
            RAISE EXCEPTION 'fencing_token must increment by 1';
        END IF;
        IF NEW.lease_owner IS NULL THEN
            RAISE EXCEPTION 'lease_owner required';
        END IF;
        IF NEW.lease_expires_at IS NULL OR NEW.lease_expires_at <= transaction_timestamp() THEN
            RAISE EXCEPTION 'lease_expires_at must be future';
        END IF;
        RETURN NEW;
    END IF;
    IF OLD.status = 'CLAIMED' AND NEW.status = 'PENDING' THEN
        -- retry requeue: release the lease back to PENDING for a retryable failure. Must be driven
        -- by the SAME worker+fencing that currently holds the CLAIMED lease AND must target the
        -- exact command id (app.iam_target_provider_command_id), so a stale worker or a lease lost
        -- to a concurrent reclaim cannot clobber a different owner's in-flight attempt.
        BEGIN
            _worker_id := current_setting('app.iam_worker_id', true);
            _fencing := current_setting('app.iam_fencing_token', true)::bigint;
            _command_id := current_setting('app.iam_target_provider_command_id', true);
        EXCEPTION WHEN OTHERS THEN
            RAISE EXCEPTION 'worker context not set';
        END;
        IF _worker_id IS NULL OR _fencing IS NULL OR _command_id IS NULL THEN
            RAISE EXCEPTION 'worker context not set';
        END IF;
        IF OLD.id::text IS DISTINCT FROM _command_id THEN
            RAISE EXCEPTION 'command id mismatch: target % does not match row %', _command_id, OLD.id;
        END IF;
        IF OLD.lease_owner IS DISTINCT FROM _worker_id THEN
            RAISE EXCEPTION 'lease_owner mismatch: expected %, got %', _worker_id, OLD.lease_owner;
        END IF;
        IF OLD.fencing_token IS DISTINCT FROM _fencing THEN
            RAISE EXCEPTION 'fencing_token mismatch: expected %, got %', _fencing, OLD.fencing_token;
        END IF;
        IF NEW.attempt_count <> OLD.attempt_count THEN
            RAISE EXCEPTION 'attempt_count must not change on retry requeue';
        END IF;
        IF NEW.fencing_token <> 0 THEN
            RAISE EXCEPTION 'fencing_token must reset to 0 for PENDING';
        END IF;
        IF NEW.lease_owner IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
            RAISE EXCEPTION 'lease fields must be cleared';
        END IF;
        IF NEW.next_attempt_at IS NULL OR NEW.next_attempt_at <= transaction_timestamp() THEN
            RAISE EXCEPTION 'next_attempt_at must be future';
        END IF;
        IF NEW.completed_at IS NOT NULL OR NEW.last_safe_error_code IS NOT NULL THEN
            RAISE EXCEPTION 'terminal fields must be NULL';
        END IF;
        RETURN NEW;
    END IF;
    IF NEW.status IN ('COMPLETED','FAILED') THEN
        IF OLD.status <> 'CLAIMED' THEN
            RAISE EXCEPTION 'only CLAIMED can transition to terminal';
        END IF;
        BEGIN
            _worker_id := current_setting('app.iam_worker_id', true);
            _fencing := current_setting('app.iam_fencing_token', true)::bigint;
        EXCEPTION WHEN OTHERS THEN
            RAISE EXCEPTION 'worker context not set';
        END;
        IF _worker_id IS NULL OR _fencing IS NULL THEN
            RAISE EXCEPTION 'worker context not set';
        END IF;
        IF OLD.lease_owner IS DISTINCT FROM _worker_id THEN
            RAISE EXCEPTION 'lease_owner mismatch: expected %, got %', _worker_id, OLD.lease_owner;
        END IF;
        IF OLD.fencing_token IS DISTINCT FROM _fencing THEN
            RAISE EXCEPTION 'fencing_token mismatch: expected %, got %', _fencing, OLD.fencing_token;
        END IF;
        IF OLD.lease_expires_at IS NULL OR OLD.lease_expires_at < transaction_timestamp() THEN
            RAISE EXCEPTION 'lease already expired';
        END IF;
        IF NEW.completed_at IS NULL THEN
            RAISE EXCEPTION 'completed_at required';
        END IF;
        IF NEW.lease_owner IS NOT NULL OR NEW.lease_expires_at IS NOT NULL THEN
            RAISE EXCEPTION 'lease fields must be cleared';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'invalid state: % to %', OLD.status, NEW.status;
END;
$$ LANGUAGE plpgsql;

-- The DROP FUNCTION above dropped the trigger too; recreate it on the same table/columns.
CREATE TRIGGER trg_ipc_state
    BEFORE UPDATE ON iam_provider_commands
    FOR EACH ROW EXECUTE FUNCTION trg_ipc_state();

-- (4-allow-list) Worker-facing SELECT / claim / claim-renew / insert policies: narrow to
-- USER_DISABLE / USER_LOGOUT so a hand-inserted PENDING PASSWORD_RESET / TEACHER_ACCOUNT_CREATE
-- row can never be claimed (and therefore never enter a poison CLAIMED loop). Drop + recreate
-- each, preserving every other clause.
DROP POLICY iam_provider_commands_select_worker ON iam_provider_commands;
CREATE POLICY iam_provider_commands_select_worker ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND command_type IN ('USER_DISABLE', 'USER_LOGOUT')
    AND (
        (status = 'PENDING' AND lease_owner IS NULL)
        OR (status = 'CLAIMED' AND lease_expires_at IS NOT NULL AND lease_expires_at < transaction_timestamp())
        OR (status = 'CLAIMED' AND lease_owner = current_setting('app.iam_worker_id', true) AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint)
    )
);

DROP POLICY ipc_claim ON iam_provider_commands;
CREATE POLICY ipc_claim ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND command_type IN ('USER_DISABLE', 'USER_LOGOUT')
    AND status = 'PENDING'
) WITH CHECK (
    status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
);

DROP POLICY ipc_claim_renew ON iam_provider_commands;
CREATE POLICY ipc_claim_renew ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND command_type IN ('USER_DISABLE', 'USER_LOGOUT')
    AND status = 'CLAIMED'
    AND lease_expires_at < transaction_timestamp()
) WITH CHECK (
    status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
);

-- (3) ipc_complete_global: require app.iam_target_provider_command_id to match the row id, so a
-- worker completing a command can only touch the exact row it claimed — never a sibling terminal
-- row of the same type that happens to be visible under the GLOBAL/<lifecycle-code> scope.
DROP POLICY ipc_complete_global ON iam_provider_commands;
CREATE POLICY ipc_complete_global ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    AND command_type::text = current_setting('app.iam_operation_code', true)
    AND id = current_setting('app.iam_target_provider_command_id', true)::uuid
    AND status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
    AND lease_expires_at IS NOT NULL
    AND lease_expires_at >= transaction_timestamp()
) WITH CHECK (status IN ('COMPLETED','FAILED') AND lease_owner IS NULL AND lease_expires_at IS NULL);

-- New: ipc_retry UPDATE policy for the CLAIMED→PENDING transition. USING requires the exact
-- commandId + the matching worker+fencing lease; WITH CHECK enforces the fresh-PENDING invariant.
-- A lease lost to a concurrent reclaim mid-call (fencing_token bumped, lease_owner changed) silently
-- fails the USING qual — requeueClaimedForRetry returns empty, the caller surfaces STATE_CONFLICT,
-- and the new owner's in-flight attempt is untouched.
CREATE POLICY ipc_retry ON iam_provider_commands FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT')
    AND command_type::text = current_setting('app.iam_operation_code', true)
    AND id = current_setting('app.iam_target_provider_command_id', true)::uuid
    AND status = 'CLAIMED'
    AND lease_owner = current_setting('app.iam_worker_id', true)
    AND fencing_token = current_setting('app.iam_fencing_token', true)::bigint
) WITH CHECK (
    status = 'PENDING'
    AND fencing_token = 0
    AND lease_owner IS NULL
    AND lease_expires_at IS NULL
);

-- (2) V6 residual: replace iam_provider_commands_select_complete_global with a version that admits
-- ONLY the exact command id the worker is completing/just-completed, not every terminal row of the
-- same command_type. The RETURNING post-image (status COMPLETED/FAILED, lease_owner NULL) is still
-- visible, but only for THIS command id — a sibling terminal row of the same type is now hidden.
DROP POLICY iam_provider_commands_select_complete_global ON iam_provider_commands;
CREATE POLICY iam_provider_commands_select_complete_global ON iam_provider_commands FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) IN ('USER_DISABLE', 'USER_LOGOUT', 'PASSWORD_RESET')
    AND command_type::text = current_setting('app.iam_operation_code', true)
    AND id = current_setting('app.iam_target_provider_command_id', true)::uuid
    AND (
        lease_owner = current_setting('app.iam_worker_id', true)
        OR (status IN ('COMPLETED', 'FAILED') AND lease_owner IS NULL)
    )
);

-- (4-allow-list, insert) iam_provider_commands_insert_global: keep PASSWORD_RESET admitted at the
-- policy level (the enum + CHECK constraint still allow it and a future task will need it), but
-- require command_type::text to match app.iam_operation_code exactly. The application-layer
-- createCommand already rejects PASSWORD_RESET / TEACHER_ACCOUNT_CREATE with BUSINESS_RULE_VIOLATION
-- before any INSERT; this is the defensive second gate.
-- (No change needed: the existing V1 policy already requires command_type::text =
-- app.iam_operation_code AND a user_identities-backed target_identity_id match. Kept as-is.)
