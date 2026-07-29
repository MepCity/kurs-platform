package org.mepcity.kursplatform.org.api;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.configuration.observability.RequestObservabilityFilter;
import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.mepcity.kursplatform.org.application.IdempotencyOutcome;
import org.mepcity.kursplatform.org.application.LifecycleResult;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.mepcity.kursplatform.org.application.OrganizationListService;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import org.mepcity.kursplatform.org.application.ForbiddenException;
import org.mepcity.kursplatform.org.application.OrganizationContextRequiredException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP contract coverage for ORG-004; lifecycle atomicity remains proven by PostgreSQL tests. */
class OrganizationControllerContractTests {
    private MockMvc mockMvc;
    private OrganizationCreationService creationService;
    private List<SafeLogEvent> diagnosticEvents;

    @BeforeEach
    void setUp() {
        creationService = mock(OrganizationCreationService.class);
        diagnosticEvents = new ArrayList<>();
        mockMvc = standaloneMockMvc(diagnosticEvents::add);
    }

    private MockMvc standaloneMockMvc(SafeEventLogger diagnosticLogger) {
        return MockMvcBuilders.standaloneSetup(new OrganizationController(creationService,
                        mock(OrganizationListService.class), new ObjectMapper().findAndRegisterModules()))
                .setControllerAdvice(new OrganizationExceptionHandler(diagnosticLogger))
                .addFilter(new RequestObservabilityFilter(event -> { }))
                .build();
    }

    @Test
    void missingAuthorizationReturns401Unauthenticated() throws Exception {
        performValidRequest().andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void missingOrInvalidIdempotencyKeyReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "contains space").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedJsonReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void nonJsonContentTypeReturns400InvalidRequest() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.TEXT_PLAIN)
                        .content(validBody()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        verify(creationService, never()).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void readOnlyOrUnknownBodyFieldsReturn422ValidationFailed() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kurum\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void everyReadOnlyOrUnknownFieldReturns422() throws Exception {
        for (String field : new String[] {"id", "status", "createdAt", "updatedAt", "rowVersion", "unexpected"}) {
            mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                            .header("Idempotency-Key", "create-" + field).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Kurum\",\"" + field + "\":\"value\"}"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void globalPlatformAdminCreateReturns201() throws Exception {
        when(creationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleResult.Committed(organization()));
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.rowVersion").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "/api/v1/organizations/11111111-1111-1111-1111-111111111111"));
    }

    @Test
    void sameIdempotencyReplayReturnsEquivalentFirstResultWithoutSecondMutation() throws Exception {
        Organization organization = organization();
        String payload = "{\"id\":\"" + organization.id() + "\",\"name\":\"Fındıklı Kur'an Kursu\","
                + "\"shortName\":null,\"defaultTimezone\":\"Europe/Istanbul\",\"status\":\"ACTIVE\","
                + "\"createdAt\":\"2026-07-22T10:00:00Z\",\"updatedAt\":\"2026-07-22T10:00:00Z\",\"rowVersion\":1}";
        when(creationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleResult.Committed(organization))
                .thenReturn(new LifecycleResult.Replayed(new IdempotencyOutcome.IdempotencyResult(
                        UUID.randomUUID(), IdempotencyOutcome.TerminalStatus.COMPLETED, (short) 201,
                        null, organization.id(), payload)));
        String first = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-replay").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                        .header("Idempotency-Key", "create-replay").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Location", "/api/v1/organizations/11111111-1111-1111-1111-111111111111"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(replay).isEqualTo(first);
        verify(creationService, times(2)).create(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void nonGlobalSessionIsForbidden() throws Exception {
        when(creationService.create(any(), any(), any(), any(), any(), any(), any())).thenThrow(new ForbiddenException());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer org-token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void contextSelectionCredentialRequiresPlatformAdminActivation() throws Exception {
        when(creationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new OrganizationContextRequiredException());
        mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer selection-token")
                        .header("Idempotency-Key", "create-1").contentType(MediaType.APPLICATION_JSON).content(validBody()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORGANIZATION_CONTEXT_REQUIRED"));
    }

    @Test
    void corruptReplayAndUnexpectedFailureAreSanitizedInternalErrors() throws Exception {
        when(creationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new LifecycleResult.Replayed(new IdempotencyOutcome.IdempotencyResult(
                        UUID.randomUUID(), IdempotencyOutcome.TerminalStatus.COMPLETED, (short) 201,
                        null, UUID.randomUUID(), "{\"id\":\"not-a-uuid\"}")))
                .thenThrow(new IllegalStateException("SQL token=secret-value"));
        for (String key : new String[] {"corrupt", "unexpected"}) {
            String body = mockMvc.perform(post("/api/v1/organizations").header("Authorization", "Bearer token")
                            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(validBody()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                    .andReturn().getResponse().getContentAsString();
            org.assertj.core.api.Assertions.assertThat(body).doesNotContain("SQL", "secret-value", "IllegalStateException");
        }
    }

    @Test
    void unexpectedFailureLogAndResponseShareRequestIdWithoutMessageLeakage() throws Exception {
        IllegalStateException root = new IllegalStateException("token=secret-value");
        root.setStackTrace(
                new StackTraceElement[] {
                    new StackTraceElement("org.postgresql.Driver", "connect", "Driver.java", 10),
                    new StackTraceElement(
                            "org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationRepository",
                            "list",
                            "JdbcOrganizationRepository.java",
                            123)
                });
        RuntimeException wrapper = new RuntimeException("SQL body", root);
        when(creationService.create(any(), any(), any(), any(), any(), any(), any())).thenThrow(wrapper);

        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-Id", "org-diagnostic-request")
                        .header("Idempotency-Key", "diagnostic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "org-diagnostic-request"))
                .andExpect(jsonPath("$.error.requestId").value("org-diagnostic-request"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(diagnosticEvents).singleElement().satisfies(event -> {
            org.assertj.core.api.Assertions.assertThat(event.fields())
                    .containsEntry("requestId", "org-diagnostic-request")
                    .containsEntry("errorType", "IllegalStateException")
                    .containsEntry("stackLocation",
                            "org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationRepository#list:123");
            org.assertj.core.api.Assertions.assertThat(event.fields().toString())
                    .doesNotContain("secret-value", "SQL body", "org.postgresql");
        });
        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("secret-value", "SQL body", "IllegalStateException", "org.postgresql");
    }

    @Test
    void telemetryFailureDoesNotChangeSafeInternalErrorResponse() throws Exception {
        mockMvc = standaloneMockMvc(event -> {
            throw new IllegalStateException("telemetry token=logger-secret");
        });
        when(creationService.create(any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("SQL token=product-secret"));

        String body = mockMvc.perform(post("/api/v1/organizations")
                        .header("Authorization", "Bearer token")
                        .header("X-Request-Id", "logger-failure-request")
                        .header("Idempotency-Key", "logger-failure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody()))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("X-Request-Id", "logger-failure-request"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.requestId").value("logger-failure-request"))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body)
                .doesNotContain("logger-secret", "product-secret", "SQL", "IllegalStateException");
    }

    private org.springframework.test.web.servlet.ResultActions performValidRequest() throws Exception {
        return mockMvc.perform(post("/api/v1/organizations").header("Idempotency-Key", "create-1")
                .contentType(MediaType.APPLICATION_JSON).content(validBody()));
    }

    private static String validBody() { return "{\"name\":\"Fındıklı Kur'an Kursu\",\"defaultTimezone\":\"Europe/Istanbul\"}"; }
    private static Organization organization() {
        Instant now = Instant.parse("2026-07-22T10:00:00Z");
        return new Organization(UUID.fromString("11111111-1111-1111-1111-111111111111"), "Fındıklı Kur'an Kursu",
                null, null, null, OrganizationStatus.ACTIVE, "Europe/Istanbul", now, now, 1,
                UUID.fromString("22222222-2222-2222-2222-222222222222"), UUID.fromString("22222222-2222-2222-2222-222222222222"));
    }
}
