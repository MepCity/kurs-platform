package org.mepcity.kursplatform.iam.api;

import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ContextSelectionListResponse(
        List<Item> items,
        Page page
) {

    public record Item(
            UUID id,
            UUID organizationId,
            String organizationName,
            String organizationStatus,
            String membershipStatus,
            Set<String> roleCodes,
            int sessionGeneration
    ) {
    }

    public record Page(String nextCursor, boolean hasNextPage) {
    }

    public static ContextSelectionListResponse from(List<ContextSelectionSummary> summaries) {
        List<Item> items = summaries.stream()
                .map(s -> new Item(s.id(), s.organizationId(), s.organizationName(),
                        s.organizationStatus(), s.membershipStatus(), s.roleCodes(), s.sessionGeneration()))
                .toList();
        return new ContextSelectionListResponse(items, new Page(null, false));
    }
}
