package org.mepcity.kursplatform.org.infrastructure.persistence;

/**
 * Raised when an audit row cannot be written. The exception propagates so the surrounding
 * transaction rolls back; it must not be swallowed and converted to success.
 */
public final class AuditWriteException extends RuntimeException {

    public AuditWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
