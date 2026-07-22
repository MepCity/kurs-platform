package org.mepcity.kursplatform.org.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.util.UUID;
import org.mepcity.kursplatform.org.application.LifecycleResult;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.springframework.http.HttpStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;

/** Platform-admin-only organization creation endpoint. */
@RestController
@ConditionalOnBean(DataSource.class)
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationCreationService creationService;
    private final ObjectMapper objectMapper;

    public OrganizationController(OrganizationCreationService creationService, ObjectMapper objectMapper) {
        this.creationService = creationService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody String rawBody) {
        String accessToken = bearerToken(authorization);
        requireIdempotencyKey(idempotencyKey);
        CreateOrganizationRequest body = parseAndValidate(rawBody);
        LifecycleResult result = creationService.create(accessToken, idempotencyKey, fingerprint(rawBody), requestId(),
                body.name().trim(), normalize(body.shortName()),
                body.defaultTimezone() == null ? "Europe/Istanbul" : body.defaultTimezone());
        if (result instanceof LifecycleResult.Committed committed) {
            return ResponseEntity.status(HttpStatus.CREATED).body(OrganizationResponse.from(committed.organization()));
        }
        LifecycleResult.Replayed replayed = (LifecycleResult.Replayed) result;
        try {
            objectMapper.readTree(replayed.result().resultPayload());
            return ResponseEntity.status(replayed.result().terminalHttpStatus())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(replayed.result().resultPayload());
        } catch (JsonProcessingException | NullPointerException exception) {
            throw new OrganizationApiException("INTERNAL_ERROR", "İdempotency replay sonucu yeniden oluşturulamadı.");
        }
    }

    private CreateOrganizationRequest parseAndValidate(String rawBody) {
        try {
            CreateOrganizationRequest request = objectMapper.readerFor(CreateOrganizationRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawBody);
            if (request == null || request.name() == null || request.name().trim().isEmpty()
                    || request.name().trim().length() > 200
                    || (request.shortName() != null && (request.shortName().trim().isEmpty()
                    || request.shortName().trim().length() > 50))) {
                throw new OrganizationApiException("VALIDATION_FAILED", "Kurum alanları geçersiz.");
            }
            String timezone = request.defaultTimezone() == null ? "Europe/Istanbul" : request.defaultTimezone();
            if (timezone.isBlank() || timezone.length() > 100) {
                throw new OrganizationApiException("VALIDATION_FAILED", "defaultTimezone geçersiz.");
            }
            try {
                ZoneId.of(timezone);
            } catch (RuntimeException exception) {
                throw new OrganizationApiException("VALIDATION_FAILED", "defaultTimezone geçersiz.");
            }
            return request;
        } catch (com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException exception) {
            throw new OrganizationApiException("VALIDATION_FAILED", "İstek gövdesinde izin verilmeyen alan var.");
        } catch (JsonProcessingException exception) {
            throw new OrganizationApiException("INVALID_REQUEST", "İstek gövdesi geçersiz.");
        }
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == 7) {
            throw new OrganizationApiException("UNAUTHENTICATED", "Authorization başlığı geçersiz.");
        }
        return authorization.substring(7);
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 128 || !key.chars().allMatch(c -> c >= 0x21 && c <= 0x7e)) {
            throw new OrganizationApiException("INVALID_REQUEST", "Idempotency-Key en fazla 128 ASCII görünür karakter olmalıdır.");
        }
    }

    private static String normalize(String value) { return value == null ? null : value.trim(); }
    private static String requestId() {
        String value = org.slf4j.MDC.get("requestId");
        return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
    }
    private static String fingerprint(String body) {
        return sha256("POST" + "/api/v1/organizations" + "CREATE" + "" + sha256(body) + "");
    }
    private static String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 kullanılamıyor", exception);
        }
    }
}
