package org.mepcity.kursplatform.iam.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

class CognitoSecurityEventServiceTests {
    private final IamAuthRepository repository = mock(IamAuthRepository.class);
    private final IamTransactionExecutor transactions = mock(IamTransactionExecutor.class);
    private final IamAuditWriter audit = mock(IamAuditWriter.class);
    private final SecurityAlertSink alerts = mock(SecurityAlertSink.class);
    private CognitoSecurityEventService service;
    private final CognitoSecurityEvent event = new CognitoSecurityEvent("eu-central-1_pool", "event-1", "AdminDisableUser", "subject-1", Instant.EPOCH);

    @BeforeEach void setUp() {
        service = new CognitoSecurityEventService(repository, transactions, audit, alerts, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        doAnswer(invocation -> invocation.getArgument(2, java.util.function.Supplier.class).get())
                .when(transactions).executeInGlobalScope(any(), any(), any());
        doAnswer(invocation -> invocation.getArgument(3, java.util.function.Supplier.class).get())
                .when(transactions).executeInAuthenticationScope(any(), any(), any(), any());
    }

    @Test void mappedEventRevokesAuditsThenCompletes() {
        UUID user = UUID.randomUUID();
        when(repository.recordCognitoSecurityEvent(event)).thenReturn(true);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), user, "issuer", "subject-1", Instant.EPOCH, null)));
        service.process(event, "issuer");
        verify(repository).revokeAllActorFamilies(any(), any(), any());
        verify(audit).write(any());
        verify(repository).completeCognitoSecurityEvent(event);
    }

    @Test void unknownSubjectStaysPendingAndEmitsSafeAlert() {
        when(repository.recordCognitoSecurityEvent(event)).thenReturn(true);
        when(repository.findUserIdentityByIssuerAndSubject("issuer", "subject-1")).thenReturn(Optional.empty());
        service.process(event, "issuer");
        verify(repository, never()).completeCognitoSecurityEvent(event);
        verify(alerts).emit(any());
    }
}
