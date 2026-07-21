package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record SecretDelivery(
        UUID id,
        UUID providerCommandId,
        UUID recipientActorUserId,
        byte[] encryptedSecret,
        String payloadKeyId,
        SecretDeliveryStatus status,
        Instant createdAt,
        Instant readyAt,
        Instant expiresAt,
        Instant consumedAt
) {

    public boolean isTerminal() {
        return status == SecretDeliveryStatus.CONSUMED || status == SecretDeliveryStatus.EXPIRED;
    }

    public boolean isEscrowed() {
        return status == SecretDeliveryStatus.ESCROWED;
    }

    public boolean isReady() {
        return status == SecretDeliveryStatus.READY;
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
