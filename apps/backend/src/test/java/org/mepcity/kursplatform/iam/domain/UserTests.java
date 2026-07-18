package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserTests {

    @Test
    void activeUserIsActive() {
        var user = createUser(UserStatus.ACTIVE);
        assertThat(user.isActive()).isTrue();
        assertThat(user.isProvisioning()).isFalse();
        assertThat(user.isSuspended()).isFalse();
    }

    @Test
    void provisioningUserIsProvisioning() {
        var user = createUser(UserStatus.PROVISIONING);
        assertThat(user.isActive()).isFalse();
        assertThat(user.isProvisioning()).isTrue();
    }

    @Test
    void suspendedUserIsSuspended() {
        var user = createUser(UserStatus.SUSPENDED);
        assertThat(user.isActive()).isFalse();
        assertThat(user.isSuspended()).isTrue();
    }

    private User createUser(UserStatus status) {
        return new User(UUID.randomUUID(), status, Instant.EPOCH,
                Instant.now(), Instant.now(), 1, null, null);
    }
}
