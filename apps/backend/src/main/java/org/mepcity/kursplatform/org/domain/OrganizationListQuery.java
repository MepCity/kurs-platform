package org.mepcity.kursplatform.org.domain;

import java.time.Instant;
import java.util.UUID;

/** Closed, SQL-safe list query; controllers never pass SQL fragments to persistence. */
public record OrganizationListQuery(OrganizationStatus status, String search, Sort sort, Order order,
                                    int limit, Position position) {
    public enum Sort { NAME, CREATED_AT }
    public enum Order { ASC, DESC }
    public sealed interface Position permits NamePosition, CreatedAtPosition { UUID id(); }
    public record NamePosition(String value, UUID id) implements Position { }
    public record CreatedAtPosition(Instant value, UUID id) implements Position { }
    public OrganizationListQuery {
        if (limit < 1) throw new IllegalArgumentException("limit");
        if (position != null && ((sort == Sort.NAME && !(position instanceof NamePosition))
                || (sort == Sort.CREATED_AT && !(position instanceof CreatedAtPosition)))) {
            throw new IllegalArgumentException("position");
        }
    }
}
