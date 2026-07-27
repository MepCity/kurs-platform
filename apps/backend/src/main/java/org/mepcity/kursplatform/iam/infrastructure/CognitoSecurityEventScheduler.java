package org.mepcity.kursplatform.iam.infrastructure;

import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

/** Scheduled queue pump. The concrete SQS client is supplied by A-010/OPS wiring. */
public final class CognitoSecurityEventScheduler {
    private final CognitoSecurityEventConsumer consumer;
    private final String workerId = "cognito-events-" + UUID.randomUUID();
    private final int batchLimit;

    public CognitoSecurityEventScheduler(CognitoSecurityEventConsumer consumer, int batchLimit) {
        this.consumer=consumer; this.batchLimit=batchLimit;
    }

    @Scheduled(fixedDelayString="${iam.security-events.poll-interval:60s}")
    public void poll() { consumer.poll(workerId,batchLimit); }
}
