package org.mepcity.kursplatform.iam.application;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.*;

import java.security.SecureRandom;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SessionRefreshServiceTests {
    @Test
    void reusedRefreshTokenRevokesTheWholeFamily() {
        IamAuthRepository repository = mock(IamAuthRepository.class);
        IamTransactionExecutor transactions = mock(IamTransactionExecutor.class);
        TokenHasher hasher = new TokenHasher() {
            public String hash(String value) { return "hash:" + value; }
            public String hashWithPepper(String value, String pepper) { return "hash:" + value + ":" + pepper; }
        };
        Clock clock = Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC);
        UUID familyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        RefreshToken used = new RefreshToken(UUID.randomUUID(), familyId, null, "hash:old", "access", Instant.now(), Instant.now(), Instant.now(), Instant.now().plusSeconds(3600), null);
        when(repository.findRefreshTokenByHash("hash:old")).thenReturn(Optional.of(used));
        when(repository.findRefreshTokenByHashForUpdate("hash:old")).thenReturn(Optional.of(used));
        when(repository.findRefreshTokenFamilyById(familyId)).thenReturn(Optional.of(new RefreshTokenFamily(familyId, userId, deviceId,
                null, Instant.now(), null, null, Instant.now())));
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get())
                .when(transactions).executeInIamAuthScope(any(), any(), any());
        doNothing().when(transactions).requireSecurityRevoke();
        SessionRefreshService service = new SessionRefreshService(repository, hasher, new SecureRandom(), clock, transactions, settings());

        assertThatThrownBy(() -> service.refresh("old", "mutation-1"))
                .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("SESSION_REVOKED");
        verify(repository).revokeRefreshTokensInFamily(familyId, clock.instant());
        verify(repository).revokeRefreshTokenFamily(familyId, clock.instant());
    }

    private IamServiceSettings settings() {
        return new IamServiceSettings() {
            public Duration accessTokenTtl() { return Duration.ofMinutes(10); }
            public Duration refreshTokenTtl() { return Duration.ofDays(30); }
            public Duration contextSelectionTokenTtl() { return Duration.ofMinutes(5); }
            public Duration activationEscrowTtl() { return Duration.ofMinutes(5); }
            public Duration idempotencyRetention() { return Duration.ofDays(1); }
            public boolean providerCommandWorkerEnabled() { return false; }
            public Duration providerCommandPollInterval() { return Duration.ofSeconds(1); }
            public int providerCommandBatchLimit() { return 1; }
            public Duration providerCommandLeaseTtl() { return Duration.ofSeconds(1); }
            public int providerCommandMaxAttempts() { return 1; }
            public Duration providerCommandBackoffBase() { return Duration.ofSeconds(1); }
            public Duration providerCommandBackoffMax() { return Duration.ofSeconds(1); }
            public double providerCommandJitter() { return 0; }
        };
    }
}
