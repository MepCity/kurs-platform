package org.mepcity.kursplatform.org.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/** HTTP wiring regression: the production controller/service/repository beans remain in the path. */
@ActiveProfiles("local-stub")
@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "KURS_PLATFORM_ENVIRONMENT=development",
        "KURS_PLATFORM_PUBLIC_API_BASE_URL=https://api-development.example.invalid",
        "KURS_PLATFORM_COGNITO_ISSUER_URI=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE",
        "KURS_PLATFORM_COGNITO_CLIENT_ID=examplepublicclientid",
        "KURS_PLATFORM_DATABASE_URL_SECRET_REF=development/platform/database-url",
        "KURS_PLATFORM_IAM_TOKEN_PEPPER_SECRET_REF=development/platform/iam-token-pepper",
        "KURS_PLATFORM_IAM_SECRET_DELIVERY_KEY_REF=development/platform/iam-secret-delivery-key",
        "KURS_PLATFORM_COGNITO_ADMIN_ROLE_REF=development/platform/cognito-admin-role"
})
@AutoConfigureMockMvc
@Import(OrganizationListHttpPostgresIntegrationTests.ListAuth.class)
class OrganizationListHttpPostgresIntegrationTests {
    @Autowired MockMvc mvc;

    @Test
    void malformedCredentialAndContextSelectionAreSafelyRejected() throws Exception {
        mvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer context"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/organizations").header("Authorization", "Bad token"))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class ListAuth {
        @Bean @Primary ActiveSessionResolver listCredentialResolver() {
            return token -> switch (token) {
                case "context" -> CredentialResolution.contextSelection();
                case "organization" -> CredentialResolution.platformAccess(ActiveSession.organization(UUID.randomUUID(), UUID.randomUUID()));
                default -> throw new org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException("UNAUTHENTICATED");
            };
        }
    }
}
