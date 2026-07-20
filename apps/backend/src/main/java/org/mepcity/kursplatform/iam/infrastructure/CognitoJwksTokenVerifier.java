package org.mepcity.kursplatform.iam.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CognitoJwksTokenVerifier implements CognitoTokenVerifier {

    private static final Logger LOG = LoggerFactory.getLogger(CognitoJwksTokenVerifier.class);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration JWKS_REFRESH_INTERVAL = Duration.ofMinutes(15);
    private static final String ALLOWED_ALGORITHM = "RS256";

    private final String expectedIssuer;
    private final String expectedClientId;
    private final HmacSha256TokenHasher hasher;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile Map<String, PublicKey> jwksCache = Map.of();
    private volatile Instant jwksLoadedAt = Instant.EPOCH;
    private final Object jwksLock = new Object();

    public CognitoJwksTokenVerifier(String expectedIssuer, String expectedClientId,
                                    HmacSha256TokenHasher hasher) {
        this.expectedIssuer = expectedIssuer;
        this.expectedClientId = expectedClientId;
        this.hasher = hasher;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(HTTP_TIMEOUT)
                .build();
    }

    @Override
    public CognitoTokenClaims verify(String cognitoAccessToken) {
        if (cognitoAccessToken == null || cognitoAccessToken.isBlank()) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı boş.");
        }
        String[] parts = cognitoAccessToken.split("\\.", 3);
        if (parts.length < 3) {
            throw new IamException("UNAUTHENTICATED", "Sağlayıcı tokenı biçimi geçersiz (JWT değil).");
        }
        JsonNode header = decodeJson(parts[0]);
        JsonNode payload = decodeJson(parts[1]);
        byte[] signature = decodeSignature(parts[2]);

        // Header fields (alg, kid) are structural: they select HOW to verify, not what to trust.
        // No payload claim is read for a trust/authorization decision until the signature below
        // has actually been checked against the key that `kid` names.
        String alg = header.path("alg").asText();
        if (!ALLOWED_ALGORITHM.equals(alg)) {
            throw new IamException("UNAUTHENTICATED", "İzin verilmeyen algoritma: " + alg);
        }
        String kid = header.path("kid").asText(null);
        if (kid == null || kid.isBlank()) {
            throw new IamException("UNAUTHENTICATED", "Token başlığında kid eksik.");
        }

        PublicKey publicKey = resolveKey(kid);
        verifySignature(parts[0] + "." + parts[1], signature, publicKey);

        // Signature confirmed the payload is authentic and untampered — claims are now safe to trust.
        String issuer = payload.path("iss").asText();
        if (!expectedIssuer.equals(issuer)) {
            throw new IamException("UNAUTHENTICATED", "Issuer beklenen değerle eşleşmiyor.");
        }
        String clientId = payload.path("client_id").asText(payload.path("aud").asText());
        if (!expectedClientId.equals(clientId)) {
            throw new IamException("UNAUTHENTICATED", "Cognito tokenı beklenen cliente ait değil.");
        }
        String tokenUse = payload.path("token_use").asText();
        if (!"access".equals(tokenUse)) {
            throw new IamException("UNAUTHENTICATED", "Yalnız access token kabul edilir. ID token reddedildi.");
        }
        String subject = payload.path("sub").asText();
        if (subject == null || subject.isBlank()) {
            throw new IamException("UNAUTHENTICATED", "Token subject eksik.");
        }
        long exp = payload.path("exp").asLong(0);
        long nbf = payload.path("nbf").asLong(0);
        long authTime = payload.path("auth_time").asLong(0);
        Instant now = Instant.now();
        if (exp == 0 || !Instant.ofEpochSecond(exp).isAfter(now)) {
            throw new IamException("UNAUTHENTICATED", "Token süresi dolmuş.");
        }
        if (nbf != 0 && Instant.ofEpochSecond(nbf).isAfter(now)) {
            throw new IamException("UNAUTHENTICATED", "Token henüz geçerli değil (nbf).");
        }
        if (authTime == 0) {
            throw new IamException("UNAUTHENTICATED", "auth_time claim eksik.");
        }

        String fingerprint = hasher.fingerprint(cognitoAccessToken);
        return new CognitoTokenClaims(
                issuer, subject, clientId, tokenUse,
                Instant.ofEpochSecond(authTime),
                Instant.ofEpochSecond(exp),
                nbf != 0 ? Instant.ofEpochSecond(nbf) : null,
                fingerprint);
    }

    private byte[] decodeSignature(String base64UrlSignature) {
        try {
            return Base64.getUrlDecoder().decode(base64UrlSignature);
        } catch (IllegalArgumentException e) {
            throw new IamException("UNAUTHENTICATED", "Token imza bölümü geçersiz Base64URL.", e);
        }
    }

    private PublicKey resolveKey(String kid) {
        ensureJwksFresh();
        PublicKey key = jwksCache.get(kid);
        if (key == null) {
            synchronized (jwksLock) {
                refreshJwks();
                key = jwksCache.get(kid);
            }
            if (key == null) {
                // Fail-closed: a single controlled refresh already happened above. An unknown kid
                // after that is either a genuinely unsupported key or an attempted key-confusion
                // attack — either way, reject rather than guess or fall back.
                throw new IamException("UNAUTHENTICATED", "JWKS anahtarı bulunamadı: " + kid);
            }
        }
        return key;
    }

    private void ensureJwksFresh() {
        if (jwksCache.isEmpty() || Instant.now().isAfter(jwksLoadedAt.plus(JWKS_REFRESH_INTERVAL))) {
            synchronized (jwksLock) {
                if (jwksCache.isEmpty() || Instant.now().isAfter(jwksLoadedAt.plus(JWKS_REFRESH_INTERVAL))) {
                    refreshJwks();
                }
            }
        }
    }

    private void refreshJwks() {
        String jwksUrl = expectedIssuer.endsWith("/")
                ? expectedIssuer + ".well-known/jwks.json"
                : expectedIssuer + "/.well-known/jwks.json";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jwksUrl))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.warn("JWKS endpoint returned status {}: {}", response.statusCode(), jwksUrl);
                if (jwksCache.isEmpty()) {
                    throw new IamException("PROVIDER_UNAVAILABLE", "JWKS anahtarları alınamadı.");
                }
                // Keep the prior healthy cache; a single bad response (e.g. a transient CDN 5xx
                // page, or a misconfigured issuer returning 200 with HTML) must not evict keys
                // that were valid moments ago and may still be signing live tokens.
                return;
            }
            Map<String, PublicKey> candidate = parseJwksKeys(response.body());
            if (candidate.isEmpty()) {
                // HTTP 200 but no usable RSA key — the body was empty, malformed, non-RSA, or every
                // key failed to parse. Atomically KEEP the existing cache rather than publishing an
                // empty one: publishing empty would unconditionally reject every subsequent token
                // (even ones signed by still-valid cached keys) until the next refresh succeeds.
                LOG.warn("JWKS response carried no usable RSA keys; keeping prior cache (size={})",
                        jwksCache.size());
                if (jwksCache.isEmpty()) {
                    throw new IamException("PROVIDER_UNAVAILABLE", "JWKS usable anahtar içermiyor.");
                }
                return;
            }
            // Atomic publish: assign the fully-built candidate map in one volatile write. Readers
            // that already hold a reference to the old map keep using it for the rest of their
            // verify() call; the next verify() re-reads the field and sees the new keys. No thread
            // can ever observe a partially-populated cache.
            this.jwksCache = candidate;
            this.jwksLoadedAt = Instant.now();
        } catch (IamException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("JWKS yenileme başarısız: {}", jwksUrl, e);
            if (jwksCache.isEmpty()) {
                throw new IamException("PROVIDER_UNAVAILABLE", "JWKS yenileme başarısız.", e);
            }
        }
    }

    private Map<String, PublicKey> parseJwksKeys(String body) throws Exception {
        JsonNode jwks = objectMapper.readTree(body);
        JsonNode keys = jwks.path("keys");
        if (!keys.isArray()) {
            LOG.warn("JWKS response missing keys array");
            return Map.of();
        }
        Map<String, PublicKey> parsed = new ConcurrentHashMap<>();
        for (JsonNode keyNode : keys) {
            String kty = keyNode.path("kty").asText();
            String kid = keyNode.path("kid").asText(null);
            if (!"RSA".equals(kty) || kid == null || kid.isBlank()) {
                continue;
            }
            try {
                String n = keyNode.path("n").asText();
                String e = keyNode.path("e").asText();
                PublicKey publicKey = buildRsaPublicKey(n, e);
                parsed.put(kid, publicKey);
            } catch (Exception ex) {
                LOG.warn("JWKS anahtarı çözülemedi: kid={}", kid, ex);
            }
        }
        return parsed;
    }

    private PublicKey buildRsaPublicKey(String nBase64, String eBase64) throws Exception {
        Base64.Decoder urlDecoder = Base64.getUrlDecoder();
        byte[] nBytes = urlDecoder.decode(nBase64);
        byte[] eBytes = urlDecoder.decode(eBase64);
        BigInteger modulus = new BigInteger(1, nBytes);
        BigInteger exponent = new BigInteger(1, eBytes);
        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulus, exponent);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }

    private void verifySignature(String signedContent, byte[] signature, PublicKey publicKey) {
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update(signedContent.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if (!sig.verify(signature)) {
                throw new IamException("UNAUTHENTICATED", "Token imzası geçersiz.");
            }
        } catch (IamException e) {
            throw e;
        } catch (Exception e) {
            throw new IamException("UNAUTHENTICATED", "Token imza doğrulaması başarısız.", e);
        }
    }

    private JsonNode decodeJson(String base64Url) {
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64Url);
            return objectMapper.readTree(decoded);
        } catch (Exception e) {
            throw new IamException("UNAUTHENTICATED", "Token bölümü çözülemedi.", e);
        }
    }
}
