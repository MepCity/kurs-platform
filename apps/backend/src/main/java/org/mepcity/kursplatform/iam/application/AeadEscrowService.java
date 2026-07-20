package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import java.util.UUID;

public interface AeadEscrowService {

    EncryptedEscrow encrypt(UUID actorUserId, String operationType, UUID deviceIdentifier,
                            String tokenFingerprint, EscrowPayload payload);

    EscrowPayload decrypt(AuthReplayEscrow escrow);

    record EncryptedEscrow(byte[] ciphertext, String aeadKeyReference, byte[] aeadNonce, String aadContext) {
    }

    record EscrowPayload(OpaqueToken contextSelectionToken, OpaqueToken accessToken, OpaqueToken refreshToken) {
    }
}
