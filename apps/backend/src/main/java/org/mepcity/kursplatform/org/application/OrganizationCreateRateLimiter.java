package org.mepcity.kursplatform.org.application;

import java.util.UUID;

/** Cluster-wide, persistent quota boundary for an authenticated ORG_CREATE actor. */
@FunctionalInterface
public interface OrganizationCreateRateLimiter {
    void check(UUID actorUserId);
}
