package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.SessionInfoResult;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionInfoResponse(
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

    public static SessionInfoResponse from(SessionInfoResult result) {
        return new SessionInfoResponse(
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
                new SessionDto(result.session().scope(), result.session().expiresAt(),
                        result.session().authenticatedAt()));
    }
}
