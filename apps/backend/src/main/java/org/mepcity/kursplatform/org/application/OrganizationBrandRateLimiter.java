package org.mepcity.kursplatform.org.application;

import java.util.UUID;

/** Persistent quota for new ORG-005 mutations; replays never call this boundary. */
@FunctionalInterface
public interface OrganizationBrandRateLimiter {
    void check(UUID actorUserId, UUID organizationId, String operationCode);
}
