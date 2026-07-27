package org.mepcity.kursplatform.org.application;

import java.time.Instant;
import java.util.UUID;
import org.mepcity.kursplatform.org.domain.OrganizationListQuery;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;

/** Fully-bound cursor payload. Its closed value hierarchy prevents sort/value mismatches. */
public record OrganizationListCursor(Context context, LastValue lastValue, UUID lastOrganizationId, Instant expiresAt) {
    public record Context(UUID actorUserId, OrganizationListTransaction.Scope scope, OrganizationStatus status,
                          String search, OrganizationListQuery.Sort sort, OrganizationListQuery.Order order, int limit) { }
    public sealed interface LastValue permits Name, CreatedAt { }
    public record Name(String value) implements LastValue { }
    public record CreatedAt(Instant value) implements LastValue { }
}
