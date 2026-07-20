package org.mepcity.kursplatform.iam.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "iam")
public class IamProperties {

    private String tokenHashPepper;
    private String escrowSecret;
    private Cognito cognito = new Cognito();
    private Tokens tokens = new Tokens();
    private Escrow escrow = new Escrow();
    private ProviderCommand providerCommand = new ProviderCommand();
    private boolean localStubProfile = false;

    @Autowired
    private Environment environment;

    public static class Cognito {
        private String issuer;
        private String clientId;
        private String region = "eu-central-1";
        private String userPoolId;
        private ManagementApi managementApi = new ManagementApi();
        /**
         * A-010 / ADR-004 gate: V1 runs an Amazon Cognito Essentials NATIVE user pool where the
         * verified access token's {@code sub} claim IS the Cognito Username used for AdminGetUser /
         * AdminDisableUser / AdminUserGlobalSignOut. Federation (SAML/OIDC IdP, social) is explicitly
         * out of V1 scope (ADR-004 §"V1 için kabul edildi"). When this is {@code false} (the default
         * and the only V1-valid value), {@code IamProperties.validate()} hard-fails in real-env
         * profiles if it is flipped on, since the status-checker / worker adapter both rely on
         * {@code sub == Username}. A future ADR revision can widen this together with storing the
         * verified provider Username separately, but until then enabling it here would silently
         * produce false-positive {@code REVOKED}/{@code UNKNOWN} verdicts.
         */
        private boolean allowFederatedIdp = false;

        public String getIssuer() { return issuer; }
        public void setIssuer(String issuer) { this.issuer = issuer; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getUserPoolId() { return userPoolId; }
        public void setUserPoolId(String userPoolId) { this.userPoolId = userPoolId; }
        public ManagementApi getManagementApi() { return managementApi; }
        public void setManagementApi(ManagementApi managementApi) { this.managementApi = managementApi; }
        public boolean isAllowFederatedIdp() { return allowFederatedIdp; }
        public void setAllowFederatedIdp(boolean allowFederatedIdp) { this.allowFederatedIdp = allowFederatedIdp; }
    }

    public static class ManagementApi {
        private String endpoint = "https://cognito-idp.eu-central-1.amazonaws.com/";
        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKeyId() { return accessKeyId; }
        public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
        public String getSessionToken() { return sessionToken; }
        public void setSessionToken(String sessionToken) { this.sessionToken = sessionToken; }
        public String getSecretAccessKey() { return secretAccessKey; }
        public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    }

    /**
     * IAM-004 provider-command worker/scheduler tuning. All fields are environment-driven so the
     * scheduler stays disabled by default and only a deployment that has deliberately set
     * {@code IAM_PROVIDER_COMMAND_WORKER_ENABLED=true} (per A-010's "production requires conscious
     * opt-in" gate) ever polls the queue.
     */
    public static class ProviderCommand {
        private Worker worker = new Worker();
        private Retry retry = new Retry();

        public Worker getWorker() { return worker; }
        public void setWorker(Worker worker) { this.worker = worker; }
        public Retry getRetry() { return retry; }
        public void setRetry(Retry retry) { this.retry = retry; }

        public static class Worker {
            private boolean enabled = false;
            private Duration pollInterval = Duration.ofSeconds(60);
            private int batchLimit = 20;
            private Duration leaseTtl = Duration.ofMinutes(5);

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public Duration getPollInterval() { return pollInterval; }
            public void setPollInterval(Duration pollInterval) { this.pollInterval = pollInterval; }
            public int getBatchLimit() { return batchLimit; }
            public void setBatchLimit(int batchLimit) { this.batchLimit = batchLimit; }
            public Duration getLeaseTtl() { return leaseTtl; }
            public void setLeaseTtl(Duration leaseTtl) { this.leaseTtl = leaseTtl; }
        }

        public static class Retry {
            private int maxAttempts = 10;
            private Duration backoffBase = Duration.ofSeconds(10);
            private Duration backoffMax = Duration.ofMinutes(15);
            private double jitter = 0.25d;

            public int getMaxAttempts() { return maxAttempts; }
            public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
            public Duration getBackoffBase() { return backoffBase; }
            public void setBackoffBase(Duration backoffBase) { this.backoffBase = backoffBase; }
            public Duration getBackoffMax() { return backoffMax; }
            public void setBackoffMax(Duration backoffMax) { this.backoffMax = backoffMax; }
            public double getJitter() { return jitter; }
            public void setJitter(double jitter) { this.jitter = jitter; }
        }
    }

    public static class Tokens {
        private Duration accessTokenTtl = Duration.ofMinutes(10);
        private Duration refreshTokenTtl = Duration.ofDays(30);
        private Duration contextSelectionTokenTtl = Duration.ofMinutes(5);

        public Duration getAccessTokenTtl() { return accessTokenTtl; }
        public void setAccessTokenTtl(Duration accessTokenTtl) { this.accessTokenTtl = accessTokenTtl; }
        public Duration getRefreshTokenTtl() { return refreshTokenTtl; }
        public void setRefreshTokenTtl(Duration refreshTokenTtl) { this.refreshTokenTtl = refreshTokenTtl; }
        public Duration getContextSelectionTokenTtl() { return contextSelectionTokenTtl; }
        public void setContextSelectionTokenTtl(Duration contextSelectionTokenTtl) { this.contextSelectionTokenTtl = contextSelectionTokenTtl; }
    }

    public static class Escrow {
        private Duration activationTtl = Duration.ofMinutes(5);
        private Duration refreshTtl = Duration.ofMinutes(10);
        private Duration idempotencyRetention = Duration.ofDays(30);

        public Duration getActivationTtl() { return activationTtl; }
        public void setActivationTtl(Duration activationTtl) { this.activationTtl = activationTtl; }
        public Duration getRefreshTtl() { return refreshTtl; }
        public void setRefreshTtl(Duration refreshTtl) { this.refreshTtl = refreshTtl; }
        public Duration getIdempotencyRetention() { return idempotencyRetention; }
        public void setIdempotencyRetention(Duration idempotencyRetention) { this.idempotencyRetention = idempotencyRetention; }
    }

    @PostConstruct
    void validate() {
        boolean stubProfileActive = environment.acceptsProfiles(Profiles.of("local-stub", "test"));
        if (localStubProfile != stubProfileActive) {
            throw new IllegalStateException(
                    "iam.local-stub-profile=" + localStubProfile + " tutarsız: aktif Spring profile "
                            + java.util.Arrays.toString(environment.getActiveProfiles())
                            + " local-stub/test kapsamı ile eşleşmiyor. Yanlış ortamda stub'a düşmeyi/gerçek "
                            + "Cognito doğrulamasını atlamayı engellemek için ikisi birbirine kilitlenmiştir.");
        }
        if (!localStubProfile) {
            if (tokenHashPepper == null || tokenHashPepper.isBlank() || tokenHashPepper.length() < 16) {
                throw new IllegalStateException(
                        "iam.token-hash-pepper must be set and at least 16 characters outside local-stub profile");
            }
            if (escrowSecret == null || escrowSecret.isBlank() || escrowSecret.length() < 16) {
                throw new IllegalStateException(
                        "iam.escrow-secret must be set and at least 16 characters outside local-stub profile");
            }
            if (cognito.issuer == null || cognito.issuer.isBlank()
                    || cognito.issuer.contains("xxxxx")) {
                throw new IllegalStateException(
                        "iam.cognito.issuer must be a real Cognito issuer outside local-stub profile");
            }
            if (cognito.clientId == null || cognito.clientId.isBlank()
                    || "dev-client-id".equals(cognito.clientId)) {
                throw new IllegalStateException(
                        "iam.cognito.client-id must be a real Cognito client id outside local-stub profile");
            }
            if (cognito.userPoolId == null || cognito.userPoolId.isBlank()) {
                throw new IllegalStateException(
                        "iam.cognito.user-pool-id must be set outside local-stub profile");
            }
            if (cognito.managementApi.accessKeyId == null || cognito.managementApi.accessKeyId.isBlank()) {
                throw new IllegalStateException(
                        "iam.cognito.management-api.access-key-id must be set outside local-stub profile");
            }
            if (cognito.managementApi.secretAccessKey == null || cognito.managementApi.secretAccessKey.isBlank()) {
                throw new IllegalStateException(
                        "iam.cognito.management-api.secret-access-key must be set outside local-stub profile");
            }
            // A-010 / ADR-004: V1 is a NATIVE Cognito Essentials pool (sub == Username). The status
            // checker and the worker adapter both rely on that invariant to resolve the Cognito
            // Username; a federated pool would silently mis-resolve it. Federation is explicitly V1
            // out-of-scope (ADR-004 §"V1 için kabul edildi"); enabling it here without the matching
            // ADR revision + verified-Username storage would produce false-positive account
            // revocations, so reject the env var outright rather than letting it half-work.
            if (cognito.allowFederatedIdp) {
                throw new IllegalStateException(
                        "iam.cognito.allow-federated-idp=true is not supported in V1 (ADR-004: native Cognito "
                                + "Essentials pool only). Federated IdP support requires an ADR revision and a "
                                + "separate verified-provider-Username storage path.");
            }
        }
    }

    public String getTokenHashPepper() { return tokenHashPepper; }
    public void setTokenHashPepper(String tokenHashPepper) { this.tokenHashPepper = tokenHashPepper; }

    public String getEscrowSecret() { return escrowSecret; }
    public void setEscrowSecret(String escrowSecret) { this.escrowSecret = escrowSecret; }

    public Cognito getCognito() { return cognito; }
    public void setCognito(Cognito cognito) { this.cognito = cognito; }

    public Tokens getTokens() { return tokens; }
    public void setTokens(Tokens tokens) { this.tokens = tokens; }

    public Escrow getEscrow() { return escrow; }
    public void setEscrow(Escrow escrow) { this.escrow = escrow; }

    public ProviderCommand getProviderCommand() { return providerCommand; }
    public void setProviderCommand(ProviderCommand providerCommand) { this.providerCommand = providerCommand; }

    public boolean isLocalStubProfile() { return localStubProfile; }
    public void setLocalStubProfile(boolean localStubProfile) { this.localStubProfile = localStubProfile; }

    public boolean requiresRealCognito() { return !localStubProfile; }
}
