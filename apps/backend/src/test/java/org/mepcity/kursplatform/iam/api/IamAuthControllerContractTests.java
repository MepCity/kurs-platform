package org.mepcity.kursplatform.iam.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.configuration.observability.RequestObservabilityFilter;
import org.mepcity.kursplatform.iam.application.ContextSelectionService;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeService;
import org.mepcity.kursplatform.iam.application.SessionActivationService;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IAM-004 Round 2: proves the common API envelope contract end-to-end through Spring's real
 * dispatch/binding machinery (not just IamExceptionHandler in isolation) — missing/malformed
 * input produces the documented {@code {"error": {code, message, requestId}}} shape, the correct
 * HTTP status, and a {@code requestId} that is byte-for-byte the same value echoed in the
 * {@code X-Request-Id} response header. Uses a standalone MockMvc (no Spring context slice) so
 * the real {@code RequestObservabilityFilter} and {@code IamExceptionHandler} run unmodified,
 * with the three IAM services mocked since only the HTTP/binding/error-envelope layer is under
 * test here — the service layer already has its own dedicated unit/integration tests.
 */
class IamAuthControllerContractTests {

    private MockMvc mockMvc;
    private ProviderTokenExchangeService exchangeService;
    private ContextSelectionService contextSelectionService;
    private SessionActivationService activationService;

    @BeforeEach
    void setUp() {
        exchangeService = mock(ProviderTokenExchangeService.class);
        contextSelectionService = mock(ContextSelectionService.class);
        activationService = mock(SessionActivationService.class);
        var controller = new IamAuthController(exchangeService, contextSelectionService, activationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new IamExceptionHandler())
                .addFilter(new RequestObservabilityFilter(event -> { }))
                .build();
    }

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    @Test
    void missingAuthorizationHeaderReturns401WithMatchingRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Idempotency-Key", "mutation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.error.requestId").value(matchesPattern(UUID_PATTERN)))
                .andExpect(result -> {
                    String headerValue = result.getResponse().getHeader("X-Request-Id");
                    String bodyRequestId = com.jayway.jsonpath.JsonPath.read(
                            result.getResponse().getContentAsString(), "$.error.requestId");
                    org.assertj.core.api.Assertions.assertThat(bodyRequestId).isEqualTo(headerValue);
                });
    }

    @Test
    void clientSuppliedRequestIdIsEchoedInHeaderAndErrorBody() throws Exception {
        String clientRequestId = "client-generated-id-123";
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("X-Request-Id", clientRequestId)
                        .header("Idempotency-Key", "mutation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.requestId").value(clientRequestId))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader("X-Request-Id"))
                                .isEqualTo(clientRequestId));
    }

    @Test
    void missingIdempotencyKeyReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Authorization", "Bearer sometoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void tooLongIdempotencyKeyReturns400InvalidRequest() throws Exception {
        String tooLong = "k".repeat(200);
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Authorization", "Bearer sometoken")
                        .header("Idempotency-Key", tooLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void idempotencyKeyWithWhitespaceReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Authorization", "Bearer sometoken")
                        .header("Idempotency-Key", "has a space")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"IOS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedJsonBodyReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Authorization", "Bearer sometoken")
                        .header("Idempotency-Key", "mutation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void invalidEnumPlatformReturns422ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/provider-token-exchange")
                        .header("Authorization", "Bearer sometoken")
                        .header("Idempotency-Key", "mutation-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceIdentifier\":\"" + UUID.randomUUID() + "\",\"platform\":\"WINDOWS_PHONE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void invalidPathUuidReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/iam/auth/context-selections/not-a-uuid/activate")
                        .header("Authorization", "Bearer sometoken")
                        .header("Idempotency-Key", "mutation-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void unexpectedServiceExceptionReturns500WithoutLeakingDetail() throws Exception {
        when(contextSelectionService.listContextSelections(any()))
                .thenThrow(new RuntimeException("db password is secretsauce123"));

        mockMvc.perform(get("/api/v1/iam/auth/context-selections")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("Beklenmeyen bir hata oluştu."))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                                .doesNotContain("secretsauce123"));
    }

    @Test
    void unmappedIamErrorCodeReturns500AsInternalErrorNotRawCode() throws Exception {
        when(contextSelectionService.listContextSelections(any()))
                .thenThrow(new IamException("SOME_UNMAPPED_CODE", "internal detail leaking here"));

        mockMvc.perform(get("/api/v1/iam/auth/context-selections")
                        .header("Authorization", "Bearer sometoken"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(result ->
                        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                                .doesNotContain("internal detail leaking here"));
    }
}
