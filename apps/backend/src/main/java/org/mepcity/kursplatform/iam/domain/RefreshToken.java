package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(
        UUID id,
        UUID familyId,
        UUID previousRefreshTokenId,
        String tokenHash,
        String accessTokenHash,
        Instant accessExpiresAt,
        Instant issuedAt,
        Instant usedAt,
        Instant expiresAt,
        Instant revokedAt
) {

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
