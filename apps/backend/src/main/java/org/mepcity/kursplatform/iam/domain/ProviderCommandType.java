package org.mepcity.kursplatform.iam.domain;

import java.util.Set;

public enum ProviderCommandType {
    TEACHER_ACCOUNT_CREATE,
    USER_DISABLE,
    USER_LOGOUT,
    PASSWORD_RESET;

    /**
     * The command types the IAM-004 worker/scheduler actually claims, resolves and completes in
     * production. {@code USER_DISABLE} and {@code USER_LOGOUT} carry no encrypted payload, so the
     * adapter can build the provider call from the resolved Cognito Username alone.
     *
     * <p>{@code PASSWORD_RESET} and {@code TEACHER_ACCOUNT_CREATE} both require decrypting
     * {@code encrypted_command_payload} (new password / new-account details) before a provider call
     * can be built. No KMS / AEAD payload-decryption service exists in this codebase yet, so this
     * allow-list deliberately EXCLUDES them at every layer:
     * <ul>
     *   <li>{@code createCommand} rejects them with {@code BUSINESS_RULE_VIOLATION} before any row
     *       is ever inserted (so no PENDING row for an unsupported type can exist via the app);</li>
     *   <li>the {@code iam_provider_commands_select_worker} / {@code ipc_claim} RLS policies
     *       (V9) filter them out of the worker's view entirely, so a hand-inserted row still
     *       cannot be claimed;</li>
     *   <li>{@code ProviderCommandScheduler.pollOnce} never returns them;</li>
     *   <li>{@code ProviderCommandWorkerAdapter} rejects them defensively with
     *       {@code BUSINESS_RULE_VIOLATION} rather than leaving the command CLAIMED.</li>
     * </ul>
     * The enum and the {@code iam_provider_commands} table schema are kept so a future task
     * (PASSWORD_RESET → IAM-004 continuation; TEACHER_ACCOUNT_CREATE / FINALIZE → STAFF-002) can
     * enable them by adding KMS decryption and widening this allow-list + the RLS policies — no
     * schema migration required.
     */
    public static final Set<ProviderCommandType> SUPPORTED_IN_WORKER = Set.of(USER_DISABLE, USER_LOGOUT);

    public boolean isSupportedInWorker() {
        return SUPPORTED_IN_WORKER.contains(this);
    }
}
