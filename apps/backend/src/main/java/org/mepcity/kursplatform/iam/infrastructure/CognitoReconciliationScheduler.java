package org.mepcity.kursplatform.iam.infrastructure;

import java.util.UUID;
import org.mepcity.kursplatform.iam.application.CognitoReconciliationService;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.springframework.scheduling.annotation.Scheduled;

/** Infrastructure timer adapter for the persistent reconciliation workflow. */
public final class CognitoReconciliationScheduler {
    private final CognitoReconciliationService service;
    private final ReconciliationLagMonitor lagMonitor;
    private final String workerId = "cognito-sweep-" + UUID.randomUUID();
    private final int batchLimit;

    public CognitoReconciliationScheduler(
            CognitoReconciliationService service,
            ReconciliationLagMonitor lagMonitor,
            int batchLimit) {
        this.service = service;
        this.lagMonitor = lagMonitor;
        this.batchLimit = batchLimit;
    }

    @Scheduled(fixedDelayString = "${iam.reconciliation.poll-interval:60s}")
    public void poll() {
        for (int i = 0; i < batchLimit && service.pollOne(workerId); i++) {
            // bounded persistent drain
        }
        lagMonitor.inspectReconciliation();
    }
}
