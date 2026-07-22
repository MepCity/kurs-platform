package org.mepcity.kursplatform.iam.application.contract;

import java.util.UUID;

/** Minimal published authentication context for another module's application service. */
public record ActiveSession(UUID userId, Scope scope, UUID organizationId) {
    public enum Scope { GLOBAL_PLATFORM_ADMIN, ORGANIZATION }

    public ActiveSession {
        java.util.Objects.requireNonNull(userId, "userId");
        java.util.Objects.requireNonNull(scope, "scope");
        if ((scope == Scope.GLOBAL_PLATFORM_ADMIN) != (organizationId == null)) {
            throw new IllegalArgumentException("Global session has no organization; organization session requires one.");
        }
    }

    public static ActiveSession globalPlatformAdmin(UUID userId) {
        return new ActiveSession(userId, Scope.GLOBAL_PLATFORM_ADMIN, null);
    }

    public static ActiveSession organization(UUID userId, UUID organizationId) {
        return new ActiveSession(userId, Scope.ORGANIZATION, java.util.Objects.requireNonNull(organizationId, "organizationId"));
    }

    public boolean isGlobalPlatformAdmin() {
        return scope == Scope.GLOBAL_PLATFORM_ADMIN;
    }
}
