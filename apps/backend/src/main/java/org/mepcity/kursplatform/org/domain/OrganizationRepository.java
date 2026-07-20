package org.mepcity.kursplatform.org.domain;

import java.util.Optional;
import java.util.UUID;

/** Persistence port; transaction and RLS context belong to the application service. */
public interface OrganizationRepository {

    Organization create(Organization organization);

    Optional<Organization> findById(UUID organizationId);

    /** Locks the visible organization before a lifecycle command revalidates its row version. */
    Optional<Organization> findByIdForUpdate(UUID organizationId);

    /** Updates only editable identity fields and increments row_version optimistically. */
    Optional<Organization> updateIdentity(Organization organization);

    /** Changes only status and increments row_version after the caller's FOR UPDATE read. */
    Optional<Organization> transitionStatus(
            UUID organizationId, OrganizationStatus expectedStatus,
            OrganizationStatus nextStatus, int expectedRowVersion, UUID updatedByUserId);
}
