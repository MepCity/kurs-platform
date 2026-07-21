package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.EscrowStatus;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.domain.UserIdentity;
import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class ProviderTokenExchangeService {

    private final IamAuthRepository repository;
    private final CognitoTokenVerifier cognitoTokenVerifier;
    private final TokenHasher tokenHasher;
    private final AeadEscrowService escrowService;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final IamTransactionExecutor transactionExecutor;
    private final IamServiceSettings settings;
    private final IamAuditWriter auditWriter;

    public ProviderTokenExchangeService(IamAuthRepository repository,
                                        CognitoTokenVerifier cognitoTokenVerifier,
                                        TokenHasher tokenHasher,
                                        AeadEscrowService escrowService,
                                        SecureRandom secureRandom,
                                        Clock clock,
                                        IamTransactionExecutor transactionExecutor,
                                        IamServiceSettings settings,
                                        IamAuditWriter auditWriter) {
        this.repository = repository;
        this.cognitoTokenVerifier = cognitoTokenVerifier;
        this.tokenHasher = tokenHasher;
        this.escrowService = escrowService;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
        this.settings = settings;
        this.auditWriter = auditWriter;
    }

    public ProviderTokenExchangeResult exchange(String cognitoAccessToken,
                                                UUID deviceIdentifier,
                                                DevicePlatform platform,
                                                String deviceName,
                                                String clientMutationId) {
        Instant now = clock.instant();
        CognitoTokenClaims claims = cognitoTokenVerifier.verify(cognitoAccessToken);
        if (claims.isExpired(now) || claims.isNotYetValid(now)) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı geçersiz veya süresi dolmuş.");
        }

        AuthResolvedActor resolved = transactionExecutor.executeInAuthenticationScope(
                null, claims.issuer(), claims.subject(),
                () -> resolveActorReadOnly(claims, now));

        UUID actorUserId = resolved.actorUserId();
        String fingerprint = buildFingerprint(claims.rawTokenFingerprint(), deviceIdentifier, actorUserId);

        return transactionExecutor.executeInIamAuthScope(
                OperationCode.PROVIDER_TOKEN_EXCHANGE,
                IamAuthScopeContext.actorOnly(actorUserId)
                        .withProviderDevice(deviceIdentifier, claims.authTime()),
                () -> performMutation(resolved, deviceIdentifier, platform, deviceName,
                        clientMutationId, fingerprint, now));
    }

    private AuthResolvedActor resolveActorReadOnly(CognitoTokenClaims claims, Instant now) {
        Optional<UserIdentity> identityOpt = repository.findUserIdentityByIssuerAndSubject(claims.issuer(), claims.subject());
        if (identityOpt.isEmpty()) {
            throw new IamException("RESOURCE_NOT_FOUND", "Platform identity eşleşmesi bulunamadı.");
        }
        UserIdentity identity = identityOpt.get();
        if (identity.isDisabled()) {
            throw new IamException("UNAUTHENTICATED", "Identity devre dışı bırakılmış.");
        }
        return new AuthResolvedActor(identity.userId(), identity, claims);
    }

    private ProviderTokenExchangeResult performMutation(AuthResolvedActor resolved,
                                                        UUID deviceIdentifier,
                                                        DevicePlatform platform,
                                                        String deviceName,
                                                        String clientMutationId,
                                                        String fingerprint,
                                                        Instant now) {
        UUID actorUserId = resolved.actorUserId();

        repository.acquireIdempotencyAdvisoryLock(actorUserId, clientMutationId);

        Optional<IdempotencyKey> existingKey = repository.findIdempotencyKey(
                actorUserId, clientMutationId, IdempotencyScope.IAM_AUTH, OperationCode.PROVIDER_TOKEN_EXCHANGE);

        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (!key.requestFingerprint().equals(fingerprint)) {
                throw new IamException("IDEMPOTENCY_KEY_REUSED",
                        "Aynı anahtar farklı cihaz veya istek özetiyle kullanılmış.");
            }
            if (key.isCompleted() && !key.isResultExpired(now)) {
                return resolveReplay(key, actorUserId, deviceIdentifier, fingerprint);
            }
            if (key.isCompleted() && key.isResultExpired(now)) {
                throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış. Yeniden giriş yapın.");
            }
        }

        User user = repository.findUserById(actorUserId)
                .orElseThrow(() -> new IamException("RESOURCE_NOT_FOUND", "Kullanıcı bulunamadı."));
        if (user.isProvisioning() || user.isSuspended()) {
            throw new IamException("ACCOUNT_NOT_READY", "Kullanıcı hesabı aktif değil.");
        }
        if (!resolved.claims().authTime().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("REAUTHENTICATION_REQUIRED", "Yeniden kimlik doğrulama gerekli.");
        }

        repository.acquireDeviceAdvisoryLock(actorUserId, deviceIdentifier);
        Optional<Instant> maxRevokedAtOpt = repository.getMaxRevokedAtForDevicePair(actorUserId, deviceIdentifier);
        if (maxRevokedAtOpt.isPresent()) {
            Instant maxRevokedAt = maxRevokedAtOpt.get();
            if (!resolved.claims().authTime().isAfter(maxRevokedAt)) {
                throw new IamException("REAUTHENTICATION_REQUIRED",
                        "Cihaz yeniden güven kazanması için yeniden kimlik doğrulama gerekli.");
            }
        }

        TrustedDevice device = repository.findActiveTrustedDevice(actorUserId, deviceIdentifier)
                .orElseGet(() -> {
                    TrustedDevice newDevice = new TrustedDevice(
                            UUID.randomUUID(), actorUserId, deviceIdentifier, deviceName, platform,
                            now, null, null);
                    return repository.saveTrustedDevice(newDevice);
                });

        OpaqueToken contextToken = OpaqueToken.generate(tokenHasher, secureRandom);
        Duration contextTokenTtl = settings.contextSelectionTokenTtl();
        Instant expiresAt = now.plus(contextTokenTtl);
        ContextSelectionToken tokenRecord = new ContextSelectionToken(
                UUID.randomUUID(), actorUserId, device.id(), contextToken.hash(),
                resolved.claims().authTime(), now, expiresAt, null, null);
        repository.saveContextSelectionToken(tokenRecord);

        Optional<PlatformAdministrator> adminOpt = repository.findActivePlatformAdministratorByUserId(actorUserId);
        Set<String> availableScopes = adminOpt.isPresent()
                ? Set.of("ORGANIZATION_SELECTION", "GLOBAL_PLATFORM_ADMIN")
                : Set.of("ORGANIZATION_SELECTION");

        String resultReference = "ctx:" + tokenRecord.id();
        Duration escrowTtl = settings.activationEscrowTtl();
        Duration idempotencyRetention = settings.idempotencyRetention();
        IdempotencyKey idempotencyKey = new IdempotencyKey(
                UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, actorUserId,
                clientMutationId, OperationCode.PROVIDER_TOKEN_EXCHANGE.name(), fingerprint,
                IdempotencyStatus.COMPLETED, tokenRecord.id(), (short) 200, null,
                null, resultReference, null, null, null,
                now, now, now.plus(escrowTtl), now.plus(idempotencyRetention));
        Optional<IdempotencyKey> conflicting = repository.insertIdempotencyKeyOrFindExisting(idempotencyKey);
        if (conflicting.isPresent()) {
            // The advisory lock above should make this unreachable; treat as a hard failure
            // rather than silently orphaning the trusted device/context token we just wrote.
            throw new IllegalStateException(
                    "Idempotency anahtarı kilit altındayken beklenmedik biçimde zaten mevcuttu.");
        }

        AeadEscrowService.EscrowPayload payload = new AeadEscrowService.EscrowPayload(
                contextToken, null, null);
        AeadEscrowService.EncryptedEscrow encrypted = escrowService.encrypt(
                actorUserId, OperationCode.PROVIDER_TOKEN_EXCHANGE.name(),
                deviceIdentifier, fingerprint, payload);
        AuthReplayEscrow escrow = new AuthReplayEscrow(
                UUID.randomUUID(), idempotencyKey.id(), actorUserId,
                OperationCode.PROVIDER_TOKEN_EXCHANGE.name(), deviceIdentifier, fingerprint,
                tokenRecord.id(), null, null,
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(),
                encrypted.aadContext(), EscrowStatus.READY, now.plus(escrowTtl), now, null);
        repository.saveAuthReplayEscrow(escrow);

        auditWriter.write(new IamAuditEvent(
                UUID.randomUUID(), null, actorUserId, MDC.get("requestId"),
                "PROVIDER_TOKEN_EXCHANGED", IamAuditEvent.EventScope.GLOBAL,
                "USER", IamAuditEvent.EventKind.ACCESS, actorUserId,
                Map.of("deviceIdentifier", device.deviceIdentifier().toString(), "contextSelectionTokenId", tokenRecord.id().toString()),
                Map.of("operationCode", OperationCode.PROVIDER_TOKEN_EXCHANGE.name())));

        return new ProviderTokenExchangeResult(
                contextToken, expiresAt, availableScopes,
                new ProviderTokenExchangeResult.UserDto(user.id(), resolveDisplayName(user), user.status().name()),
                adminOpt.map(a -> new ProviderTokenExchangeResult.PlatformAdministratorDto("ACTIVE")).orElse(null),
                new ProviderTokenExchangeResult.DeviceDto(
                        device.id(), device.deviceIdentifier(), device.platform(), device.deviceName(), device.trustedAt()));
    }

    private ProviderTokenExchangeResult resolveReplay(IdempotencyKey key, UUID actorUserId,
                                                       UUID deviceIdentifier, String expectedFingerprint) {
        Optional<AuthReplayEscrow> escrowOpt = repository.findAuthReplayEscrowByIdempotencyKeyId(key.id());
        if (escrowOpt.isEmpty()) {
            throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış. Yeniden giriş yapın.");
        }
        AuthReplayEscrow escrow = escrowOpt.get();
        if (!escrow.isReady() || escrow.isExpired(clock.instant())) {
            throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış. Yeniden giriş yapın.");
        }
        if (!escrow.tokenFingerprint().equals(expectedFingerprint)) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED",
                    "Replay escrow fingerprint eşleşmiyor.");
        }
        if (!escrow.deviceIdentifier().equals(deviceIdentifier)) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED",
                    "Replay escrow cihaz eşleşmiyor.");
        }
        if (!escrow.actorUserId().equals(actorUserId)) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED",
                    "Replay escrow aktör eşleşmiyor.");
        }
        AeadEscrowService.EscrowPayload payload = escrowService.decrypt(escrow);
        User user = repository.findUserById(actorUserId).orElseThrow();
        Optional<PlatformAdministrator> adminOpt = repository.findActivePlatformAdministratorByUserId(actorUserId);
        Set<String> availableScopes = adminOpt.isPresent()
                ? Set.of("ORGANIZATION_SELECTION", "GLOBAL_PLATFORM_ADMIN")
                : Set.of("ORGANIZATION_SELECTION");
        ContextSelectionToken tokenRecord = repository.findContextSelectionTokenByHash(payload.contextSelectionToken().hash())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Önceki context token bulunamadı."));
        TrustedDevice device = repository.findActiveTrustedDevice(actorUserId, deviceIdentifier)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Güvenilir cihaz bulunamadı."));
        return new ProviderTokenExchangeResult(
                payload.contextSelectionToken(), tokenRecord.expiresAt(), availableScopes,
                new ProviderTokenExchangeResult.UserDto(user.id(), resolveDisplayName(user), user.status().name()),
                adminOpt.map(a -> new ProviderTokenExchangeResult.PlatformAdministratorDto("ACTIVE")).orElse(null),
                new ProviderTokenExchangeResult.DeviceDto(
                        device.id(), device.deviceIdentifier(), device.platform(), device.deviceName(), device.trustedAt()));
    }

    private String buildFingerprint(String cognitoTokenFingerprint, UUID deviceIdentifier, UUID actorUserId) {
        return "PTE|" + cognitoTokenFingerprint + "|" + deviceIdentifier + "|" + actorUserId;
    }

    private String resolveDisplayName(User user) {
        return "Kullanıcı " + user.id().toString().substring(0, 8);
    }
}
