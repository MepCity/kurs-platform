package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionActivationResult(
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

    public record SessionDto(
            String scope,
            OpaqueToken accessToken,
            OpaqueToken refreshToken,
            String tokenType,
            Instant expiresAt,
            Instant refreshExpiresAt,
            Instant authenticatedAt
    ) {
    }
}
