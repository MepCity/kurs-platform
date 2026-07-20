package org.mepcity.kursplatform.iam.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class CognitoManagementApiUserStatusChecker implements CognitoUserStatusChecker {

    private static final Logger LOG = LoggerFactory.getLogger(CognitoManagementApiUserStatusChecker.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final String region;
    private final String userPoolId;
    private final CognitoSigV4Signer signer;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CognitoManagementApiUserStatusChecker(String region, String userPoolId,
                                                 String accessKeyId, String secretAccessKey) {
        this(region, userPoolId, accessKeyId, secretAccessKey, null);
    }

    /**
     * @param sessionToken optional AWS session token (STS/temporary credentials). When present it
     *                      is signed as an additional canonical header per the SigV4 spec, so
     *                      temporary credentials work without requiring long-lived static keys.
     */
    public CognitoManagementApiUserStatusChecker(String region, String userPoolId,
                                                 String accessKeyId, String secretAccessKey,
                                                 String sessionToken) {
        this.region = region;
        this.userPoolId = userPoolId;
        this.signer = new CognitoSigV4Signer(region, accessKeyId, secretAccessKey, sessionToken);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Override
    public ProviderUserStatus checkCanonicalStatus(UUID userIdentifier, String issuer, String subject) {
        if (subject == null || subject.isBlank()) {
            LOG.warn("checkCanonicalStatus called without subject; cannot resolve Cognito user deterministically");
            return ProviderUserStatus.UNKNOWN;
        }
        String endpoint = "https://cognito-idp." + region + ".amazonaws.com/";
        // AdminGetUser uses `subject` (the verified access token's `sub` claim) as Cognito's
        // Username parameter. This is the V1-native-pool invariant: V1 runs a Cognito Essentials
        // NATIVE user pool (ADR-004, A-010) where sub == Username; federation is explicitly V1
        // out-of-scope and rejected by IamProperties.validate(). A future ADR revision that enables
        // federation would need to store the verified provider Username separately and look that up
        // here instead — silently using sub for a federated user would 400/404 and be fail-closed
        // below rather than producing a false-positive account revocation.
        String payload;
        try {
            payload = objectMapper.writeValueAsString(java.util.Map.of(
                    "UserPoolId", userPoolId, "Username", subject));
        } catch (Exception e) {
            LOG.warn("Failed to serialize AdminGetUser payload", e);
            return ProviderUserStatus.UNKNOWN;
        }
        try {
            HttpRequest request = buildSignedRequest(endpoint, payload, "AdminGetUser");
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                JsonNode body = objectMapper.readTree(response.body());
                boolean enabled = body.path("Enabled").asBoolean(false);
                String userStatus = body.path("UserStatus").asText("");
                if (!enabled) {
                    return ProviderUserStatus.DISABLED;
                }
                if ("ARCHIVED".equalsIgnoreCase(userStatus) || "COMPROMISED".equalsIgnoreCase(userStatus)) {
                    return ProviderUserStatus.REVOKED;
                }
                return ProviderUserStatus.ACTIVE;
            }
            // Parse the AWS error type rather than trusting HTTP status. Cognito returns
            // UserNotFoundException / ResourceNotFoundException over HTTP 400 (and sometimes 404);
            // either means "no such user in this pool". That is AMBIGUOUS for a status check — it
            // could be a federated-user Username mismatch, a deleted-but-not-yet-replicated user,
            // a wrong user pool id, or a genuinely compromised-then-deleted account. We must NOT
            // infer REVOKED from it (the pre-fix behavior), because a wrong inference would
            // false-positive-revoke sessions for a user who is actually fine. Fail-closed UNKNOWN
            // instead: the caller refuses to open a new session and audits a security event, which
            // is the safe outcome for an indeterminate provider verdict.
            String awsErrorType = extractAwsErrorType(response.body());
            if ("UserNotFoundException".equals(awsErrorType)
                    || "ResourceNotFoundException".equals(awsErrorType)) {
                LOG.warn("Cognito AdminGetUser returned {} for subject={} (status {}); treating as UNKNOWN not REVOKED",
                        awsErrorType, subject, status);
                return ProviderUserStatus.UNKNOWN;
            }
            if (status == 401 || status == 403) {
                LOG.warn("Cognito management API auth failed: status={} type={}", status, awsErrorType);
                return ProviderUserStatus.UNKNOWN;
            }
            LOG.warn("Cognito management API unexpected status {} type {}", status, awsErrorType);
            return ProviderUserStatus.UNKNOWN;
        } catch (Exception e) {
            // Network failures, timeouts, and connection resets land here alongside JSON parse
            // errors; all are treated as indeterminate (UNKNOWN), never as a confirmed disabled/
            // revoked verdict. Logging the full exception (not just getMessage()) so timeouts vs.
            // genuine bugs are distinguishable in production logs.
            LOG.warn("Cognito management API call failed for subject={}", subject, e);
            return ProviderUserStatus.UNKNOWN;
        }
    }

    private String extractAwsErrorType(String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String type = node.path("__type").asText("");
            int slash = type.lastIndexOf('#');
            return slash >= 0 ? type.substring(slash + 1) : type;
        } catch (Exception e) {
            return "";
        }
    }

    private HttpRequest buildSignedRequest(String endpoint, String payload, String target) throws Exception {
        return signer.signAdminApiRequest(endpoint, payload, target);
    }
}
