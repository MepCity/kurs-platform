package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

import java.util.UUID;

public record AuthResolvedActor(
        UUID actorUserId,
        UserIdentity identity,
        CognitoTokenClaims claims
) {
}
