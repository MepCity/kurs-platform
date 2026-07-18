package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record ContextSelectionToken(
        UUID id,
        UUID userId,
        UUID trustedDeviceId,
        String tokenHash,
        Instant authenticatedAt,
        Instant issuedAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
) {

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isUsable(Instant now) {
        return !isConsumed() && !isRevoked() && !isExpired(now);
    }
}
