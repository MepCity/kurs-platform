package org.mepcity.kursplatform.iam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Set;
import java.util.regex.Pattern;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;

/**
 * Strict parser for successful Cognito user-pool management API events.
 *
 * <p>AWS documents that user-specific Cognito CloudTrail records contain {@code UserSub}, not
 * {@code UserName}; the emitted JSON exposes that stable identifier as
 * {@code additionalEventData.sub}. Request usernames, passwords and tokens are never read.</p>
 */
public final class CognitoSecurityEventParser {
    private static final String COGNITO_EVENT_SOURCE = "cognito-idp.amazonaws.com";
    private static final Set<String> ALLOWED_EVENTS = Set.of(
            "AdminDisableUser",
            "AdminUserGlobalSignOut",
            "AdminResetUserPassword",
            "AdminSetUserPassword");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z0-9._:/+=@-]{1,256}");

    private final ObjectMapper objectMapper;
    private final String expectedAccountId;
    private final String expectedRegion;
    private final String expectedUserPoolId;

    public CognitoSecurityEventParser(
            ObjectMapper objectMapper,
            String expectedAccountId,
            String expectedRegion,
            String expectedUserPoolId) {
        this.objectMapper = objectMapper;
        this.expectedAccountId = requireExpected("AWS account", expectedAccountId);
        this.expectedRegion = requireExpected("AWS region", expectedRegion);
        this.expectedUserPoolId = requireExpected("Cognito user pool", expectedUserPoolId);
    }

    public CognitoSecurityEvent parse(String deliveryBody) {
        try {
            JsonNode root = objectMapper.readTree(deliveryBody);
            requireEquals(root, "version", "0");
            requireEquals(root, "detail-type", "AWS API Call via CloudTrail");
            requireEquals(root, "source", "aws.cognito-idp");
            requireEquals(root, "account", expectedAccountId);
            requireEquals(root, "region", expectedRegion);

            JsonNode detail = root.path("detail");
            if (!detail.isObject()) {
                throw new IllegalArgumentException("EventBridge detail alanı eksik.");
            }
            requireEquals(detail, "eventSource", COGNITO_EVENT_SOURCE);
            requireEquals(detail, "eventType", "AwsApiCall");
            requireEquals(detail, "eventCategory", "Management");
            requireEquals(detail, "recipientAccountId", expectedAccountId);
            requireEquals(detail, "awsRegion", expectedRegion);
            if (!detail.path("managementEvent").asBoolean(false)
                    || detail.path("readOnly").asBoolean(true)) {
                throw new IllegalArgumentException("CloudTrail yönetim/yazma olayı değil.");
            }
            if (detail.hasNonNull("errorCode")) {
                throw new IllegalArgumentException("Başarısız Cognito çağrısı güvenilir olay değildir.");
            }

            String eventName = requiredIdentifier(detail, "eventName");
            if (!ALLOWED_EVENTS.contains(eventName)) {
                throw new IllegalArgumentException("CloudTrail event allow-list dışında.");
            }
            String userPoolId = requiredIdentifier(
                    detail.path("requestParameters"), "userPoolId");
            if (!expectedUserPoolId.equals(userPoolId)) {
                throw new IllegalArgumentException("CloudTrail user pool eşleşmedi.");
            }
            String subject = requiredSubject(detail.path("additionalEventData"), "sub");
            return new CognitoSecurityEvent(
                    userPoolId,
                    requiredIdentifier(detail, "eventID"),
                    eventName,
                    subject,
                    Instant.parse(requiredText(detail, "eventTime")));
        } catch (Exception exception) {
            throw new IllegalArgumentException("CloudTrail güvenlik olayı işlenemedi.", exception);
        }
    }

    private static void requireEquals(JsonNode root, String field, String expected) {
        if (!expected.equals(requiredText(root, field))) {
            throw new IllegalArgumentException("CloudTrail " + field + " eşleşmedi.");
        }
    }

    private static String requiredIdentifier(JsonNode root, String field) {
        String value = requiredText(root, field);
        if (!SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("CloudTrail identifier biçimi geçersiz.");
        }
        return value;
    }

    private static String requiredSubject(JsonNode root, String field) {
        String value = requiredText(root, field);
        if (value.codePointCount(0, value.length()) > 512) {
            throw new IllegalArgumentException("CloudTrail subject sınırı aşıldı.");
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (Character.isISOControl(codePoint)) {
                throw new IllegalArgumentException("CloudTrail subject kontrol karakteri içeriyor.");
            }
            offset += Character.charCount(codePoint);
        }
        return value;
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException("CloudTrail zorunlu alanı eksik.");
        }
        return value.textValue();
    }

    private static String requireExpected(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " yapılandırılmalıdır.");
        }
        return value;
    }
}
