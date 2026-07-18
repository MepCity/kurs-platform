package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record TrustedDevice(
        UUID id,
        UUID userId,
        UUID deviceIdentifier,
        String deviceName,
        DevicePlatform platform,
        Instant trustedAt,
        Instant lastSeenAt,
        Instant revokedAt
) {

    public boolean isActive() {
        return revokedAt == null;
    }
}
