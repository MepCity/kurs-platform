package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthReplayEscrowTests {

    @Test
    void isReadyReturnsTrueOnlyForReadyStatus() {
        AuthReplayEscrow ready = createEscrow(EscrowStatus.READY);
        AuthReplayEscrow expired = createEscrow(EscrowStatus.EXPIRED);
        AuthReplayEscrow revoked = createEscrow(EscrowStatus.REVOKED);
        assertThat(ready.isReady()).isTrue();
        assertThat(expired.isReady()).isFalse();
        assertThat(revoked.isReady()).isFalse();
    }

    @Test
    void isTerminalReturnsTrueForExpiredAndRevoked() {
        AuthReplayEscrow expired = createEscrow(EscrowStatus.EXPIRED);
        AuthReplayEscrow revoked = createEscrow(EscrowStatus.REVOKED);
        assertThat(expired.isTerminal()).isTrue();
        assertThat(revoked.isTerminal()).isTrue();
    }

    @Test
    void isExpiredReturnsTrueWhenNowIsAfterExpiresAt() {
        AuthReplayEscrow escrow = new AuthReplayEscrow(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PROVIDER_TOKEN_EXCHANGE", UUID.randomUUID(), "fp",
                UUID.randomUUID(), null, null,
                new byte[]{1}, "key-ref", new byte[]{1}, "aad",
                EscrowStatus.READY, Instant.now().minusSeconds(60), Instant.now(), null);
        assertThat(escrow.isExpired(Instant.now())).isTrue();
    }

    private AuthReplayEscrow createEscrow(EscrowStatus status) {
        return new AuthReplayEscrow(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "PROVIDER_TOKEN_EXCHANGE", UUID.randomUUID(), "fp",
                UUID.randomUUID(), null, null,
                new byte[]{1}, "key-ref", new byte[]{1}, "aad",
                status, Instant.now().plusSeconds(300), Instant.now(), null);
    }
}
