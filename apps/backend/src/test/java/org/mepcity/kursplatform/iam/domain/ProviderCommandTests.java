package org.mepcity.kursplatform.iam.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderCommandTests {

    @Test
    void isTerminalReturnsTrueForCompletedAndFailed() {
        ProviderCommand completed = createCommand(ProviderCommandStatus.COMPLETED);
        ProviderCommand failed = createCommand(ProviderCommandStatus.FAILED);
        assertThat(completed.isTerminal()).isTrue();
        assertThat(failed.isTerminal()).isTrue();
    }

    @Test
    void isTerminalReturnsFalseForPendingAndClaimed() {
        ProviderCommand pending = createCommand(ProviderCommandStatus.PENDING);
        ProviderCommand claimed = createCommand(ProviderCommandStatus.CLAIMED);
        assertThat(pending.isTerminal()).isFalse();
        assertThat(claimed.isTerminal()).isFalse();
    }

    @Test
    void isUserLifecycleCommandReturnsTrueForDisableLogoutReset() {
        ProviderCommand disable = createCommand(ProviderCommandType.USER_DISABLE, ProviderCommandStatus.PENDING);
        ProviderCommand logout = createCommand(ProviderCommandType.USER_LOGOUT, ProviderCommandStatus.PENDING);
        ProviderCommand reset = createCommand(ProviderCommandType.PASSWORD_RESET, ProviderCommandStatus.PENDING);
        assertThat(disable.isUserLifecycleCommand()).isTrue();
        assertThat(logout.isUserLifecycleCommand()).isTrue();
        assertThat(reset.isUserLifecycleCommand()).isTrue();
    }

    @Test
    void isProvisioningCommandReturnsTrueOnlyForTeacherAccountCreate() {
        ProviderCommand provisioning = createCommand(ProviderCommandType.TEACHER_ACCOUNT_CREATE, ProviderCommandStatus.PENDING);
        ProviderCommand disable = createCommand(ProviderCommandType.USER_DISABLE, ProviderCommandStatus.PENDING);
        assertThat(provisioning.isProvisioningCommand()).isTrue();
        assertThat(disable.isProvisioningCommand()).isFalse();
    }

    private ProviderCommand createCommand(ProviderCommandStatus status) {
        return createCommand(ProviderCommandType.USER_DISABLE, status);
    }

    private ProviderCommand createCommand(ProviderCommandType type, ProviderCommandStatus status) {
        return new ProviderCommand(
                UUID.randomUUID(), "idem-key", "cognito", type,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                status, 0, Instant.now(), null, 0, null,
                Instant.now(), null, null);
    }
}
