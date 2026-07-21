package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;

import java.util.UUID;

public interface CognitoUserStatusChecker {

    ProviderUserStatus checkCanonicalStatus(UUID userIdentifier, String issuer, String subject);
}
