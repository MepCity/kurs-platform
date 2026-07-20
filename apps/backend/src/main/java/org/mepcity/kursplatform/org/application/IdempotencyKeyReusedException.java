package org.mepcity.kursplatform.org.application;

/**
 * Raised when an idempotency key is reused with a different fingerprint. Maps to the API contract's
 * {@code 409 IDEMPOTENCY_KEY_REUSED} fail-closed behaviour.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException() {
        super(null, null, false, false);
    }
}
