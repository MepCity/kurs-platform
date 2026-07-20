package org.mepcity.kursplatform.iam.infrastructure;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorkerAdapter;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves IAM-004 Fix Round 1 item: stub Cognito adapters must only ever be selected under the
 * 'local-stub' or 'test' Spring profile, and the real JWKS/management-API adapters must be
 * selected everywhere else (default/staging/production) — never the other way around, and never
 * both/neither. Uses a real Spring context (not just reading the @Profile annotation) so a future
 * refactor that accidentally breaks the gating fails this test, not just a code review.
 */
class IamProfileGatingTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IamCoreConfiguration.class, IamInfrastructureConfiguration.class,
                    IamLocalStubConfiguration.class, IamPropertiesServiceSettings.class)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withPropertyValues(
                    "iam.token-hash-pepper=default-profile-pepper-min-16-chars",
                    "iam.escrow-secret=default-profile-escrow-min-16-chars",
                    "iam.cognito.issuer=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_real",
                    "iam.cognito.client-id=real-client-id",
                    "iam.cognito.user-pool-id=eu-central-1_real",
                    "iam.cognito.management-api.access-key-id=real-access-key",
                    "iam.cognito.management-api.secret-access-key=real-secret-key");

    @Test
    void defaultProfileSelectsRealCognitoAdaptersNotStubs() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CognitoTokenVerifier.class)).isInstanceOf(CognitoJwksTokenVerifier.class);
            assertThat(context.getBean(CognitoUserStatusChecker.class)).isInstanceOf(CognitoManagementApiUserStatusChecker.class);
            assertThat(context.getBean(ProviderCommandWorkerAdapter.class)).isInstanceOf(CognitoProviderCommandWorkerAdapter.class);
        });
    }

    @Test
    void stagingLikeProfileStillSelectsRealCognitoAdapters() {
        contextRunner.withPropertyValues("spring.profiles.active=staging").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(CognitoTokenVerifier.class)).isInstanceOf(CognitoJwksTokenVerifier.class);
            assertThat(context.getBean(CognitoUserStatusChecker.class)).isInstanceOf(CognitoManagementApiUserStatusChecker.class);
        });
    }

    @Test
    void localStubProfileSelectsStubAdaptersNotReal() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local-stub", "iam.local-stub-profile=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CognitoTokenVerifier.class))
                            .isInstanceOf(IamLocalStubConfiguration.LocalStubCognitoTokenVerifier.class);
                    assertThat(context.getBean(CognitoUserStatusChecker.class))
                            .isInstanceOf(IamLocalStubConfiguration.LocalStubCognitoUserStatusChecker.class);
                    assertThat(context.getBean(ProviderCommandWorkerAdapter.class))
                            .isNotInstanceOf(CognitoProviderCommandWorkerAdapter.class);
                });
    }

    @Test
    void testProfileSelectsStubAdaptersNotReal() {
        contextRunner
                .withPropertyValues("spring.profiles.active=test", "iam.local-stub-profile=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CognitoTokenVerifier.class))
                            .isInstanceOf(IamLocalStubConfiguration.LocalStubCognitoTokenVerifier.class);
                    assertThat(context.getBean(CognitoUserStatusChecker.class))
                            .isInstanceOf(IamLocalStubConfiguration.LocalStubCognitoUserStatusChecker.class);
                    assertThat(context.getBean(ProviderCommandWorkerAdapter.class))
                            .isNotInstanceOf(CognitoProviderCommandWorkerAdapter.class);
                });
    }

    @Test
    void mismatchedLocalStubFlagAgainstDefaultProfileFailsFast() {
        // iam.local-stub-profile=true set while no local-stub/test profile is active must not
        // silently bypass the pepper/escrow/Cognito validation — IamProperties.validate() rejects
        // the inconsistency outright rather than letting real Cognito beans run unvalidated.
        contextRunner.withPropertyValues("iam.local-stub-profile=true").run(context ->
                assertThat(context).hasFailed());
    }
}
