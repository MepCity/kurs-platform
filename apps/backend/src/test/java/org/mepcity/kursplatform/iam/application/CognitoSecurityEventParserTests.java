package org.mepcity.kursplatform.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CognitoSecurityEventParserTests {
    private static final String ACCOUNT = "111122223333";
    private static final String REGION = "eu-central-1";
    private static final String POOL = "eu-central-1_EXAMPLE";
    private static final String SUBJECT = "11111111-2222-3333-4444-555555555555";
    private final CognitoSecurityEventParser parser =
            new CognitoSecurityEventParser(new ObjectMapper(), ACCOUNT, REGION, POOL);

    @Test
    void parsesAwsDocumentedUserSubForEverySupportedManagementEvent() throws IOException {
        for (String fixture : new String[] {
                "admin-disable-user.json",
                "admin-user-global-sign-out.json",
                "admin-reset-user-password.json",
                "admin-set-user-password.json"
        }) {
            var event = parser.parse(fixture(fixture));

            assertThat(event.subject()).isEqualTo(SUBJECT);
            assertThat(event.userPoolId()).isEqualTo(POOL);
            assertThat(event.eventName()).doesNotContain("RevokeToken");
        }
    }

    @Test
    void rejectsUsernameEvenWhenItLooksLikeAPlatformSubject() {
        String body = valid("AdminDisableUser")
                .replace("\"additionalEventData\":{\"sub\":\"" + SUBJECT + "\"},", "")
                .replace("\"userPoolId\":\"" + POOL + "\"",
                        "\"userPoolId\":\"" + POOL + "\",\"username\":\"" + SUBJECT + "\"");

        assertRejected(body);
    }

    @Test
    void preservesBoundedNonUuidUnicodeSubjectExactly() {
        String subject = "federated|öğrenci/例-42";

        var event = parser.parse(valid("AdminDisableUser").replace(SUBJECT, subject));

        assertThat(event.subject()).isEqualTo(subject);
    }

    @Test
    void rejectsControlCharactersAndOversizedSubjects() {
        assertRejected(valid("AdminDisableUser").replace(
                SUBJECT, "subject\\u0000suffix"));
        assertRejected(valid("AdminDisableUser").replace(
                SUBJECT, "x".repeat(513)));
    }

    @Test
    void rejectsRevokeTokenBecauseItCannotBeMappedWithoutReadingTheToken() throws IOException {
        assertRejected(fixture("revoke-token.json"));
    }

    @Test
    void rejectsFailedCallAndWrongAccountRegionPoolSourceOrEventType() {
        for (String body : new String[] {
                valid("AdminDisableUser").replace("\"readOnly\":false",
                        "\"readOnly\":false,\"errorCode\":\"NotAuthorizedException\""),
                valid("AdminDisableUser").replace(ACCOUNT, "999900001111"),
                valid("AdminDisableUser").replace(REGION, "us-east-1"),
                valid("AdminDisableUser").replace(POOL, "eu-central-1_OTHER"),
                valid("AdminDisableUser").replace("cognito-idp.amazonaws.com", "other.amazonaws.com"),
                valid("AdminDisableUser").replace("AwsApiCall", "AwsServiceEvent")
        }) {
            assertRejected(body);
        }
    }

    private static void assertRejected(String body) {
        assertThatThrownBy(() -> new CognitoSecurityEventParser(
                new ObjectMapper(), ACCOUNT, REGION, POOL).parse(body))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CloudTrail güvenlik olayı işlenemedi.")
                .hasMessageNotContaining(SUBJECT);
    }

    private static String fixture(String name) throws IOException {
        try (var stream = CognitoSecurityEventParserTests.class.getResourceAsStream(
                "/cloudtrail/cognito/" + name)) {
            if (stream == null) {
                throw new IOException("Fixture bulunamadı: " + name);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String valid(String eventName) {
        return """
                {"version":"0","id":"eventbridge-envelope","detail-type":"AWS API Call via CloudTrail",
                "source":"aws.cognito-idp","account":"%s","time":"2026-07-27T10:00:01Z",
                "region":"%s","resources":[],"detail":
                {"eventVersion":"1.09","eventTime":"2026-07-27T10:00:00Z",
                "eventSource":"cognito-idp.amazonaws.com","eventName":"%s",
                "awsRegion":"%s","eventID":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                "eventType":"AwsApiCall","managementEvent":true,"readOnly":false,
                "recipientAccountId":"%s","eventCategory":"Management",
                "requestParameters":{"userPoolId":"%s"},
                "additionalEventData":{"sub":"%s"},"responseElements":null}}
                """.formatted(ACCOUNT, REGION, eventName, REGION, ACCOUNT, POOL, SUBJECT);
    }
}
