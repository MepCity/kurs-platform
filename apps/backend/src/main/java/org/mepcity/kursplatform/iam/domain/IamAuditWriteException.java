package org.mepcity.kursplatform.iam.domain;

/** Thrown when an audit row cannot be written; propagates to roll back the surrounding mutation. */
public class IamAuditWriteException extends RuntimeException {

    public IamAuditWriteException(String message) {
        super(message);
    }

    public IamAuditWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
