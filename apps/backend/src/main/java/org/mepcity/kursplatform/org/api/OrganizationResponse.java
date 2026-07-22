package org.mepcity.kursplatform.org.api;

import java.time.Instant;
import java.util.UUID;
import org.mepcity.kursplatform.org.domain.Organization;

/** Public representation deliberately excludes internal ownership and branding columns. */
public record OrganizationResponse(UUID id, String name, String shortName, String defaultTimezone,
                                   String status, Instant createdAt, Instant updatedAt, int rowVersion) {
    static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.id(), organization.name(), organization.shortName(),
                organization.defaultTimezone(), organization.status().name(), organization.createdAt(),
                organization.updatedAt(), organization.rowVersion());
    }
}
