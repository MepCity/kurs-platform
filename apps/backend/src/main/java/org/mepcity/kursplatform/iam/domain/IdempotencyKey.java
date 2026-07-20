package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record IdempotencyKey(
        UUID id,
        IdempotencyScope scopeType,
        UUID organizationId,
        UUID userId,
        String clientMutationId,
        String operationType,
        String requestFingerprint,
        IdempotencyStatus status,
        UUID resultEntityId,
        Short terminalHttpStatus,
        String terminalErrorCode,
        String resultPayload,
        String resultReference,
        String leaseOwner,
        Long leaseGeneration,
        Instant leaseExpiresAt,
        Instant createdAt,
        Instant completedAt,
        Instant resultExpiresAt,
        Instant keyRetentionExpiresAt
) {

    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == IdempotencyStatus.FAILED;
    }

    public boolean isPending() {
        return status == IdempotencyStatus.PENDING;
    }

    public boolean hasResult() {
        return resultReference != null || resultPayload != null;
    }

    public boolean isResultExpired(Instant now) {
        return resultExpiresAt != null && !now.isBefore(resultExpiresAt);
    }
}
