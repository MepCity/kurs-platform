package org.mepcity.kursplatform.org.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Provider-independent idempotency port for ORG lifecycle writes.
 *
 * <p>Implementations participate in the caller's transaction. The contract is fail-closed under
 * concurrent access:
 *
 * <ol>
 *   <li>{@link #resolveOrClaim} performs the lookup/claim step atomically. A fresh claim is inserted
 *       with {@code INSERT ... ON CONFLICT DO NOTHING}; if a row already exists, it is locked with
 *       {@code SELECT ... FOR UPDATE} and re-evaluated. The outcome is one of:
 *       {@link IdempotencyOutcome.Claimed} (this transaction owns the lease),
 *       {@link IdempotencyOutcome.Replay} (terminal record with matching fingerprint),
 *       {@link IdempotencyOutcome.Clash} (terminal record with a different fingerprint) or
 *       {@link IdempotencyOutcome.Pending} (in-flight {@code PENDING} record with a valid lease).
 *   <li>{@link #markCompleted} writes the {@code COMPLETED} terminal result in the same
 *       transaction, but only if the caller still holds the lease: the update matches the record id
 *       <em>and</em> the lease owner + lease generation + a still-valid lease. A stale owner or an
 *       expired/re-claimed lease cannot complete the record.
 * </ol>
 *
 * <p>On audit/DB failure the surrounding transaction rolls back. The implementation MUST NOT
 * attempt to persist a {@code FAILED} terminal result on the same failing transaction: the claim
 * (and any partial audit row) vanishes with the rollback, so the record is never left partially
 * terminal. A re-claimed expired lease increments {@code lease_generation} exactly once via a
 * conditional {@code UPDATE}; concurrent re-claim attempts race-safely on the row lock.
 */
public interface IdempotencyRecorder {

    /**
     * Lookup or claim the idempotency record for the given key and fingerprint.
     *
     * @param scopeType {@code GLOBAL} or {@code ORGANIZATION} (never {@code IAM_AUTH})
     * @param organizationId target organization; required for {@code ORGANIZATION}, must be
     *     {@code null} for {@code GLOBAL}
     * @param actorUserId the authenticated actor performing the mutation
     * @param clientMutationId the caller's idempotency key
     * @param operationType the operation code (e.g. {@code ORG_SUSPEND})
     * @param requestFingerprint canonical method/path/target/body/version fingerprint
     * @param leaseOwner short-lived owner token for the {@code PENDING} claim
     * @param leaseExpiresAt absolute lease expiry; must be before {@code keyRetentionExpiresAt}
     * @param keyRetentionExpiresAt tombstone retention bound
     * @return claim, replay, clash or pending outcome; never {@code null}
     */
    IdempotencyOutcome resolveOrClaim(
            String scopeType,
            UUID organizationId,
            UUID actorUserId,
            String clientMutationId,
            String operationType,
            String requestFingerprint,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant keyRetentionExpiresAt);

    /**
     * Mark the claimed record as {@code COMPLETED} with the mutation's terminal result. The
     * recorded lease owner + generation from the matching {@link IdempotencyOutcome.Claimed} must be
     * supplied; a stale owner or generation cannot complete the record.
     */
    void markCompleted(
            UUID idempotencyKeyId,
            String leaseOwner,
            long leaseGeneration,
            UUID resultEntityId,
            short terminalHttpStatus,
            Instant keyRetentionExpiresAt);
}
