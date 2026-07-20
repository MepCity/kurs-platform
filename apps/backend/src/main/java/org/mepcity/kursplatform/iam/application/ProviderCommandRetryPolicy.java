package org.mepcity.kursplatform.iam.application;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * IAM-004 retry/backoff classification for provider-command outcomes. Distinguishes transient
 * failures (worth retrying with bounded exponential backoff + jitter) from terminal ones (4xx that
 * will never succeed by retrying, e.g. a mis-signed SigV4 request), and decides when the worker
 * must stop retrying and write a safe terminal {@code FAILED} + {@code PROVIDER_COMMAND_EXHAUSTED}
 * audit instead of looping forever.
 *
 * <p>The retryable / non-retryable classification is the one piece of provider-command behavior the
 * contract treats as implementation-defined (the schema carries {@code next_attempt_at} /
 * {@code attempt_count} / {@code last_safe_error_code} but does not mandate the curve); this class
 * IS that definition, so the worker, the service and the tests all consult the same source.
 *
 * <p>Cognito note: AWS returns {@code TooManyRequestsException} and {@code UserNotFoundException}
 * over HTTP 400, NOT via the HTTP status alone — the worker adapter parses the JSON {@code __type}
 * code, so the strings classified here are the safe error codes the adapter already emits
 * ({@code PROVIDER_RATE_LIMITED} for throttling, {@code PROVIDER_ERROR_5XX} for 5xx, etc.), never
 * raw AWS exception names.
 */
public final class ProviderCommandRetryPolicy {

    /**
     * Safe error codes that indicate a transient failure the worker can usefully retry after
     * backoff. Network failures, throttling and provider-side 5xx all land here.
     */
    public static final Set<String> RETRYABLE_ERROR_CODES = Set.of(
            "PROVIDER_UNAVAILABLE",
            "PROVIDER_RATE_LIMITED",
            "PROVIDER_ERROR_5XX"
    );

    /**
     * Safe error codes that indicate a permanent failure for THIS command — retrying the identical
     * request will never succeed. The command goes straight to {@code FAILED} on the first attempt
     * rather than burning the whole retry budget.
     */
    public static final Set<String> TERMINAL_ERROR_CODES = Set.of(
            "PROVIDER_AUTH_FAILED",
            "PROVIDER_ERROR_4XX"
    );

    private final int maxAttempts;
    private final Duration backoffBase;
    private final Duration backoffMax;
    private final double jitterFraction;
    private final SecureRandom random;

    public ProviderCommandRetryPolicy(int maxAttempts, Duration backoffBase, Duration backoffMax,
                                      double jitterFraction, SecureRandom random) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (backoffBase.isNegative() || backoffBase.isZero()) {
            throw new IllegalArgumentException("backoffBase must be positive");
        }
        if (backoffMax.compareTo(backoffBase) < 0) {
            throw new IllegalArgumentException("backoffMax must be >= backoffBase");
        }
        if (jitterFraction < 0d || jitterFraction > 0.5d) {
            throw new IllegalArgumentException("jitterFraction must be in [0, 0.5]");
        }
        this.maxAttempts = maxAttempts;
        this.backoffBase = backoffBase;
        this.backoffMax = backoffMax;
        this.jitterFraction = jitterFraction;
        this.random = random;
    }

    public boolean isRetryable(String safeErrorCode) {
        return safeErrorCode != null && RETRYABLE_ERROR_CODES.contains(safeErrorCode);
    }

    public boolean isTerminalFailure(String safeErrorCode) {
        return safeErrorCode != null && TERMINAL_ERROR_CODES.contains(safeErrorCode);
    }

    /**
     * @param attemptCount the attempt that just failed (1-based; the row's attempt_count is already
     *                     incremented by {@code claimProviderCommandAtomically}).
     * @return {@code true} once {@code attemptCount} has reached {@code maxAttempts} — the caller
     *         must stop retrying and mark the command {@code FAILED} + {@code PROVIDER_COMMAND_EXHAUSTED}.
     */
    public boolean isExhausted(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Bounded exponential backoff {@code base * 2^(attempt-1)} capped at {@code backoffMax}, plus a
     * symmetric random jitter of {@code ±jitterFraction} so two workers that hit the same 429 at
     * the same instant do not retry in lockstep. {@code attempt} is 1-based (the attempt that just
     * failed), so the first retry waits roughly {@code base}, not {@code base * 2}.
     */
    public Instant nextAttemptAt(Instant now, int attempt) {
        long multiplier = 1L << (attempt - 1);
        long baseMillis = backoffBase.toMillis() * multiplier;
        long cappedMillis = Math.min(baseMillis, backoffMax.toMillis());
        long jitterRange = (long) (cappedMillis * jitterFraction);
        long jitterDelta = jitterRange == 0
                ? 0L
                : (long) ((random.nextDouble() * 2d - 1d) * jitterRange);
        long delayedMillis = Math.max(cappedMillis + jitterDelta, backoffBase.toMillis());
        return now.plusMillis(delayedMillis);
    }
}
