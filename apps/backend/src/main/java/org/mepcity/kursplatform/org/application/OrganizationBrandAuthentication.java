package org.mepcity.kursplatform.org.application;

import java.util.UUID;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;

/** ORG adapter over IAM's published credential boundary. */
public final class OrganizationBrandAuthentication {
    private final ActiveSessionResolver sessions;

    public OrganizationBrandAuthentication(ActiveSessionResolver sessions) {
        this.sessions = sessions;
    }

    public Actor authenticate(String bearerToken, UUID organizationId) {
        CredentialResolution resolution;
        try {
            resolution = sessions.resolveCredential(bearerToken);
        } catch (CredentialAuthenticationException exception) {
            throw new OrganizationCredentialException(OrganizationCredentialException.Code.valueOf(exception.code()));
        }
        if (resolution.kind() == CredentialResolution.Kind.CONTEXT_SELECTION) {
            throw new OrganizationCredentialException(OrganizationCredentialException.Code.ORGANIZATION_CONTEXT_REQUIRED);
        }
        ActiveSession actor = resolution.activeSession();
        if (!actor.isGlobalPlatformAdmin() && !organizationId.equals(actor.organizationId())) {
            throw new ForbiddenException();
        }
        return new Actor(actor.userId(), actor.isGlobalPlatformAdmin());
    }

    public record Actor(UUID userId, boolean platformAdministrator) { }
}
