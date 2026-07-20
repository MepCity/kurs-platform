package org.mepcity.kursplatform.iam.infrastructure.stub;

import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.infrastructure.HmacSha256TokenHasher;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public class StubCognitoTokenVerifier implements CognitoTokenVerifier {

    private final String expectedIssuer;
    private final String expectedClientId;
    private final HmacSha256TokenHasher hasher;

    public StubCognitoTokenVerifier(String expectedIssuer, String expectedClientId, HmacSha256TokenHasher hasher) {
        this.expectedIssuer = expectedIssuer;
        this.expectedClientId = expectedClientId;
        this.hasher = hasher;
    }

    @Override
    public CognitoTokenClaims verify(String cognitoAccessToken) {
        if (cognitoAccessToken == null || cognitoAccessToken.isBlank()) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı boş.");
        }
        if (!cognitoAccessToken.startsWith("stub-cognito:")) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı biçimi geçersiz.");
        }
        String[] parts = cognitoAccessToken.substring("stub-cognito:".length()).split("\\|", 5);
        if (parts.length < 4) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı içeriği geçersiz.");
        }
        String subject = parts[0];
        String clientId = parts[1];
        long authTimeEpoch = Long.parseLong(parts[2]);
        long expEpoch = Long.parseLong(parts[3]);
        Instant authTime = Instant.ofEpochSecond(authTimeEpoch);
        Instant expiresAt = Instant.ofEpochSecond(expEpoch);
        if (!clientId.equals(expectedClientId)) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı beklenen cliente ait değil.");
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı süresi dolmuş.");
        }
        String fingerprint = hasher.fingerprint(cognitoAccessToken);
        return new CognitoTokenClaims(
                expectedIssuer, subject, clientId, "access",
                authTime, expiresAt, null, fingerprint);
    }

    public static String createStubToken(String subject, String clientId, Instant authTime, Instant expiresAt) {
        return "stub-cognito:" + subject + "|" + clientId + "|"
                + authTime.getEpochSecond() + "|" + expiresAt.getEpochSecond();
    }

    public static String createStubToken(String subject, String clientId, Instant authTime) {
        return createStubToken(subject, clientId, authTime, Instant.now().plus(1, ChronoUnit.HOURS));
    }

    public static String randomSubject() {
        return UUID.randomUUID().toString();
    }
}
