package org.mepcity.kursplatform.iam.application;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.TokenHasher;

/**
 * AEAD-protected, query-bound device-list cursor.  The key is derived from the configured token
 * pepper through the existing {@link TokenHasher} port; neither the position nor the actor is
 * visible to a client and a cursor cannot be moved between actors or page sizes.
 */
final class DeviceCursorCodec {
    private static final String PURPOSE = "iam-device-list-cursor-v1";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom random;
    private final Instant now;
    private final Duration ttl;

    DeviceCursorCodec(TokenHasher hasher, Instant now, Duration ttl) {
        this(hasher, now, ttl, new SecureRandom());
    }

    DeviceCursorCodec(TokenHasher hasher, Instant now, Duration ttl, SecureRandom random) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Cursor TTL pozitif olmalıdır.");
        }
        this.key = new SecretKeySpec(Base64.getDecoder().decode(hasher.hashWithPepper(PURPOSE, PURPOSE)), "AES");
        this.now = now;
        this.ttl = ttl;
        this.random = random;
    }

    String encode(UUID actor, int limit, Instant trustedAt, UUID id) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            ByteBuffer plaintext = ByteBuffer.allocate(8 + 8 + 16);
            plaintext.putLong(now.plus(ttl).toEpochMilli()).putLong(trustedAt.toEpochMilli())
                    .putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits());
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, actor, limit);
            byte[] encrypted = cipher.doFinal(plaintext.array());
            ByteBuffer token = ByteBuffer.allocate(NONCE_BYTES + encrypted.length);
            token.put(nonce).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Cursor oluşturulamadı.", exception);
        }
    }

    Cursor decode(UUID actor, int limit, String cursor) {
        try {
            byte[] token = Base64.getUrlDecoder().decode(cursor);
            if (token.length <= NONCE_BYTES) throw invalid();
            ByteBuffer buffer = ByteBuffer.wrap(token);
            byte[] nonce = new byte[NONCE_BYTES];
            buffer.get(nonce);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, nonce, actor, limit).doFinal(encrypted);
            if (plaintext.length != 32) throw invalid();
            ByteBuffer position = ByteBuffer.wrap(plaintext);
            Instant expiresAt = Instant.ofEpochMilli(position.getLong());
            if (!expiresAt.isAfter(now)) throw invalid();
            return new Cursor(Instant.ofEpochMilli(position.getLong()), new UUID(position.getLong(), position.getLong()));
        } catch (IamException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private Cipher cipher(int mode, byte[] nonce, UUID actor, int limit) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD((PURPOSE + "|" + actor + "|" + limit).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private IamException invalid() {
        return new IamException("INVALID_CURSOR", "Cursor geçersiz veya süresi dolmuş.");
    }

    record Cursor(Instant trustedAt, UUID id) { }
}
