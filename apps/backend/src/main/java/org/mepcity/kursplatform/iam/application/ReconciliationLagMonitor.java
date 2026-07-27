package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.OperationCode;

/** Persistent, deterministic 2/5-minute operational gate shared by both schedulers. */
public final class ReconciliationLagMonitor {
    static final Duration WARNING_AT = Duration.ofMinutes(2);
    static final Duration CRITICAL_AT = Duration.ofMinutes(5);
    static final Duration ALARM_COOLDOWN = Duration.ofMinutes(5);

    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final Clock clock;
    private final SecurityAlertSink alerts;
    private final String userPoolId;

    public ReconciliationLagMonitor(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            Clock clock,
            SecurityAlertSink alerts,
            String userPoolId) {
        this.repository = repository;
        this.transactions = transactions;
        this.clock = clock;
        this.alerts = alerts;
        this.userPoolId = userPoolId;
    }

    public void inspectEvents() {
        inspectPersistent("EVENT", OperationCode.COGNITO_SECURITY_EVENT_PROCESS);
    }

    public void inspectReconciliation() {
        inspectPersistent("RECONCILIATION", OperationCode.COGNITO_RECONCILIATION_SWEEP);
    }

    private void inspectPersistent(String checkpoint, OperationCode operationCode) {
        Instant now = clock.instant();
        Optional<Instant> oldest = transactions.executeInGlobalScope(
                operationCode,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> "EVENT".equals(checkpoint)
                        ? repository.findOldestPendingCognitoEventTime(userPoolId)
                        : repository.findOldestDueReconciliationTime(userPoolId, now));
        oldest.flatMap(value -> severity(Duration.between(value, now)))
                .ifPresent(severity -> emitIfDue(checkpoint, operationCode, oldest.orElseThrow(), severity));
    }

    private void emitIfDue(
            String checkpoint,
            OperationCode operationCode,
            Instant checkpointAt,
            SecurityAlertSink.Severity severity) {
        Instant now = clock.instant();
        boolean claimed = transactions.executeInGlobalScope(
                operationCode,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.claimCognitoLagAlarm(
                        userPoolId,
                        checkpoint,
                        severity.name(),
                        now,
                        now.minus(ALARM_COOLDOWN)));
        if (!claimed) {
            return;
        }
        try {
            alerts.emit(new SecurityAlertSink.SecurityAlert(
                    SecurityAlertSink.Type.RECONCILIATION_LAG,
                    severity,
                    now,
                    Map.of(
                            "checkpoint", checkpoint,
                            "lagSeconds",
                            Long.toString(Duration.between(checkpointAt, now).toSeconds()))));
        } catch (RuntimeException ignored) {
            // Monitoring failure cannot alter the durable worker state.
        }
    }

    static Optional<SecurityAlertSink.Severity> severity(Duration lag) {
        if (lag.compareTo(CRITICAL_AT) >= 0) {
            return Optional.of(SecurityAlertSink.Severity.CRITICAL);
        }
        if (lag.compareTo(WARNING_AT) >= 0) {
            return Optional.of(SecurityAlertSink.Severity.WARNING);
        }
        return Optional.empty();
    }
}
