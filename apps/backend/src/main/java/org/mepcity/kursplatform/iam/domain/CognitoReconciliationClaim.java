package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;
import java.util.UUID;

public record CognitoReconciliationClaim(
        UUID identityId, UUID userId, String issuer, String subject, String userPoolId,
        String workerId, long fencingToken, Instant leaseExpiresAt) {
}
