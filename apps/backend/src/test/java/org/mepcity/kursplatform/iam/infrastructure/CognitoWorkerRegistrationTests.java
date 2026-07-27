package org.mepcity.kursplatform.iam.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.PlatformTransactionManager;

class CognitoWorkerRegistrationTests {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(
                    IamCoreConfiguration.class,
                    IamInfrastructureConfiguration.class,
                    IamPropertiesServiceSettings.class)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withBean(SafeEventLogger.class, () -> event -> { })
            .withPropertyValues(
                    "iam.token-hash-pepper=cognito-worker-pepper-minimum",
                    "iam.escrow-secret=cognito-worker-escrow-minimum",
                    "iam.cognito.issuer=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_real",
                    "iam.cognito.client-id=real-client-id",
                    "iam.cognito.region=eu-central-1",
                    "iam.cognito.user-pool-id=eu-central-1_real",
                    "iam.cognito.account-id=111122223333",
                    "iam.cognito.management-api.access-key-id=real-access-key",
                    "iam.cognito.management-api.secret-access-key=real-secret-key",
                    "iam.security-events.enabled=true");

    @Test
    void enabledEventWorkerRegistersQueueAndPersistentDatabaseSchedulers() {
        runner.withBean(CognitoEventQueueClient.class, () -> mock(CognitoEventQueueClient.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(CognitoSecurityEventScheduler.class);
                    assertThat(context).hasSingleBean(CognitoPendingEventScheduler.class);
                    assertThat(context).hasSingleBean(
                            org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor.class);
                });
    }

    @Test
    void enabledEventWorkerFailsFastWithoutQueueAdapter() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void workersRemainDisabledByDefault() {
        runner.withPropertyValues("iam.security-events.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(CognitoSecurityEventScheduler.class);
                    assertThat(context).doesNotHaveBean(CognitoPendingEventScheduler.class);
                });
    }
}
