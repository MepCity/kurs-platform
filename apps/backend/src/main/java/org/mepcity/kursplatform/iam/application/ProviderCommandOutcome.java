package org.mepcity.kursplatform.iam.application;

/**
 * Result of a real provider call, as reported by a {@link ProviderCommandWorkerAdapter}. Maps
 * directly onto {@link ProviderCommandService#completeCommand}'s {@code success}/{@code
 * safeErrorCode} pair — {@code safeErrorCode} must never carry a raw provider error message
 * (could leak account existence, internal provider state, etc.), only one of the fixed, safe codes
 * the IAM_GIRIS_OTURUM_API_SOZLESMESI.md contract defines for provider-command failures.
 */
public record ProviderCommandOutcome(boolean success, String safeErrorCode) {

    public static ProviderCommandOutcome ofSuccess() {
        return new ProviderCommandOutcome(true, null);
    }

    public static ProviderCommandOutcome ofFailure(String safeErrorCode) {
        return new ProviderCommandOutcome(false, safeErrorCode);
    }
}
