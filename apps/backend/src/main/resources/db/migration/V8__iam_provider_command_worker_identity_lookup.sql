-- IAM-004 Fix Round 2: the real provider-command worker (ProviderCommandWorker) needs the
-- provider's own user identifier (Cognito Username == user_identities.subject) for the identity a
-- claimed command targets, in order to make the actual provider API call. Unlike V1's
-- user_identities_select_global_provider_command (which additionally requires app.iam_target_user_id,
-- known only at CREATE time), the worker calls this strictly AFTER claim, when the row's
-- target_user_id is not retrievable for these command types (CHECK constraint) — so this policy
-- keys on id alone, same trust model as every other target-scoped GLOBAL policy in this schema:
-- the session var is set only by JdbcIamAuthRepository from data it already read back under RLS,
-- never from external input.
--
-- An EXISTS-subquery join back to iam_provider_commands was deliberately rejected here: Postgres
-- evaluates that subquery under iam_provider_commands' own RLS policies, some of which
-- themselves join user_identities — a real "infinite recursion detected in policy" cross-reference
-- cycle, confirmed while first building this migration.

CREATE POLICY user_identities_select_provider_command_worker ON user_identities FOR SELECT USING (
    current_user = 'iam_runtime'
    AND current_setting('app.iam_operation_scope', true) = 'GLOBAL'
    AND current_setting('app.iam_operation_code', true) = 'PROVIDER_COMMAND_CLAIM'
    AND id = current_setting('app.iam_target_identity_id', true)::uuid
);

-- No new GRANT needed: V1 already grants full-table SELECT on user_identities to iam_runtime;
-- this policy is the only thing narrowing which rows the worker can actually see under this scope.
