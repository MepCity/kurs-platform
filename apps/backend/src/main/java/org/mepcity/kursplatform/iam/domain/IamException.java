package org.mepcity.kursplatform.iam.domain;

public class IamException extends RuntimeException {

    private final String errorCode;

    public IamException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public IamException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
