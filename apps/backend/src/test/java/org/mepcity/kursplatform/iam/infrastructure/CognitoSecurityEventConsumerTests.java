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
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;

class CognitoSecurityEventConsumerTests {
    @Test void poisonRetriesThenDeadLettersAtBoundedAttemptWithoutPayloadInAlert() {
        var queue=mock(CognitoEventQueueClient.class);
        var service=mock(CognitoSecurityEventService.class);
        var alerts=new ArrayList<SecurityAlertSink.SecurityAlert>();
        when(queue.receive(2)).thenReturn(List.of(
                new CognitoEventQueueClient.Delivery("handle-1", "{bad", 2),
                new CognitoEventQueueClient.Delivery("handle-2", "{bad", 3)));
        var consumer=new CognitoSecurityEventConsumer(queue,
                new CognitoSecurityEventParser(new ObjectMapper()), service, alerts::add,
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), "issuer", 3);

        assertThat(consumer.poll("worker-1",2)).isZero();
        verify(queue).retry("handle-1");
        verify(queue).deadLetter("handle-2");
        verify(queue,never()).acknowledge(any());
        assertThat(alerts).extracting(SecurityAlertSink.SecurityAlert::type)
                .contains(SecurityAlertSink.Type.UNKNOWN_EVENT, SecurityAlertSink.Type.POISON_EVENT);
        assertThat(alerts).allSatisfy(alert -> assertThat(alert.attributes()).doesNotContainKeys("payload","token","password"));
    }
}
