package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshTokenFamily(
        UUID id,
        UUID userId,
        UUID trustedDeviceId,
        UUID organizationMembershipId,
        Instant authenticatedAt,
        Integer issuedAtSessionGeneration,
        Instant revokedAt,
        Instant createdAt
) {

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isOrganizationScoped() {
        return organizationMembershipId != null;
    }

    public boolean isGlobalPlatformAdmin() {
        return organizationMembershipId == null;
    }
}
