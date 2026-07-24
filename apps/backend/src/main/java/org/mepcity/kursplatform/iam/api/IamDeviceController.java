package org.mepcity.kursplatform.iam.api;

import java.util.List;
import java.util.UUID;
import org.mepcity.kursplatform.iam.application.DeviceSessionService;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;

/** HTTP surface fixed by IAM_CIHAZ_VE_OTURUM_IPTALI_SOZLESMESI.md §§7–10. */
@RestController
@ConditionalOnBean(DataSource.class)
@RequestMapping("/api/v1/iam")
public final class IamDeviceController {
    private final DeviceSessionService devices;
    public IamDeviceController(DeviceSessionService devices) { this.devices = devices; }

    @GetMapping("/devices")
    public ResponseEntity<DeviceListResponse> list(@RequestHeader("Authorization") String authorization,
                                                   @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(new DeviceListResponse(devices.list(token(authorization), limit).stream().map(DeviceResponse::from).toList()));
    }

    @PostMapping("/devices/{deviceId}/revoke")
    public ResponseEntity<DeviceRevokeResponse> revokeOwn(@RequestHeader("Authorization") String authorization,
                                                           @RequestHeader("Idempotency-Key") String key,
                                                           @PathVariable UUID deviceId) {
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokeOwnDevice(token(authorization), deviceId, key);
        return ResponseEntity.ok(new DeviceRevokeResponse(DeviceResponse.from(result.device()), result.currentDevice(), result.revokedRefreshTokenFamilyCount(), result.replayed()));
    }

    @PostMapping("/organizations/{organizationId}/memberships/{membershipId}/session-revoke")
    public ResponseEntity<MembershipRevokeResponse> revokeMembership(@RequestHeader("Authorization") String authorization,
                                                                       @RequestHeader("Idempotency-Key") String key,
                                                                       @PathVariable UUID organizationId, @PathVariable UUID membershipId) {
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokeOrganizationSessions(token(authorization), organizationId, membershipId, key);
        return ResponseEntity.ok(new MembershipRevokeResponse(result.organizationMembershipId(), result.revokedRefreshTokenFamilyCount(), result.replayed()));
    }

    @PostMapping("/platform-admin/users/{userId}/devices/{deviceId}/revoke")
    public ResponseEntity<DeviceRevokeResponse> revokePlatform(@RequestHeader("Authorization") String authorization,
                                                                @RequestHeader("Idempotency-Key") String key,
                                                                @PathVariable UUID userId, @PathVariable UUID deviceId) {
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokePlatformDevice(token(authorization), userId, deviceId, key);
        return ResponseEntity.ok(new DeviceRevokeResponse(DeviceResponse.from(result.device()), false, result.revokedRefreshTokenFamilyCount(), result.replayed()));
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IamException("UNAUTHENTICATED", "Authorization başlığı geçersiz.");
        return authorization.substring(7);
    }
    public record DeviceListResponse(List<DeviceResponse> items) { }
    public record DeviceResponse(UUID id, UUID deviceIdentifier, String platform, String deviceName, java.time.Instant trustedAt, java.time.Instant lastSeenAt) {
        static DeviceResponse from(org.mepcity.kursplatform.iam.domain.TrustedDevice d) { return new DeviceResponse(d.id(), d.deviceIdentifier(), d.platform().name(), d.deviceName(), d.trustedAt(), d.lastSeenAt()); }
    }
    public record DeviceRevokeResponse(DeviceResponse device, boolean isCurrentDevice, int revokedRefreshTokenFamilyCount, boolean replayed) { }
    public record MembershipRevokeResponse(UUID organizationMembershipId, int revokedRefreshTokenFamilyCount, boolean replayed) { }
}
