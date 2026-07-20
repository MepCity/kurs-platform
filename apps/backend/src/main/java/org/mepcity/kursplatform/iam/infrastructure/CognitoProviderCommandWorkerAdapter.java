package org.mepcity.kursplatform.iam.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.application.ProviderCommandOutcome;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorkerAdapter;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Real Cognito Admin API caller for the two provider-command types that carry no encrypted payload
 * — {@code USER_DISABLE} (AdminDisableUser) and {@code USER_LOGOUT} (AdminUserGlobalSignOut).
 * {@code PASSWORD_RESET} and {@code TEACHER_ACCOUNT_CREATE} both require decrypting {@code
 * encrypted_command_payload} (a new password, or new-account details) before a provider call can
 * even be built; no KMS/payload-decryption service exists anywhere in V1, so both are rejected
 * here with {@link UnsupportedOperationException} rather than faked. {@link
 * org.mepcity.kursplatform.iam.application.ProviderCommandWorker} maps that to a terminal
 * {@code BUSINESS_RULE_VIOLATION} completion, so the command goes FAILED (with audit) and the
 * scheduler stops retrying it instead of looping forever.
 *
 * <p>Error classification parses the AWS JSON {@code __type} code rather than trusting the HTTP
 * status alone, because Cognito returns {@code UserNotFoundException} and {@code
 * TooManyRequestsException} over HTTP 400 (not 404 / 429):
 * <ul>
 *   <li>{@code UserNotFoundException} → idempotent SUCCESS: the command's intent (user disabled /
 *       signed out globally) is already satisfied — the user is gone, retrying would never change
 *       that, so treat the command as done instead of looping;</li>
 *   <li>{@code TooManyRequestsException} → {@code PROVIDER_RATE_LIMITED} (retryable);</li>
 *   <li>5xx → {@code PROVIDER_ERROR_5XX} (retryable);</li>
 *   <li>4xx (other) → {@code PROVIDER_ERROR_4XX} (terminal);</li>
 *   <li>401/403 → {@code PROVIDER_AUTH_FAILED} (terminal — a mis-signed request will never succeed
 *       by retrying);</li>
 *   <li>network failure / timeout → {@code PROVIDER_UNAVAILABLE} (retryable);</li>
 *   <li>unknown 404 (no {@code __type}) → {@code PROVIDER_UNAVAILABLE} (fail-closed — we do NOT
 *       infer REVOKED, since a 404 without a Cognito error body is ambiguous and an unverified
 *       assumption would risk a false-positive account revocation).</li>
 * </ul>
 */
public class CognitoProviderCommandWorkerAdapter implements ProviderCommandWorkerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(CognitoProviderCommandWorkerAdapter.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final java.util.Set<String> IDEMPOTENT_USER_GONE_CODES =
            java.util.Set.of("UserNotFoundException", "ResourceNotFoundException");

    private final String userPoolId;
    private final String endpoint;
    private final CognitoSigV4Signer signer;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CognitoProviderCommandWorkerAdapter(String region, String userPoolId,
                                               String accessKeyId, String secretAccessKey, String sessionToken) {
        this(region, userPoolId, accessKeyId, secretAccessKey, sessionToken,
                "https://cognito-idp." + region + ".amazonaws.com/");
    }

    /** Test-only seam: lets tests point the adapter at a local fake endpoint instead of AWS. */
    CognitoProviderCommandWorkerAdapter(String region, String userPoolId, String accessKeyId,
                                        String secretAccessKey, String sessionToken, String endpoint) {
        this.userPoolId = userPoolId;
        this.endpoint = endpoint;
        this.signer = new CognitoSigV4Signer(region, accessKeyId, secretAccessKey, sessionToken);
        this.httpClient = HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build();
    }

    @Override
    public ProviderCommandOutcome execute(ProviderCommandType commandType, String subject, String issuer) {
        String adminApiTarget = switch (commandType) {
            case USER_DISABLE -> "AdminDisableUser";
            case USER_LOGOUT -> "AdminUserGlobalSignOut";
            case PASSWORD_RESET, TEACHER_ACCOUNT_CREATE -> throw new UnsupportedOperationException(
                    commandType + " bir şifrelenmiş payload gerektirir; bu adapter payload çözme desteklemiyor.");
        };
        try {
            String payload = objectMapper.writeValueAsString(Map.of("UserPoolId", userPoolId, "Username", subject));
            HttpRequest request = signer.signAdminApiRequest(endpoint, payload, adminApiTarget);
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 200) {
                return ProviderCommandOutcome.ofSuccess();
            }
            String awsErrorType = extractAwsErrorType(response.body());
            // User is already gone from the provider — the command's intent (disabled/logged out)
            // is already satisfied, so idempotent success (not an endless retry loop). Cognito
            // reports this as HTTP 400 + UserNotFoundException, NOT as HTTP 404.
            if (IDEMPOTENT_USER_GONE_CODES.contains(awsErrorType)) {
                return ProviderCommandOutcome.ofSuccess();
            }
            LOG.warn("Cognito {} call for command type {} returned status {} type {}",
                    adminApiTarget, commandType, status, awsErrorType);
            return ProviderCommandOutcome.ofFailure(mapStatusToSafeErrorCode(status, awsErrorType));
        } catch (Exception e) {
            LOG.warn("Cognito {} call for command type {} failed", adminApiTarget, commandType, e);
            return ProviderCommandOutcome.ofFailure("PROVIDER_UNAVAILABLE");
        }
    }

    private String mapStatusToSafeErrorCode(int statusCode, String awsErrorType) {
        if ("TooManyRequestsException".equals(awsErrorType)) {
            return "PROVIDER_RATE_LIMITED";
        }
        if (statusCode == 401 || statusCode == 403) {
            return "PROVIDER_AUTH_FAILED";
        }
        if (statusCode >= 500 && statusCode < 600) {
            return "PROVIDER_ERROR_5XX";
        }
        if (statusCode >= 400 && statusCode < 500) {
            // 4xx other than the idempotent-user-gone / auth / throttle cases above — a permanent
            // client-side error that retrying the identical request will not fix.
            return "PROVIDER_ERROR_4XX";
        }
        // Anything else (1xx/3xx/unknown) is indeterminate — fail-closed as retryable rather than
        // silently marking the command FAILED on a transient protocol oddity.
        return "PROVIDER_UNAVAILABLE";
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
}
