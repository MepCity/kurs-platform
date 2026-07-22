package org.mepcity.kursplatform.org.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;

/** Authorization classification must happen before the ORG transaction is opened. */
class OrganizationCreationServiceTests {

    @Test
    void contextSelectionCredentialIsRejectedBeforeAnyOrganizationMutation() {
        OrganizationLifecycleService lifecycleService = mock(OrganizationLifecycleService.class);
        ActiveSessionResolver credentialResolver = mock(ActiveSessionResolver.class);
        when(credentialResolver.resolveCredential("selection-token"))
                .thenReturn(CredentialResolution.contextSelection());
        OrganizationCreationService service = new OrganizationCreationService(
                lifecycleService, credentialResolver, Clock.systemUTC());

        assertThatThrownBy(() -> service.create("selection-token", "create-1", "fingerprint", "request-1",
                "Fındıklı Kur'an Kursu", null, "Europe/Istanbul"))
                .isInstanceOf(OrganizationContextRequiredException.class);

        verify(lifecycleService, never()).create(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
