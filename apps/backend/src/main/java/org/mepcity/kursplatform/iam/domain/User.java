package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record User(
        UUID id,
        UserStatus status,
        Instant reauthenticationRequiredAfter,
        Instant createdAt,
        Instant updatedAt,
        int rowVersion,
        UUID createdByUserId,
        UUID updatedByUserId
) {

    public static final Instant EPOCH = Instant.EPOCH;

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean isProvisioning() {
        return status == UserStatus.PROVISIONING;
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }
}
