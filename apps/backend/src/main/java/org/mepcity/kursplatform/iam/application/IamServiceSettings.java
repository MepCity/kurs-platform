package org.mepcity.kursplatform.iam.application;

import java.time.Duration;

public interface IamServiceSettings {

    Duration accessTokenTtl();

    Duration refreshTokenTtl();

    Duration contextSelectionTokenTtl();

    Duration activationEscrowTtl();

    Duration idempotencyRetention();

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
