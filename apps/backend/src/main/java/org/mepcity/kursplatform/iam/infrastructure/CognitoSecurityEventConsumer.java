package org.mepcity.kursplatform.iam.infrastructure;

import java.time.Clock;
import java.util.Map;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventParser;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;

/** Bounded at-least-once queue consumer; no raw payload is logged or persisted. */
public final class CognitoSecurityEventConsumer {
    private final CognitoEventQueueClient queue;
    private final CognitoSecurityEventParser parser;
    private final CognitoSecurityEventService service;
    private final SecurityAlertSink alerts;
    private final Clock clock;
    private final String issuer;
    private final int maxAttempts;

    public CognitoSecurityEventConsumer(CognitoEventQueueClient queue, CognitoSecurityEventParser parser,
            CognitoSecurityEventService service, SecurityAlertSink alerts, Clock clock,
            String issuer, int maxAttempts) {
        this.queue=queue; this.parser=parser; this.service=service; this.alerts=alerts;
        this.clock=clock; this.issuer=issuer; this.maxAttempts=maxAttempts;
    }

    public int poll(String workerId, int limit) {
        int processed=0;
        for (var delivery : queue.receive(limit)) {
            try {
                service.process(parser.parse(delivery.body()), issuer, workerId);
                queue.acknowledge(delivery.deliveryHandle());
                processed++;
            } catch (IllegalArgumentException poison) {
                alerts.emit(new SecurityAlertSink.SecurityAlert(SecurityAlertSink.Type.UNKNOWN_EVENT,
                        SecurityAlertSink.Severity.WARNING, clock.instant(),
                        Map.of("receiveCount", Integer.toString(delivery.receiveCount()))));
                if (delivery.receiveCount() >= maxAttempts) {
                    queue.deadLetter(delivery.deliveryHandle());
                    alerts.emit(new SecurityAlertSink.SecurityAlert(SecurityAlertSink.Type.POISON_EVENT,
                            SecurityAlertSink.Severity.CRITICAL, clock.instant(),
                            Map.of("receiveCount", Integer.toString(delivery.receiveCount()))));
                } else queue.retry(delivery.deliveryHandle());
            } catch (RuntimeException failure) {
                queue.retry(delivery.deliveryHandle());
                alerts.emit(new SecurityAlertSink.SecurityAlert(SecurityAlertSink.Type.LOCAL_REVOCATION_FAILED,
                        SecurityAlertSink.Severity.CRITICAL, clock.instant(), Map.of("worker", workerId)));
            }
        }
        return processed;
    }
}
