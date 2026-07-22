package org.mepcity.kursplatform.org.infrastructure;

/** Infrastructure serialization error is deliberately translated to a sanitised HTTP 500. */
public final class OrganizationResultSerializationException extends RuntimeException {
    public OrganizationResultSerializationException(Throwable cause) { super(null, cause, false, false); }
}
