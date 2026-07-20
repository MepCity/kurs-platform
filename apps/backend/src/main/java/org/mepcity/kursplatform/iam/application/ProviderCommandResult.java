package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;

import java.time.Instant;
import java.util.UUID;

public record ProviderCommandResult(
        UUID id,
        String idempotencyKey,
        String provider,
        ProviderCommandType commandType,
        UUID targetUserId,
        UUID targetIdentityId,
        UUID organizationId,
        ProviderCommandStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        Instant leaseExpiresAt,
        long fencingToken,
        String leaseOwner,
        Instant createdAt,
        Instant completedAt,
        String lastSafeErrorCode
) {
}
