package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTests {

    @Test
    void tokenWithoutUsedAtIsNotUsed() {
        var token = createToken(null, null);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void tokenWithUsedAtIsUsed() {
        var token = createToken(Instant.now(), null);
        assertThat(token.isUsed()).isTrue();
    }

    @Test
    void tokenWithoutRevokedAtIsNotRevoked() {
        var token = createToken(null, null);
        assertThat(token.isRevoked()).isFalse();
    }

    @Test
    void tokenWithRevokedAtIsRevoked() {
        var token = createToken(null, Instant.now());
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void tokenPastExpiresAtIsExpired() {
        var pastExpiry = Instant.now().minus(1, ChronoUnit.HOURS);
        var token = createToken(null, null, pastExpiry);
        assertThat(token.isExpired(Instant.now())).isTrue();
    }

    @Test
    void tokenBeforeExpiresAtIsNotExpired() {
        var futureExpiry = Instant.now().plus(1, ChronoUnit.HOURS);
        var token = createToken(null, null, futureExpiry);
        assertThat(token.isExpired(Instant.now())).isFalse();
    }

    private RefreshToken createToken(Instant usedAt, Instant revokedAt) {
        return createToken(usedAt, revokedAt, Instant.now().plus(30, ChronoUnit.DAYS));
    }

    private RefreshToken createToken(Instant usedAt, Instant revokedAt, Instant expiresAt) {
        return new RefreshToken(UUID.randomUUID(), UUID.randomUUID(), null,
                "token-hash", "access-hash",
                Instant.now().plus(10, ChronoUnit.MINUTES),
                Instant.now(), usedAt, expiresAt, revokedAt);
    }
}
