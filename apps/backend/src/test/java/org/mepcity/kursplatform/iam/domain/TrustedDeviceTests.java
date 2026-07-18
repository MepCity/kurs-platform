package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TrustedDeviceTests {

    @Test
    void deviceWithoutRevokedAtIsActive() {
        var device = createDevice(null);
        assertThat(device.isActive()).isTrue();
    }

    @Test
    void deviceWithRevokedAtIsNotActive() {
        var device = createDevice(Instant.now());
        assertThat(device.isActive()).isFalse();
    }

    private TrustedDevice createDevice(Instant revokedAt) {
        return new TrustedDevice(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "Pixel 8", DevicePlatform.ANDROID,
                Instant.now(), null, revokedAt);
    }
}
