package org.mepcity.kursplatform.iam.infrastructure.stub;

import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class StubCognitoUserStatusChecker implements CognitoUserStatusChecker {

    private final Map<String, ProviderUserStatus> override = new ConcurrentHashMap<>();
    private final ProviderUserStatus defaultStatus;

    public StubCognitoUserStatusChecker(ProviderUserStatus defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public void setStatus(String subject, ProviderUserStatus status) {
        override.put(subject, status);
    }

    @Override
    public ProviderUserStatus checkCanonicalStatus(UUID userIdentifier, String issuer, String subject) {
        if (subject != null && override.containsKey(subject)) {
            return override.get(subject);
        }
        return defaultStatus;
    }
}
