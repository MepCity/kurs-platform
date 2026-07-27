package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.mepcity.kursplatform.org.application.InvalidCursorException;
import org.mepcity.kursplatform.org.application.OrganizationListCursor;
import org.mepcity.kursplatform.org.application.OrganizationListCursorCodec;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;

/** AES-GCM keeps cursor state opaque and authenticates every actor/query binding as AAD. */
public final class AesGcmOrganizationListCursorCodec implements OrganizationListCursorCodec {
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private static final String PURPOSE = "org-list-cursor-v1";
    private final SecretKeySpec key;
    private final Clock clock;
    private final SecureRandom random;

    public AesGcmOrganizationListCursorCodec(String secret, Clock clock, SecureRandom random) {
        try {
            // Local/test deployments that have no secret manager still get a process-local random
            // key. A restart invalidates cursors safely; production supplies the external secret.
            if (secret == null || secret.isBlank()) {
                byte[] generated = new byte[32];
                random.nextBytes(generated);
                secret = Base64.getEncoder().encodeToString(generated);
            }
            key = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                    .digest((PURPOSE + "|" + secret).getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception exception) { throw new IllegalStateException("Cursor anahtarı üretilemedi", exception); }
        this.clock = clock;
        this.random = random;
    }

    @Override
    public String encode(OrganizationListCursor cursor) {
        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            random.nextBytes(nonce);
            byte[] value = value(cursor.lastValue());
            ByteBuffer plaintext = ByteBuffer.allocate(8 + 1 + 4 + value.length + 16);
            plaintext.putLong(cursor.expiresAt().toEpochMilli());
            plaintext.put((byte) (cursor.lastValue() instanceof OrganizationListCursor.Name ? 1 : 2));
            plaintext.putInt(value.length).put(value);
            plaintext.putLong(cursor.lastOrganizationId().getMostSignificantBits()).putLong(cursor.lastOrganizationId().getLeastSignificantBits());
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, cursor.context());
            byte[] encrypted = cipher.doFinal(plaintext.array());
            return Base64.getUrlEncoder().withoutPadding().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                    .put(nonce).put(encrypted).array());
        } catch (Exception exception) { throw new IllegalStateException("Cursor oluşturulamadı", exception); }
    }

    @Override
    public OrganizationListCursor decode(String token, OrganizationListCursor.Context expected) {
        try {
            byte[] input = Base64.getUrlDecoder().decode(token);
            if (input.length <= NONCE_LENGTH) throw invalid();
            ByteBuffer source = ByteBuffer.wrap(input);
            byte[] nonce = new byte[NONCE_LENGTH]; source.get(nonce);
            byte[] plaintext = cipher(Cipher.DECRYPT_MODE, nonce, expected)
                    .doFinal(source.array(), source.position(), source.remaining());
            ByteBuffer value = ByteBuffer.wrap(plaintext);
            Instant expiresAt = Instant.ofEpochMilli(value.getLong());
            byte type = value.get();
            int length = value.getInt();
            if (length < 0 || length > value.remaining() - 16 || !expiresAt.isAfter(clock.instant())) throw invalid();
            byte[] raw = new byte[length]; value.get(raw);
            UUID id = new UUID(value.getLong(), value.getLong());
            if (value.hasRemaining()) throw invalid();
            OrganizationListCursor.LastValue last;
            if (type == 1 && expected.sort() == OrganizationListQuery.Sort.NAME) {
                last = new OrganizationListCursor.Name(new String(raw, StandardCharsets.UTF_8));
            } else if (type == 2 && expected.sort() == OrganizationListQuery.Sort.CREATED_AT && raw.length == 8) {
                last = new OrganizationListCursor.CreatedAt(Instant.ofEpochMilli(ByteBuffer.wrap(raw).getLong()));
            } else {
                throw invalid();
            }
            return new OrganizationListCursor(expected, last, id, expiresAt);
        } catch (InvalidCursorException exception) { throw exception;
        } catch (Exception exception) { throw invalid(); }
    }

    private static byte[] value(OrganizationListCursor.LastValue value) {
        if (value instanceof OrganizationListCursor.Name name) return name.value().getBytes(StandardCharsets.UTF_8);
        ByteBuffer bytes = ByteBuffer.allocate(8); bytes.putLong(((OrganizationListCursor.CreatedAt) value).value().toEpochMilli());
        return bytes.array();
    }

    private Cipher cipher(int mode, byte[] nonce, OrganizationListCursor.Context context) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, key, new GCMParameterSpec(TAG_LENGTH, nonce));
        cipher.updateAAD((PURPOSE + "|" + context.actorUserId() + "|" + context.scope() + "|" + context.status()
                + "|" + context.search() + "|" + context.sort() + "|" + context.order() + "|" + context.limit())
                .getBytes(StandardCharsets.UTF_8));
        return cipher;
    }

    private static InvalidCursorException invalid() { return new InvalidCursorException(); }
}
