package org.mepcity.kursplatform.org.infrastructure.persistence;

/**
 * Raised when an idempotency record cannot be read, claimed or completed. The exception propagates
 * so the surrounding transaction rolls back atomically; it must not be swallowed or converted to a
 * terminal {@code FAILED} write on the same failing transaction.
 */
public final class IdempotencyRecordException extends RuntimeException {

    public IdempotencyRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
