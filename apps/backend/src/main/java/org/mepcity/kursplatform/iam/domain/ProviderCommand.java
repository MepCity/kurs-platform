package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record ProviderCommand(
        UUID id,
        String idempotencyKey,
        String provider,
        ProviderCommandType commandType,
        UUID targetUserId,
        UUID targetIdentityId,
        UUID organizationId,
        String usernameLookupHash,
        String payloadFingerprint,
        byte[] encryptedCommandPayload,
        String payloadKeyId,
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

    public boolean isTerminal() {
        return status == ProviderCommandStatus.COMPLETED || status == ProviderCommandStatus.FAILED;
    }

    public boolean isPending() {
        return status == ProviderCommandStatus.PENDING;
    }

    public boolean isClaimed() {
        return status == ProviderCommandStatus.CLAIMED;
    }

    public boolean isUserLifecycleCommand() {
        return commandType == ProviderCommandType.USER_DISABLE
                || commandType == ProviderCommandType.USER_LOGOUT
                || commandType == ProviderCommandType.PASSWORD_RESET;
    }

    public boolean isProvisioningCommand() {
        return commandType == ProviderCommandType.TEACHER_ACCOUNT_CREATE;
    }
}
