package org.mepcity.kursplatform.iam.application;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IAM-004 retry/backoff classification: network failures, throttling and provider-side 5xx must be
 * retried with bounded exponential backoff, never marked terminal FAILED on the first attempt; 4xx
 * and auth failures must never be retried. {@code jitterFraction=0} is used throughout except the
 * dedicated jitter test so the backoff curve is deterministic and does not depend on the injected
 * {@link SecureRandom}.
 */
class ProviderCommandRetryPolicyTests {

    private static ProviderCommandRetryPolicy policy(int maxAttempts, Duration base, Duration max, double jitter) {
        return new ProviderCommandRetryPolicy(maxAttempts, base, max, jitter, new SecureRandom());
    }

    @Test
    void networkRateLimitAnd5xxAreRetryable() {
        ProviderCommandRetryPolicy policy = policy(10, Duration.ofSeconds(10), Duration.ofMinutes(15), 0d);

        assertThat(policy.isRetryable("PROVIDER_UNAVAILABLE")).isTrue();
        assertThat(policy.isRetryable("PROVIDER_RATE_LIMITED")).isTrue();
        assertThat(policy.isRetryable("PROVIDER_ERROR_5XX")).isTrue();
    }

    @Test
    void authFailureAnd4xxAreNeverRetryable() {
        ProviderCommandRetryPolicy policy = policy(10, Duration.ofSeconds(10), Duration.ofMinutes(15), 0d);

        assertThat(policy.isRetryable("PROVIDER_AUTH_FAILED")).isFalse();
        assertThat(policy.isRetryable("PROVIDER_ERROR_4XX")).isFalse();
        assertThat(policy.isRetryable(null)).isFalse();
        assertThat(policy.isRetryable("SOME_UNKNOWN_CODE")).isFalse();
    }

    @Test
    void authFailureAnd4xxAreClassifiedTerminal() {
        ProviderCommandRetryPolicy policy = policy(10, Duration.ofSeconds(10), Duration.ofMinutes(15), 0d);

        assertThat(policy.isTerminalFailure("PROVIDER_AUTH_FAILED")).isTrue();
        assertThat(policy.isTerminalFailure("PROVIDER_ERROR_4XX")).isTrue();
        assertThat(policy.isTerminalFailure("PROVIDER_UNAVAILABLE")).isFalse();
        assertThat(policy.isTerminalFailure(null)).isFalse();
    }

    @Test
    void isExhaustedTrueOnceAttemptCountReachesMax() {
        ProviderCommandRetryPolicy policy = policy(3, Duration.ofSeconds(10), Duration.ofMinutes(15), 0d);

        assertThat(policy.isExhausted(1)).isFalse();
        assertThat(policy.isExhausted(2)).isFalse();
        assertThat(policy.isExhausted(3)).isTrue();
        assertThat(policy.isExhausted(4)).isTrue();
    }

    @Test
    void nextAttemptAtGrowsExponentiallyAndCapsAtBackoffMax() {
        ProviderCommandRetryPolicy policy = policy(10, Duration.ofSeconds(10), Duration.ofSeconds(90), 0d);
        Instant now = Instant.parse("2026-07-20T10:00:00Z");

        assertThat(policy.nextAttemptAt(now, 1)).isEqualTo(now.plusSeconds(10));
        assertThat(policy.nextAttemptAt(now, 2)).isEqualTo(now.plusSeconds(20));
        assertThat(policy.nextAttemptAt(now, 3)).isEqualTo(now.plusSeconds(40));
        assertThat(policy.nextAttemptAt(now, 4)).isEqualTo(now.plusSeconds(80));
        // 2^4 * 10s = 160s would exceed backoffMax=90s: must cap, not keep growing unbounded.
        assertThat(policy.nextAttemptAt(now, 5)).isEqualTo(now.plusSeconds(90));
        assertThat(policy.nextAttemptAt(now, 20)).isEqualTo(now.plusSeconds(90));
    }

    @Test
    void nextAttemptAtWithJitterStaysWithinBoundsAndNeverBelowBackoffBase() {
        Duration base = Duration.ofSeconds(10);
        Duration max = Duration.ofMinutes(15);
        ProviderCommandRetryPolicy policy = policy(10, base, max, 0.25d);
        Instant now = Instant.parse("2026-07-20T10:00:00Z");

        for (int attempt = 1; attempt <= 6; attempt++) {
            Instant next = policy.nextAttemptAt(now, attempt);
            long cappedMillis = Math.min(base.toMillis() * (1L << (attempt - 1)), max.toMillis());
            long lowerBound = Math.max(base.toMillis(), (long) (cappedMillis * 0.75d));
            long upperBound = (long) (cappedMillis * 1.25d);
            long actualMillis = next.toEpochMilli() - now.toEpochMilli();
            assertThat(actualMillis).isBetween(lowerBound, upperBound);
        }
    }

    @Test
    void constructorRejectsInvalidMaxAttempts() {
        assertThatThrownBy(() -> new ProviderCommandRetryPolicy(0, Duration.ofSeconds(10),
                Duration.ofMinutes(15), 0.25d, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsNonPositiveBackoffBase() {
        assertThatThrownBy(() -> new ProviderCommandRetryPolicy(10, Duration.ZERO,
                Duration.ofMinutes(15), 0.25d, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsBackoffMaxBelowBackoffBase() {
        assertThatThrownBy(() -> new ProviderCommandRetryPolicy(10, Duration.ofMinutes(15),
                Duration.ofSeconds(10), 0.25d, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructorRejectsJitterOutOfRange() {
        assertThatThrownBy(() -> new ProviderCommandRetryPolicy(10, Duration.ofSeconds(10),
                Duration.ofMinutes(15), 0.51d, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProviderCommandRetryPolicy(10, Duration.ofSeconds(10),
                Duration.ofMinutes(15), -0.01d, new SecureRandom()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
