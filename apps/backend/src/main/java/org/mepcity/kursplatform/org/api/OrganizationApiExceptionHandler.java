package org.mepcity.kursplatform.org.api;

import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.IdempotencyKeyReusedException;
import org.mepcity.kursplatform.org.application.IdempotencyPendingException;
import org.mepcity.kursplatform.org.application.OrganizationConflictException;
import org.mepcity.kursplatform.org.application.OrganizationNotVisibleException;
import org.mepcity.kursplatform.org.application.OrganizationStateConflictException;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.mepcity.kursplatform.org.application.OrganizationCredentialException;
import org.mepcity.kursplatform.org.application.RateLimitExceededException;

/** ORG-005's stable error envelope. No database or credential detail is exposed. */
@RestControllerAdvice(assignableTypes = OrganizationBrandController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class OrganizationApiExceptionHandler {
    @ExceptionHandler(ForbiddenException.class) ResponseEntity<Map<String,Object>> forbidden() { return error(HttpStatus.FORBIDDEN,"FORBIDDEN"); }
    @ExceptionHandler(OrganizationNotVisibleException.class) ResponseEntity<Map<String,Object>> missing() { return error(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"); }
    @ExceptionHandler({OrganizationConflictException.class,IdempotencyKeyReusedException.class,IdempotencyPendingException.class}) ResponseEntity<Map<String,Object>> conflict(RuntimeException ex) { return error(HttpStatus.CONFLICT, ex instanceof IdempotencyKeyReusedException ? "IDEMPOTENCY_KEY_REUSED" : ex instanceof IdempotencyPendingException ? "IDEMPOTENCY_PENDING" : "VERSION_CONFLICT"); }
    @ExceptionHandler(OrganizationStateConflictException.class) ResponseEntity<Map<String,Object>> stateConflict() { return error(HttpStatus.CONFLICT, "STATE_CONFLICT"); }
    @ExceptionHandler(RateLimitExceededException.class) ResponseEntity<Map<String,Object>> rateLimited(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", Long.toString(ex.retryAfterSeconds()))
                .body(error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED").getBody());
    }
    @ExceptionHandler(UnauthenticatedException.class) ResponseEntity<Map<String,Object>> unauthenticated() { return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"); }
    @ExceptionHandler(InvalidRequestException.class) ResponseEntity<Map<String,Object>> invalidRequest() { return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST"); }
    @ExceptionHandler(ValidationException.class) ResponseEntity<Map<String,Object>> validationException(ValidationException ex) { return validation(ex.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class) ResponseEntity<Map<String,Object>> invalid(IllegalArgumentException ex) {
        if ("UNAUTHENTICATED".equals(ex.getMessage())) return error(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        if ("INVALID_REQUEST".equals(ex.getMessage())) return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
        return validation(ex.getMessage());
    }
    @ExceptionHandler(OrganizationCredentialException.class) ResponseEntity<Map<String,Object>> credential(OrganizationCredentialException ex) { return switch(ex.code()) { case UNAUTHENTICATED -> error(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED"); case SESSION_REVOKED -> error(HttpStatus.UNAUTHORIZED,"SESSION_REVOKED"); case ORGANIZATION_CONTEXT_REQUIRED -> error(HttpStatus.FORBIDDEN,"ORGANIZATION_CONTEXT_REQUIRED"); case ACCOUNT_NOT_READY -> error(HttpStatus.FORBIDDEN,"ACCOUNT_NOT_READY"); }; }
    @ExceptionHandler({MissingRequestHeaderException.class, HttpMediaTypeNotSupportedException.class,
            MethodArgumentTypeMismatchException.class}) ResponseEntity<Map<String,Object>> malformed(Exception ex) { return error(HttpStatus.BAD_REQUEST,"INVALID_REQUEST"); }
    @ExceptionHandler(HttpMessageNotReadableException.class) ResponseEntity<Map<String,Object>> unreadable(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException) return validation("body.UNKNOWN");
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST");
    }
    @ExceptionHandler(Exception.class) ResponseEntity<Map<String,Object>> unexpected(Exception ex) { return error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR"); }
    private static ResponseEntity<Map<String,Object>> validation(String fieldCode) {
        String[] split = fieldCode == null ? new String[] { "body", "INVALID" } : fieldCode.split("\\.", 2);
        String field = split.length == 2 ? split[0] : "body";
        String code = split.length == 2 ? split[1] : "INVALID";
        String id = MDC.get("requestId"); if (id == null) id = UUID.randomUUID().toString();
        return ResponseEntity.unprocessableEntity().body(Map.of("error", Map.of(
                "code", "VALIDATION_FAILED", "message", "Gönderilen bilgiler doğrulanamadı.",
                "requestId", id, "fieldErrors", java.util.List.of(Map.of("field", field, "code", code, "message", "Alan geçersiz.")))));
    }
    private static ResponseEntity<Map<String,Object>> error(HttpStatus status,String code){String id=MDC.get("requestId");if(id==null)id=UUID.randomUUID().toString();return ResponseEntity.status(status).body(Map.of("error",Map.of("code",code,"message","İstek işlenemedi.","requestId",id)));}
}
