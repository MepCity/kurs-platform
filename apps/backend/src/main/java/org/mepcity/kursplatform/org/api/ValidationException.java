package org.mepcity.kursplatform.org.api;

/** Request reached the endpoint but violates its declared JSON contract. */
final class ValidationException extends RuntimeException {
    ValidationException(String code) { super(code); }
}
