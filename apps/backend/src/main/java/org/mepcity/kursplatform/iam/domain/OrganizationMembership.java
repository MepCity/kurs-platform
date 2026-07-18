package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMembership(
        UUID id,
        UUID organizationId,
        UUID userId,
        UUID personId,
        UserStatus status,
        int sessionGeneration,
        Instant reauthenticationRequiredAfter,
        UUID grantedByUserId,
        Instant grantedAt
) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isProvisioning() {
        return status == UserStatus.PROVISIONING;
    }
}
