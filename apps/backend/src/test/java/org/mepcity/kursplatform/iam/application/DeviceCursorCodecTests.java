package org.mepcity.kursplatform.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.infrastructure.HmacSha256TokenHasher;

class DeviceCursorCodecTests {
    private static final Instant NOW = Instant.parse("2026-07-24T12:00:00Z");
    private final HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("cursor-test-secret-at-least-16-chars");

    @Test
    void encryptsPositionAndBindsItToActorAndLimit() {
        UUID actor = UUID.randomUUID();
        UUID device = UUID.randomUUID();
        DeviceCursorCodec codec = new DeviceCursorCodec(hasher, NOW, Duration.ofMinutes(5));

        String cursor = codec.encode(actor, 20, NOW.minusSeconds(1), device);

        assertThat(cursor).doesNotContain(actor.toString()).doesNotContain(device.toString()).doesNotContain("2026-07-24");
        assertThat(codec.decode(actor, 20, cursor)).isEqualTo(new DeviceCursorCodec.Cursor(NOW.minusSeconds(1), device));
        assertInvalid(() -> codec.decode(UUID.randomUUID(), 20, cursor));
        assertInvalid(() -> codec.decode(actor, 21, cursor));
    }

    @Test
    void rejectsTamperedAndExpiredCursors() {
        UUID actor = UUID.randomUUID();
        String cursor = new DeviceCursorCodec(hasher, NOW, Duration.ofSeconds(1))
                .encode(actor, 10, NOW, UUID.randomUUID());

        assertInvalid(() -> new DeviceCursorCodec(hasher, NOW, Duration.ofMinutes(1)).decode(actor, 10,
                cursor.substring(0, cursor.length() - 1) + "A"));
        assertInvalid(() -> new DeviceCursorCodec(hasher, NOW.plusSeconds(2), Duration.ofMinutes(1)).decode(actor, 10, cursor));
    }

    @Test
    void rejectsAnyPrefixTamperingAndMalformedLengthsBecauseExpiryIsAuthenticated() {
        UUID actor = UUID.randomUUID();
        DeviceCursorCodec codec = new DeviceCursorCodec(hasher, NOW, Duration.ofMinutes(5));
        byte[] raw = java.util.Base64.getUrlDecoder().decode(codec.encode(actor, 10, NOW, UUID.randomUUID()));

        raw[0] ^= 1; // the first byte is now nonce, never an unauthenticated expiry prefix
        assertInvalid(() -> codec.decode(actor, 10, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw)));
        assertInvalid(() -> codec.decode(actor, 10, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[12])));
        assertInvalid(() -> codec.decode(actor, 10, java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[13])));
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("INVALID_CURSOR");
    }
}
