package org.mepcity.kursplatform.org.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.org.application.BrandSettings;
import org.mepcity.kursplatform.org.application.OrganizationBrandAuthentication;
import org.mepcity.kursplatform.org.application.OrganizationBrandService;
import org.springframework.http.MediaType;
import org.slf4j.MDC;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP shape, JSON and header gates for all six file-free ORG-005 endpoints. */
class OrganizationBrandControllerContractTests {
    private MockMvc mvc;
    private OrganizationBrandService service;
    private OrganizationBrandAuthentication authentication;
    private final UUID org = UUID.randomUUID();

    @BeforeEach void setUp() {
        service = mock(OrganizationBrandService.class);
        authentication = mock(OrganizationBrandAuthentication.class);
        when(authentication.authenticate(anyString(), eq(org)))
                .thenReturn(new OrganizationBrandAuthentication.Actor(UUID.randomUUID(), false));
        mvc = MockMvcBuilders.standaloneSetup(new OrganizationBrandController(service, authentication, Clock.systemUTC(), new ObjectMapper()))
                .setControllerAdvice(new OrganizationApiExceptionHandler()).build();
    }

    @Test void getEndpointsReturnJsonAndEtag() throws Exception {
        when(service.brand(any(), eq(org), eq(false), anyString())).thenReturn(new BrandSettings.Brand("#2E7D32", "#E65100", 7, null));
        when(service.palette(any(), eq(org), eq(false), anyString())).thenReturn(new BrandSettings.Palette(7, List.of()));
        when(service.modules(any(), eq(org), eq(false), anyString())).thenReturn(new BrandSettings.Modules(7, List.of()));
        for (String path : List.of("brand", "brand-colors", "modules")) {
            mvc.perform(get("/api/v1/organizations/{id}/" + path, org).header("Authorization", "Bearer access"))
                    .andExpect(status().isOk()).andExpect(header().string("ETag", "\"7\""))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
        }
    }

    @Test void brandWireShapeNeverContainsPaletteAndPassesRequestIdToService() throws Exception {
        MDC.put("requestId", "request-42");
        when(service.brand(any(), eq(org), eq(false), eq("request-42")))
                .thenReturn(new BrandSettings.Brand("#2E7D32", "#E65100", 7, null));
        mvc.perform(get("/api/v1/organizations/{id}/brand", org).header("Authorization", "Bearer access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#2E7D32"))
                .andExpect(jsonPath("$.secondaryColor").value("#E65100"))
                .andExpect(jsonPath("$.rowVersion").value(7))
                .andExpect(jsonPath("$.logo").value(nullValue()))
                .andExpect(jsonPath("$.colors").doesNotExist())
                .andExpect(jsonPath("$.items").doesNotExist());
        MDC.remove("requestId");
    }

    @Test void writesRequireValidHeadersAndJson() throws Exception {
        mvc.perform(patch("/api/v1/organizations/{id}/brand", org).header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON).content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc.perform(patch("/api/v1/organizations/{id}/brand", org).header("Authorization", "Bearer access")
                        .header("Idempotency-Key", "key").header("If-Match-Row-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"unknown\":true}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(put("/api/v1/organizations/{id}/brand-colors", org).header("Authorization", "Bearer access")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        mvc.perform(patch("/api/v1/organizations/{id}/modules", org).header("Authorization", "Bearer ")
                        .header("Idempotency-Key", "x").header("If-Match-Row-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test void absentOrMalformedAuthorizationAndMutationHeadersFailClosed() throws Exception {
        mvc.perform(get("/api/v1/organizations/{id}/brand", org))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
        mvc.perform(patch("/api/v1/organizations/{id}/brand", org).header("Authorization", "Bearer access")
                        .header("Idempotency-Key", "key").header("If-Match-Row-Version", "not-a-number")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }
}
