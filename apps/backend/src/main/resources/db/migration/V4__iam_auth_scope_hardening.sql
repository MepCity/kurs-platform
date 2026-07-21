-- IAM-004 Fix Round 1: close RLS gaps that made the actor-facing IAM_AUTH read/mutation flows
-- (session activation, session info, context selection listing) unreachable under FORCE ROW
-- LEVEL SECURITY. These reads/updates were previously only exercised in tests via raw JDBC
-- connections with hand-picked SET LOCAL values, never through the actual service call graph;
-- once the transaction executor started genuinely applying app.iam_* vars to the connection the
-- repository uses (see SpringIamTransactionExecutor), every one of the policies below was found
-- missing and the corresponding read/update silently returned zero rows (or, for INSERT/UPDATE,
-- was rejected outright).

-- organizations has no RLS (see V1) but was never granted to iam_runtime at all, so any join
-- against it from iam_runtime failed with a permission error, not merely an empty RLS result.
GRANT SELECT ON organizations TO iam_runtime;

-- Bootstrap read for context selection tokens: the raw context token value is presented by the
-- client itself and hashed by the application before this policy ever runs, so token_hash is
-- exactly as strong a bootstrap key as the (issuer, subject) pair AUTHENTICATION scope uses for
-- user_identities. Actor identity is not yet known at this point in PLATFORM_ADMIN_ACTIVATE /
-- CONTEXT_ACTIVATE / CONTEXT_SELECTION_LIST — that's the whole reason this read has to happen
-- first, inside the same transaction, before app.iam_actor_user_id can be set.
CREATE POLICY context_selection_tokens_select_bootstrap ON context_selection_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE', 'CONTEXT_SELECTION_LIST')
    AND token_hash = current_setting('app.iam_context_token_hash', true)
);

-- Once the bootstrap read above resolves actor + device, the transaction re-applies
-- app.iam_actor_user_id / app.iam_current_trusted_device_id (see IamTransactionExecutor.
-- refreshIamAuthScope) so the remaining reads in the SAME transaction can rely on them.
CREATE POLICY trusted_devices_select_activation ON trusted_devices FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND id = current_setting('app.iam_current_trusted_device_id', true)::uuid
);

CREATE POLICY user_identities_select_actor_device ON user_identities FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

-- platform_administrators_select_iam_auth (V1) only allowed PLATFORM_ADMIN_ACTIVATE; the
-- provider token exchange flow (to decide whether GLOBAL_PLATFORM_ADMIN scope is offered) and
-- the session-info read (to report admin status) need the same self-lookup.
DROP POLICY platform_administrators_select_iam_auth ON platform_administrators;

CREATE POLICY platform_administrators_select_iam_auth ON platform_administrators FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PROVIDER_TOKEN_EXCHANGE', 'PLATFORM_ADMIN_ACTIVATE', 'SESSION_INFO')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
    AND revoked_at IS NULL
);

-- "List my own active memberships" was never actually grantable under IAM_AUTH scope: the only
-- pre-existing organization_memberships SELECT policy for that scope required an already-known
-- target_membership_id + target_organization_id, which is precisely what this read is used to
-- discover in the first place (ContextSelectionService.listContextSelections,
-- SessionActivationService.activateContext, SessionInfoService.resolveSession).
CREATE POLICY organization_memberships_select_actor_list ON organization_memberships FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('CONTEXT_SELECTION_LIST', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

CREATE POLICY organization_membership_roles_select_actor_list ON organization_membership_roles FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('CONTEXT_SELECTION_LIST', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.id = organization_membership_roles.organization_membership_id
          AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
    )
);

-- SessionInfoService resolves "who am I" from a bearer access token hash alone, before it knows
-- which user/device/family the token belongs to — the same bootstrap pattern as context tokens.
-- refresh_token_families is deliberately NOT made visible via an EXISTS-into-refresh_tokens
-- subquery here: refresh_tokens' own SELECT policies (V1) already reference refresh_token_families
-- (session_refresh/session_logout/plat_dev_revoke), so a reverse reference back into refresh_tokens
-- from a refresh_token_families policy is a real "infinite recursion detected in policy" cycle in
-- Postgres. SessionInfoService instead resolves the token first (self-contained, below), then calls
-- IamTransactionExecutor.refreshIamAuthScope(...withFamily(...)) to reveal app.iam_current_family_id
-- before reading the family row by id — no cross-table subquery needed on either side.
CREATE POLICY refresh_tokens_select_session_info ON refresh_tokens FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_INFO'
    AND access_token_hash = current_setting('app.iam_access_token_hash', true)
);

CREATE POLICY refresh_token_families_select_session_info ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) = 'SESSION_INFO'
    AND id = current_setting('app.iam_current_family_id', true)::uuid
);

-- Replay resolution (SessionActivationService.resolveReplay) reads the family recorded in a
-- completed idempotency key's escrow; there was no IAM_AUTH-scope SELECT policy for it at all
-- (only ORGANIZATION/GLOBAL scoped ones, which don't apply here).
CREATE POLICY refresh_token_families_select_actor_replay ON refresh_token_families FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
);

-- Disabled/revoked-provider family revocation (SessionActivationService: revokeAllActorFamilies)
-- runs inside the PLATFORM_ADMIN_ACTIVATE / CONTEXT_ACTIVATE mutation transaction, but no
-- IAM_AUTH-scope UPDATE policy permitted it — the security revoke was silently updating zero
-- rows while the surrounding application code assumed it had taken effect.
CREATE POLICY refresh_token_families_update_actor_revoke ON refresh_token_families FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND user_id = current_setting('app.iam_actor_user_id', true)::uuid
) WITH CHECK (revoked_at IS NOT NULL);

CREATE POLICY refresh_tokens_update_actor_revoke ON refresh_tokens FOR UPDATE USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('PLATFORM_ADMIN_ACTIVATE', 'CONTEXT_ACTIVATE')
    AND EXISTS (
        SELECT 1 FROM refresh_token_families rtf
        WHERE rtf.id = refresh_tokens.family_id
          AND rtf.user_id = current_setting('app.iam_actor_user_id', true)::uuid
    )
) WITH CHECK (revoked_at IS NOT NULL);
