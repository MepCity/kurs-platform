package org.mepcity.kursplatform.iam.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEventClaim;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

class CognitoSecurityEventServiceTests {
    private final IamAuthRepository repository = mock(IamAuthRepository.class);
    private final IamTransactionExecutor transactions = mock(IamTransactionExecutor.class);
    private final IamAuditWriter audit = mock(IamAuditWriter.class);
    private final SecurityAlertSink alerts = mock(SecurityAlertSink.class);
    private CognitoSecurityEventService service;
    private final CognitoSecurityEvent event = new CognitoSecurityEvent("eu-central-1_pool", "event-1", "AdminDisableUser", "subject-1", Instant.EPOCH);

    @BeforeEach void setUp() {
        service = new CognitoSecurityEventService(
                repository,
                transactions,
                audit,
                alerts,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                "issuer",
                "eu-central-1_pool");
        doAnswer(invocation -> invocation.getArgument(2, java.util.function.Supplier.class).get())
                .when(transactions).executeInGlobalScope(any(), any(), any());
        doAnswer(invocation -> invocation.getArgument(3, java.util.function.Supplier.class).get())
                .when(transactions).executeInAuthenticationScope(any(), any(), any(), any());
    }

    @Test void mappedEventRevokesAuditsThenCompletes() {
        UUID user = UUID.randomUUID();
        var claim = new CognitoSecurityEventClaim(event, "direct-consumer", 1, Instant.EPOCH.plusSeconds(120));
        when(repository.claimCognitoSecurityEvent(any(), any(), any(), any())).thenReturn(Optional.of(claim));
        var identity = new UserIdentity(
                UUID.randomUUID(), user, "issuer", "subject-1", Instant.EPOCH, null);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(identity));
        when(repository.revalidateCognitoEventSubject(
                "issuer", "subject-1", "eu-central-1_pool"))
                .thenReturn(Optional.of(identity));
        assertThat(service.ingest(event, "direct-consumer"))
                .isEqualTo(CognitoEventProcessingResult.COMPLETED);
        verify(repository).revokeAllActorFamilies(any(), any(), any());
        verify(audit).write(any());
        verify(repository).completeCognitoSecurityEvent(any(CognitoSecurityEventClaim.class), any());
    }

    @Test void unknownSubjectStaysPendingAndEmitsSafeAlert() {
        var claim = new CognitoSecurityEventClaim(event, "direct-consumer", 1, Instant.EPOCH.plusSeconds(120));
        when(repository.claimCognitoSecurityEvent(any(), any(), any(), any())).thenReturn(Optional.of(claim));
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1")).thenReturn(Optional.empty());
        assertThat(service.ingest(event, "direct-consumer"))
                .isEqualTo(CognitoEventProcessingResult.PERSISTED_PENDING);
        verify(repository, never()).completeCognitoSecurityEvent(any(), any());
        verify(repository).releaseCognitoSecurityEvent(claim, Instant.EPOCH.plusSeconds(30));
        verify(alerts).emit(any());
    }

    @Test
    void alertSinkFailureDoesNotUndoDurablePendingOwnership() {
        var claim = new CognitoSecurityEventClaim(
                event, "direct-consumer", 1, Instant.EPOCH.plusSeconds(120));
        when(repository.claimCognitoSecurityEvent(any(), any(), any(), any()))
                .thenReturn(Optional.of(claim));
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.empty());
        org.mockito.Mockito.doThrow(new IllegalStateException("alert unavailable"))
                .when(alerts).emit(any());

        assertThat(service.ingest(event, "direct-consumer"))
                .isEqualTo(CognitoEventProcessingResult.PERSISTED_PENDING);
    }
}
