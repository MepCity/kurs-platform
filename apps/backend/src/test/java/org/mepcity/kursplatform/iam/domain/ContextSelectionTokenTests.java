package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContextSelectionTokenTests {

    @Test
    void freshTokenIsUsable() {
        var token = createToken(null, null, Instant.now().plus(5, ChronoUnit.MINUTES));
        assertThat(token.isUsable(Instant.now())).isTrue();
    }

    @Test
    void consumedTokenIsNotUsable() {
        var token = createToken(Instant.now(), null, Instant.now().plus(5, ChronoUnit.MINUTES));
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void revokedTokenIsNotUsable() {
        var token = createToken(null, Instant.now(), Instant.now().plus(5, ChronoUnit.MINUTES));
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    @Test
    void expiredTokenIsNotUsable() {
        var token = createToken(null, null, Instant.now().minus(1, ChronoUnit.MINUTES));
        assertThat(token.isUsable(Instant.now())).isFalse();
    }

    private ContextSelectionToken createToken(Instant consumedAt, Instant revokedAt, Instant expiresAt) {
        return new ContextSelectionToken(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "hash", Instant.now(), Instant.now(),
                expiresAt, consumedAt, revokedAt);
    }
}
