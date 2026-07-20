package org.mepcity.kursplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

public record Organization(
        UUID id,
        String name,
        String shortName,
        String primaryColor,
        String secondaryColor,
        OrganizationStatus status,
        String defaultTimezone,
        Instant createdAt,
        Instant updatedAt,
        int rowVersion,
        UUID createdByUserId,
        UUID updatedByUserId
) {

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public boolean isSuspended() {
        return status == OrganizationStatus.SUSPENDED;
    }

    public boolean isArchived() {
        return status == OrganizationStatus.ARCHIVED;
    }
}
