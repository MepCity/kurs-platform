package org.mepcity.kursplatform.org.application;

import org.mepcity.kursplatform.org.domain.Organization;

/** Serializes the safe public organization representation stored for idempotency replay. */
@FunctionalInterface
public interface OrganizationResultSerializer {
    String serialize(Organization organization);
}
