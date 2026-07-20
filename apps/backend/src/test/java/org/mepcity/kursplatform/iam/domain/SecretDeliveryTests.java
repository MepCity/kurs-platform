package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SecretDeliveryTests {

    @Test
    void isTerminalReturnsTrueForConsumedAndExpired() {
        SecretDelivery consumed = createDelivery(SecretDeliveryStatus.CONSUMED);
        SecretDelivery expired = createDelivery(SecretDeliveryStatus.EXPIRED);
        assertThat(consumed.isTerminal()).isTrue();
        assertThat(expired.isTerminal()).isTrue();
    }

    @Test
    void isTerminalReturnsFalseForEscrowedAndReady() {
        SecretDelivery escrowed = createDelivery(SecretDeliveryStatus.ESCROWED);
        SecretDelivery ready = createDelivery(SecretDeliveryStatus.READY);
        assertThat(escrowed.isTerminal()).isFalse();
        assertThat(ready.isTerminal()).isFalse();
    }

    @Test
    void isExpiredReturnsTrueWhenNowIsAfterExpiresAt() {
        Instant past = Instant.now().minusSeconds(60);
        SecretDelivery delivery = new SecretDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new byte[]{1}, "key-1", SecretDeliveryStatus.ESCROWED,
                Instant.now().minusSeconds(120), null, past, null);
        assertThat(delivery.isExpired(Instant.now())).isTrue();
    }

    @Test
    void isExpiredReturnsFalseWhenNowIsBeforeExpiresAt() {
        Instant future = Instant.now().plusSeconds(300);
        SecretDelivery delivery = new SecretDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new byte[]{1}, "key-1", SecretDeliveryStatus.ESCROWED,
                Instant.now().minusSeconds(10), null, future, null);
        assertThat(delivery.isExpired(Instant.now())).isFalse();
    }

    private SecretDelivery createDelivery(SecretDeliveryStatus status) {
        return new SecretDelivery(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new byte[]{1}, "key-1", status,
                Instant.now(), null, Instant.now().plusSeconds(300), null);
    }
}
