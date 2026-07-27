package org.mepcity.kursplatform.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReconciliationLagMonitorTests {
    private static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @Test
    void thresholdsAreInclusiveAtExactlyTwoAndFiveMinutes() {
        assertThat(ReconciliationLagMonitor.severity(Duration.ofSeconds(119))).isEmpty();
        assertThat(ReconciliationLagMonitor.severity(Duration.ofMinutes(2)))
                .contains(SecurityAlertSink.Severity.WARNING);
        assertThat(ReconciliationLagMonitor.severity(Duration.ofSeconds(299)))
                .contains(SecurityAlertSink.Severity.WARNING);
        assertThat(ReconciliationLagMonitor.severity(Duration.ofMinutes(5)))
                .contains(SecurityAlertSink.Severity.CRITICAL);
    }

    @Test
    void productionInspectionReadsPersistentCheckpointAndClaimsCooldownBeforeSafeAlert() {
        var repository = mock(IamAuthRepository.class);
        var transactions = mock(IamTransactionExecutor.class);
        var emitted = new ArrayList<SecurityAlertSink.SecurityAlert>();
        doAnswer(invocation -> invocation.getArgument(2, java.util.function.Supplier.class).get())
                .when(transactions).executeInGlobalScope(any(), any(), any());
        when(repository.findOldestPendingCognitoEventTime("pool"))
                .thenReturn(Optional.of(NOW.minusSeconds(120)));
        when(repository.claimCognitoLagAlarm(
                "pool", "EVENT", "WARNING", NOW, NOW.minusSeconds(300)))
                .thenReturn(true, false);
        var monitor = new ReconciliationLagMonitor(
                repository,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC),
                emitted::add,
                "pool");

        monitor.inspectEvents();
        monitor.inspectEvents();

        assertThat(emitted).singleElement().satisfies(alert -> {
            assertThat(alert.severity()).isEqualTo(SecurityAlertSink.Severity.WARNING);
            assertThat(alert.attributes()).containsOnlyKeys("checkpoint", "lagSeconds");
        });
        verify(repository, times(2)).claimCognitoLagAlarm(
                "pool", "EVENT", "WARNING", NOW, NOW.minusSeconds(300));
    }
}
