package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.springframework.stereotype.Component;

/** Only needs IamProperties (always registered by IamCoreConfiguration) — no DataSource. */
@Component
public class IamPropertiesServiceSettings implements IamServiceSettings {

    private final IamProperties properties;

    public IamPropertiesServiceSettings(IamProperties properties) {
        this.properties = properties;
    }

    @Override
    public java.time.Duration accessTokenTtl() {
        return properties.getTokens().getAccessTokenTtl();
    }

    @Override
    public java.time.Duration refreshTokenTtl() {
        return properties.getTokens().getRefreshTokenTtl();
    }

    @Override
    public java.time.Duration contextSelectionTokenTtl() {
        return properties.getTokens().getContextSelectionTokenTtl();
    }

    @Override
    public java.time.Duration activationEscrowTtl() {
        return properties.getEscrow().getActivationTtl();
    }

    @Override
    public java.time.Duration idempotencyRetention() {
        return properties.getEscrow().getIdempotencyRetention();
    }

    @Override
    public boolean providerCommandWorkerEnabled() {
        return properties.getProviderCommand().getWorker().isEnabled();
    }

    @Override
    public java.time.Duration providerCommandPollInterval() {
        return properties.getProviderCommand().getWorker().getPollInterval();
    }

    @Override
    public int providerCommandBatchLimit() {
        return properties.getProviderCommand().getWorker().getBatchLimit();
    }

    @Override
    public java.time.Duration providerCommandLeaseTtl() {
        return properties.getProviderCommand().getWorker().getLeaseTtl();
    }

    @Override
    public int providerCommandMaxAttempts() {
        return properties.getProviderCommand().getRetry().getMaxAttempts();
    }

    @Override
    public java.time.Duration providerCommandBackoffBase() {
        return properties.getProviderCommand().getRetry().getBackoffBase();
    }

    @Override
    public java.time.Duration providerCommandBackoffMax() {
        return properties.getProviderCommand().getRetry().getBackoffMax();
    }

    @Override
    public double providerCommandJitter() {
        return properties.getProviderCommand().getRetry().getJitter();
    }
}
