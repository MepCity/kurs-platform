package org.mepcity.kursplatform.org.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable input for an ORG lifecycle command (suspend/activate/archive).
 *
 * <p>Carries the actor identity, target organization, optimistic rowVersion, idempotency key +
 * fingerprint and lease metadata. HTTP wiring and request parsing belong to the ORG-004/ORG-005
 * controllers; ORG-003 consumes this record directly from tests.
 */
public record LifecycleRequest(
        UUID actorId,
        UUID organizationId,
        int rowVersion,
        String clientMutationId,
        String requestFingerprint,
        String requestId,
        String leaseOwner,
        Instant leaseExpiresAt,
        Instant keyRetentionExpiresAt) {

    public LifecycleRequest {
        java.util.Objects.requireNonNull(actorId, "actorId");
        java.util.Objects.requireNonNull(organizationId, "organizationId");
        java.util.Objects.requireNonNull(clientMutationId, "clientMutationId");
        java.util.Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        java.util.Objects.requireNonNull(leaseOwner, "leaseOwner");
        java.util.Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        java.util.Objects.requireNonNull(keyRetentionExpiresAt, "keyRetentionExpiresAt");
    }
}
