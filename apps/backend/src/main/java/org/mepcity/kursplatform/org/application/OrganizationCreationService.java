package org.mepcity.kursplatform.org.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;

/** Application boundary for the platform-admin organization creation command. */
public class OrganizationCreationService {
    private static final Duration LEASE_TTL = Duration.ofMinutes(5);
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(30);
    private final OrganizationLifecycleService lifecycleService;
    private final ActiveSessionResolver sessionResolver;
    private final Clock clock;

    public OrganizationCreationService(OrganizationLifecycleService lifecycleService,
                                       ActiveSessionResolver sessionResolver, Clock clock) {
        this.lifecycleService = lifecycleService;
        this.sessionResolver = sessionResolver;
        this.clock = clock;
    }

    public LifecycleResult create(String accessToken, String idempotencyKey, String fingerprint, String requestId,
                                  String name, String shortName, String defaultTimezone) {
        CredentialResolution credential;
        try {
            credential = sessionResolver.resolveCredential(accessToken);
        } catch (CredentialAuthenticationException exception) {
            throw new OrganizationAuthenticationException();
        }
        if (credential.kind() == CredentialResolution.Kind.CONTEXT_SELECTION) {
            throw new OrganizationContextRequiredException();
        }
        ActiveSession session = credential.activeSession();
        if (!session.isGlobalPlatformAdmin()) {
            throw new ForbiddenException();
        }
        Instant now = clock.instant();
        return lifecycleService.create(new LifecycleRequest(session.userId(), UUID.randomUUID(), 1, idempotencyKey,
                fingerprint, requestId, UUID.randomUUID().toString(), now.plus(LEASE_TTL),
                now.plus(IDEMPOTENCY_RETENTION)), name, shortName, defaultTimezone);
    }
}
