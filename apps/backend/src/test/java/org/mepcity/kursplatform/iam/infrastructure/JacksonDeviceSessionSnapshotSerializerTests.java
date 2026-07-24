package org.mepcity.kursplatform.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.DeviceSessionService.DeviceRevokeResult;
import org.mepcity.kursplatform.iam.application.DeviceSessionService.MembershipRevokeResult;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;

class JacksonDeviceSessionSnapshotSerializerTests {
    private final JacksonDeviceSessionSnapshotSerializer serializer =
            new JacksonDeviceSessionSnapshotSerializer(new ObjectMapper().findAndRegisterModules());

    @Test
    void roundTripsTypedDeviceResultAndMarksReplay() {
        TrustedDevice device = new TrustedDevice(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Pixel", DevicePlatform.ANDROID,
                Instant.parse("2026-07-24T12:00:00.123456Z"),
                Instant.parse("2026-07-24T12:01:00.654321Z"),
                Instant.parse("2026-07-24T12:02:00.987654Z"));

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

    @Test
    void deviceSnapshotRejectsMissingUnknownNegativeAndInvalidTypedFields() {
        String id = UUID.randomUUID().toString();
        String user = UUID.randomUUID().toString();
        String identifier = UUID.randomUUID().toString();
        String base = "{\"version\":1,\"kind\":\"device-revoke\",\"id\":\"" + id + "\",\"userId\":\"" + user
                + "\",\"deviceIdentifier\":\"" + identifier + "\",\"deviceName\":\"x\",\"platform\":\"ANDROID\","
                + "\"trustedAt\":\"2026-07-24T12:00:00.123456Z\",\"lastSeenAt\":\"2026-07-24T12:01:00.654321Z\","
                + "\"familyCount\":0,\"currentDevice\":false";
        String[] invalid = {"{\"version\":1,\"kind\":\"device-revoke\"}", base + ",\"familyCount\":-1}",
                base + ",\"unknown\":true}", base.replace(id, "not-a-uuid") + "}",
                base.replace("2026-07-24T12:00:00.123456Z", "not-an-instant") + "}"};
        for (String payload : invalid) {
            assertThatThrownBy(() -> serializer.readDeviceRevoke(payload)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void roundTripsMembershipSnapshotAndRejectsMalformedPayloadsFailClosed() {
        MembershipRevokeResult value = new MembershipRevokeResult(UUID.randomUUID(), UUID.randomUUID(), 3,
                Instant.parse("2026-07-24T12:02:00.987654Z"), 2);
        assertThat(serializer.readMembershipRevoke(serializer.serialize(value))).isEqualTo(value);

        String[] invalid = {
                "{\"version\":2,\"kind\":\"membership-revoke\"}",
                "{\"version\":1,\"kind\":\"device-revoke\"}",
                "{\"version\":1,\"kind\":\"membership-revoke\",\"membershipId\":\"not-a-uuid\"}",
                "{\"version\":1,\"kind\":\"membership-revoke\",\"membershipId\":\"" + UUID.randomUUID() + "\",\"organizationId\":\"" + UUID.randomUUID() + "\",\"sessionGeneration\":-1,\"reauthenticationRequiredAfterEpochMillis\":1,\"familyCount\":0}",
                "{\"version\":1,\"kind\":\"membership-revoke\",\"unexpected\":true}"
        };
        for (String payload : invalid) {
            assertThatThrownBy(() -> serializer.readMembershipRevoke(payload)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
