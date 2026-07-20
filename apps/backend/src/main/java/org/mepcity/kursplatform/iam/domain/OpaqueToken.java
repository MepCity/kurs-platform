package org.mepcity.kursplatform.iam.domain;

import java.security.SecureRandom;
import java.util.Base64;

public record OpaqueToken(String value, String hash) {

    public static OpaqueToken generate(TokenHasher hasher, SecureRandom random) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String tokenValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = hasher.hash(tokenValue);
        return new OpaqueToken(tokenValue, hash);
    }
}
