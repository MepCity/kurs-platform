package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.*;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;
import java.util.Optional;
import org.slf4j.MDC;

/** Implements the one-time refresh-token rotation and self-logout contract. */
public class SessionRefreshService {
    private final IamAuthRepository repository;
    private final TokenHasher tokenHasher;
    private final SecureRandom secureRandom;
    private final Clock clock;
    private final IamTransactionExecutor transactions;
    private final IamServiceSettings settings;
    private final AeadEscrowService escrowService;
    private final IamAuditWriter auditWriter;

    public SessionRefreshService(IamAuthRepository repository, TokenHasher tokenHasher, SecureRandom secureRandom,
                                 Clock clock, IamTransactionExecutor transactions, IamServiceSettings settings) {
        this(repository, tokenHasher, secureRandom, clock, transactions, settings, null, null);
    }

    public SessionRefreshService(IamAuthRepository repository, TokenHasher tokenHasher, SecureRandom secureRandom,
                                 Clock clock, IamTransactionExecutor transactions, IamServiceSettings settings,
                                 AeadEscrowService escrowService, IamAuditWriter auditWriter) {
        this.repository = repository;
        this.tokenHasher = tokenHasher;
        this.secureRandom = secureRandom;
        this.clock = clock;
        this.transactions = transactions;
        this.settings = settings;
        this.escrowService = escrowService;
        this.auditWriter = auditWriter;
    }

    public SessionRefreshResult refresh(String refreshTokenValue, String clientMutationId) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new IamException("VALIDATION_FAILED", "refreshToken zorunludur.");
        }
        String hash = tokenHasher.hash(refreshTokenValue);
        RefreshOutcome outcome = transactions.executeInIamAuthScope(OperationCode.SESSION_REFRESH,
                IamTransactionExecutor.IamAuthScopeContext.bootstrapRefreshToken(hash),
                () -> rotate(hash, clientMutationId));
        if (outcome.failClosed()) {
            throw new IamException("SESSION_REVOKED", "Refresh token tekrar kullanıldı; oturum kapatıldı.");
        }
        return outcome.result();
    }

    public void logout(String accessTokenValue, String refreshTokenValue, String clientMutationId) {
        if (refreshTokenValue == null || refreshTokenValue.isBlank()) {
            throw new IamException("VALIDATION_FAILED", "refreshToken zorunludur.");
        }
        String hash = tokenHasher.hash(refreshTokenValue);
        String accessHash = tokenHasher.hash(accessTokenValue);
        transactions.executeInIamAuthScope(OperationCode.SESSION_LOGOUT,
                IamTransactionExecutor.IamAuthScopeContext.bootstrapRefreshToken(hash).withAccessTokenHash(accessHash), () -> {
                    RefreshToken bootstrapToken = repository.findRefreshTokenByHash(hash)
                            .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Refresh token geçersiz."));
                    transactions.refreshIamAuthScope(IamTransactionExecutor.IamAuthScopeContext.bootstrapFamily(bootstrapToken.familyId()));
                    RefreshTokenFamily family = repository.findRefreshTokenFamilyById(bootstrapToken.familyId())
                            .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Oturum bulunamadı."));
                    transactions.refreshIamAuthScope(IamTransactionExecutor.IamAuthScopeContext.actorAndDevice(family.userId(), family.trustedDeviceId()).withFamily(family.id()));
                    Instant now = clock.instant();
                    String fingerprint = "SL|" + family.userId() + "|" + family.trustedDeviceId() + "|" + family.id()
                            + "|" + accessHash + "|" + hash;
                    repository.acquireIdempotencyAdvisoryLock(family.userId(), clientMutationId);
                    Optional<IdempotencyKey> existing = repository.findIdempotencyKey(family.userId(), clientMutationId,
                            IdempotencyScope.IAM_AUTH, OperationCode.SESSION_LOGOUT);
                    if (existing.isPresent()) {
                        if (!fingerprint.equals(existing.get().requestFingerprint())) {
                            throw new IamException("IDEMPOTENCY_KEY_REUSED", "Aynı anahtar farklı oturumla kullanılmış.");
                        }
                        if (existing.get().isCompleted()) return null;
                    }
                    if (family.isRevoked() || bootstrapToken.isRevoked()) {
                        throw new IamException("SESSION_REVOKED", "Oturum zaten kapatılmış.");
                    }
                    AuthSession accessSession = repository.findAuthSessionByAccessTokenHash(accessHash)
                            .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Access token geçersiz."));
                    if (!family.id().equals(accessSession.refreshTokenFamilyId()) || !family.userId().equals(accessSession.userId())
                            || !java.util.Objects.equals(family.organizationMembershipId(), accessSession.organizationMembershipId())) {
                        throw new IamException("UNAUTHENTICATED", "Access ve refresh token aileleri eşleşmiyor.");
                    }
                    RefreshToken token = repository.findRefreshTokenByHashForUpdate(hash)
                            .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Refresh token geçersiz."));
                    repository.revokeRefreshTokensInFamily(family.id(), now);
                    repository.revokeRefreshTokenFamily(family.id(), now);
                    IdempotencyKey record = new IdempotencyKey(UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, family.userId(),
                            clientMutationId, OperationCode.SESSION_LOGOUT.name(), fingerprint, IdempotencyStatus.COMPLETED,
                            family.id(), (short) 204, null, null, "fam:" + family.id(), null, null, null,
                            now, now, null, now.plus(settings.idempotencyRetention()));
                    if (repository.insertIdempotencyKeyOrFindExisting(record).isPresent()) {
                        throw new IamException("IDEMPOTENCY_KEY_REUSED", "Idempotency kaydı çakıştı.");
                    }
                    writeLogoutAudit(family);
                    return null;
                });
    }

    private RefreshOutcome rotate(String hash, String key) {
        Instant now = clock.instant();
        RefreshToken bootstrapToken = repository.findRefreshTokenByHash(hash)
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Refresh token geçersiz."));
        transactions.refreshIamAuthScope(IamTransactionExecutor.IamAuthScopeContext.bootstrapFamily(bootstrapToken.familyId()));
        RefreshTokenFamily family = repository.findRefreshTokenFamilyById(bootstrapToken.familyId())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Oturum bulunamadı."));
        transactions.refreshIamAuthScope(IamTransactionExecutor.IamAuthScopeContext.actorAndDevice(family.userId(), family.trustedDeviceId()).withFamily(family.id()));
        String fingerprint = "SR|" + bootstrapToken.tokenHash() + "|" + family.trustedDeviceId() + "|" + family.userId() + "|" + family.id();
        repository.acquireIdempotencyAdvisoryLock(family.userId(), key);
        Optional<IdempotencyKey> existing = repository.findIdempotencyKey(family.userId(), key,
                IdempotencyScope.IAM_AUTH, OperationCode.SESSION_REFRESH);
        if (existing.isPresent()) {
            IdempotencyKey prior = existing.get();
            if (!fingerprint.equals(prior.requestFingerprint())) {
                throw new IamException("IDEMPOTENCY_KEY_REUSED", "Aynı anahtar farklı refresh token ile kullanılmış.");
            }
            if (prior.isCompleted()) {
                return replay(prior, fingerprint, family);
            }
        }
        RefreshToken old = repository.findRefreshTokenByHash(hash)
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Refresh token geçersiz."));
        if (old.isUsed()) {
            transactions.requireSecurityRevoke();
            repository.revokeRefreshTokensInFamily(family.id(), now);
            repository.revokeRefreshTokenFamily(family.id(), now);
            writeAudit("SESSION_REFRESH_REUSE_DETECTED", family, null);
            return RefreshOutcome.terminal();
        }
        if (old.isRevoked() || old.isExpired(now) || family.isRevoked()) {
            throw new IamException("SESSION_REVOKED", "Oturum geçersiz.");
        }
        User user = repository.findUserById(family.userId())
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Kullanıcı bulunamadı."));
        TrustedDevice device = repository.findTrustedDeviceById(user.id(), family.trustedDeviceId())
                .filter(TrustedDevice::isActive).orElseThrow(() -> new IamException("SESSION_REVOKED", "Cihaz iptal edilmiş."));
        if (!user.isActive() || !family.authenticatedAt().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Oturum eşiği geçersiz.");
        }

        OpaqueToken access = OpaqueToken.generate(tokenHasher, secureRandom);
        OpaqueToken refresh = OpaqueToken.generate(tokenHasher, secureRandom);
        repository.markRefreshTokenUsed(old.id(), now);
        RefreshToken next = new RefreshToken(UUID.randomUUID(), family.id(), old.id(), refresh.hash(), access.hash(),
                now.plus(settings.accessTokenTtl()), now, null, now.plus(settings.refreshTokenTtl()), null);
        repository.saveRefreshToken(next);

        SessionRefreshResult.OrganizationMembershipDto membership = null;
        String scope = SessionScope.GLOBAL_PLATFORM_ADMIN.name();
        if (family.isOrganizationScoped()) {
            OrganizationMembership m = repository.findOrganizationMembershipByIdAndUserId(family.organizationMembershipId(), user.id())
                    .filter(OrganizationMembership::isActive).orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik geçersiz."));
            ContextSelectionSummary summary = repository.findContextSelectionSummaries(user.id()).stream()
                    .filter(s -> s.id().equals(m.id())).findFirst().orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik geçersiz."));
            if (!"ACTIVE".equals(summary.organizationStatus()) || summary.roleCodes().isEmpty()
                    || family.issuedAtSessionGeneration() == null || family.issuedAtSessionGeneration() != m.sessionGeneration()
                    || !family.authenticatedAt().isAfter(m.reauthenticationRequiredAfter())) {
                throw new IamException("SESSION_REVOKED", "Üyelik oturumu geçersiz.");
            }
            scope = SessionScope.ORGANIZATION.name();
            membership = new SessionRefreshResult.OrganizationMembershipDto(m.id(), m.organizationId(), summary.organizationName(),
                    summary.organizationStatus(), summary.membershipStatus(), summary.roleCodes(), m.sessionGeneration());
        } else {
            repository.findActivePlatformAdministratorByUserId(user.id())
                    .orElseThrow(() -> new IamException("SESSION_REVOKED", "Platform yöneticisi yetkisi iptal edilmiş."));
        }
        SessionRefreshResult result = new SessionRefreshResult(membership, new SessionRefreshResult.SessionDto(scope, access, refresh, "Bearer",
                now.plus(settings.accessTokenTtl()), now.plus(settings.refreshTokenTtl()), family.authenticatedAt()));
        persistReplay(family, key, fingerprint, access, refresh, next, now);
        writeAudit("SESSION_REFRESHED", family, membership == null ? null : membership.organizationId());
        return RefreshOutcome.success(result);
    }

    private RefreshOutcome replay(IdempotencyKey key, String fingerprint, RefreshTokenFamily family) {
        Instant now = clock.instant();
        Optional<AuthReplayEscrow> escrowCandidate = repository.findAuthReplayEscrowByIdempotencyKeyId(key.id());
        if (key.isResultExpired(now) || escrowService == null || escrowCandidate.isEmpty()) {
            return reconcileReplay(family, escrowCandidate.orElse(null));
        }
        AuthReplayEscrow escrow = escrowCandidate.get();
        if (!escrow.isReady() || escrow.isExpired(now) || !fingerprint.equals(escrow.tokenFingerprint())
                || !family.id().equals(escrow.resultRefreshTokenFamilyId())
                || !escrow.resultRefreshTokenId().equals(key.resultEntityId())) {
            return reconcileReplay(family, escrow);
        }
        RefreshToken resultToken = repository.findRefreshTokenById(escrow.resultRefreshTokenId()).orElse(null);
        if (resultToken == null || resultToken.isRevoked() || !family.id().equals(resultToken.familyId())) {
            return reconcileReplay(family, escrow);
        }
        if (family.isRevoked()) {
            return reconcileReplay(family, escrow);
        }
        User user = repository.findUserById(family.userId()).filter(User::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Kullanıcı aktif değil."));
        repository.findTrustedDeviceById(user.id(), family.trustedDeviceId()).filter(TrustedDevice::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Cihaz iptal edilmiş."));
        if (!family.authenticatedAt().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Oturum canlı değil.");
        }
        SessionRefreshResult.OrganizationMembershipDto membership = null;
        String scope = SessionScope.GLOBAL_PLATFORM_ADMIN.name();
        if (family.isOrganizationScoped()) {
            OrganizationMembership m = repository.findOrganizationMembershipByIdAndUserId(family.organizationMembershipId(), user.id())
                    .filter(OrganizationMembership::isActive).orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik aktif değil."));
            ContextSelectionSummary s = repository.findContextSelectionSummaries(user.id()).stream().filter(x -> x.id().equals(m.id())).findFirst()
                    .orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik görünmüyor."));
            if (!"ACTIVE".equals(s.organizationStatus()) || s.roleCodes().isEmpty() || family.issuedAtSessionGeneration() == null
                    || family.issuedAtSessionGeneration() != m.sessionGeneration() || !family.authenticatedAt().isAfter(m.reauthenticationRequiredAfter())) {
                throw new IamException("SESSION_REVOKED", "Üyelik oturumu canlı değil.");
            }
            scope = SessionScope.ORGANIZATION.name();
            membership = new SessionRefreshResult.OrganizationMembershipDto(m.id(), m.organizationId(), s.organizationName(), s.organizationStatus(),
                    s.membershipStatus(), s.roleCodes(), m.sessionGeneration());
        } else {
            repository.findActivePlatformAdministratorByUserId(user.id()).orElseThrow(() -> new IamException("SESSION_REVOKED", "Platform yöneticisi aktif değil."));
        }
        try {
            var payload = escrowService.decrypt(escrow);
            return RefreshOutcome.success(new SessionRefreshResult(membership, new SessionRefreshResult.SessionDto(scope,
                    payload.accessToken(), payload.refreshToken(), "Bearer", resultToken.accessExpiresAt(),
                    resultToken.expiresAt(), family.authenticatedAt())));
        } catch (RuntimeException decryptFailure) {
            return reconcileReplay(family, escrow);
        }
    }

    /**
     * A completed key without a trustworthy replay response is a security event, not an invitation
     * to rotate again.  This method returns an outcome so the enclosing transaction can commit the
     * revocation, escrow zeroization and audit before refresh() exposes the terminal error.
     */
    private RefreshOutcome reconcileReplay(RefreshTokenFamily family, AuthReplayEscrow escrow) {
        transactions.requireSecurityRevoke();
        repository.revokeRefreshTokensInFamily(family.id(), clock.instant());
        boolean familyTransitioned = repository.revokeRefreshTokenFamilyIfActive(family.id());
        boolean escrowTransitioned = false;
        if (escrow != null) {
            // This is a single conditional UPDATE.  A READY escrow is zeroized even when another
            // security path already revoked the family; a terminal escrow changes no row.
            escrowTransitioned = repository.revokeAuthReplayEscrow(escrow.id());
        }
        if (familyTransitioned || escrowTransitioned) {
            writeAudit("SESSION_REFRESH_REPLAY_RECONCILED", family, null);
        }
        return RefreshOutcome.terminal();
    }

    private void persistReplay(RefreshTokenFamily family, String mutation, String fingerprint, OpaqueToken access,
                               OpaqueToken refresh, RefreshToken next, Instant now) {
        if (escrowService == null) return;
        IdempotencyKey record = new IdempotencyKey(UUID.randomUUID(), IdempotencyScope.IAM_AUTH, null, family.userId(), mutation,
                OperationCode.SESSION_REFRESH.name(), fingerprint, IdempotencyStatus.COMPLETED, next.id(), (short) 200,
                null, null, "fam:" + family.id(), null, null, null, now, now,
                now.plus(java.time.Duration.ofMinutes(10)), now.plus(settings.idempotencyRetention()));
        if (repository.insertIdempotencyKeyOrFindExisting(record).isPresent()) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED", "Idempotency kaydı çakıştı.");
        }
        var encrypted = escrowService.encrypt(family.userId(), OperationCode.SESSION_REFRESH.name(), family.trustedDeviceId(), fingerprint,
                new AeadEscrowService.EscrowPayload(null, access, refresh));
        repository.saveAuthReplayEscrow(new AuthReplayEscrow(UUID.randomUUID(), record.id(), family.userId(),
                OperationCode.SESSION_REFRESH.name(), family.trustedDeviceId(), fingerprint, null, family.id(), next.id(),
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(), encrypted.aadContext(),
                EscrowStatus.READY, now.plus(java.time.Duration.ofMinutes(10)), now, null));
    }

    private void writeAudit(String action, RefreshTokenFamily family, UUID ignoredOrganizationId) {
        if (auditWriter == null) return;
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("operationCode", OperationCode.SESSION_REFRESH.name());
        metadata.put("refreshTokenFamilyId", family.id().toString());
        metadata.put("trustedDeviceId", family.trustedDeviceId().toString());
        if (family.organizationMembershipId() != null) {
            metadata.put("organizationMembershipId", family.organizationMembershipId().toString());
        }
        auditWriter.write(new IamAuditEvent(UUID.randomUUID(), null, family.userId(), MDC.get("requestId"), action,
                IamAuditEvent.EventScope.GLOBAL,
                "USER", IamAuditEvent.EventKind.SECURITY, family.userId(), Map.of(),
                metadata));
    }

    private void writeLogoutAudit(RefreshTokenFamily family) {
        if (auditWriter == null) return;
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("operationCode", OperationCode.SESSION_LOGOUT.name());
        metadata.put("refreshTokenFamilyId", family.id().toString());
        metadata.put("trustedDeviceId", family.trustedDeviceId().toString());
        if (family.organizationMembershipId() != null) metadata.put("organizationMembershipId", family.organizationMembershipId().toString());
        auditWriter.write(new IamAuditEvent(UUID.randomUUID(), null, family.userId(), MDC.get("requestId"), "SESSION_LOGGED_OUT",
                IamAuditEvent.EventScope.GLOBAL, "USER", IamAuditEvent.EventKind.ACCESS, family.userId(), Map.of(),
                metadata));
    }

    private record RefreshOutcome(SessionRefreshResult result, boolean failClosed) {
        static RefreshOutcome success(SessionRefreshResult result) {
            return new RefreshOutcome(result, false);
        }

        static RefreshOutcome terminal() {
            return new RefreshOutcome(null, true);
        }
    }
}
