package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthSession(
        UUID userId,
        UUID trustedDeviceId,
        UUID refreshTokenFamilyId,
        UUID refreshTokenId,
        SessionScope scope,
        UUID organizationMembershipId,
        Instant authenticatedAt,
        Instant accessExpiresAt
) {

    public boolean isOrganizationScoped() {
        return scope == SessionScope.ORGANIZATION;
    }

    public boolean isGlobalPlatformAdmin() {
        return scope == SessionScope.GLOBAL_PLATFORM_ADMIN;
    }

    public boolean isAccessExpired(Instant now) {
        return !now.isBefore(accessExpiresAt);
    }
}
