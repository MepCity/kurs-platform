package org.mepcity.kursplatform.iam.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.mepcity.kursplatform.iam.application.DeviceSessionService.DeviceRevokeResult;
import org.mepcity.kursplatform.iam.application.DeviceSessionService.MembershipRevokeResult;
import org.mepcity.kursplatform.iam.application.DeviceSessionSnapshotSerializer;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;

/** Jackson is deliberately confined to infrastructure; unknown or malformed snapshots fail closed. */
final class JacksonDeviceSessionSnapshotSerializer implements DeviceSessionSnapshotSerializer {
    private static final int VERSION = 1;
    private final ObjectMapper mapper;

    JacksonDeviceSessionSnapshotSerializer(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override public String serialize(DeviceRevokeResult value) {
        TrustedDevice d = value.device();
        return write(new DeviceSnapshot(VERSION, "device-revoke", d.id(), d.userId(), d.deviceIdentifier(), d.deviceName(),
                d.platform(), d.trustedAt().toEpochMilli(), d.lastSeenAt().toEpochMilli(),
                d.revokedAt() == null ? null : d.revokedAt().toEpochMilli(), value.revokedRefreshTokenFamilyCount(), value.currentDevice()));
    }

    @Override public String serialize(MembershipRevokeResult value) {
        return write(new MembershipSnapshot(VERSION, "membership-revoke", value.organizationMembershipId(), value.organizationId(),
                value.sessionGeneration(), value.reauthenticationRequiredAfter().toEpochMilli(), value.revokedRefreshTokenFamilyCount()));
    }

    @Override public DeviceRevokeResult readDeviceRevoke(String payload) {
        DeviceSnapshot s = read(payload, DeviceSnapshot.class);
        if (s.version != VERSION || !"device-revoke".equals(s.kind) || s.id == null || s.userId == null || s.deviceIdentifier == null
                || s.deviceName == null || s.platform == null || s.trustedAtEpochMillis <= 0 || s.lastSeenAtEpochMillis <= 0 || s.familyCount < 0) throw invalid();
        return new DeviceRevokeResult(new TrustedDevice(s.id, s.userId, s.deviceIdentifier, s.deviceName, s.platform,
                java.time.Instant.ofEpochMilli(s.trustedAtEpochMillis), java.time.Instant.ofEpochMilli(s.lastSeenAtEpochMillis),
                s.revokedAtEpochMillis == null ? null : java.time.Instant.ofEpochMilli(s.revokedAtEpochMillis)), s.familyCount, s.currentDevice, true);
    }

    @Override public MembershipRevokeResult readMembershipRevoke(String payload) {
        MembershipSnapshot s = read(payload, MembershipSnapshot.class);
        if (s.version != VERSION || !"membership-revoke".equals(s.kind) || s.membershipId == null || s.organizationId == null
                || s.sessionGeneration < 0 || s.reauthenticationRequiredAfterEpochMillis <= 0 || s.familyCount < 0) throw invalid();
        return new MembershipRevokeResult(s.membershipId, s.organizationId, s.sessionGeneration,
                java.time.Instant.ofEpochMilli(s.reauthenticationRequiredAfterEpochMillis), s.familyCount);
    }

    private String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Snapshot yazılamadı."); }
    }
    private <T> T read(String payload, Class<T> type) {
        try { return mapper.readValue(payload, type); }
        catch (Exception exception) { throw invalid(); }
    }
    private IllegalArgumentException invalid() { return new IllegalArgumentException("Geçersiz snapshot."); }

    private record DeviceSnapshot(int version, String kind, UUID id, UUID userId, UUID deviceIdentifier, String deviceName,
                                  DevicePlatform platform, long trustedAtEpochMillis, long lastSeenAtEpochMillis, Long revokedAtEpochMillis,
                                  int familyCount, boolean currentDevice) { }
    private record MembershipSnapshot(int version, String kind, UUID membershipId, UUID organizationId, int sessionGeneration,
                                      long reauthenticationRequiredAfterEpochMillis, int familyCount) { }
}
