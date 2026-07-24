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
import jakarta.servlet.http.HttpServletRequest;
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
                                                   @RequestParam(required = false) String cursor,
                                                   @RequestParam(defaultValue = "50") int limit) {
        var result = devices.list(token(authorization), cursor, limit);
        return ResponseEntity.ok(new DeviceListResponse(result.items().stream()
                .map(item -> DeviceResponse.from(item.device(), item.currentDevice())).toList(),
                new PageResponse(result.nextCursor(), result.hasNextPage())));
    }

    @PostMapping("/devices/{deviceId}/revoke")
    public ResponseEntity<DeviceRevokeResponse> revokeOwn(@RequestHeader("Authorization") String authorization,
                                                           @RequestHeader("Idempotency-Key") String key,
                                                           @PathVariable UUID deviceId, HttpServletRequest request) {
        rejectBody(request);
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokeOwnDevice(token(authorization), deviceId, key);
        return ResponseEntity.ok(new DeviceRevokeResponse(DeviceResponse.from(result.device(), result.currentDevice()), result.currentDevice(), result.revokedRefreshTokenFamilyCount()));
    }

    @PostMapping("/organizations/{organizationId}/memberships/{membershipId}/session-revoke")
    public ResponseEntity<MembershipRevokeResponse> revokeMembership(@RequestHeader("Authorization") String authorization,
                                                                       @RequestHeader("Idempotency-Key") String key,
                                                                       @PathVariable UUID organizationId, @PathVariable UUID membershipId, HttpServletRequest request) {
        rejectBody(request);
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokeOrganizationSessions(token(authorization), organizationId, membershipId, key);
        return ResponseEntity.ok(new MembershipRevokeResponse(result.organizationMembershipId(), result.organizationId(),
                result.sessionGeneration(), result.reauthenticationRequiredAfter(), result.revokedRefreshTokenFamilyCount()));
    }

    @PostMapping("/platform-admin/users/{userId}/devices/{deviceId}/revoke")
    public ResponseEntity<DeviceRevokeResponse> revokePlatform(@RequestHeader("Authorization") String authorization,
                                                                @RequestHeader("Idempotency-Key") String key,
                                                                @PathVariable UUID userId, @PathVariable UUID deviceId, HttpServletRequest request) {
        rejectBody(request);
        IdempotencyKeyValidator.requireValid(key);
        var result = devices.revokePlatformDevice(token(authorization), userId, deviceId, key);
        return ResponseEntity.ok(new DeviceRevokeResponse(DeviceResponse.from(result.device(), false), false, result.revokedRefreshTokenFamilyCount()));
    }

    private String token(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) throw new IamException("UNAUTHENTICATED", "Authorization başlığı geçersiz.");
        return authorization.substring(7);
    }
    private void rejectBody(HttpServletRequest request) {
        try {
            if (request.getContentLengthLong() > 0 || request.getInputStream().read() != -1) {
                throw new IamException("INVALID_REQUEST", "Bu uç nokta istek gövdesi kabul etmez.");
            }
        } catch (java.io.IOException e) { throw new IamException("INVALID_REQUEST", "İstek gövdesi okunamadı."); }
    }
    public record DeviceListResponse(List<DeviceResponse> items, PageResponse page) { }
    public record PageResponse(String nextCursor, boolean hasNextPage) { }
    public record DeviceResponse(UUID id, UUID deviceIdentifier, String platform, String deviceName, java.time.Instant trustedAt, java.time.Instant lastSeenAt, java.time.Instant revokedAt, boolean isCurrentDevice) {
        static DeviceResponse from(org.mepcity.kursplatform.iam.domain.TrustedDevice d) { return from(d, false); }
        static DeviceResponse from(org.mepcity.kursplatform.iam.domain.TrustedDevice d, boolean current) { return new DeviceResponse(d.id(), d.deviceIdentifier(), d.platform().name(), d.deviceName(), d.trustedAt(), d.lastSeenAt(), d.revokedAt(), current); }
    }
    public record DeviceRevokeResponse(DeviceResponse device, boolean isCurrentDevice, int revokedRefreshTokenFamilyCount) { }
    public record MembershipRevokeResponse(UUID organizationMembershipId, UUID organizationId, int sessionGeneration,
                                           java.time.Instant reauthenticationRequiredAfter, int revokedRefreshTokenFamilyCount) { }
}
