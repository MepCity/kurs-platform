package org.mepcity.kursplatform.iam.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.ProviderCommandOutcome;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IAM-004 Round 2: proves the worker adapter makes a genuinely SigV4-signed HTTP call to the
 * Cognito Admin API target for each supported command type (not a fake/no-op success), that the
 * response status is correctly mapped to a ProviderCommandOutcome, and that PASSWORD_RESET /
 * TEACHER_ACCOUNT_CREATE are rejected before any network call is attempted (no payload-decryption
 * path exists yet — see class-level javadoc on CognitoProviderCommandWorkerAdapter).
 */
class CognitoProviderCommandWorkerAdapterTests {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private String startFakeCognito(int statusCode, String body, AtomicReference<String> capturedTarget,
                                     AtomicReference<String> capturedAuthorizationHeader) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            capturedTarget.set(exchange.getRequestHeaders().getFirst("X-Amz-Target"));
            capturedAuthorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    @Test
    void userDisableSendsGenuinelySignedAdminDisableUserRequestAndMapsSuccessOn200() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(200, "{}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_DISABLE, "subject-1", "issuer-1");

        assertThat(outcome.success()).isTrue();
        assertThat(target.get()).isEqualTo("CognitoIdentityServiceProvider.AdminDisableUser");
        assertThat(authHeader.get()).startsWith("AWS4-HMAC-SHA256 Credential=AKIAFAKE/");
        assertThat(authHeader.get()).contains("SignedHeaders=content-type;host;x-amz-date;x-amz-target");
    }

    @Test
    void userLogoutSendsAdminUserGlobalSignOutRequest() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(200, "{}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_LOGOUT, "subject-1", "issuer-1");

        assertThat(outcome.success()).isTrue();
        assertThat(target.get()).isEqualTo("CognitoIdentityServiceProvider.AdminUserGlobalSignOut");
    }

    @Test
    void userAlreadyGoneFrom404IsTreatedAsSuccessNotAnEndlessRetry() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(404, "{\"__type\":\"UserNotFoundException\"}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_DISABLE, "subject-1", "issuer-1");

        assertThat(outcome.success()).isTrue();
    }

    @Test
    void authFailureMapsToSafeProviderAuthFailedCodeNotRawAwsBody() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(403, "{\"__type\":\"NotAuthorizedException\",\"message\":\"secret internal detail\"}",
                target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_DISABLE, "subject-1", "issuer-1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.safeErrorCode()).isEqualTo("PROVIDER_AUTH_FAILED");
    }

    @Test
    void rateLimitMapsToSafeProviderRateLimitedCode() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(429, "{\"__type\":\"TooManyRequestsException\"}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_DISABLE, "subject-1", "issuer-1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.safeErrorCode()).isEqualTo("PROVIDER_RATE_LIMITED");
    }

    @Test
    void networkFailureMapsToProviderUnavailableNotAnUnhandledException() {
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, "http://localhost:1/");

        ProviderCommandOutcome outcome = adapter.execute(ProviderCommandType.USER_DISABLE, "subject-1", "issuer-1");

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.safeErrorCode()).isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void passwordResetIsRejectedBeforeAnyNetworkCall() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(200, "{}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        assertThatThrownBy(() -> adapter.execute(ProviderCommandType.PASSWORD_RESET, "subject-1", "issuer-1"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(target.get()).as("no HTTP call must have been attempted").isNull();
    }

    @Test
    void teacherAccountCreateIsRejectedBeforeAnyNetworkCall() throws Exception {
        AtomicReference<String> target = new AtomicReference<>();
        AtomicReference<String> authHeader = new AtomicReference<>();
        String endpoint = startFakeCognito(200, "{}", target, authHeader);
        CognitoProviderCommandWorkerAdapter adapter = new CognitoProviderCommandWorkerAdapter(
                "eu-west-1", "pool-1", "AKIAFAKE", "secret-fake", null, endpoint);

        assertThatThrownBy(() -> adapter.execute(ProviderCommandType.TEACHER_ACCOUNT_CREATE, null, null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(target.get()).as("no HTTP call must have been attempted").isNull();
    }
}
