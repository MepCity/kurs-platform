package org.mepcity.kursplatform.iam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CognitoSecurityEventParserTests {
    private final CognitoSecurityEventParser parser = new CognitoSecurityEventParser(new ObjectMapper());

    @Test
    void acceptsOnlyAllowListedDocumentedCloudTrailEnvelopeFields() {
        var event = parser.parse("""
                {"eventSource":"cognito-idp.amazonaws.com","eventName":"AdminDisableUser",
                "eventID":"event-1","eventTime":"2026-07-27T10:00:00Z",
                "requestParameters":{"userPoolId":"eu-central-1_pool","username":"subject-1"}}
                """);

        assertThat(event.eventId()).isEqualTo("event-1");
        assertThat(event.subject()).isEqualTo("subject-1");
    }

    @Test
    void rejectsWrongSourceUnknownEventMissingIdAndMalformedSubjectWithoutRetainingPayload() {
        for (String body : new String[] {
                "{\"eventSource\":\"other.amazonaws.com\"}",
                "{\"eventSource\":\"cognito-idp.amazonaws.com\",\"eventName\":\"AdminEnableUser\",\"eventID\":\"x\",\"eventTime\":\"2026-07-27T10:00:00Z\",\"requestParameters\":{\"userPoolId\":\"pool\",\"username\":\"subject\"}}",
                "{\"eventSource\":\"cognito-idp.amazonaws.com\",\"eventName\":\"AdminDisableUser\",\"eventTime\":\"2026-07-27T10:00:00Z\",\"requestParameters\":{\"userPoolId\":\"pool\",\"username\":\"subject\"}}",
                "{\"eventSource\":\"cognito-idp.amazonaws.com\",\"eventName\":\"AdminDisableUser\",\"eventID\":\"x\",\"eventTime\":\"2026-07-27T10:00:00Z\",\"requestParameters\":{\"userPoolId\":\"pool\",\"username\":\"bad subject\"}}" }) {
            assertThatThrownBy(() -> parser.parse(body)).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("CloudTrail güvenlik olayı işlenemedi.");
        }
    }
}
