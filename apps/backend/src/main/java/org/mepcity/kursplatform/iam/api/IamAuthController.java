package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.application.ContextSelectionService;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeService;
import org.mepcity.kursplatform.iam.application.SessionActivationService;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.util.UUID;

/** {@code @ConditionalOnBean(DataSource.class)}: its constructor dependencies all ultimately need
 *  a real DataSource (see IamInfrastructureConfiguration) — without one, this controller must not
 *  be a mandatory bean, or a DB-free context load (e.g. a wiring smoke test) fails to start. */
@RestController
@ConditionalOnBean(DataSource.class)
@RequestMapping("/api/v1/iam/auth")
public class IamAuthController {

    private final ProviderTokenExchangeService providerTokenExchangeService;
    private final ContextSelectionService contextSelectionService;
    private final SessionActivationService sessionActivationService;

    public IamAuthController(ProviderTokenExchangeService providerTokenExchangeService,
                             ContextSelectionService contextSelectionService,
                             SessionActivationService sessionActivationService) {
        this.providerTokenExchangeService = providerTokenExchangeService;
        this.contextSelectionService = contextSelectionService;
        this.sessionActivationService = sessionActivationService;
    }

    @PostMapping("/provider-token-exchange")
    public ResponseEntity<ProviderTokenExchangeResponse> providerTokenExchange(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody ProviderTokenExchangeRequest request) {
        String cognitoAccessToken = extractBearerToken(authorization);
        IdempotencyKeyValidator.requireValid(idempotencyKey);
        validateRequest(request);
        DevicePlatform platform = DevicePlatform.valueOf(request.platform().toUpperCase());
        var result = providerTokenExchangeService.exchange(
                cognitoAccessToken, request.deviceIdentifier(), platform,
                request.deviceName(), idempotencyKey);
        return ResponseEntity.ok(ProviderTokenExchangeResponse.from(result));
    }

    @GetMapping("/context-selections")
    public ResponseEntity<ContextSelectionListResponse> listContextSelections(
            @RequestHeader("Authorization") String authorization) {
        String contextSelectionToken = extractBearerToken(authorization);
        var summaries = contextSelectionService.listContextSelections(contextSelectionToken);
        return ResponseEntity.ok(ContextSelectionListResponse.from(summaries));
    }

    @PostMapping("/platform-admin/activate")
    public ResponseEntity<SessionActivationResponse> activatePlatformAdmin(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        String contextSelectionToken = extractBearerToken(authorization);
        IdempotencyKeyValidator.requireValid(idempotencyKey);
        var result = sessionActivationService.activatePlatformAdmin(contextSelectionToken, idempotencyKey);
        return ResponseEntity.ok(SessionActivationResponse.from(result));
    }

    @PostMapping("/context-selections/{organizationMembershipId}/activate")
    public ResponseEntity<SessionActivationResponse> activateContext(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @PathVariable UUID organizationMembershipId) {
        String contextSelectionToken = extractBearerToken(authorization);
        IdempotencyKeyValidator.requireValid(idempotencyKey);
        var result = sessionActivationService.activateContext(
                contextSelectionToken, organizationMembershipId, idempotencyKey);
        return ResponseEntity.ok(SessionActivationResponse.from(result));
    }

    private String extractBearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IamException("UNAUTHENTICATED", "Authorization başlığı geçersiz.");
        }
        return authorization.substring("Bearer ".length());
    }

    private void validateRequest(ProviderTokenExchangeRequest request) {
        if (request == null) {
            throw new IamException("INVALID_REQUEST", "İstek gövdesi boş.");
        }
        if (request.deviceIdentifier() == null) {
            throw new IamException("VALIDATION_FAILED", "deviceIdentifier zorunludur.");
        }
        if (request.platform() == null || request.platform().isBlank()) {
            throw new IamException("VALIDATION_FAILED", "platform zorunludur.");
        }
        if (!request.platform().equalsIgnoreCase("IOS") && !request.platform().equalsIgnoreCase("ANDROID")) {
            throw new IamException("VALIDATION_FAILED", "platform IOS veya ANDROID olmalıdır.");
        }
    }
}
