package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.domain.IamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

/**
 * Every response this handler produces carries the SAME requestId the client sees in the
 * {@code X-Request-Id} response header — that value is validated once by
 * {@code RequestObservabilityFilter} and published to SLF4J MDC under the key {@code "requestId"}
 * for the remainder of the request; it is never re-generated here. See
 * {@code API_GENEL_KURALLARI.md} §3.2/§5.2 for the header/envelope contract this implements.
 */
@RestControllerAdvice
public class IamExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(IamExceptionHandler.class);

    @ExceptionHandler(IamException.class)
    public ResponseEntity<Map<String, Object>> handleIamException(IamException ex) {
        HttpStatus httpStatus = mapHttpStatus(ex.errorCode());
        String code = httpStatus == HttpStatus.INTERNAL_SERVER_ERROR ? "INTERNAL_ERROR" : ex.errorCode();
        String message = httpStatus == HttpStatus.INTERNAL_SERVER_ERROR
                ? "Beklenmeyen bir hata oluştu."
                : (ex.getMessage() != null ? ex.getMessage() : "IAM hatası oluştu.");
        if (httpStatus == HttpStatus.INTERNAL_SERVER_ERROR) {
            LOG.error("Unmapped IAM error code {}", ex.errorCode(), ex);
        }
        return respond(httpStatus, code, message);
    }

    /** Malformed JSON body, or the body stream was empty where one was required. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "İstek gövdesi okunamadı veya eksik.");
    }

    /** A path/query parameter (e.g. a UUID) could not be converted to its target type. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "'" + ex.getName() + "' parametresi geçersiz biçimde.");
    }

    /** Missing Authorization is a credential problem; any other missing header is a bad request. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException ex) {
        if ("Authorization".equalsIgnoreCase(ex.getHeaderName())) {
            return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authorization başlığı zorunludur.");
        }
        return respond(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "'" + ex.getHeaderName() + "' başlığı zorunludur.");
    }

    /** Closed-enum parsing (e.g. DevicePlatform.valueOf) rejecting an out-of-set value. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED",
                "Gönderilen bilgiler doğrulanamadı.");
    }

    /** Last resort: never leak a stack trace, SQL detail, token, or other exception internals. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception in IAM API", ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Beklenmeyen bir hata oluştu.");
    }

    private ResponseEntity<Map<String, Object>> respond(HttpStatus httpStatus, String code, String message) {
        String requestId = MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        Map<String, Object> body = Map.of(
                "error", Map.of("code", code, "message", message, "requestId", requestId));
        return ResponseEntity.status(httpStatus).body(body);
    }

    private HttpStatus mapHttpStatus(String errorCode) {
        return switch (errorCode) {
            case "UNAUTHENTICATED", "SESSION_REVOKED" -> HttpStatus.UNAUTHORIZED;
            case "FORBIDDEN", "ORGANIZATION_CONTEXT_REQUIRED", "ACCOUNT_NOT_READY", "REAUTHENTICATION_REQUIRED" -> HttpStatus.FORBIDDEN;
            case "RESOURCE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "IDEMPOTENCY_KEY_REUSED", "STATE_CONFLICT", "VERSION_CONFLICT" -> HttpStatus.CONFLICT;
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "VALIDATION_FAILED", "BUSINESS_RULE_VIOLATION" -> HttpStatus.UNPROCESSABLE_ENTITY;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "PROVIDER_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
