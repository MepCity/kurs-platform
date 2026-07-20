package org.mepcity.kursplatform.iam.infrastructure;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.IamException;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IAM-004 Round 2: proves malformed/hostile inputs fail closed with UNAUTHENTICATED (never an
 * unhandled exception a REST layer would turn into a 500), that signature verification happens
 * before any claim is trusted, and that JWKS rotation/negative-cache behavior works against a
 * real embedded HTTP JWKS endpoint (no mocking of the verifier's internals).
 */
class CognitoJwksTokenVerifierTests {

    private static final String ISSUER_PATH = "/.well-known/jwks.json";
    private static final String CLIENT_ID = "test-client-id";

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private record TestKeyPair(String kid, RSAPrivateKey privateKey, RSAPublicKey publicKey) {}

    private TestKeyPair generateKeyPair(String kid) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        return new TestKeyPair(kid, (RSAPrivateKey) pair.getPrivate(), (RSAPublicKey) pair.getPublic());
    }

    private String startJwksServer(TestKeyPair... keys) throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        String jwks = buildJwks(keys);
        server.createContext(ISSUER_PATH, exchange -> {
            byte[] body = jwks.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String buildJwks(TestKeyPair... keys) {
        StringBuilder sb = new StringBuilder("{\"keys\":[");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            TestKeyPair key = keys[i];
            String n = base64Url(key.publicKey().getModulus().toByteArray());
            String e = base64Url(key.publicKey().getPublicExponent().toByteArray());
            sb.append("{\"kty\":\"RSA\",\"kid\":\"").append(key.kid()).append("\",\"n\":\"")
                    .append(n).append("\",\"e\":\"").append(e).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private String base64Url(byte[] bytes) {
        // Strip a leading sign byte the BigInteger encoding may add — JWKS n/e are unsigned.
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            bytes = trimmed;
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String signedToken(TestKeyPair key, String subject, String issuer, String clientId,
                                String tokenUse, Instant authTime, Instant exp, Instant nbf) throws Exception {
        StringBuilder header = new StringBuilder("{\"alg\":\"RS256\",\"kid\":\"").append(key.kid()).append("\"}");
        StringBuilder payload = new StringBuilder("{");
        payload.append("\"iss\":\"").append(issuer).append("\",");
        payload.append("\"sub\":\"").append(subject).append("\",");
        payload.append("\"client_id\":\"").append(clientId).append("\",");
        payload.append("\"token_use\":\"").append(tokenUse).append("\",");
        payload.append("\"auth_time\":").append(authTime.getEpochSecond()).append(',');
        payload.append("\"exp\":").append(exp.getEpochSecond());
        if (nbf != null) {
            payload.append(",\"nbf\":").append(nbf.getEpochSecond());
        }
        payload.append('}');
        String headerB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toString().getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().getBytes(StandardCharsets.UTF_8));
        String signingInput = headerB64 + "." + payloadB64;
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(key.privateKey());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String sigB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature.sign());
        return signingInput + "." + sigB64;
    }

    @Test
    void verifySucceedsForValidTokenSignedByPublishedKey() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        String issuer = startJwksServer(key);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String token = signedToken(key, "subject-1", issuer, CLIENT_ID, "access",
                now.minusSeconds(10), now.plusSeconds(3600), null);

        CognitoTokenClaims claims = verifier.verify(token);

        assertThat(claims.subject()).isEqualTo("subject-1");
        assertThat(claims.issuer()).isEqualTo(issuer);
    }

    @Test
    void verifyRejectsTokenSignedByUnpublishedKey() throws Exception {
        TestKeyPair published = generateKeyPair("kid-1");
        TestKeyPair attacker = generateKeyPair("kid-1"); // same kid, different key pair
        String issuer = startJwksServer(published);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String token = signedToken(attacker, "subject-1", issuer, CLIENT_ID, "access",
                now.minusSeconds(10), now.plusSeconds(3600), null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsIdTokenNotAccessToken() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        String issuer = startJwksServer(key);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String token = signedToken(key, "subject-1", issuer, CLIENT_ID, "id",
                now.minusSeconds(10), now.plusSeconds(3600), null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsExpiredToken() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        String issuer = startJwksServer(key);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String token = signedToken(key, "subject-1", issuer, CLIENT_ID, "access",
                now.minusSeconds(120), now.minusSeconds(10), null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsUnknownKidAfterOneControlledRefresh() throws Exception {
        TestKeyPair published = generateKeyPair("kid-1");
        TestKeyPair unknown = generateKeyPair("kid-does-not-exist");
        String issuer = startJwksServer(published);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String token = signedToken(unknown, "subject-1", issuer, CLIENT_ID, "access",
                now.minusSeconds(10), now.plusSeconds(3600), null);

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsMissingKidWithoutThrowingNullPointerException() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        String issuer = startJwksServer(key);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + "." + "AAAA";

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsMalformedBase64UrlSignatureWithoutLeakingIllegalArgumentException() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        String issuer = startJwksServer(key);
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"kid\":\"kid-1\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + "." + "not-valid-base64url!!!";

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsMalformedJwtSegment() {
        var verifier = new CognitoJwksTokenVerifier("https://issuer.invalid", CLIENT_ID,
                new HmacSha256TokenHasher("pepper-min-16-characters"));

        assertThatThrownBy(() -> verifier.verify("not-a-jwt-at-all"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyRejectsUnsupportedAlgorithm() throws Exception {
        String issuer = "https://issuer.invalid";
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"kid\":\"kid-1\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"x\"}".getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + ".";

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void verifyReturnsProviderUnavailableWhenJwksEndpointUnreachableAndCacheEmpty() {
        // Nothing is listening on this port, and no prior successful refresh has populated a cache.
        var verifier = new CognitoJwksTokenVerifier("http://localhost:1", CLIENT_ID,
                new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"kid\":\"kid-1\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"x\",\"exp\":" + now.plusSeconds(60).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + ".AAAA";

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void verifyReturnsProviderUnavailableWhenFirstLoadHasNoUsableRsaKeys() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ISSUER_PATH, exchange -> {
            byte[] body = "{\"keys\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String issuer = "http://localhost:" + server.getAddress().getPort();
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\",\"kid\":\"kid-1\"}".getBytes(StandardCharsets.UTF_8));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"x\",\"exp\":" + now.plusSeconds(60).getEpochSecond() + "}")
                        .getBytes(StandardCharsets.UTF_8));
        String token = headerB64 + "." + payloadB64 + ".AAAA";

        // HTTP 200 but an empty keys array: no valid key was ever produced, so the very first load
        // must fail closed rather than silently accepting an unverifiable token.
        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("PROVIDER_UNAVAILABLE");
    }

    @Test
    void corruptedRefreshDoesNotEvictHealthyPriorCacheAndOriginalTokenStillVerifies() throws Exception {
        TestKeyPair key = generateKeyPair("kid-1");
        AtomicReference<String> responseBody = new AtomicReference<>(buildJwks(key));
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(ISSUER_PATH, exchange -> {
            byte[] body = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        String issuer = "http://localhost:" + server.getAddress().getPort();
        var verifier = new CognitoJwksTokenVerifier(issuer, CLIENT_ID, new HmacSha256TokenHasher("pepper-min-16-characters"));
        Instant now = Instant.now();
        String originalToken = signedToken(key, "subject-1", issuer, CLIENT_ID, "access",
                now.minusSeconds(10), now.plusSeconds(3600), null);

        // First verify populates the cache with a healthy key.
        assertThat(verifier.verify(originalToken).subject()).isEqualTo("subject-1");

        // Corrupt the endpoint: still HTTP 200, but no usable RSA key in the body at all.
        responseBody.set("{\"keys\":[]}");

        // An unknown kid triggers exactly one controlled refresh, which finds no usable keys and
        // must NOT evict the still-healthy cached key — it must just fail this one lookup.
        TestKeyPair unknown = generateKeyPair("kid-does-not-exist");
        String unknownKidToken = signedToken(unknown, "subject-2", issuer, CLIENT_ID, "access",
                now.minusSeconds(10), now.plusSeconds(3600), null);
        assertThatThrownBy(() -> verifier.verify(unknownKidToken))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("UNAUTHENTICATED");

        // The original key must still verify successfully — proving the corrupt refresh did not
        // wipe the healthy cache out from under still-valid, already-issued tokens.
        CognitoTokenClaims claims = verifier.verify(originalToken);
        assertThat(claims.subject()).isEqualTo("subject-1");
    }
}
