package org.mepcity.kursplatform.iam.domain;

import java.time.Instant;

public record CognitoSecurityEventClaim(
        CognitoSecurityEvent event,
        String workerId,
        long fencingToken,
        Instant leaseExpiresAt) {
}
