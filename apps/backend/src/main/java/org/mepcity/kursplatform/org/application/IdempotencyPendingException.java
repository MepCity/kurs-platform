package org.mepcity.kursplatform.org.application;

/**
 * Raised when an idempotency key has an in-flight {@code PENDING} claim with a valid lease owned by
 * another transaction. The caller must not start a duplicate execution; it may retry once the lease
 * expires or the in-flight transaction terminates.
 */
public final class IdempotencyPendingException extends RuntimeException {
    public IdempotencyPendingException() {
        super(null, null, false, false);
    }
}
