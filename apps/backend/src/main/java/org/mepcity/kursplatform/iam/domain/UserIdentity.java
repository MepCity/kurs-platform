package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record UserIdentity(
        UUID id,
        UUID userId,
        String issuer,
        String subject,
        Instant createdAt,
        Instant disabledAt
) {

    public boolean isDisabled() {
        return disabledAt != null;
    }
}
