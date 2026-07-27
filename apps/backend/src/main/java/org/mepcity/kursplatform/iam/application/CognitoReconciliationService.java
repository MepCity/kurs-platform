package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;

/** Canonical AdminGetUser sweep for identities that still own active platform families. */
public final class CognitoReconciliationService {
    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final CognitoUserStatusChecker provider;
    private final IamAuditWriter audit;
    private final SecurityAlertSink alerts;
    private final Clock clock;
    private final String issuer;
    private final String userPoolId;

    public CognitoReconciliationService(IamAuthRepository repository, IamTransactionExecutor transactions,
            CognitoUserStatusChecker provider, IamAuditWriter audit, SecurityAlertSink alerts,
            Clock clock, String issuer, String userPoolId) {
        this.repository=repository; this.transactions=transactions; this.provider=provider;
        this.audit=audit; this.alerts=alerts; this.clock=clock; this.issuer=issuer; this.userPoolId=userPoolId;
    }

    public boolean pollOne(String workerId) {
        var claim = transactions.executeInGlobalScope(OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()), () -> {
                    repository.discoverCognitoReconciliationTargets(issuer, userPoolId);
                    return repository.claimCognitoReconciliationTarget(userPoolId, workerId,
                            clock.instant(), clock.instant().plus(Duration.ofMinutes(2)));
                });
        if (claim.isEmpty()) return false;
        var target = claim.get();
        ProviderUserStatus status = provider.checkCanonicalStatus(target.userId(), target.issuer(), target.subject());
        transactions.executeInGlobalScope(OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(target.userId()).withTargetUser(target.userId()), () -> {
                    if (status == ProviderUserStatus.DISABLED || status == ProviderUserStatus.REVOKED) {
                        repository.revokeAllActorFamilies(target.userId(), OperationCode.COGNITO_RECONCILIATION_SWEEP, clock.instant());
                        repository.elevateUserReauthenticationRequiredAfter(target.userId(), clock.instant());
                        audit.write(new IamAuditEvent(UUID.randomUUID(), null, target.userId(), null,
                                "IAM_PROVIDER_SESSION_REVOKED", IamAuditEvent.EventScope.GLOBAL,
                                "USER", IamAuditEvent.EventKind.SECURITY, target.userId(),
                                Map.of("providerStatus", status.name()),
                                Map.of("operationCode", OperationCode.COGNITO_RECONCILIATION_SWEEP.name())));
                    }
                    repository.completeCognitoReconciliationTarget(target, status.name(), clock.instant(),
                            clock.instant().plus(Duration.ofMinutes(1)));
                    return null;
                });
        if (status == ProviderUserStatus.UNKNOWN) {
            alerts.emit(new SecurityAlertSink.SecurityAlert(SecurityAlertSink.Type.PROVIDER_UNAVAILABLE,
                    SecurityAlertSink.Severity.WARNING, clock.instant(), Map.of("identityId", target.identityId().toString())));
        }
        return true;
    }
}
