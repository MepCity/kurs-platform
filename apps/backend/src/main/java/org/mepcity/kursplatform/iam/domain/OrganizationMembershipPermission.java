package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMembershipPermission(
        UUID id,
        UUID organizationId,
        UUID targetMembershipRoleId,
        MembershipRole targetRoleCode,
        String permissionCode,
        UUID grantedByMembershipRoleId,
        MembershipRole grantedRoleCode,
        UUID grantedByPlatformAdminUserId,
        Instant grantedAt,
        Instant revokedAt
) {

    public boolean isActive() {
        return revokedAt == null;
    }
}
