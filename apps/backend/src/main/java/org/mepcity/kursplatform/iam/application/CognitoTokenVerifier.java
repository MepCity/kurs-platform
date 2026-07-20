package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.CognitoTokenClaims;

public interface CognitoTokenVerifier {

    CognitoTokenClaims verify(String cognitoAccessToken);
}
