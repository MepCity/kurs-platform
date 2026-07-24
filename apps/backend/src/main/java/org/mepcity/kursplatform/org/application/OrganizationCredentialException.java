package org.mepcity.kursplatform.org.application;

/** Safe credential outcome crossing the ORG application-to-HTTP boundary. */
public final class OrganizationCredentialException extends RuntimeException {
    public enum Code { UNAUTHENTICATED, SESSION_REVOKED, ORGANIZATION_CONTEXT_REQUIRED, ACCOUNT_NOT_READY }
    private final Code code;
    public OrganizationCredentialException(Code code) { this.code = code; }
    public Code code() { return code; }
}
