package org.mepcity.kursplatform.iam.infrastructure;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.EscrowStatus;
import org.mepcity.kursplatform.iam.domain.OpaqueToken;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AeadEscrowServiceImplTests {

    @Test
    void encryptDecryptRoundTripPreservesContextToken() {
        AeadEscrowServiceImpl escrowService = new AeadEscrowServiceImpl("test-secret-key");
        UUID actorUserId = UUID.randomUUID();
        UUID deviceIdentifier = UUID.randomUUID();
        String operationType = "PROVIDER_TOKEN_EXCHANGE";
        String tokenFingerprint = "fp-123";
        OpaqueToken contextToken = new OpaqueToken("ctx-val-1", "ctx-hash-1");

        AeadEscrowService.EscrowPayload payload = new AeadEscrowService.EscrowPayload(contextToken, null, null);
        AeadEscrowService.EncryptedEscrow encrypted = escrowService.encrypt(
                actorUserId, operationType, deviceIdentifier, tokenFingerprint, payload);

        assertThat(encrypted.ciphertext()).isNotEmpty();
        assertThat(encrypted.aeadKeyReference()).isNotBlank();
        assertThat(encrypted.aeadNonce()).isNotEmpty();
        assertThat(encrypted.aadContext()).isNotBlank();

        AuthReplayEscrow escrow = new AuthReplayEscrow(
                UUID.randomUUID(), UUID.randomUUID(), actorUserId,
                operationType, deviceIdentifier, tokenFingerprint,
                UUID.randomUUID(), null, null,
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(),
                encrypted.aadContext(), EscrowStatus.READY,
                Instant.now().plusSeconds(300), Instant.now(), null);

        AeadEscrowService.EscrowPayload decrypted = escrowService.decrypt(escrow);
        assertThat(decrypted.contextSelectionToken()).isNotNull();
        assertThat(decrypted.contextSelectionToken().value()).isEqualTo("ctx-val-1");
        assertThat(decrypted.contextSelectionToken().hash()).isEqualTo("ctx-hash-1");
    }

    @Test
    void encryptDecryptRoundTripPreservesAccessAndRefreshTokens() {
        AeadEscrowServiceImpl escrowService = new AeadEscrowServiceImpl("test-secret-key");
        UUID actorUserId = UUID.randomUUID();
        UUID deviceIdentifier = UUID.randomUUID();
        OpaqueToken accessToken = new OpaqueToken("access-val-1", "access-hash-1");
        OpaqueToken refreshToken = new OpaqueToken("refresh-val-1", "refresh-hash-1");

        AeadEscrowService.EscrowPayload payload = new AeadEscrowService.EscrowPayload(null, accessToken, refreshToken);
        AeadEscrowService.EncryptedEscrow encrypted = escrowService.encrypt(
                actorUserId, "CONTEXT_ACTIVATE", deviceIdentifier, "fp-456", payload);

        AuthReplayEscrow escrow = new AuthReplayEscrow(
                UUID.randomUUID(), UUID.randomUUID(), actorUserId,
                "CONTEXT_ACTIVATE", deviceIdentifier, "fp-456",
                null, UUID.randomUUID(), UUID.randomUUID(),
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(),
                encrypted.aadContext(), EscrowStatus.READY,
                Instant.now().plusSeconds(300), Instant.now(), null);

        AeadEscrowService.EscrowPayload decrypted = escrowService.decrypt(escrow);
        assertThat(decrypted.accessToken()).isNotNull();
        assertThat(decrypted.accessToken().value()).isEqualTo("access-val-1");
        assertThat(decrypted.refreshToken()).isNotNull();
        assertThat(decrypted.refreshToken().value()).isEqualTo("refresh-val-1");
    }

    @Test
    void decryptWithWrongAadFails() {
        AeadEscrowServiceImpl escrowService = new AeadEscrowServiceImpl("test-secret-key");
        UUID actorUserId = UUID.randomUUID();
        UUID deviceIdentifier = UUID.randomUUID();
        OpaqueToken contextToken = new OpaqueToken("ctx-val-1", "ctx-hash-1");

        AeadEscrowService.EscrowPayload payload = new AeadEscrowService.EscrowPayload(contextToken, null, null);
        AeadEscrowService.EncryptedEscrow encrypted = escrowService.encrypt(
                actorUserId, "PROVIDER_TOKEN_EXCHANGE", deviceIdentifier, "correct-fp", payload);

        AuthReplayEscrow escrowWithWrongAad = new AuthReplayEscrow(
                UUID.randomUUID(), UUID.randomUUID(), actorUserId,
                "PROVIDER_TOKEN_EXCHANGE", deviceIdentifier, "wrong-fp",
                UUID.randomUUID(), null, null,
                encrypted.ciphertext(), encrypted.aeadKeyReference(), encrypted.aeadNonce(),
                "wrong-aad", EscrowStatus.READY,
                Instant.now().plusSeconds(300), Instant.now(), null);

        try {
            escrowService.decrypt(escrowWithWrongAad);
            assertThat(false).as("Decrypt should have failed").isTrue();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(org.mepcity.kursplatform.iam.domain.IamException.class);
        }
    }
}
