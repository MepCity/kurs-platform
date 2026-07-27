package org.mepcity.kursplatform.iam.infrastructure;

import java.time.Clock;
import java.util.Map;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventParser;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;

/** Bounded at-least-once queue consumer; no raw payload is logged or persisted. */
public final class CognitoSecurityEventConsumer {
    private final CognitoEventQueueClient queue;
    private final CognitoSecurityEventParser parser;
    private final CognitoSecurityEventService service;
    private final ReconciliationLagMonitor lagMonitor;
    private final SecurityAlertSink alerts;
    private final Clock clock;
    private final int maxAttempts;

    public CognitoSecurityEventConsumer(
            CognitoEventQueueClient queue,
            CognitoSecurityEventParser parser,
            CognitoSecurityEventService service,
            ReconciliationLagMonitor lagMonitor,
            SecurityAlertSink alerts,
            Clock clock,
            int maxAttempts) {
        this.queue = queue;
        this.parser = parser;
        this.service = service;
        this.lagMonitor = lagMonitor;
        this.alerts = alerts;
        this.clock = clock;
        this.maxAttempts = maxAttempts;
    }

    public int poll(String workerId, int limit) {
        int processed = 0;
        for (var delivery : queue.receive(limit)) {
            try {
                service.ingest(parser.parse(delivery.body()), workerId);
                queue.acknowledge(delivery.deliveryHandle());
                processed++;
            } catch (IllegalArgumentException poison) {
                emitBestEffort(new SecurityAlertSink.SecurityAlert(
                        SecurityAlertSink.Type.UNKNOWN_EVENT,
                        SecurityAlertSink.Severity.WARNING,
                        clock.instant(),
                        Map.of("receiveCount", Integer.toString(delivery.receiveCount()))));
                if (delivery.receiveCount() >= maxAttempts) {
                    queue.deadLetter(delivery.deliveryHandle());
                    emitBestEffort(new SecurityAlertSink.SecurityAlert(
                            SecurityAlertSink.Type.POISON_EVENT,
                            SecurityAlertSink.Severity.CRITICAL,
                            clock.instant(),
                            Map.of("receiveCount", Integer.toString(delivery.receiveCount()))));
                } else {
                    queue.retry(delivery.deliveryHandle());
                }
            } catch (RuntimeException failure) {
                queue.retry(delivery.deliveryHandle());
                emitBestEffort(new SecurityAlertSink.SecurityAlert(
                        SecurityAlertSink.Type.LOCAL_REVOCATION_FAILED,
                        SecurityAlertSink.Severity.CRITICAL,
                        clock.instant(),
                        Map.of("worker", workerId)));
            }
        }
        lagMonitor.inspectEvents();
        return processed;
    }

    private void emitBestEffort(SecurityAlertSink.SecurityAlert alert) {
        try {
            alerts.emit(alert);
        } catch (RuntimeException ignored) {
            // Queue retry/ACK ownership is authoritative; alert transport is best effort.
        }
    }
}
