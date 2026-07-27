package org.mepcity.kursplatform.iam.infrastructure;

import java.util.UUID;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.springframework.scheduling.annotation.Scheduled;

/** Reclaims canonical pending events independently of their original SQS delivery. */
public final class CognitoPendingEventScheduler {
    private final CognitoSecurityEventService service;
    private final ReconciliationLagMonitor lagMonitor;
    private final String workerId = "cognito-event-db-" + UUID.randomUUID();
    private final int batchLimit;

    public CognitoPendingEventScheduler(
            CognitoSecurityEventService service,
            ReconciliationLagMonitor lagMonitor,
            int batchLimit) {
        this.service = service;
        this.lagMonitor = lagMonitor;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${iam.security-events.pending-poll-interval:30s}")
    public void poll() {
        for (int i = 0; i < batchLimit && service.processNextPending(workerId); i++) {
            // bounded persistent drain
        }
        lagMonitor.inspectEvents();
    }
}
