package org.mepcity.kursplatform.iam.application;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

public final class CognitoReconciliationScheduler {
    private final CognitoReconciliationService service;
    private final String workerId="cognito-sweep-"+ UUID.randomUUID();
    private final int batchLimit;
    public CognitoReconciliationScheduler(CognitoReconciliationService service,int batchLimit) {
        this.service=service; this.batchLimit=batchLimit;
    }
    @Scheduled(fixedDelayString="${iam.reconciliation.poll-interval:60s}")
    public void poll() {
        for (int i=0;i<batchLimit && service.pollOne(workerId);i++) { /* bounded drain */ }
    }
}
