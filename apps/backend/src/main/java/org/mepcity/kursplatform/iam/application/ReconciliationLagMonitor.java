package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/** Deterministic 2/5 minute operational gate shared by event and canonical sweep schedulers. */
public final class ReconciliationLagMonitor {
    static final Duration WARNING_AFTER = Duration.ofMinutes(2);
    static final Duration CRITICAL_AFTER = Duration.ofMinutes(5);
    private final Clock clock;
    private final SecurityAlertSink alerts;

    public ReconciliationLagMonitor(Clock clock, SecurityAlertSink alerts) {
        this.clock = clock;
        this.alerts = alerts;
    }

    public void inspect(Instant checkpointAt, String checkpoint) {
        Duration lag = Duration.between(checkpointAt, clock.instant());
        if (lag.compareTo(CRITICAL_AFTER) > 0) {
            emit(SecurityAlertSink.Severity.CRITICAL, checkpoint, lag);
        } else if (lag.compareTo(WARNING_AFTER) > 0) {
            emit(SecurityAlertSink.Severity.WARNING, checkpoint, lag);
        }
    }

    private void emit(SecurityAlertSink.Severity severity, String checkpoint, Duration lag) {
        alerts.emit(new SecurityAlertSink.SecurityAlert(
                SecurityAlertSink.Type.RECONCILIATION_LAG,
                severity,
                clock.instant(),
                Map.of("checkpoint", checkpoint, "lagSeconds", Long.toString(lag.toSeconds()))));
    }
}
