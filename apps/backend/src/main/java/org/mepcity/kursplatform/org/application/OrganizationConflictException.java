package org.mepcity.kursplatform.org.application;

/** Raised when rowVersion/state precondition fails (VERSION_CONFLICT / STATE_CONFLICT semantics). */
public final class OrganizationConflictException extends RuntimeException {
    public OrganizationConflictException() {
        super(null, null, false, false);
    }
}
