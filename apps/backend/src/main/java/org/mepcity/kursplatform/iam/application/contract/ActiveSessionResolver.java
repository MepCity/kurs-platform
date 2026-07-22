package org.mepcity.kursplatform.iam.application.contract;

/** Published IAM boundary; implementations validate the bearer access token fail-closed. */
public interface ActiveSessionResolver {
    CredentialResolution resolveCredential(String credential);
}
