package org.mepcity.kursplatform.iam.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.TokenHasher;

/** Opaque, actor-bound and MAC-protected pagination cursor; clients cannot manufacture a valid value. */
final class DeviceCursorCodec {
    private static final String PURPOSE = "iam-device-list-cursor-v1";
    private final TokenHasher hasher;
    DeviceCursorCodec(TokenHasher hasher) { this.hasher = hasher; }

    String encode(UUID actor, Instant trustedAt, UUID id) {
        String payload = actor + "|" + trustedAt + "|" + id;
        String signed = payload + "|" + hasher.hashWithPepper(payload, PURPOSE);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signed.getBytes(StandardCharsets.UTF_8));
    }

    Cursor decode(UUID actor, String cursor) {
        try {
            String signed = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = signed.split("\\|", -1);
            if (parts.length != 4 || !actor.toString().equals(parts[0])) throw invalid();
            String payload = String.join("|", parts[0], parts[1], parts[2]);
            if (!java.security.MessageDigest.isEqual(hasher.hashWithPepper(payload, PURPOSE).getBytes(StandardCharsets.UTF_8),
                    parts[3].getBytes(StandardCharsets.UTF_8))) throw invalid();
            return new Cursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (IllegalArgumentException exception) { throw invalid(); }
    }
    private IamException invalid() { return new IamException("INVALID_REQUEST", "Cursor geçersiz."); }
    record Cursor(Instant trustedAt, UUID id) { }
}
