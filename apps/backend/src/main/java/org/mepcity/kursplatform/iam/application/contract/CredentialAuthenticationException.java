package org.mepcity.kursplatform.iam.application.contract;

/** Sanitised authentication failure published to modules that consume IAM credentials. */
public final class CredentialAuthenticationException extends RuntimeException {
    private final String code;

    public CredentialAuthenticationException(String code) {
        super(null, null, false, false);
        this.code = code;
    }

    public String code() { return code; }
}
