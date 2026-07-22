package org.mepcity.kursplatform.iam.application.contract;

import java.util.UUID;

/** Minimal published authentication context for another module's application service. */
public record ActiveSession(UUID userId, String scope) {
    public boolean isGlobalPlatformAdmin() {
        return "GLOBAL_PLATFORM_ADMIN".equals(scope);
    }
}
