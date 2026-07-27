package org.mepcity.kursplatform.org.application;

import java.util.UUID;
import java.util.function.Supplier;

/** Executes an ORG_LIST operation with the least-privileged RLS context already established. */
@FunctionalInterface
public interface OrganizationListTransaction {
    <T> T execute(UUID actorUserId, Scope scope, UUID organizationId, Supplier<T> action);
    enum Scope { GLOBAL, ORGANIZATION }
}
