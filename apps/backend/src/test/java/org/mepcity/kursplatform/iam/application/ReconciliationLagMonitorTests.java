package org.mepcity.kursplatform.iam.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ReconciliationLagMonitorTests {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Test void emitsWarningStrictlyAfterTwoMinutesAndCriticalStrictlyAfterFiveMinutes() {
        var emitted = new ArrayList<SecurityAlertSink.SecurityAlert>();
        var monitor = new ReconciliationLagMonitor(Clock.fixed(NOW, ZoneOffset.UTC), emitted::add);

        monitor.inspect(NOW.minusSeconds(120), "events");
        assertThat(emitted).isEmpty();
        monitor.inspect(NOW.minusSeconds(121), "events");
        monitor.inspect(NOW.minusSeconds(301), "sweep");

        assertThat(emitted).extracting(SecurityAlertSink.SecurityAlert::severity)
                .containsExactly(SecurityAlertSink.Severity.WARNING, SecurityAlertSink.Severity.CRITICAL);
        assertThat(emitted).allSatisfy(alert -> assertThat(alert.attributes().keySet())
                .containsExactlyInAnyOrder("checkpoint", "lagSeconds"));
    }
}
