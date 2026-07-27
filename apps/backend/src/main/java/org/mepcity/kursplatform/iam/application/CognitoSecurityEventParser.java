package org.mepcity.kursplatform.iam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;

import java.time.Instant;

/** Strict parser for the documented common CloudTrail management-event envelope. */
public final class CognitoSecurityEventParser {
    private static final String COGNITO_EVENT_SOURCE = "cognito-idp.amazonaws.com";
    private final ObjectMapper objectMapper;

    public CognitoSecurityEventParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CognitoSecurityEvent parse(String deliveryBody) {
        try {
            JsonNode root = objectMapper.readTree(deliveryBody);
            if (!COGNITO_EVENT_SOURCE.equals(root.path("eventSource").asText())) {
                throw new IllegalArgumentException("CloudTrail eventSource kabul edilmedi.");
            }
            JsonNode request = root.path("requestParameters");
            return new CognitoSecurityEvent(
                    request.path("userPoolId").asText(), root.path("eventID").asText(),
                    root.path("eventName").asText(), request.path("username").asText(),
                    Instant.parse(root.path("eventTime").asText()));
        } catch (Exception exception) {
            throw new IllegalArgumentException("CloudTrail güvenlik olayı işlenemedi.", exception);
        }
    }
}
