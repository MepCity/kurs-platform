package org.mepcity.kursplatform.iam.application;

import java.time.Instant;
import java.util.Map;

/**
 * Provider-independent, deliberately small boundary for security operational signals.
 * Implementations must only accept the safe identifiers supplied by {@link SecurityAlert}.
 */
public interface SecurityAlertSink {

    void emit(SecurityAlert alert);

    record SecurityAlert(Type type, Severity severity, Instant occurredAt, Map<String, String> attributes) {
        public SecurityAlert {
            if (type == null || severity == null || occurredAt == null || attributes == null) {
                throw new IllegalArgumentException("Security alert alanları zorunludur.");
            }
            Map<String, String> safeAttributes = Map.copyOf(attributes);
            if (safeAttributes.keySet().stream().anyMatch(key -> !key.matches("[a-zA-Z][a-zA-Z0-9]{0,63}")
                    || !safeAttributes.get(key).matches("[A-Za-z0-9._:-]{1,128}"))) {
                throw new IllegalArgumentException("Security alert güvenli olmayan özellik içeriyor.");
            }
            attributes = safeAttributes;
        }
    }

    enum Severity { WARNING, CRITICAL }

    enum Type {
        RECONCILIATION_LAG,
        UNKNOWN_SUBJECT,
        UNKNOWN_SECURITY_EVENT,
        POISON_EVENT,
        PROVIDER_UNAVAILABLE,
        CURSOR_STALLED,
        EVENT_REVOCATION_FAILED
    }
}
