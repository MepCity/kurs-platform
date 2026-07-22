package org.mepcity.kursplatform.org.application;

/** A valid context-selection token cannot be used as a platform access token. */
public final class OrganizationContextRequiredException extends RuntimeException {
    public OrganizationContextRequiredException() { super(null, null, false, false); }
}
