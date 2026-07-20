package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokenTests {

    @Test
    void generateProducesTokenWithHash() {
        TokenHasher hasher = new TestTokenHasher();
        SecureRandom random = new SecureRandom();
        OpaqueToken token = OpaqueToken.generate(hasher, random);
        assertThat(token.value()).isNotBlank();
        assertThat(token.hash()).isNotBlank();
        assertThat(token.value()).isNotEqualTo(token.hash());
    }

    @Test
    void generateProducesDifferentTokensEachTime() {
        TokenHasher hasher = new TestTokenHasher();
        SecureRandom random = new SecureRandom();
        OpaqueToken token1 = OpaqueToken.generate(hasher, random);
        OpaqueToken token2 = OpaqueToken.generate(hasher, random);
        assertThat(token1.value()).isNotEqualTo(token2.value());
        assertThat(token1.hash()).isNotEqualTo(token2.hash());
    }

    private static class TestTokenHasher implements TokenHasher {
        @Override
        public String hash(String tokenValue) {
            return "hash-" + tokenValue;
        }

        @Override
        public String hashWithPepper(String tokenValue, String pepper) {
            return "hash-" + pepper + "-" + tokenValue;
        }
    }
}
