package org.mepcity.kursplatform.iam.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Typed audit event for the IAM_AUTH/GLOBAL flows IAM_GIRIS_OTURUM_API_SOZLESMESI.md §14 requires
 * to be audited: provider-token-exchange, platform-admin-activate, context-activation, the
 * disabled/revoked fail-closed branch, and provider-command completion. Every one of these six
 * {@code actionType}s always has a NULL {@code oldValue} in the {@code audit_action_catalog}
 * (V7 migration) — there is no "before" state for an access/security event — so this type omits
 * an old-value field entirely rather than carrying an always-null one.
 */
public record IamAuditEvent(
        UUID id,
        UUID organizationId,
        UUID actorUserId,
        String requestId,
        String actionType,
        EventScope eventScope,
        String targetEntityType,
        EventKind eventKind,
        UUID targetEntityId,
        Map<String, Object> newValue,
        Map<String, Object> eventMetadata) {

    public enum EventScope { GLOBAL, ORGANIZATION }

    public enum EventKind { ACCESS, SECURITY }

    public IamAuditEvent {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actionType, "actionType");
        Objects.requireNonNull(eventScope, "eventScope");
        Objects.requireNonNull(targetEntityType, "targetEntityType");
        Objects.requireNonNull(eventKind, "eventKind");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        newValue = newValue == null ? Map.of() : Map.copyOf(newValue);
        eventMetadata = eventMetadata == null ? Map.of() : Map.copyOf(eventMetadata);
        if (eventScope == EventScope.ORGANIZATION && organizationId == null) {
            throw new IllegalArgumentException("organizationId zorunludur (ORGANIZATION scope)");
        }
        if (eventScope == EventScope.GLOBAL && organizationId != null) {
            throw new IllegalArgumentException("organizationId GLOBAL scope için null olmalı");
        }
    }
}
