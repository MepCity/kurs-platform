package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record AuthReplayEscrow(
        UUID id,
        UUID idempotencyKeyId,
        UUID actorUserId,
        String operationType,
        UUID deviceIdentifier,
        String tokenFingerprint,
        UUID resultContextTokenId,
        UUID resultRefreshTokenFamilyId,
        UUID resultRefreshTokenId,
        byte[] ciphertext,
        String aeadKeyReference,
        byte[] aeadNonce,
        String aadContext,
        EscrowStatus status,
        Instant expiresAt,
        Instant createdAt,
        Instant deletedAt
) {

    public boolean isReady() {
        return status == EscrowStatus.READY;
    }

    public boolean isTerminal() {
        return status == EscrowStatus.EXPIRED || status == EscrowStatus.REVOKED;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
