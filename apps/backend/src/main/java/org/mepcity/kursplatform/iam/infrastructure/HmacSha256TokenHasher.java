package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.domain.TokenHasher;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public class HmacSha256TokenHasher implements TokenHasher {

    private final byte[] pepper;

    public HmacSha256TokenHasher(String pepper) {
        this.pepper = pepper.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String hash(String tokenValue) {
        return hashWithPepper(tokenValue, "");
    }

    @Override
    public String hashWithPepper(String tokenValue, String pepper) {
        try {
            byte[] combined = (pepper + ":" + new String(this.pepper, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(combined, "HmacSHA256"));
            byte[] result = mac.doFinal(tokenValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Token hash hesaplanamadı", e);
        }
    }

    public String fingerprint(String tokenValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] result = digest.digest(tokenValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().withoutPadding().encodeToString(result);
        } catch (Exception e) {
            throw new IllegalStateException("Fingerprint hesaplanamadı", e);
        }
    }
}
