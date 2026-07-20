package org.mepcity.kursplatform.org.application;

import org.mepcity.kursplatform.org.domain.Organization;

/**
 * Result of an ORG lifecycle command.
 *
 * <p>{@link #committed(Organization)} signals a fresh mutation that persisted the status change,
 * audit row and idempotency {@code COMPLETED} terminal result in the same transaction.
 * {@link #replay(IdempotencyOutcome.IdempotencyResult)} signals that a prior terminal record with a
 * matching fingerprint was replayed without running a second mutation or writing a second audit.
 */
public sealed interface LifecycleResult permits LifecycleResult.Committed, LifecycleResult.Replayed {

    record Committed(Organization organization) implements LifecycleResult {
        public static Committed committed(Organization organization) {
            return new Committed(organization);
        }
    }

    record Replayed(IdempotencyOutcome.IdempotencyResult result) implements LifecycleResult {
        public static Replayed replay(IdempotencyOutcome.IdempotencyResult result) {
            return new Replayed(result);
        }
    }
}
