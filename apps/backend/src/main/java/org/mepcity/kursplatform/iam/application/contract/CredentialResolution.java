package org.mepcity.kursplatform.iam.application.contract;

/** Sanitised credential classification; no raw credential or hash crosses this boundary. */
public record CredentialResolution(Kind kind, ActiveSession activeSession) {
    public enum Kind { PLATFORM_ACCESS, CONTEXT_SELECTION }
    public CredentialResolution {
        java.util.Objects.requireNonNull(kind, "kind");
        if ((kind == Kind.PLATFORM_ACCESS) != (activeSession != null)) {
            throw new IllegalArgumentException("Only platform access credentials carry an active session.");
        }
    }
    public static CredentialResolution platformAccess(ActiveSession session) { return new CredentialResolution(Kind.PLATFORM_ACCESS, java.util.Objects.requireNonNull(session, "session")); }
    public static CredentialResolution contextSelection() { return new CredentialResolution(Kind.CONTEXT_SELECTION, null); }
}
