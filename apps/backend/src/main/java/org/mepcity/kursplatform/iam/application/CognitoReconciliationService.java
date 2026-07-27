package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.CognitoReconciliationClaim;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;

/** Canonical AdminGetUser sweep for identities that still own active platform families. */
public final class CognitoReconciliationService {
    private static final Duration LEASE_TTL = Duration.ofMinutes(2);
    private static final Duration CHECK_INTERVAL = Duration.ofMinutes(1);

    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final CognitoUserStatusChecker provider;
    private final IamAuditWriter audit;
    private final SecurityAlertSink alerts;
    private final Clock clock;
    private final String issuer;
    private final String userPoolId;

    public CognitoReconciliationService(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            CognitoUserStatusChecker provider,
            IamAuditWriter audit,
            SecurityAlertSink alerts,
            Clock clock,
            String issuer,
            String userPoolId) {
        this.repository = repository;
        this.transactions = transactions;
        this.provider = provider;
        this.audit = audit;
        this.alerts = alerts;
        this.clock = clock;
        this.issuer = issuer;
        this.userPoolId = userPoolId;
    }

    public boolean pollOne(String workerId) {
        var claim = transactions.executeInGlobalScope(
                OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> {
                    repository.discoverCognitoReconciliationTargets(issuer, userPoolId);
                    return repository.claimCognitoReconciliationTarget(
                            userPoolId,
                            workerId,
                            clock.instant(),
                            clock.instant().plus(LEASE_TTL));
                });
        if (claim.isEmpty()) {
            return false;
        }
        CognitoReconciliationClaim target = claim.orElseThrow();
        ProviderUserStatus status;
        try {
            status = provider.checkCanonicalStatus(
                    target.userId(), target.issuer(), target.subject());
        } catch (RuntimeException unavailable) {
            releaseForRetry(target);
            emitBestEffort(providerUnavailableAlert(target));
            return true;
        }

        transactions.executeInGlobalScope(
                OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(target.userId())
                        .withTargetUser(target.userId()),
                () -> {
                    if (isNewTerminalStatus(target, status)) {
                        revokeAndAudit(target, status);
                    }
                    repository.completeCognitoReconciliationTarget(
                            target,
                            status.name(),
                            clock.instant(),
                            clock.instant().plus(CHECK_INTERVAL));
                    return null;
                });
        if (status == ProviderUserStatus.UNKNOWN) {
            emitBestEffort(providerUnavailableAlert(target));
        }
        return true;
    }

    private void releaseForRetry(CognitoReconciliationClaim target) {
        transactions.executeInGlobalScope(
                OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(target.userId())
                        .withTargetUser(target.userId()),
                () -> {
                    repository.releaseCognitoReconciliationTarget(
                            target, clock.instant().plus(CHECK_INTERVAL));
                    return null;
                });
    }

    private void revokeAndAudit(
            CognitoReconciliationClaim target, ProviderUserStatus status) {
        repository.revokeAllActorFamilies(
                target.userId(),
                OperationCode.COGNITO_RECONCILIATION_SWEEP,
                clock.instant());
        repository.elevateUserReauthenticationRequiredAfter(
                target.userId(), clock.instant());
        audit.write(new IamAuditEvent(
                UUID.randomUUID(),
                null,
                target.userId(),
                null,
                "IAM_PROVIDER_SESSION_REVOKED",
                IamAuditEvent.EventScope.GLOBAL,
                "USER",
                IamAuditEvent.EventKind.SECURITY,
                target.userId(),
                Map.of("providerStatus", status.name()),
                Map.of("operationCode",
                        OperationCode.COGNITO_RECONCILIATION_SWEEP.name())));
    }

    private static boolean isNewTerminalStatus(
            CognitoReconciliationClaim target, ProviderUserStatus status) {
        boolean terminal = status == ProviderUserStatus.DISABLED
                || status == ProviderUserStatus.REVOKED;
        return terminal && !status.name().equals(target.lastProviderStatus());
    }

    private SecurityAlertSink.SecurityAlert providerUnavailableAlert(
            CognitoReconciliationClaim target) {
        return new SecurityAlertSink.SecurityAlert(
                SecurityAlertSink.Type.PROVIDER_UNAVAILABLE,
                SecurityAlertSink.Severity.WARNING,
                clock.instant(),
                Map.of("identityId", target.identityId().toString()));
    }

    private void emitBestEffort(SecurityAlertSink.SecurityAlert alert) {
        try {
            alerts.emit(alert);
        } catch (RuntimeException ignored) {
            // Alert delivery must not corrupt the persistent reconciliation checkpoint.
        }
    }
}
