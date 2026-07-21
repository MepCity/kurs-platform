package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.application.ProviderCommandOutcome;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorkerAdapter;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** IamProperties/HmacSha256TokenHasher come from IamCoreConfiguration (always active, no
 *  DataSource needed) — this class only adds the local-stub/test-only bean fallbacks. */
@Configuration
@Profile({"local-stub", "test"})
public class IamLocalStubConfiguration {

    @Bean
    @ConditionalOnMissingBean(CognitoTokenVerifier.class)
    CognitoTokenVerifier stubCognitoTokenVerifier(IamProperties properties, HmacSha256TokenHasher hasher) {
        return new LocalStubCognitoTokenVerifier(properties.getCognito().getIssuer(), properties.getCognito().getClientId(), hasher);
    }

    @Bean
    @ConditionalOnMissingBean(CognitoUserStatusChecker.class)
    CognitoUserStatusChecker stubCognitoUserStatusChecker() {
        return new LocalStubCognitoUserStatusChecker();
    }

    @Bean
    @ConditionalOnMissingBean(ProviderCommandWorkerAdapter.class)
    ProviderCommandWorkerAdapter stubProviderCommandWorkerAdapter() {
        return (commandType, subject, issuer) -> ProviderCommandOutcome.ofSuccess();
    }

    public static final class LocalStubCognitoTokenVerifier implements CognitoTokenVerifier {
        private final String expectedIssuer;
        private final String expectedClientId;
        private final HmacSha256TokenHasher hasher;

        public LocalStubCognitoTokenVerifier(String expectedIssuer, String expectedClientId, HmacSha256TokenHasher hasher) {
            this.expectedIssuer = expectedIssuer;
            this.expectedClientId = expectedClientId;
            this.hasher = hasher;
        }

        @Override
        public org.mepcity.kursplatform.iam.domain.CognitoTokenClaims verify(String cognitoAccessToken) {
            if (cognitoAccessToken == null || cognitoAccessToken.isBlank() || !cognitoAccessToken.startsWith("stub-cognito:")) {
                throw new org.mepcity.kursplatform.iam.domain.IamException("UNAUTHENTICATED", "Stub token geçersiz.");
            }
            String[] parts = cognitoAccessToken.substring("stub-cognito:".length()).split("\\|", 4);
            if (parts.length < 3) {
                throw new org.mepcity.kursplatform.iam.domain.IamException("UNAUTHENTICATED", "Stub token içeriği geçersiz.");
            }
            String subject = parts[0];
            String clientId = parts[1];
            long authTimeEpoch = Long.parseLong(parts[2]);
            if (!clientId.equals(expectedClientId)) {
                throw new org.mepcity.kursplatform.iam.domain.IamException("UNAUTHENTICATED", "Stub token cliente ait değil.");
            }
            String fingerprint = hasher.fingerprint(cognitoAccessToken);
            return new org.mepcity.kursplatform.iam.domain.CognitoTokenClaims(
                    expectedIssuer, subject, clientId, "access",
                    java.time.Instant.ofEpochSecond(authTimeEpoch),
                    java.time.Instant.now().plus(1, java.time.temporal.ChronoUnit.HOURS),
                    null, fingerprint);
        }
    }

    public static final class LocalStubCognitoUserStatusChecker implements CognitoUserStatusChecker {
        @Override
        public ProviderUserStatus checkCanonicalStatus(java.util.UUID userIdentifier, String issuer, String subject) {
            return ProviderUserStatus.ACTIVE;
        }
    }
}
