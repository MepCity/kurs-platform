package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.SessionRefreshResult;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record SessionRefreshResponse(OrganizationMembershipDto organizationMembership, SessionDto session) {
    public record OrganizationMembershipDto(UUID id, UUID organizationId, String organizationName,
                                            String organizationStatus, String membershipStatus,
                                            Set<String> roleCodes, int sessionGeneration) { }
    public record SessionDto(String scope, String accessToken, String refreshToken, String tokenType,
                             Instant expiresAt, Instant refreshExpiresAt, Instant authenticatedAt) { }
    public static SessionRefreshResponse from(SessionRefreshResult result) {
        var m = result.organizationMembership();
        var s = result.session();
        return new SessionRefreshResponse(m == null ? null : new OrganizationMembershipDto(m.id(), m.organizationId(),
                m.organizationName(), m.organizationStatus(), m.membershipStatus(), m.roleCodes(), m.sessionGeneration()),
                new SessionDto(s.scope(), s.accessToken().value(), s.refreshToken().value(), s.tokenType(),
                        s.expiresAt(), s.refreshExpiresAt(), s.authenticatedAt()));
    }
}
