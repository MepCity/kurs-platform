package org.mepcity.kursplatform.org.api;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.IdempotencyKeyReusedException;
import org.mepcity.kursplatform.org.application.IdempotencyPendingException;
import org.mepcity.kursplatform.org.application.OrganizationContextRequiredException;
import org.mepcity.kursplatform.org.application.OrganizationAuthenticationException;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.mepcity.kursplatform.org.application.InvalidCursorException;
import org.mepcity.kursplatform.org.application.OrganizationListValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice(assignableTypes = OrganizationController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class OrganizationExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger("kurs-platform.observability");
    private static final Pattern SAFE_ERROR_TYPE = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,127}");
    private static final Pattern SAFE_STACK_PART = Pattern.compile("[A-Za-z_$][A-Za-z0-9_.$]{0,255}");

    @ExceptionHandler(OrganizationApiException.class)
    ResponseEntity<Map<String, Object>> api(OrganizationApiException exception) {
        return response(status(exception.code()), exception.code(), exception.getMessage());
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Map<String, Object>> missingHeader(MissingRequestHeaderException exception) {
        if ("Authorization".equalsIgnoreCase(exception.getHeaderName())) {
            return response(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authorization başlığı zorunludur.");
        }
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Zorunlu istek başlığı eksik.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> unsupportedMediaType() {
        return response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Content-Type application/json olmalıdır.");
    }

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Map<String, Object>> forbidden() {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.");
    }

    @ExceptionHandler(OrganizationContextRequiredException.class)
    ResponseEntity<Map<String, Object>> contextRequired() {
        return response(HttpStatus.FORBIDDEN, "ORGANIZATION_CONTEXT_REQUIRED", "Önce platform yöneticisi oturumu etkinleştirilmelidir.");
    }

    @ExceptionHandler(OrganizationAuthenticationException.class)
    ResponseEntity<Map<String, Object>> credential(OrganizationAuthenticationException exception) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Oturum doğrulanamadı.");
    }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<Map<String, Object>> rateLimited(RateLimitExceededException exception) {
        ResponseEntity<Map<String, Object>> body = response(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "İstek sınırı aşıldı.");
        return ResponseEntity.status(body.getStatusCode()).headers(body.getHeaders())
                .header("Retry-After", Long.toString(exception.retryAfterSeconds())).body(body.getBody());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ResponseEntity<Map<String, Object>> reused() {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency-Key farklı bir istek için kullanılmış.");
    }

    @ExceptionHandler(IdempotencyPendingException.class)
    ResponseEntity<Map<String, Object>> pending() {
        return response(HttpStatus.CONFLICT, "IDEMPOTENCY_PENDING", "Aynı istek hâlen işleniyor.");
    }

    @ExceptionHandler(InvalidCursorException.class)
    ResponseEntity<Map<String, Object>> invalidCursor() { return response(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Sayfalama imleci geçersiz."); }

    @ExceptionHandler(OrganizationListValidationException.class)
    ResponseEntity<Map<String, Object>> listValidation() { return response(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "Gönderilen bilgiler doğrulanamadı."); }

    private static HttpStatus status(String code) {
        return switch (code) {
            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "ORGANIZATION_CONTEXT_REQUIRED", "FORBIDDEN" -> HttpStatus.FORBIDDEN;
            case "IDEMPOTENCY_KEY_REUSED", "IDEMPOTENCY_PENDING" -> HttpStatus.CONFLICT;
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            case "RATE_LIMITED" -> HttpStatus.TOO_MANY_REQUESTS;
            case "VALIDATION_FAILED" -> HttpStatus.UNPROCESSABLE_ENTITY;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception exception) {
        String requestId = requestId();
        LOG.error(
                "organization.request.unexpected requestId={} errorType={} stackLocation={}",
                requestId,
                safeErrorType(exception),
                safeStackLocation(exception));
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "İşlem tamamlanamadı.");
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message, "requestId", requestId())));
    }

    static String safeErrorType(Throwable error) {
        Throwable root = rootCause(error);
        String type = root.getClass().getSimpleName();
        return SAFE_ERROR_TYPE.matcher(type).matches() ? type : "Throwable";
    }

    static String safeStackLocation(Throwable error) {
        for (StackTraceElement frame : rootCause(error).getStackTrace()) {
            if (frame.getClassName().startsWith("org.mepcity.kursplatform.")
                    && SAFE_STACK_PART.matcher(frame.getClassName()).matches()
                    && SAFE_STACK_PART.matcher(frame.getMethodName()).matches()) {
                return frame.getClassName() + "#" + frame.getMethodName() + ":" + Math.max(0, frame.getLineNumber());
            }
        }
        return "unavailable";
    }

    private static Throwable rootCause(Throwable error) {
        Throwable current = error;
        for (int depth = 0; depth < 16 && current.getCause() != null && current.getCause() != current; depth++) {
            current = current.getCause();
        }
        return current;
    }

    private static String requestId() {
        String requestId = org.slf4j.MDC.get("requestId");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
