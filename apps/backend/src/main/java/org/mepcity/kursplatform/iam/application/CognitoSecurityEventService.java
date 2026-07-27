package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

/** Transactional core; delivery adapters may retry but never carry SQS values into this API. */
public final class CognitoSecurityEventService {
    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final IamAuditWriter audit;
    private final SecurityAlertSink alerts;
    private final Clock clock;

    public CognitoSecurityEventService(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            IamAuditWriter audit,
            SecurityAlertSink alerts,
            Clock clock) {
        this.repository = repository;
        this.transactions = transactions;
        this.audit = audit;
        this.alerts = alerts;
        this.clock = clock;
    }
    public void process(CognitoSecurityEvent event, String issuer) {
        process(event, issuer, "direct-consumer");
    }

    public void process(CognitoSecurityEvent event, String issuer, String workerId) {
        var claim = transactions.executeInGlobalScope(OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.claimCognitoSecurityEvent(event, workerId, clock.instant(),
                        clock.instant().plus(Duration.ofMinutes(2))));
        if (claim.isEmpty()) return;
        var identity = transactions.executeInAuthenticationScope(UUID.randomUUID(), issuer, event.subject(),
                () -> repository.findUserIdentityByIssuerAndSubject(issuer, event.subject()));
        if (identity.isEmpty()) {
            transactions.executeInGlobalScope(OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                    IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()), () -> {
                        repository.releaseCognitoSecurityEvent(claim.get()); return null;
                    });
            alerts.emit(new SecurityAlertSink.SecurityAlert(SecurityAlertSink.Type.UNKNOWN_SUBJECT,
                    SecurityAlertSink.Severity.WARNING, clock.instant(), Map.of("eventId", event.eventId())));
            return;
        }
        UserIdentity mapped = identity.get();
        transactions.executeInGlobalScope(OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(mapped.userId()).withTargetUser(mapped.userId()), () -> {
                    repository.revokeAllActorFamilies(mapped.userId(), OperationCode.COGNITO_SECURITY_EVENT_PROCESS, clock.instant());
                    repository.elevateUserReauthenticationRequiredAfter(mapped.userId(), clock.instant());
                    audit.write(new IamAuditEvent(
                            UUID.randomUUID(), null, mapped.userId(), null,
                            "IAM_PROVIDER_SESSION_REVOKED", IamAuditEvent.EventScope.GLOBAL,
                            "USER", IamAuditEvent.EventKind.SECURITY, mapped.userId(),
                            Map.of("providerStatus", "REVOKED"),
                            Map.of("operationCode", OperationCode.COGNITO_SECURITY_EVENT_PROCESS.name())));
                    repository.completeCognitoSecurityEvent(claim.get(), clock.instant()); return null;
                });
    }
}
