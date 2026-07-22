package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.RefreshToken;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.SessionScope;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

public class SessionInfoService {

    private final IamAuthRepository repository;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final IamTransactionExecutor transactionExecutor;

    public SessionInfoService(IamAuthRepository repository, TokenHasher tokenHasher, Clock clock,
                              IamTransactionExecutor transactionExecutor) {
        this.repository = repository;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
    }

    public SessionInfoResult resolveSession(String accessTokenValue) {
        String accessTokenHash = tokenHasher.hash(accessTokenValue);
        return transactionExecutor.executeInIamAuthScope(
                OperationCode.SESSION_INFO,
                IamAuthScopeContext.bootstrapAccessToken(accessTokenHash),
                () -> resolveSessionHash(accessTokenHash));
    }

    public org.mepcity.kursplatform.iam.application.contract.ActiveSession resolveActiveSession(String accessTokenValue) {
        SessionInfoResult result = resolveSession(accessTokenValue);
        if (SessionScope.GLOBAL_PLATFORM_ADMIN.name().equals(result.session().scope())) {
            return org.mepcity.kursplatform.iam.application.contract.ActiveSession.globalPlatformAdmin(result.user().id());
        }
        return org.mepcity.kursplatform.iam.application.contract.ActiveSession.organization(
                result.user().id(), result.organizationMembership().organizationId());
    }

    /**
     * Resolves the session in two RLS-visible steps rather than the single JOIN
     * findAuthSessionByAccessTokenHash used to perform: refresh_tokens' own SELECT policies
     * already reference refresh_token_families (for SESSION_REFRESH/SESSION_LOGOUT/platform
     * device-revoke), so a refresh_token_families policy that referenced refresh_tokens back
     * (to bootstrap by access token hash) creates a Postgres "infinite recursion detected in
     * policy" cycle. Reading refresh_tokens by hash first, then revealing app.iam_current_family_id
     * via refreshIamAuthScope before reading refresh_token_families by id, avoids any cross-table
     * subquery in either policy.
     */
    private SessionInfoResult resolveSessionHash(String accessTokenHash) {
        Instant now = clock.instant();
        RefreshToken token = repository.findRefreshTokenByAccessTokenHash(accessTokenHash)
                .filter(t -> !t.isRevoked() && !t.isUsed())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Access token geçersiz."));
        if (token.isExpired(now)) {
            throw new IamException("UNAUTHENTICATED", "Access token süresi dolmuş.");
        }

        transactionExecutor.refreshIamAuthScope(IamAuthScopeContext.bootstrapFamily(token.familyId()));

        RefreshTokenFamily family = repository.findRefreshTokenFamilyById(token.familyId())
                .filter(f -> !f.isRevoked())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Oturum bulunamadı."));

        transactionExecutor.refreshIamAuthScope(
                IamAuthScopeContext.actorAndDevice(family.userId(), family.trustedDeviceId()));

        User user = repository.findUserById(family.userId())
                .orElseThrow(() -> new IamException("UNAUTHENTICATED", "Kullanıcı bulunamadı."));
        if (!user.isActive()) {
            throw new IamException("ACCOUNT_NOT_READY", "Kullanıcı hesabı aktif değil.");
        }

        // Same liveness invariant activation enforces: a device revoked after this session was
        // issued must kill the session on every subsequent read, not just at the next activation.
        TrustedDevice device = repository.findTrustedDeviceById(family.userId(), family.trustedDeviceId())
                .filter(TrustedDevice::isActive)
                .orElseThrow(() -> new IamException("SESSION_REVOKED", "Cihaz iptal edilmiş veya bulunamadı."));

        if (!family.authenticatedAt().isAfter(user.reauthenticationRequiredAfter())) {
            throw new IamException("SESSION_REVOKED", "Kullanıcı oturum eşiği geçersiz.");
        }

        SessionInfoResult.UserDto userDto = new SessionInfoResult.UserDto(
                user.id(), resolveDisplayName(user), user.status().name());
        SessionInfoResult.DeviceDto deviceDto = new SessionInfoResult.DeviceDto(
                device.id(), device.deviceIdentifier(), device.platform().name(),
                device.deviceName(), device.trustedAt());

        SessionScope scope = family.isGlobalPlatformAdmin() ? SessionScope.GLOBAL_PLATFORM_ADMIN : SessionScope.ORGANIZATION;
        SessionInfoResult.PlatformAdministratorDto adminDto;
        SessionInfoResult.OrganizationMembershipDto membershipDto = null;

        if (scope == SessionScope.GLOBAL_PLATFORM_ADMIN) {
            // A global family with no active platform_administrators row is a revoked admin grant
            // outliving its session — the same fail-closed rule IAM-001 requires of activation.
            repository.findActivePlatformAdministratorByUserId(user.id())
                    .orElseThrow(() -> new IamException("SESSION_REVOKED", "Platform yöneticisi ataması iptal edilmiş."));
            adminDto = new SessionInfoResult.PlatformAdministratorDto("ACTIVE");
        } else {
            adminDto = null;
            UUID organizationMembershipId = family.organizationMembershipId();
            OrganizationMembership membership = repository.findOrganizationMembershipByIdAndUserId(
                            organizationMembershipId, user.id())
                    .orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik bulunamadı."));
            ContextSelectionSummary summary = repository.findContextSelectionSummaries(user.id()).stream()
                    .filter(s -> s.id().equals(organizationMembershipId))
                    .findFirst()
                    .orElseThrow(() -> new IamException("SESSION_REVOKED", "Üyelik bulunamadı."));

            if (!membership.isActive()) {
                throw new IamException("SESSION_REVOKED", "Üyelik aktif değil.");
            }
            if (!"ACTIVE".equals(summary.membershipStatus())) {
                throw new IamException("SESSION_REVOKED", "Üyelik aktif değil.");
            }
            if (!"ACTIVE".equals(summary.organizationStatus())) {
                throw new IamException("SESSION_REVOKED", "Kurum durumu oturuma uygun değil.");
            }
            if (summary.roleCodes().isEmpty()) {
                throw new IamException("SESSION_REVOKED", "Aktif rol bulunamadı.");
            }
            if (family.issuedAtSessionGeneration() == null
                    || family.issuedAtSessionGeneration() != membership.sessionGeneration()) {
                throw new IamException("SESSION_REVOKED", "Oturum nesli geçersiz.");
            }
            if (!family.authenticatedAt().isAfter(membership.reauthenticationRequiredAfter())) {
                throw new IamException("SESSION_REVOKED", "Üyelik oturum eşiği geçersiz.");
            }

            membershipDto = new SessionInfoResult.OrganizationMembershipDto(
                    summary.id(), summary.organizationId(), summary.organizationName(),
                    summary.organizationStatus(), summary.membershipStatus(),
                    summary.roleCodes(), summary.sessionGeneration());
        }

        SessionInfoResult.SessionDto sessionDto = new SessionInfoResult.SessionDto(
                scope.name(), token.accessExpiresAt(), family.authenticatedAt());

        return new SessionInfoResult(userDto, adminDto, deviceDto, membershipDto, sessionDto);
    }

    private String resolveDisplayName(User user) {
        return "Kullanıcı " + user.id().toString().substring(0, 8);
    }
}
