package org.mepcity.kursplatform.iam.application;

import java.time.Duration;

public interface IamServiceSettings {

    Duration accessTokenTtl();

    Duration refreshTokenTtl();

    Duration contextSelectionTokenTtl();

    Duration activationEscrowTtl();

    Duration idempotencyRetention();

    /** Maximum lifetime of an encrypted, actor/query-bound device listing cursor. */
    default Duration deviceCursorTtl() {
        return Duration.ofMinutes(15);
    }

    // --- Provider-command worker / scheduler (IAM-004) ---

    boolean providerCommandWorkerEnabled();

    Duration providerCommandPollInterval();

    int providerCommandBatchLimit();

    Duration providerCommandLeaseTtl();

    int providerCommandMaxAttempts();

    Duration providerCommandBackoffBase();

    Duration providerCommandBackoffMax();

    double providerCommandJitter();
}
