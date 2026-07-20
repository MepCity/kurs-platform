package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProviderTokenExchangeResult(
        OpaqueToken contextSelectionToken,
        Instant contextSelectionTokenExpiresAt,
        Set<String> availableScopes,
        UserDto user,
        PlatformAdministratorDto platformAdministrator,
        DeviceDto device
) {

    public record UserDto(UUID id, String displayName, String status) {
    }

    public record PlatformAdministratorDto(String status) {
    }

    public record DeviceDto(UUID id, UUID deviceIdentifier, DevicePlatform platform, String deviceName, Instant trustedAt) {
    }
}
