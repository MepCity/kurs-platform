package org.mepcity.kursplatform.org.application;

import java.util.UUID;

/**
 * Outcome of an idempotency lookup/claim. The lifecycle service reacts to each variant:
 *
 * <ul>
 *   <li>{@link Claimed} — this transaction owns a fresh {@code PENDING} claim (either a brand-new
 *       insert or a successfully re-claimed expired lease). Carries the record id, lease owner and
 *       lease generation so the service can mark it {@code COMPLETED} with the matching lease.
 *   <li>{@link Replay} — a prior terminal record with a matching fingerprint exists. The service
 *       MUST NOT run the mutation or write a second audit row; it returns the replayed result.
 *   <li>{@link Clash} — a prior terminal record exists with a different fingerprint. The service
 *       fails closed ({@code 409 IDEMPOTENCY_KEY_REUSED}); no mutation or audit is written.
 *   <li>{@link Pending} — a prior {@code PENDING} claim with a valid (non-expired) lease still
 *       exists, owned by another in-flight transaction. The service fails closed without running a
 *       second concurrent execution; the caller may retry later.
 * </ul>
 *
 * <p>The four variants are exhaustive: the recorder never returns a nonterminal {@code PENDING}
 * result that pretends to be terminal. A second concurrent caller with the same key and fingerprint
 * never starts a duplicate mutation.
 */
public sealed interface IdempotencyOutcome
        permits IdempotencyOutcome.Claimed, IdempotencyOutcome.Replay, IdempotencyOutcome.Clash, IdempotencyOutcome.Pending {

    /**
     * This transaction owns a {@code PENDING} claim. Carries the record id, lease owner token and
     * lease generation; {@code markCompleted} must echo the exact owner+generation to prove the
     * caller still holds the lease (stale owners cannot complete).
     */
    record Claimed(UUID claimId, String leaseOwner, long leaseGeneration) implements IdempotencyOutcome {
        public Claimed {
            java.util.Objects.requireNonNull(claimId, "claimId");
            java.util.Objects.requireNonNull(leaseOwner, "leaseOwner");
            if (leaseGeneration < 1) {
                throw new IllegalArgumentException("leaseGeneration must be >= 1");
            }
        }
    }

    /** Existing terminal record with matching fingerprint; caller replays {@code result}. */
    record Replay(IdempotencyResult result) implements IdempotencyOutcome {
        public Replay {
            java.util.Objects.requireNonNull(result, "result");
        }
    }

    /** Existing terminal record with a different fingerprint; caller fails closed. */
    record Clash() implements IdempotencyOutcome {}

    /** Existing {@code PENDING} record whose lease is still valid; caller must not start a duplicate execution. */
    record Pending() implements IdempotencyOutcome {}

    /** Terminal status carried by a prior record. */
    enum TerminalStatus { COMPLETED, FAILED }

    /**
     * Replayable terminal result of a prior mutation. Mirrors the minimal, safe subset of
     * {@code idempotency_keys} terminal columns. No secret-bearing payload is exposed.
     */
    record IdempotencyResult(
            UUID id,
            TerminalStatus status,
            short terminalHttpStatus,
            String terminalErrorCode,
            UUID resultEntityId) {

        public boolean isCompleted() {
            return status == TerminalStatus.COMPLETED;
        }
    }
}
