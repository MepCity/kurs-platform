package org.mepcity.kursplatform.org.api;

import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.IdempotencyKeyReusedException;
import org.mepcity.kursplatform.org.application.IdempotencyPendingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;

@RestControllerAdvice(assignableTypes = OrganizationController.class)
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

    @ExceptionHandler(ForbiddenException.class)
    ResponseEntity<Map<String, Object>> forbidden() {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bu işlem için yetkiniz yok.");
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
            case "UNAUTHENTICATED" -> HttpStatus.UNAUTHORIZED;
            case "INVALID_REQUEST" -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }

    private static ResponseEntity<Map<String, Object>> response(HttpStatus status, String code, String message) {
        String requestId = org.slf4j.MDC.get("requestId");
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();
        return ResponseEntity.status(status).body(Map.of("error", Map.of("code", code, "message", message, "requestId", requestId)));
    }
}
