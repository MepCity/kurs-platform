package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.EscrowStatus;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;
import org.mepcity.kursplatform.iam.domain.RefreshToken;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.SessionScope;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class SessionActivationService {

    private final IamAuthRepository repository;
    private final CognitoUserStatusChecker cognitoUserStatusChecker;
    private final TokenHasher tokenHasher;
    private final AeadEscrowService escrowService;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final IamTransactionExecutor transactionExecutor;
    private final IamServiceSettings settings;
    private final IamAuditWriter auditWriter;
    private final ProviderCommandService providerCommandService;

    public SessionActivationService(IamAuthRepository repository,
                                    CognitoUserStatusChecker cognitoUserStatusChecker,
                                    TokenHasher tokenHasher,
                                    AeadEscrowService escrowService,
                                    SecureRandom secureRandom,
                                    Clock clock,
                                    IamTransactionExecutor transactionExecutor,
                                    IamServiceSettings settings,
                                    IamAuditWriter auditWriter,
                                    ProviderCommandService providerCommandService) {
        this.repository = repository;
        this.cognitoUserStatusChecker = cognitoUserStatusChecker;
        this.tokenHasher = tokenHasher;
        this.escrowService = escrowService;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
        this.settings = settings;
        this.auditWriter = auditWriter;
        this.providerCommandService = providerCommandService;
    }

    public SessionActivationResult activatePlatformAdmin(String contextSelectionTokenValue,
                                                         String clientMutationId) {
        Instant now = clock.instant();
        String tokenHash = tokenHasher.hash(contextSelectionTokenValue);

        ActivationOutcome outcome = transactionExecutor.executeInIamAuthScope(
                OperationCode.PLATFORM_ADMIN_ACTIVATE,
                IamAuthScopeContext.bootstrapContextToken(tokenHash),
                () -> performPlatformAdminMutation(tokenHash, clientMutationId, now));

        if (outcome.disabledRevokedSecurityCommitted()) {
            throw new IamException("SESSION_REVOKED", "Sağlayıcı kullanıcı durumu geçersiz.");
        }
        if (outcome.providerUnavailable()) {
            throw new IamException("PROVIDER_UNAVAILABLE", "Sağlayıcı durumu belirsiz. Yeni oturum kurulamadı.");
        }
        return outcome.result();
    }

    public SessionActivationResult activateContext(String contextSelectionTokenValue,
                                                   UUID organizationMembershipId,
                                                   String clientMutationId) {
        Instant now = clock.instant();
        String tokenHash = tokenHasher.hash(contextSelectionTokenValue);

        ActivationOutcome outcome = transactionExecutor.executeInIamAuthScope(
                OperationCode.CONTEXT_ACTIVATE,
                IamAuthScopeContext.bootstrapContextToken(tokenHash).withMembership(organizationMembershipId, null),
                () -> performContextActivationMutation(tokenHash, organizationMembershipId, clientMutationId, now));

        if (outcome.disabledRevokedSecurityCommitted()) {
            throw new IamException("SESSION_REVOKED", "Sağlayıcı kullanıcı durumu geçersiz.");
        }
        if (outcome.providerUnavailable()) {
            throw new IamException("PROVIDER_UNAVAILABLE", "Sağlayıcı durumu belirsiz. Yeni oturum kurulamadı.");
        }
        return outcome.result();
    }

    /**
     * Everything below — the bootstrap context-token read, its conditional consumption, actor /
     * device / membership resolution, reauthentication and provider-status checks, family and
     * first refresh/access token issuance, and the idempotency + replay-escrow bookkeeping — runs
     * inside the ONE mutation transaction opened by executeInIamAuthScope. Token usability and
     * the membership/org STATE_CONFLICT checks are deliberately evaluated only on the
     * non-replay path: re-validating them ahead of the idempotency/replay check would let a
     * status change that happens strictly AFTER a successful activation (token consumed, org
     * later suspended, etc.) block a client's legitimate retry of an operation that already
     * completed.
     */
    private ActivationOutcome performPlatformAdminMutation(String tokenHash, String clientMutationId, Instant now) {
        ContextSelectionToken contextToken = repository.findContextSelectionTokenByHash(tokenHash)
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Context selection token geçersiz."));

        transactionExecutor.refreshIamAuthScope(
                IamAuthScopeContext.actorAndDevice(contextToken.userId(), contextToken.trustedDeviceId()));

        // Checked unconditionally, ahead of the idempotency/replay lookup below: a device revoked
        // after a prior activation already completed must still fail closed on replay — the whole
        // point of a device revoke is to kill every session tied to it, so a replay of an
        // already-completed idempotency key must never resurrect a session for a revoked device.
        TrustedDevice device = repository.findTrustedDeviceById(contextToken.userId(), contextToken.trustedDeviceId())
                .filter(TrustedDevice::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Güvenilir cihaz bulunamadı veya iptal edilmiş."));
        User user = repository.findUserById(contextToken.userId())
                .orElseThrow(() -> new IamException("ACCOUNT_NOT_READY", "Kullanıcı bulunamadı."));
        if (!user.isActive()) {
            throw new IamException("ACCOUNT_NOT_READY", "Kullanıcı hesabı aktif değil.");
        }

        String fingerprint = buildPlatformAdminFingerprint(contextToken.tokenHash(), device.deviceIdentifier(), user.id());

        repository.acquireIdempotencyAdvisoryLock(user.id(), clientMutationId);

        Optional<IdempotencyKey> existingKey = repository.findIdempotencyKey(
                user.id(), clientMutationId, IdempotencyScope.IAM_AUTH, OperationCode.PLATFORM_ADMIN_ACTIVATE);
        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (!key.requestFingerprint().equals(fingerprint)) {
                throw new IamException("IDEMPOTENCY_KEY_REUSED",
                        "Aynı anahtar farklı token/üyelik/istekle kullanılmış.");
            }
            if (key.isCompleted() && !key.isResultExpired(now)) {
                return new ActivationOutcome(resolveReplay(key, user, device, null, null, fingerprint), false, false);
            }
            if (key.isCompleted() && key.isResultExpired(now)) {
                throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış. Yeniden giriş yapın.");
            }
        }

        if (!contextToken.isUsable(now)) {
            throw new IamException("UNAUTHENTICATED", "Context selection token süresi dolmuş veya tüketilmiş.");
        }

        PlatformAdministrator admin = repository.findActivePlatformAdministratorByUserId(user.id())
                .orElseThrow(() -> new IamException("FORBIDDEN", "Kullanıcı aktif platform yöneticisi değil."));

        if (!contextToken.authenticatedAt().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Oturum eşiği geçersiz. Yeniden kimlik doğrulama gerekli.");
        }

        ProviderUserStatus providerStatus = checkCanonicalProviderStatus(user.id(), contextToken);
        if (providerStatus == ProviderUserStatus.DISABLED || providerStatus == ProviderUserStatus.REVOKED) {
            repository.revokeAllActorFamilies(user.id(), OperationCode.PLATFORM_ADMIN_ACTIVATE, now);
            repository.elevateUserReauthenticationRequiredAfter(user.id(), now);
            auditWriter.write(new IamAuditEvent(
                    UUID.randomUUID(), null, user.id(), MDC.get("requestId"),
                    "IAM_PROVIDER_SESSION_REVOKED", IamAuditEvent.EventScope.GLOBAL,
                    "USER", IamAuditEvent.EventKind.SECURITY, user.id(),
                    Map.of("providerStatus", providerStatus.name()),
                    Map.of("operationCode", OperationCode.PLATFORM_ADMIN_ACTIVATE.name())));
            // Enqueue the provider-side disable so Cognito actually reflects the verdict, not just
            // our local session state. The command id is opaque here — we only need it created so
            // the worker/scheduler picks it up. An idempotency key derived from actor + mutation
            // makes a replay (the same activation retry) collapse onto the first command row rather
            // than producing duplicates. DISABLED → USER_DISABLE; REVOKED → USER_LOGOUT (global
            // sign-out) — the closest supported V1 command to each verdict.
            resolveTrustedDeviceIdentity(contextToken).ifPresent(identity -> enqueueLifecycleCommand(
                    providerStatus, identity.id(), identity.userId(),
                    "PLATFORM_ADMIN_ACTIVATE|" + user.id() + "|" + contextToken.id() + "|" + clientMutationId));
            return new ActivationOutcome(null, true, false);
        }
        if (providerStatus == ProviderUserStatus.UNKNOWN) {
            // No session, no family, no token, no context-token consumption happens on this path —
            // the audit write below is the only thing this transaction commits, so there is nothing
            // else that could be lost or wrongly persisted alongside it.
            writeStatusCheckBlockedAudit(user.id(), null,
                    OperationCode.PLATFORM_ADMIN_ACTIVATE, IamAuditEvent.EventScope.GLOBAL,
                    "IAM_PROVIDER_STATUS_CHECK_BLOCKED");
            return new ActivationOutcome(null, false, true);
        }

        boolean consumed = repository.consumeContextSelectionTokenIfAvailable(contextToken.id(), now);
        if (!consumed) {
            throw new IamException("UNAUTHENTICATED", "Context token zaten tüketilmiş veya iptal edilmiş.");
        }

        OpaqueToken accessToken = OpaqueToken.generate(tokenHasher, secureRandom);
        OpaqueToken refreshToken = OpaqueToken.generate(tokenHasher, secureRandom);
        RefreshTokenFamily family = new RefreshTokenFamily(
                UUID.randomUUID(), user.id(), device.id(), null,
                contextToken.authenticatedAt(), null, null, now);
        repository.saveRefreshTokenFamily(family);

        Duration accessTokenTtl = settings.accessTokenTtl();
        Duration refreshTokenTtl = settings.refreshTokenTtl();
        RefreshToken refreshTokenRecord = new RefreshToken(
                UUID.randomUUID(), family.id(), null, refreshToken.hash(), accessToken.hash(),
                now.plus(accessTokenTtl), now, null, now.plus(refreshTokenTtl), null);
        repository.saveRefreshToken(refreshTokenRecord);

        persistIdempotencyAndEscrow(user.id(), clientMutationId,
                OperationCode.PLATFORM_ADMIN_ACTIVATE, fingerprint, device.deviceIdentifier(),
                null, family.id(), refreshTokenRecord.id(),
                accessToken, refreshToken, now);

        auditWriter.write(new IamAuditEvent(
                UUID.randomUUID(), null, user.id(), MDC.get("requestId"),
                "PLATFORM_ADMIN_ACTIVATED", IamAuditEvent.EventScope.GLOBAL,
                "USER", IamAuditEvent.EventKind.ACCESS, user.id(),
                Map.of("deviceIdentifier", device.deviceIdentifier().toString(), "refreshTokenFamilyId", family.id().toString()),
                Map.of("operationCode", OperationCode.PLATFORM_ADMIN_ACTIVATE.name())));

        SessionActivationResult result = buildResult(user, device, null, null,
                SessionScope.GLOBAL_PLATFORM_ADMIN, accessToken, refreshToken,
                contextToken.authenticatedAt(), now);
        return new ActivationOutcome(result, false, false);
    }

    private ActivationOutcome performContextActivationMutation(String tokenHash, UUID organizationMembershipId,
                                                                String clientMutationId, Instant now) {
        ContextSelectionToken contextToken = repository.findContextSelectionTokenByHash(tokenHash)
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Context selection token geçersiz."));

        transactionExecutor.refreshIamAuthScope(
                IamAuthScopeContext.actorAndDevice(contextToken.userId(), contextToken.trustedDeviceId())
                        .withMembership(organizationMembershipId, null));

        // Checked unconditionally, ahead of the idempotency/replay lookup below: a device revoked
        // after a prior activation already completed must still fail closed on replay — the whole
        // point of a device revoke is to kill every session tied to it, so a replay of an
        // already-completed idempotency key must never resurrect a session for a revoked device.
        TrustedDevice device = repository.findTrustedDeviceById(contextToken.userId(), contextToken.trustedDeviceId())
                .filter(TrustedDevice::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Güvenilir cihaz bulunamadı veya iptal edilmiş."));
        User user = repository.findUserById(contextToken.userId())
                .orElseThrow(() -> new IamException("ACCOUNT_NOT_READY", "Kullanıcı bulunamadı."));
        if (!user.isActive()) {
            throw new IamException("ACCOUNT_NOT_READY", "Kullanıcı hesabı aktif değil.");
        }

        OrganizationMembership membership = repository.findOrganizationMembershipByIdAndUserId(
                        organizationMembershipId, user.id())
                .orElseThrow(() -> new IamException("RESOURCE_NOT_FOUND", "Üyelik bulunamadı."));
        List<ContextSelectionSummary> summaries = repository.findContextSelectionSummaries(user.id());
        ContextSelectionSummary summary = summaries.stream()
                .filter(s -> s.id().equals(organizationMembershipId))
                .findFirst()
                .orElseThrow(() -> new IamException("RESOURCE_NOT_FOUND", "Üyelik bulunamadı."));

        // app.iam_target_organization_id is only known once the membership row is resolved;
        // the audit_logs_insert_iam_organization RLS policy needs it before either audit write below.
        transactionExecutor.refreshIamAuthScope(
                IamAuthScopeContext.actorAndDevice(contextToken.userId(), contextToken.trustedDeviceId())
                        .withMembership(organizationMembershipId, membership.organizationId()));

        String fingerprint = buildContextActivateFingerprint(
                contextToken.tokenHash(), organizationMembershipId, device.deviceIdentifier(), user.id());

        repository.acquireIdempotencyAdvisoryLock(user.id(), clientMutationId);

        Optional<IdempotencyKey> existingKey = repository.findIdempotencyKey(
                user.id(), clientMutationId, IdempotencyScope.IAM_AUTH, OperationCode.CONTEXT_ACTIVATE);
        if (existingKey.isPresent()) {
            IdempotencyKey key = existingKey.get();
            if (!key.requestFingerprint().equals(fingerprint)) {
                throw new IamException("IDEMPOTENCY_KEY_REUSED",
                        "Aynı anahtar farklı token/üyelik/istekle kullanılmış.");
            }
            if (key.isCompleted() && !key.isResultExpired(now)) {
                return new ActivationOutcome(resolveReplay(key, user, device, membership, summary, fingerprint), false, false);
            }
            if (key.isCompleted() && key.isResultExpired(now)) {
                throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış. Yeniden giriş yapın.");
            }
        }

        if (!contextToken.isUsable(now)) {
            throw new IamException("UNAUTHENTICATED", "Context selection token süresi dolmuş veya tüketilmiş.");
        }
        if (!membership.isActive()) {
            throw new IamException("STATE_CONFLICT", "Üyelik aktif değil.");
        }
        if (!"ACTIVE".equals(summary.membershipStatus())) {
            throw new IamException("STATE_CONFLICT", "Üyelik aktif değil.");
        }
        if (!"ACTIVE".equals(summary.organizationStatus())) {
            throw new IamException("STATE_CONFLICT", "Kurum durumu oturuma uygun değil.");
        }
        if (summary.roleCodes().isEmpty()) {
            throw new IamException("STATE_CONFLICT", "Aktif rol bulunamadı.");
        }

        if (!contextToken.authenticatedAt().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Kullanıcı oturum eşiği geçersiz.");
        }
        if (!contextToken.authenticatedAt().isAfter(membership.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Üyelik oturum eşiği geçersiz.");
        }

        ProviderUserStatus providerStatus = checkCanonicalProviderStatus(user.id(), contextToken);
        if (providerStatus == ProviderUserStatus.DISABLED || providerStatus == ProviderUserStatus.REVOKED) {
            repository.revokeAllActorFamilies(user.id(), OperationCode.CONTEXT_ACTIVATE, now);
            repository.elevateUserReauthenticationRequiredAfter(user.id(), now);
            auditWriter.write(new IamAuditEvent(
                    UUID.randomUUID(), membership.organizationId(), user.id(), MDC.get("requestId"),
                    "IAM_ORG_PROVIDER_SESSION_REVOKED", IamAuditEvent.EventScope.ORGANIZATION,
                    "USER", IamAuditEvent.EventKind.SECURITY, user.id(),
                    Map.of("providerStatus", providerStatus.name(), "organizationMembershipId", membership.id().toString()),
                    Map.of("operationCode", OperationCode.CONTEXT_ACTIVATE.name())));
            resolveTrustedDeviceIdentity(contextToken).ifPresent(identity -> enqueueLifecycleCommand(
                    providerStatus, identity.id(), identity.userId(),
                    "CONTEXT_ACTIVATE|" + user.id() + "|" + membership.id() + "|" + clientMutationId));
            return new ActivationOutcome(null, true, false);
        }
        if (providerStatus == ProviderUserStatus.UNKNOWN) {
            writeStatusCheckBlockedAudit(user.id(), membership.organizationId(),
                    OperationCode.CONTEXT_ACTIVATE, IamAuditEvent.EventScope.ORGANIZATION,
                    "IAM_ORG_PROVIDER_STATUS_CHECK_BLOCKED");
            return new ActivationOutcome(null, false, true);
        }

        boolean consumed = repository.consumeContextSelectionTokenIfAvailable(contextToken.id(), now);
        if (!consumed) {
            throw new IamException("UNAUTHENTICATED", "Context token zaten tüketilmiş veya iptal edilmiş.");
        }

        OpaqueToken accessToken = OpaqueToken.generate(tokenHasher, secureRandom);
        OpaqueToken refreshToken = OpaqueToken.generate(tokenHasher, secureRandom);
        RefreshTokenFamily family = new RefreshTokenFamily(
                UUID.randomUUID(), user.id(), device.id(), membership.id(),
                contextToken.authenticatedAt(), membership.sessionGeneration(), null, now);
        repository.saveRefreshTokenFamily(family);

        Duration accessTokenTtl = settings.accessTokenTtl();
        Duration refreshTokenTtl = settings.refreshTokenTtl();
        RefreshToken refreshTokenRecord = new RefreshToken(
                UUID.randomUUID(), family.id(), null, refreshToken.hash(), accessToken.hash(),
                now.plus(accessTokenTtl), now, null, now.plus(refreshTokenTtl), null);
        repository.saveRefreshToken(refreshTokenRecord);

        auditWriter.write(new IamAuditEvent(
                UUID.randomUUID(), membership.organizationId(), user.id(), MDC.get("requestId"),
                "CONTEXT_ACTIVATED", IamAuditEvent.EventScope.ORGANIZATION,
                "USER", IamAuditEvent.EventKind.ACCESS, user.id(),
                Map.of("deviceIdentifier", device.deviceIdentifier().toString(),
                        "refreshTokenFamilyId", family.id().toString(),
                        "organizationMembershipId", membership.id().toString()),
                Map.of("operationCode", OperationCode.CONTEXT_ACTIVATE.name())));

        persistIdempotencyAndEscrow(user.id(), clientMutationId,
                OperationCode.CONTEXT_ACTIVATE, fingerprint, device.deviceIdentifier(),
                membership.id(), family.id(), refreshTokenRecord.id(),
                accessToken, refreshToken, now);

        SessionActivationResult result = buildResult(user, device, membership, summary,
                SessionScope.ORGANIZATION, accessToken, refreshToken,
                contextToken.authenticatedAt(), now);
        return new ActivationOutcome(result, false, false);
    }

    private ProviderUserStatus checkCanonicalProviderStatus(UUID userId, ContextSelectionToken contextToken) {
        Optional<UserIdentity> identity = repository.findUserIdentityByTrustedDevice(contextToken.trustedDeviceId());
        String subject = identity.map(UserIdentity::subject).orElse(null);
        String issuer = identity.map(UserIdentity::issuer).orElse(null);
        return cognitoUserStatusChecker.checkCanonicalStatus(userId, issuer, subject);
    }

    /**
     * Returns the resolved provider identity for the trusted device the context token was issued
     * under, so the DISABLED/REVOKED branch can enqueue the right target_identity_id /
     * resolving_user_id pair onto the provider command it creates. Empty if the device has no
     * identity row (the same case {@link #checkCanonicalProviderStatus} reports as UNKNOWN).
     */
    private Optional<UserIdentity> resolveTrustedDeviceIdentity(ContextSelectionToken contextToken) {
        return repository.findUserIdentityByTrustedDevice(contextToken.trustedDeviceId());
    }

    private void persistIdempotencyAndEscrow(UUID actorUserId, String clientMutationId,
                                             OperationCode operationCode, String fingerprint,
                                             UUID deviceIdentifier, UUID membershipId,
                                             UUID familyId, UUID refreshTokenId,
                                             OpaqueToken accessToken, OpaqueToken refreshToken,
                                             Instant now) {
        String resultReference = "fam:" + familyId;
        Duration escrowTtl = settings.activationEscrowTtl();
        Duration idempotencyRetention = settings.idempotencyRetention();
        IdempotencyKey idempotencyKey = new IdempotencyKey(
                UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, actorUserId,
                clientMutationId, operationCode.name(), fingerprint,
                IdempotencyStatus.COMPLETED, familyId, (short) 200, null,
                null, resultReference, null, null, null,
                now, now, now.plus(escrowTtl), now.plus(idempotencyRetention));
        Optional<IdempotencyKey> conflicting = repository.insertIdempotencyKeyOrFindExisting(idempotencyKey);
        if (conflicting.isPresent()) {
            throw new IllegalStateException(
                    "Idempotency anahtarı kilit altındayken beklenmedik biçimde zaten mevcuttu.");
        }

        AeadEscrowService.EscrowPayload payload = new AeadEscrowService.EscrowPayload(
                null, accessToken, refreshToken);
        AeadEscrowService.EncryptedEscrow encrypted = escrowService.encrypt(
                actorUserId, operationCode.name(), deviceIdentifier, fingerprint, payload);
        AuthReplayEscrow escrow = new AuthReplayEscrow(
                UUID.randomUUID(), idempotencyKey.id(), actorUserId,
                operationCode.name(), deviceIdentifier, fingerprint,
                null, familyId, refreshTokenId,
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(),
                encrypted.aadContext(), EscrowStatus.READY, now.plus(escrowTtl), now, null);
        repository.saveAuthReplayEscrow(escrow);
    }

    private SessionActivationResult resolveReplay(IdempotencyKey key, User user, TrustedDevice device,
                                                   OrganizationMembership membership,
                                                   ContextSelectionSummary summary,
                                                   String expectedFingerprint) {
        AuthReplayEscrow escrow = repository.findAuthReplayEscrowByIdempotencyKeyId(key.id())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış."));
        if (!escrow.isReady() || escrow.isExpired(clock.instant())) {
            throw new IamException("UNAUTHENTICATED", "Önceki oturum sonlanmış.");
        }
        if (!escrow.tokenFingerprint().equals(expectedFingerprint)) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED", "Replay escrow fingerprint eşleşmiyor.");
        }
        if (!escrow.actorUserId().equals(user.id())) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED", "Replay escrow aktör eşleşmiyor.");
        }
        AeadEscrowService.EscrowPayload payload = escrowService.decrypt(escrow);
        RefreshTokenFamily family = repository.findRefreshTokenFamilyById(escrow.resultRefreshTokenFamilyId())
                .orElseThrow();
        SessionScope scope = family.isGlobalPlatformAdmin() ? SessionScope.GLOBAL_PLATFORM_ADMIN : SessionScope.ORGANIZATION;
        return buildResult(user, device, membership, summary,
                scope, payload.accessToken(), payload.refreshToken(),
                family.authenticatedAt(), clock.instant());
    }

    private SessionActivationResult buildResult(User user, TrustedDevice device,
                                                OrganizationMembership membership,
                                                ContextSelectionSummary summary,
                                                SessionScope scope,
                                                OpaqueToken accessToken, OpaqueToken refreshToken,
                                                Instant authenticatedAt, Instant now) {
        SessionActivationResult.UserDto userDto = new SessionActivationResult.UserDto(
                user.id(), resolveDisplayName(user), user.status().name());
        SessionActivationResult.DeviceDto deviceDto = new SessionActivationResult.DeviceDto(
                device.id(), device.deviceIdentifier(), device.platform().name(),
                device.deviceName(), device.trustedAt());
        SessionActivationResult.PlatformAdministratorDto adminDto = scope == SessionScope.GLOBAL_PLATFORM_ADMIN
                ? new SessionActivationResult.PlatformAdministratorDto("ACTIVE") : null;
        SessionActivationResult.OrganizationMembershipDto membershipDto = null;
        if (membership != null && summary != null) {
            membershipDto = new SessionActivationResult.OrganizationMembershipDto(
                    membership.id(), membership.organizationId(),
                    summary.organizationName(), summary.organizationStatus(),
                    summary.membershipStatus(), summary.roleCodes(),
                    membership.sessionGeneration());
        }
        Duration accessTokenTtl = settings.accessTokenTtl();
        Duration refreshTokenTtl = settings.refreshTokenTtl();
        SessionActivationResult.SessionDto sessionDto = new SessionActivationResult.SessionDto(
                scope.name(), accessToken, refreshToken, "Bearer",
                now.plus(accessTokenTtl), now.plus(refreshTokenTtl), authenticatedAt);
        return new SessionActivationResult(userDto, adminDto, deviceDto, membershipDto, sessionDto);
    }

    private String buildPlatformAdminFingerprint(String contextTokenHash, UUID deviceIdentifier, UUID actorUserId) {
        return "PAA|" + contextTokenHash + "|" + deviceIdentifier + "|" + actorUserId;
    }

    private String buildContextActivateFingerprint(String contextTokenHash, UUID membershipId,
                                                    UUID deviceIdentifier, UUID actorUserId) {
        return "CA|" + contextTokenHash + "|" + membershipId + "|" + deviceIdentifier + "|" + actorUserId;
    }

    private String resolveDisplayName(User user) {
        return "Kullanıcı " + user.id().toString().substring(0, 8);
    }

    /**
     * Enqueues the closest supported V1 provider-command for a DISABLED/REVOKED verdict —
     * USER_DISABLE for DISABLED, USER_LOGOUT (global sign-out) for REVOKED — so
     * ProviderCommandScheduler actually reflects the verdict in Cognito instead of only revoking
     * the local session. {@link ProviderCommandService#createCommand} opens its own GLOBAL-scope
     * transaction internally, but since this call happens while the caller's IAM_AUTH transaction
     * is already active on this thread, it joins that same transaction (Spring's default
     * PROPAGATION_REQUIRED) rather than opening a second one — the command row commits or rolls
     * back together with the revoke/audit above it. No encrypted payload exists for either type
     * (V1 has no KMS/payload-decryption service), so the fingerprint is a fixed constant: the
     * idempotency key already uniquely identifies this specific verdict for this specific actor.
     */
    private void enqueueLifecycleCommand(ProviderUserStatus providerStatus, UUID targetIdentityId,
                                         UUID resolvingUserId, String idempotencyKeySeed) {
        ProviderCommandType commandType = providerStatus == ProviderUserStatus.DISABLED
                ? ProviderCommandType.USER_DISABLE
                : ProviderCommandType.USER_LOGOUT;
        providerCommandService.createCommand(commandType, null, targetIdentityId, resolvingUserId,
                null, null, "NONE", null, null, idempotencyKeySeed);
    }

    /**
     * Audited per IAM_GIRIS_OTURUM_API_SOZLESMESI.md §14: an UNKNOWN provider verdict blocks the
     * activation but must still leave a visible security trail. Written directly through {@link
     * #auditWriter} — {@code JdbcIamAuditWriter} always binds to the caller's already-active
     * transaction (never a second connection), so this commits or rolls back as part of the SAME
     * IAM_AUTH transaction the caller opened, exactly like every other audit write in this class.
     */
    private void writeStatusCheckBlockedAudit(UUID userId, UUID organizationId, OperationCode operationCode,
                                              IamAuditEvent.EventScope scope, String actionType) {
        auditWriter.write(new IamAuditEvent(
                UUID.randomUUID(), organizationId, userId, MDC.get("requestId"),
                actionType, scope,
                "USER", IamAuditEvent.EventKind.SECURITY, userId,
                Map.of(),
                Map.of("operationCode", operationCode.name(), "providerStatus", "UNKNOWN")));
    }

    private record ActivationOutcome(SessionActivationResult result,
                                     boolean disabledRevokedSecurityCommitted,
                                     boolean providerUnavailable) {
    }
}
