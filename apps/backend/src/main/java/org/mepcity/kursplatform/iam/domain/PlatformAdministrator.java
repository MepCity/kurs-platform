package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record PlatformAdministrator(
        UUID id,
        UUID userId,
        UUID grantedByUserId,
        Instant grantedAt,
        Instant revokedAt
) {

    public boolean isActive() {
        return revokedAt == null;
    }
}
