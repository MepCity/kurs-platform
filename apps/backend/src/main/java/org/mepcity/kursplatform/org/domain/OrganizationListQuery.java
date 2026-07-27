package org.mepcity.kursplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

/** Closed, SQL-safe list query; controllers never pass SQL fragments to persistence. */
public record OrganizationListQuery(OrganizationStatus status, String search, Sort sort, Order order,
                                    int limit, Object lastValue, UUID lastId) {
    public enum Sort { NAME, CREATED_AT }
    public enum Order { ASC, DESC }
    public OrganizationListQuery {
        if (limit < 1) throw new IllegalArgumentException("limit");
        if (lastValue != null && !(lastValue instanceof String) && !(lastValue instanceof Instant)) {
            throw new IllegalArgumentException("lastValue");
        }
    }
}
