package org.mepcity.kursplatform.org.application;

/** Raised when an authenticated actor exhausts an ORG operation's configured quota. */
public final class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    public RateLimitExceededException(long retryAfterSeconds) {
        super(null, null, false, false);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
