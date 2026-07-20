package org.mepcity.kursplatform.org.application;

/** Raised when the actor is not an active platform administrator for a lifecycle command. */
public final class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
        super(null, null, false, false);
    }
}
