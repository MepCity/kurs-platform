package org.mepcity.kursplatform.org.application;

/**
 * Raised when the ORG transaction cannot establish the server-set RLS context, acquire a
 * contractual lock or write a mutation/audit/idempotency row. The exception propagates so the
 * surrounding transaction rolls back atomically.
 */
public final class OrganizationPersistenceStateException extends RuntimeException {

    public OrganizationPersistenceStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
