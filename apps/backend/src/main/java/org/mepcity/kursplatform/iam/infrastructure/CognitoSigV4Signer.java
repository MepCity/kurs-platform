package org.mepcity.kursplatform.iam.infrastructure;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Hand-rolled AWS SigV4 signer for the Cognito IDP Admin API (JSON 1.1 protocol, POST /). Shared
 * by every Cognito management-API caller in this module so the signing logic exists in exactly one
 * place; supports session-token (temporary/STS) credentials in addition to static access keys.
 */
public final class CognitoSigV4Signer {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);

    private final String region;
    private final String accessKeyId;
    private final String secretAccessKey;
    private final String sessionToken;

    public CognitoSigV4Signer(String region, String accessKeyId, String secretAccessKey, String sessionToken) {
        this.region = region;
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = (sessionToken == null || sessionToken.isBlank()) ? null : sessionToken;
    }

    public HttpRequest signAdminApiRequest(String endpoint, String payload, String target) throws Exception {
        Instant now = Instant.now();
        String amzDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(now);
        String dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC).format(now);
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        String payloadHash = hexSha256(payloadBytes);
        boolean hasSessionToken = sessionToken != null;
        String signedHeaderNames = hasSessionToken
                ? "content-type;host;x-amz-date;x-amz-security-token;x-amz-target"
                : "content-type;host;x-amz-date;x-amz-target";
        StringBuilder canonicalHeaders = new StringBuilder()
                .append("content-type:application/x-amz-json-1.1\n")
                .append("host:cognito-idp.").append(region).append(".amazonaws.com\n")
                .append("x-amz-date:").append(amzDate).append('\n');
        if (hasSessionToken) {
            canonicalHeaders.append("x-amz-security-token:").append(sessionToken).append('\n');
        }
        canonicalHeaders.append("x-amz-target:CognitoIdentityServiceProvider.").append(target).append('\n');
        String canonicalRequest = "POST\n/\n\n" + canonicalHeaders + "\n" + signedHeaderNames + "\n" + payloadHash;
        String canonicalHash = hexSha256(canonicalRequest.getBytes(StandardCharsets.UTF_8));
        String scope = dateStamp + "/" + region + "/cognito-idp/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n" + canonicalHash;
        byte[] signingKey = deriveSigningKey(dateStamp);
        String signature = hexHmacSha256(stringToSign.getBytes(StandardCharsets.UTF_8), signingKey);
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + scope + ", "
                + "SignedHeaders=" + signedHeaderNames + ", Signature=" + signature;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/x-amz-json-1.1")
                .header("X-Amz-Date", amzDate)
                .header("X-Amz-Target", "CognitoIdentityServiceProvider." + target)
                .header("Authorization", authorization);
        if (hasSessionToken) {
            builder.header("X-Amz-Security-Token", sessionToken);
        }
        return builder.POST(HttpRequest.BodyPublishers.ofString(payload)).build();
    }

    private byte[] deriveSigningKey(String dateStamp) throws Exception {
        byte[] secret = ("AWS4" + secretAccessKey).getBytes(StandardCharsets.UTF_8);
        byte[] dateKey = hmacSha256(dateStamp.getBytes(StandardCharsets.UTF_8), secret);
        byte[] regionKey = hmacSha256(region.getBytes(StandardCharsets.UTF_8), dateKey);
        byte[] serviceKey = hmacSha256("cognito-idp".getBytes(StandardCharsets.UTF_8), regionKey);
        return hmacSha256("aws4_request".getBytes(StandardCharsets.UTF_8), serviceKey);
    }

    private byte[] hmacSha256(byte[] data, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String hexHmacSha256(byte[] data, byte[] key) throws Exception {
        return toHex(hmacSha256(data, key));
    }

    private String hexSha256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(data));
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
