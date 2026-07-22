package org.mepcity.kursplatform.org.application;

/** Persistent rate-limit storage failed; callers must fail closed. */
public final class RateLimitStorageException extends RuntimeException {
    public RateLimitStorageException(Throwable cause) { super(null, cause, false, false); }
}
