package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.ProviderCommandType;

/**
 * Port to the actual identity provider — the piece {@link ProviderCommandWorker} calls between
 * claiming a command and reporting its fenced completion. Implementations perform the real,
 * network-bound provider call (e.g. Cognito's Admin API); they must never touch the database or
 * any RLS-scoped connection themselves — that stays entirely inside {@link ProviderCommandService}.
 *
 * <p>V1 implementations support only {@link ProviderCommandType#SUPPORTED_IN_WORKER} ({@code
 * USER_DISABLE}, {@code USER_LOGOUT}). {@code PASSWORD_RESET} and {@code TEACHER_ACCOUNT_CREATE}
 * require decrypting {@code encrypted_command_payload} before a provider call can be built; no
 * KMS / AEAD payload-decryption service exists in V1, so adapters reject those two types with
 * {@link UnsupportedOperationException}, which {@link ProviderCommandWorker} maps to a terminal
 * {@code BUSINESS_RULE_VIOLATION} completion rather than leaving the command CLAIMED.
 */
public interface ProviderCommandWorkerAdapter {

    /**
     * @param subject the provider's own user identifier (Cognito {@code Username} — in V1's native
     *                Essentials pool this is the verified access token's {@code sub} claim), already
     *                resolved by the caller from {@code target_identity_id}.
     * @throws UnsupportedOperationException if this adapter does not implement the given command
     *                                        type (e.g. {@code PASSWORD_RESET} /
     *                                        {@code TEACHER_ACCOUNT_CREATE} in V1). The worker maps
     *                                        this to a terminal {@code BUSINESS_RULE_VIOLATION}
     *                                        completion — the command goes {@code FAILED}, not
     *                                        CLAIMED.
     */
    ProviderCommandOutcome execute(ProviderCommandType commandType, String subject, String issuer);
}
