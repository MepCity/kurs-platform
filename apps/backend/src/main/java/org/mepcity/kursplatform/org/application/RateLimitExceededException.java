package org.mepcity.kursplatform.org.application;

/** Raised before the ORG transaction when an authenticated actor exhausted the create quota. */
public final class RateLimitExceededException extends RuntimeException {
    private final long retryAfterSeconds;
    public RateLimitExceededException(long retryAfterSeconds) {
        super(null, null, false, false);
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
