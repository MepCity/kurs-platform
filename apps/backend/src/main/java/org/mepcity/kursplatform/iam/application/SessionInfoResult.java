package org.mepcity.kursplatform.iam.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionInfoResult(
        UserDto user,
        PlatformAdministratorDto platformAdministrator,
        DeviceDto device,
        OrganizationMembershipDto organizationMembership,
        SessionDto session
) {

    public record UserDto(UUID id, String displayName, String status) {
    }

    public record PlatformAdministratorDto(String status) {
    }

    public record DeviceDto(UUID id, UUID deviceIdentifier, String platform, String deviceName, Instant trustedAt) {
    }

    public record OrganizationMembershipDto(
            UUID id,
            UUID organizationId,
            String organizationName,
            String organizationStatus,
            String membershipStatus,
            Set<String> roleCodes,
            int sessionGeneration
    ) {
    }

    public record SessionDto(String scope, Instant expiresAt, Instant authenticatedAt) {
    }
}
