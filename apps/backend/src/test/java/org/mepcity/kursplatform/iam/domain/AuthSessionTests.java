package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthSessionTests {

    @Test
    void isOrganizationScopedReturnsTrueForOrganizationScope() {
        AuthSession session = createSession(SessionScope.ORGANIZATION, UUID.randomUUID());
        assertThat(session.isOrganizationScoped()).isTrue();
        assertThat(session.isGlobalPlatformAdmin()).isFalse();
    }

    @Test
    void isGlobalPlatformAdminReturnsTrueForGlobalScope() {
        AuthSession session = createSession(SessionScope.GLOBAL_PLATFORM_ADMIN, null);
        assertThat(session.isGlobalPlatformAdmin()).isTrue();
        assertThat(session.isOrganizationScoped()).isFalse();
    }

    @Test
    void isAccessExpiredReturnsTrueWhenNowIsAfterAccessExpiresAt() {
        AuthSession session = new AuthSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SessionScope.ORGANIZATION, UUID.randomUUID(),
                Instant.now().minusSeconds(120), Instant.now().minusSeconds(60));
        assertThat(session.isAccessExpired(Instant.now())).isTrue();
    }

    @Test
    void isAccessExpiredReturnsFalseWhenNowIsBeforeAccessExpiresAt() {
        AuthSession session = new AuthSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                SessionScope.ORGANIZATION, UUID.randomUUID(),
                Instant.now().minusSeconds(120), Instant.now().plusSeconds(300));
        assertThat(session.isAccessExpired(Instant.now())).isFalse();
    }

    private AuthSession createSession(SessionScope scope, UUID membershipId) {
        return new AuthSession(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scope, membershipId, Instant.now().minusSeconds(60), Instant.now().plusSeconds(300));
    }
}
