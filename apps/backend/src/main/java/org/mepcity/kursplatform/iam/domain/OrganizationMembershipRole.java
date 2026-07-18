package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMembershipRole(
        UUID id,
        UUID organizationMembershipId,
        UUID organizationId,
        MembershipRole role,
        UUID grantedByUserId,
        Instant grantedAt,
        Instant revokedAt
) {

    public boolean isActive() {
        return revokedAt == null;
    }
}
