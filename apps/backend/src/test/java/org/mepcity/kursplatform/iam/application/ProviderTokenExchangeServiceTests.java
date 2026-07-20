package org.mepcity.kursplatform.iam.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.domain.UserIdentity;
import org.mepcity.kursplatform.iam.domain.UserStatus;
import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProviderTokenExchangeServiceTests {

    private IamAuthRepository repository;
    private CognitoTokenVerifier cognitoTokenVerifier;
    private TokenHasher tokenHasher;
    private AeadEscrowService escrowService;
    private SecureRandom secureRandom;
    private Clock clock;
    private IamTransactionExecutor transactionExecutor;
    private IamServiceSettings settings;
    private ProviderTokenExchangeService service;

    private final Instant fixedNow = Instant.parse("2026-07-20T10:00:00Z");
    private final UUID actorUserId = UUID.randomUUID();
    private final UUID deviceIdentifier = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(IamAuthRepository.class);
        cognitoTokenVerifier = mock(CognitoTokenVerifier.class);
        tokenHasher = mock(TokenHasher.class);
        escrowService = mock(AeadEscrowService.class);
        secureRandom = new SecureRandom();
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        transactionExecutor = new NoopTransactionExecutor();
        settings = new TestServiceSettings();
        service = new ProviderTokenExchangeService(repository, cognitoTokenVerifier, tokenHasher,
                escrowService, secureRandom, clock, transactionExecutor, settings, mock(IamAuditWriter.class));

        when(tokenHasher.hash(any(String.class))).thenReturn("hash-" + UUID.randomUUID());
        when(escrowService.encrypt(any(), any(), any(), any(), any()))
                .thenReturn(new AeadEscrowService.EncryptedEscrow(
                        new byte[]{1}, "key-ref", new byte[]{1}, "aad"));
        doNothing().when(repository).acquireDeviceAdvisoryLock(any(), any());
    }

    @Test
    void exchangeSucceedsForValidCognitoTokenAndActiveUser() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(120), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.ACTIVE,
                        Instant.EPOCH, fixedNow.minusSeconds(120), fixedNow.minusSeconds(120),
                        1, null, null)));
        when(repository.getMaxRevokedAtForDevicePair(actorUserId, deviceIdentifier))
                .thenReturn(Optional.empty());
        when(repository.findActiveTrustedDevice(actorUserId, deviceIdentifier))
                .thenReturn(Optional.empty());
        TrustedDevice savedDevice = new TrustedDevice(UUID.randomUUID(), actorUserId, deviceIdentifier,
                "Pixel 8", DevicePlatform.ANDROID, fixedNow, null, null);
        when(repository.saveTrustedDevice(any())).thenReturn(savedDevice);
        when(repository.findActivePlatformAdministratorByUserId(actorUserId))
                .thenReturn(Optional.empty());
        when(repository.findIdempotencyKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        var result = service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1");

        assertThat(result.contextSelectionToken()).isNotNull();
        assertThat(result.availableScopes()).containsExactly("ORGANIZATION_SELECTION");
        assertThat(result.user().id()).isEqualTo(actorUserId);
        assertThat(result.platformAdministrator()).isNull();
    }

    @Test
    void exchangeFailsWhenIdentityNotFound() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("RESOURCE_NOT_FOUND");
    }

    @Test
    void exchangeFailsWhenUserSuspended() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(120), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.SUSPENDED,
                        Instant.EPOCH, fixedNow.minusSeconds(120), fixedNow.minusSeconds(120),
                        1, null, null)));

        assertThatThrownBy(() -> service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_NOT_READY");
    }

    @Test
    void exchangeFailsWhenReauthBarrierNotMet() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(120).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(120), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(180), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.ACTIVE,
                        fixedNow.minusSeconds(60), fixedNow.minusSeconds(180), fixedNow.minusSeconds(180),
                        1, null, null)));

        assertThatThrownBy(() -> service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("REAUTHENTICATION_REQUIRED");
    }

    @Test
    void exchangeFailsWhenDeviceReauthBarrierNotMet() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(120), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.ACTIVE,
                        Instant.EPOCH, fixedNow.minusSeconds(120), fixedNow.minusSeconds(120),
                        1, null, null)));
        when(repository.getMaxRevokedAtForDevicePair(actorUserId, deviceIdentifier))
                .thenReturn(Optional.of(fixedNow.minusSeconds(30)));

        assertThatThrownBy(() -> service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("REAUTHENTICATION_REQUIRED");
    }

    @Test
    void exchangeReturnsPlatformAdminScopeWhenUserIsAdmin() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(120), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.ACTIVE,
                        Instant.EPOCH, fixedNow.minusSeconds(120), fixedNow.minusSeconds(120),
                        1, null, null)));
        when(repository.getMaxRevokedAtForDevicePair(actorUserId, deviceIdentifier))
                .thenReturn(Optional.empty());
        when(repository.findActiveTrustedDevice(actorUserId, deviceIdentifier))
                .thenReturn(Optional.empty());
        TrustedDevice savedDevice = new TrustedDevice(UUID.randomUUID(), actorUserId, deviceIdentifier,
                "Pixel 8", DevicePlatform.ANDROID, fixedNow, null, null);
        when(repository.saveTrustedDevice(any())).thenReturn(savedDevice);
        when(repository.findActivePlatformAdministratorByUserId(actorUserId))
                .thenReturn(Optional.of(new PlatformAdministrator(UUID.randomUUID(), actorUserId, null,
                        fixedNow.minusSeconds(120), null)));
        when(repository.findIdempotencyKey(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        var result = service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1");

        assertThat(result.availableScopes()).contains("ORGANIZATION_SELECTION", "GLOBAL_PLATFORM_ADMIN");
        assertThat(result.platformAdministrator()).isNotNull();
    }

    @Test
    void exchangeFailsWithIdempotencyKeyReusedWhenFingerprintMismatch() {
        String cognitoToken = "stub-cognito:subject-1|client-1|" + fixedNow.minusSeconds(60).getEpochSecond();
        CognitoTokenClaims claims = new CognitoTokenClaims(
                "issuer", "subject-1", "client-1", "access",
                fixedNow.minusSeconds(60), fixedNow.plusSeconds(3600), null, "fp-1");
        when(cognitoTokenVerifier.verify(cognitoToken)).thenReturn(claims);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), actorUserId, "issuer", "subject-1",
                        fixedNow.minusSeconds(120), null)));
        when(repository.findUserById(actorUserId))
                .thenReturn(Optional.of(new User(actorUserId, UserStatus.ACTIVE,
                        Instant.EPOCH, fixedNow.minusSeconds(120), fixedNow.minusSeconds(120),
                        1, null, null)));
        IdempotencyKey existingKey = new IdempotencyKey(
                UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, actorUserId,
                "mutation-1", "PROVIDER_TOKEN_EXCHANGE", "different-fingerprint",
                IdempotencyStatus.PENDING, null, null, null, null, null,
                null, null, null, fixedNow, null, null, fixedNow.plusSeconds(300));
        when(repository.findIdempotencyKey(actorUserId, "mutation-1", IdempotencyScope.IAM_AUTH,
                OperationCode.PROVIDER_TOKEN_EXCHANGE))
                .thenReturn(Optional.of(existingKey));

        assertThatThrownBy(() -> service.exchange(cognitoToken, deviceIdentifier, DevicePlatform.ANDROID,
                "Pixel 8", "mutation-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    private static class NoopTransactionExecutor implements IamTransactionExecutor {
        @Override
        public <T> T executeInAuthenticationScope(UUID actorUserId, String issuer, String subject,
                                                  Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInIamAuthScope(OperationCode operationCode, IamAuthScopeContext context,
                                           Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInGlobalScope(OperationCode operationCode, IamAuthScopeContext context,
                                          Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInProvisioningScope(OperationCode operationCode, IamAuthScopeContext context,
                                                 Supplier<T> action) {
            return action.get();
        }

        @Override
        public void refreshIamAuthScope(IamAuthScopeContext context) {
            // no-op: unit tests don't exercise real RLS session variables
        }
    }

    private static class TestServiceSettings implements IamServiceSettings {
        @Override public java.time.Duration accessTokenTtl() { return java.time.Duration.ofMinutes(10); }
        @Override public java.time.Duration refreshTokenTtl() { return java.time.Duration.ofDays(30); }
        @Override public java.time.Duration contextSelectionTokenTtl() { return java.time.Duration.ofMinutes(5); }
        @Override public java.time.Duration activationEscrowTtl() { return java.time.Duration.ofMinutes(5); }
        @Override public java.time.Duration idempotencyRetention() { return java.time.Duration.ofDays(30); }
        @Override public boolean providerCommandWorkerEnabled() { return false; }
        @Override public java.time.Duration providerCommandPollInterval() { return java.time.Duration.ofSeconds(60); }
        @Override public int providerCommandBatchLimit() { return 20; }
        @Override public java.time.Duration providerCommandLeaseTtl() { return java.time.Duration.ofMinutes(5); }
        @Override public int providerCommandMaxAttempts() { return 10; }
        @Override public java.time.Duration providerCommandBackoffBase() { return java.time.Duration.ofSeconds(10); }
        @Override public java.time.Duration providerCommandBackoffMax() { return java.time.Duration.ofMinutes(15); }
        @Override public double providerCommandJitter() { return 0.25d; }
    }
}
