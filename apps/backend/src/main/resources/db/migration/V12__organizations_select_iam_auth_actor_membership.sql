-- IAM-004 Fix Round 4: organizations has been under FORCE ROW LEVEL SECURITY since
-- V3__org_organizations_and_runtime.sql, and every one of its SELECT policies requires
-- current_user = 'org_runtime'. iam_runtime was granted plain SELECT on the table by
-- V4__iam_auth_scope_hardening.sql (under the V1-era assumption that organizations had no RLS at
-- all — true when V1 ran, no longer true once V3 enabled + forced it), but a GRANT does not admit
-- a FORCE-RLS table on its own: with zero permissive policies matching current_user = 'iam_runtime',
-- iam_runtime has always seen ZERO rows in organizations, for every operation code, with no error
-- (RLS default-deny, not a permission failure).
--
-- JdbcIamAuthRepository.findContextSelectionSummaries joins organization_memberships to
-- organizations from the iam_runtime connection and is the sole source of organization
-- name/status for THREE call sites: ContextSelectionService.listContextSelections
-- (CONTEXT_SELECTION_LIST), SessionActivationService.performContextActivationMutation
-- (CONTEXT_ACTIVATE), and SessionInfoService.resolve (SESSION_INFO, added this round). All three
-- have silently returned an empty organization summary for every session ever since V3 — masked
-- until now because none of them had a real-Postgres test that actually seeded an organization row
-- and exercised the JOIN; CONTEXT_ACTIVATE in particular had no test coverage at all before this
-- round's SessionInfoService liveness-check tests exposed it.
--
-- New, additive, purely-iam_runtime-scoped SELECT policy: visible only to the actor's OWN
-- organizations (an EXISTS against organization_memberships keyed on the actor id), for exactly the
-- three operation codes that legitimately need it. This does not touch any org_runtime policy —
-- Postgres OR's multiple permissive policies together, and none of the pre-existing org_runtime-only
-- policies ever matched a current_user = 'iam_runtime' connection, so there is no overlap to reconcile.
CREATE POLICY organizations_select_iam_auth_actor_membership ON organizations FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'IAM_AUTH'
    AND current_setting('app.iam_operation_code', true) IN ('CONTEXT_SELECTION_LIST', 'CONTEXT_ACTIVATE', 'SESSION_INFO')
    AND EXISTS (
        SELECT 1 FROM organization_memberships om
        WHERE om.organization_id = organizations.id
          AND om.user_id = current_setting('app.iam_actor_user_id', true)::uuid
    )
);
