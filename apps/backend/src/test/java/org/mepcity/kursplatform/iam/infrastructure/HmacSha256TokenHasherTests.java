package org.mepcity.kursplatform.iam.infrastructure;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class HmacSha256TokenHasherTests {

    @Test
    void hashProducesDeterministicOutput() {
        HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("test-pepper");
        String hash1 = hasher.hash("token-value-1");
        String hash2 = hasher.hash("token-value-1");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void hashProducesDifferentOutputForDifferentInputs() {
        HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("test-pepper");
        String hash1 = hasher.hash("token-value-1");
        String hash2 = hasher.hash("token-value-2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hashWithPepperProducesDifferentOutputFromHash() {
        HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("test-pepper");
        String hash = hasher.hash("token-value-1");
        String hashWithPepper = hasher.hashWithPepper("token-value-1", "extra-pepper");
        assertThat(hash).isNotEqualTo(hashWithPepper);
    }

    @Test
    void fingerprintProducesDeterministicOutput() {
        HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("test-pepper");
        String fp1 = hasher.fingerprint("token-value-1");
        String fp2 = hasher.fingerprint("token-value-1");
        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void differentPeppersProduceDifferentHashes() {
        HmacSha256TokenHasher hasher1 = new HmacSha256TokenHasher("pepper-1");
        HmacSha256TokenHasher hasher2 = new HmacSha256TokenHasher("pepper-2");
        String hash1 = hasher1.hash("token-value-1");
        String hash2 = hasher2.hash("token-value-1");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void opaqueTokenGenerateUsesHasher() {
        HmacSha256TokenHasher hasher = new HmacSha256TokenHasher("test-pepper");
        SecureRandom random = new SecureRandom();
        OpaqueToken token = OpaqueToken.generate(hasher, random);
        String expectedHash = hasher.hash(token.value());
        assertThat(token.hash()).isEqualTo(expectedHash);
    }
}
