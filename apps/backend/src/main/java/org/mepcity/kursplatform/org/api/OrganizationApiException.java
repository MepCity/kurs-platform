package org.mepcity.kursplatform.org.api;

final class OrganizationApiException extends RuntimeException {
    private final String code;

    OrganizationApiException(String code, String message) {
        super(message);
        this.code = code;
    }

    String code() { return code; }
}
