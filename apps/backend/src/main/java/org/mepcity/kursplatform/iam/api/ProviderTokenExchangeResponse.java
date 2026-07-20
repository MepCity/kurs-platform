package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeResult;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ProviderTokenExchangeResponse(
        String contextSelectionToken,
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

    public record DeviceDto(UUID id, UUID deviceIdentifier, String platform, String deviceName, Instant trustedAt) {
    }

    public static ProviderTokenExchangeResponse from(ProviderTokenExchangeResult result) {
        return new ProviderTokenExchangeResponse(
                result.contextSelectionToken().value(),
                result.contextSelectionTokenExpiresAt(),
                result.availableScopes(),
                new UserDto(result.user().id(), result.user().displayName(), result.user().status()),
                result.platformAdministrator() != null
                        ? new PlatformAdministratorDto(result.platformAdministrator().status()) : null,
                new DeviceDto(
                        result.device().id(), result.device().deviceIdentifier(),
                        result.device().platform().name(), result.device().deviceName(),
                        result.device().trustedAt()));
    }
}
