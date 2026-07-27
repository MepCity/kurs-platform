package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.mepcity.kursplatform.core.observability.SafeLogEvent;
import org.mepcity.kursplatform.core.observability.SafeLogSeverity;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;

/** Production baseline: operational tooling consumes the existing structured safe-log boundary. */
public final class ObservabilitySecurityAlertSink implements SecurityAlertSink {
    private final SafeEventLogger logger;

    public ObservabilitySecurityAlertSink(SafeEventLogger logger) { this.logger = logger; }

    @Override public void emit(SecurityAlert alert) {
        logger.log(SafeLogEvent.securityAlert("iam.security." + alert.type().name().toLowerCase(),
                alert.severity() == Severity.CRITICAL ? SafeLogSeverity.ERROR : SafeLogSeverity.WARNING,
                alert.occurredAt(), alert.attributes()));
    }
}
