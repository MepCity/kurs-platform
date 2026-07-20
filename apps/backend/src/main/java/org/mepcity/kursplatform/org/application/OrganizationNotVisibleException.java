package org.mepcity.kursplatform.org.application;

/** Raised when the target organization is not visible under the current RLS context. */
public final class OrganizationNotVisibleException extends RuntimeException {
    public OrganizationNotVisibleException() {
        super(null, null, false, false);
    }
}
