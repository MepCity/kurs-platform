package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.UUID;

public class AeadEscrowServiceImpl implements AeadEscrowService {

    private static final int NONCE_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final String KEY_REFERENCE = "iam-escrow-v1";

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom;

    public AeadEscrowServiceImpl(String secret) {
        byte[] keyBytes = deriveKey(secret);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }

    @Override
    public EncryptedEscrow encrypt(UUID actorUserId, String operationType, UUID deviceIdentifier,
                                   String tokenFingerprint, EscrowPayload payload) {
        try {
            String plaintext = serializePayload(payload);
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);
            String aadContext = actorUserId + "|" + operationType + "|" + deviceIdentifier + "|" + tokenFingerprint;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(aadContext.getBytes(StandardCharsets.UTF_8));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedEscrow(ciphertext, KEY_REFERENCE, nonce, aadContext);
        } catch (Exception e) {
            throw new IamException("INTERNAL_ERROR", "Escrow şifreleme başarısız.", e);
        }
    }

    @Override
    public EscrowPayload decrypt(AuthReplayEscrow escrow) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, escrow.aeadNonce()));
            cipher.updateAAD(escrow.aadContext().getBytes(StandardCharsets.UTF_8));
            byte[] plaintext = cipher.doFinal(escrow.ciphertext());
            return deserializePayload(new String(plaintext, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IamException("UNAUTHENTICATED", "Escrow çözme başarısız. Oturum sonlanmış olabilir.", e);
        }
    }

    private String serializePayload(EscrowPayload payload) {
        StringBuilder sb = new StringBuilder();
        sb.append(payload.contextSelectionToken() != null ? "1" : "0");
        if (payload.contextSelectionToken() != null) {
            sb.append(":").append(payload.contextSelectionToken().value())
              .append(":").append(payload.contextSelectionToken().hash());
        }
        sb.append("|");
        sb.append(payload.accessToken() != null ? "1" : "0");
        if (payload.accessToken() != null) {
            sb.append(":").append(payload.accessToken().value())
              .append(":").append(payload.accessToken().hash());
        }
        sb.append("|");
        sb.append(payload.refreshToken() != null ? "1" : "0");
        if (payload.refreshToken() != null) {
            sb.append(":").append(payload.refreshToken().value())
              .append(":").append(payload.refreshToken().hash());
        }
        return sb.toString();
    }

    private EscrowPayload deserializePayload(String serialized) {
        String[] parts = serialized.split("\\|", 3);
        OpaqueToken contextToken = parseToken(parts[0]);
        OpaqueToken accessToken = parseToken(parts[1]);
        OpaqueToken refreshToken = parseToken(parts.length > 2 ? parts[2] : "0");
        return new EscrowPayload(contextToken, accessToken, refreshToken);
    }

    private OpaqueToken parseToken(String part) {
        if (part == null || part.isEmpty() || part.startsWith("0")) {
            return null;
        }
        String[] segments = part.split(":", 3);
        if (segments.length < 3) {
            return null;
        }
        return new OpaqueToken(segments[1], segments[2]);
    }

    private byte[] deriveKey(String secret) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(secret.getBytes(StandardCharsets.UTF_8));
            byte[] key = new byte[32];
            System.arraycopy(hash, 0, key, 0, 32);
            return key;
        } catch (Exception e) {
            throw new IllegalStateException("Escrow anahtarı türetilemedi", e);
        }
    }
}
