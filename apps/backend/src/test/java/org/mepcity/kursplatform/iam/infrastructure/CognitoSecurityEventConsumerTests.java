package org.mepcity.kursplatform.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventParser;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;

class CognitoSecurityEventConsumerTests {
    @Test void poisonRetriesThenDeadLettersAtBoundedAttemptWithoutPayloadInAlert() {
        var queue=mock(CognitoEventQueueClient.class);
        var service=mock(CognitoSecurityEventService.class);
        var lagMonitor = mock(ReconciliationLagMonitor.class);
        var alerts=new ArrayList<SecurityAlertSink.SecurityAlert>();
        when(queue.receive(2)).thenReturn(List.of(
                new CognitoEventQueueClient.Delivery("handle-1", "{bad", 2),
                new CognitoEventQueueClient.Delivery("handle-2", "{bad", 3)));
        var consumer=new CognitoSecurityEventConsumer(queue,
                new CognitoSecurityEventParser(
                        new ObjectMapper(), "111122223333", "eu-central-1", "eu-central-1_pool"),
                service, lagMonitor, alerts::add,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), 3);

        assertThat(consumer.poll("worker-1",2)).isZero();
        verify(queue).retry("handle-1");
        verify(queue).deadLetter("handle-2");
        verify(queue,never()).acknowledge(any());
        assertThat(alerts).extracting(SecurityAlertSink.SecurityAlert::type)
                .contains(SecurityAlertSink.Type.UNKNOWN_EVENT, SecurityAlertSink.Type.POISON_EVENT);
        assertThat(alerts).allSatisfy(alert -> assertThat(alert.attributes()).doesNotContainKeys("payload","token","password"));
        verify(lagMonitor).inspectEvents();
    }

    @Test
    void ackOccursForDurablePendingButServiceFailureRetriesWithoutAck() {
        var queue = mock(CognitoEventQueueClient.class);
        var service = mock(CognitoSecurityEventService.class);
        var lagMonitor = mock(ReconciliationLagMonitor.class);
        when(queue.receive(2)).thenReturn(List.of(
                new CognitoEventQueueClient.Delivery("pending-handle", validEvent("event-pending"), 1),
                new CognitoEventQueueClient.Delivery("failed-handle", validEvent("event-failed"), 1)));
        when(service.ingest(any(), org.mockito.ArgumentMatchers.eq("worker-1")))
                .thenReturn(org.mepcity.kursplatform.iam.application.CognitoEventProcessingResult.PERSISTED_PENDING)
                .thenThrow(new IllegalStateException("database unavailable"));
        var consumer = new CognitoSecurityEventConsumer(
                queue,
                parser(),
                service,
                lagMonitor,
                ignored -> { },
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                3);

        assertThat(consumer.poll("worker-1", 2)).isEqualTo(1);

        verify(queue).acknowledge("pending-handle");
        verify(queue).retry("failed-handle");
        verify(queue, never()).acknowledge("failed-handle");
    }

    @Test
    void alertSinkFailureDoesNotChangePoisonDlqDecision() {
        var queue = mock(CognitoEventQueueClient.class);
        var service = mock(CognitoSecurityEventService.class);
        when(queue.receive(1)).thenReturn(List.of(
                new CognitoEventQueueClient.Delivery("poison-handle", "{bad", 3)));
        SecurityAlertSink failingAlerts = alert -> {
            throw new IllegalStateException("alert unavailable");
        };
        var consumer = new CognitoSecurityEventConsumer(
                queue,
                parser(),
                service,
                mock(ReconciliationLagMonitor.class),
                failingAlerts,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
                3);

        consumer.poll("worker-1", 1);

        verify(queue).deadLetter("poison-handle");
        verify(queue, never()).acknowledge(any());
    }

    private static CognitoSecurityEventParser parser() {
        return new CognitoSecurityEventParser(
                new ObjectMapper(), "111122223333", "eu-central-1", "eu-central-1_pool");
    }

    private static String validEvent(String eventId) {
        return """
                {"version":"0","id":"eventbridge-envelope","detail-type":"AWS API Call via CloudTrail",
                "source":"aws.cognito-idp","account":"111122223333",
                "time":"2026-07-27T10:00:01Z","region":"eu-central-1","resources":[],"detail":
                {"eventTime":"2026-07-27T10:00:00Z",
                "eventSource":"cognito-idp.amazonaws.com","eventName":"AdminDisableUser",
                "awsRegion":"eu-central-1","eventID":"%s","eventType":"AwsApiCall",
                "managementEvent":true,"readOnly":false,"recipientAccountId":"111122223333",
                "eventCategory":"Management","requestParameters":{"userPoolId":"eu-central-1_pool"},
                "additionalEventData":{"sub":"11111111-2222-3333-4444-555555555555"}}}
                """.formatted(eventId);
    }
}
