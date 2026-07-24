package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.IamException;

public final class IamRateLimitExceededException extends IamException {
    private final long retryAfterSeconds;
    public IamRateLimitExceededException(long retryAfterSeconds) {
        super("RATE_LIMITED", "Çok fazla istek gönderildi.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
