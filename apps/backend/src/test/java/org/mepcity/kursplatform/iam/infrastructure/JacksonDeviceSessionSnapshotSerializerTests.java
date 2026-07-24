package org.mepcity.kursplatform.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.DeviceSessionService.DeviceRevokeResult;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;

class JacksonDeviceSessionSnapshotSerializerTests {
    private final JacksonDeviceSessionSnapshotSerializer serializer =
            new JacksonDeviceSessionSnapshotSerializer(new ObjectMapper().findAndRegisterModules());

    @Test
    void roundTripsTypedDeviceResultAndMarksReplay() {
        TrustedDevice device = new TrustedDevice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pixel", DevicePlatform.ANDROID,
                Instant.parse("2026-07-24T12:00:00Z"), Instant.parse("2026-07-24T12:01:00Z"), Instant.parse("2026-07-24T12:02:00Z"));

        DeviceRevokeResult replay = serializer.readDeviceRevoke(serializer.serialize(new DeviceRevokeResult(device, 2, true, false)));

        assertThat(replay.device()).isEqualTo(device);
        assertThat(replay.revokedRefreshTokenFamilyCount()).isEqualTo(2);
        assertThat(replay.currentDevice()).isTrue();
        assertThat(replay.replayed()).isTrue();
    }

    @Test
    void rejectsUnknownOrWrongSnapshotTypeFailClosed() {
        assertThatThrownBy(() -> serializer.readDeviceRevoke("{\"version\":1,\"kind\":\"membership-revoke\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> serializer.readDeviceRevoke("not-json")).isInstanceOf(IllegalArgumentException.class);
    }
}
