package org.mepcity.kursplatform.org.api;

import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.IdempotencyKeyReusedException;
import org.mepcity.kursplatform.org.application.IdempotencyPendingException;
import org.mepcity.kursplatform.org.application.OrganizationContextRequiredException;
import org.mepcity.kursplatform.org.application.OrganizationAuthenticationException;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.HttpMediaTypeNotSupportedException;

@RestControllerAdvice(assignableTypes = OrganizationController.class)
@org.springframework.core.annotation.Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE)
class OrganizationExceptionHandler {
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
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "İşlem tamamlanamadı.");
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        String requestId = org.slf4j.MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message, "requestId", requestId)));
    }
}
