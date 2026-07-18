package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenFamilyTests {

    @Test
    void familyWithMembershipIdIsOrganizationScoped() {
        var family = createFamily(UUID.randomUUID());
        assertThat(family.isOrganizationScoped()).isTrue();
        assertThat(family.isGlobalPlatformAdmin()).isFalse();
    }

    @Test
    void familyWithoutMembershipIdIsGlobalPlatformAdmin() {
        var family = createFamily(null);
        assertThat(family.isOrganizationScoped()).isFalse();
        assertThat(family.isGlobalPlatformAdmin()).isTrue();
    }

    @Test
    void familyWithoutRevokedAtIsNotRevoked() {
        var family = createFamily(UUID.randomUUID());
        assertThat(family.isRevoked()).isFalse();
    }

    @Test
    void familyWithRevokedAtIsRevoked() {
        var family = new RefreshTokenFamily(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), Instant.now(), 1,
                Instant.now(), Instant.now());
        assertThat(family.isRevoked()).isTrue();
    }

    private RefreshTokenFamily createFamily(UUID membershipId) {
        return new RefreshTokenFamily(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), membershipId, Instant.now(), 1,
                null, Instant.now());
    }
}
