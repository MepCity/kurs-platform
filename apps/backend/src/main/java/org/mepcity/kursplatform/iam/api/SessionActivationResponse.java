package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.SessionActivationResult;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionActivationResponse(
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
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant expiresAt,
            Instant refreshExpiresAt,
            Instant authenticatedAt
    ) {
    }

    public static SessionActivationResponse from(SessionActivationResult result) {
        return new SessionActivationResponse(
                new UserDto(result.user().id(), result.user().displayName(), result.user().status()),
                result.platformAdministrator() != null
                        ? new PlatformAdministratorDto(result.platformAdministrator().status()) : null,
                new DeviceDto(
                        result.device().id(), result.device().deviceIdentifier(),
                        result.device().platform(), result.device().deviceName(), result.device().trustedAt()),
                result.organizationMembership() != null
                        ? new OrganizationMembershipDto(
                                result.organizationMembership().id(),
                                result.organizationMembership().organizationId(),
                                result.organizationMembership().organizationName(),
                                result.organizationMembership().organizationStatus(),
                                result.organizationMembership().membershipStatus(),
                                result.organizationMembership().roleCodes(),
                                result.organizationMembership().sessionGeneration())
                        : null,
                new SessionDto(
                        result.session().scope(),
                        result.session().accessToken().value(),
                        result.session().refreshToken().value(),
                        result.session().tokenType(),
                        result.session().expiresAt(),
                        result.session().refreshExpiresAt(),
                        result.session().authenticatedAt()));
    }
}
