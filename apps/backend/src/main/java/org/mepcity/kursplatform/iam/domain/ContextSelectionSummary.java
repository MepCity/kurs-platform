package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ContextSelectionSummary(
        UUID id,
        UUID organizationId,
        String organizationName,
        String organizationStatus,
        String membershipStatus,
        Set<String> roleCodes,
        int sessionGeneration
) {
}
