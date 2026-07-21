package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;

public record CognitoTokenClaims(
        String issuer,
        String subject,
        String clientId,
        String tokenUse,
        Instant authTime,
        Instant expiresAt,
        Instant notBefore,
        String rawTokenFingerprint
) {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }

    public boolean isNotYetValid(Instant now) {
        return notBefore != null && now.isBefore(notBefore);
    }
}
