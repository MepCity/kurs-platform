package org.mepcity.kursplatform.iam.application;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityAlertSinkTests {
    @Test
    void rejectsSensitiveOrFreeFormAlertValues() {
        assertThatThrownBy(() -> new SecurityAlertSink.SecurityAlert(
                SecurityAlertSink.Type.POISON_EVENT, SecurityAlertSink.Severity.CRITICAL,
                Instant.EPOCH, Map.of("payload", "password secret")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
