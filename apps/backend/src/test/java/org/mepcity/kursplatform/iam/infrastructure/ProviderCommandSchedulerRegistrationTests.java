package org.mepcity.kursplatform.iam.infrastructure;

import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.ProviderCommandScheduler;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The real application ({@link org.mepcity.kursplatform.KursPlatformBackendApplication}) is
 * {@code @SpringBootApplication}-annotated, so its default classpath component scan is rooted at
 * {@code org.mepcity.kursplatform} and covers every class in {@code
 * org.mepcity.kursplatform.iam.application}, including {@link ProviderCommandScheduler}. This
 * class used to ALSO be {@code @Configuration}-annotated itself, in addition to the explicit
 * {@code @Bean} factory method in {@link IamInfrastructureConfiguration} — meaning classpath scan
 * registered a SECOND bean definition of the same type/default name, which Spring Boot's
 * bean-definition-overriding-disabled default turned into a hard {@code
 * BeanDefinitionOverrideException} at context startup (application refuses to boot at all) the
 * moment the worker was enabled with a real {@link DataSource} present.
 *
 * <p>{@code PackageScanProbe} below reproduces that real classpath scan (scoped to the actual
 * package, not just re-registering the class directly — {@code ApplicationContextRunner}'s
 * {@code withUserConfiguration} would register ANY class handed to it regardless of whether it
 * carries a stereotype annotation, which would not actually prove component scan finds it), so
 * these tests fail exactly the way the real application would if {@link ProviderCommandScheduler}
 * were ever re-annotated {@code @Configuration}/{@code @Component} again.
 */
class ProviderCommandSchedulerRegistrationTests {

    @Configuration
    @ComponentScan(basePackageClasses = ProviderCommandScheduler.class)
    static class PackageScanProbe {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(IamCoreConfiguration.class, IamInfrastructureConfiguration.class,
                    PackageScanProbe.class, IamPropertiesServiceSettings.class)
            .withBean(DataSource.class, () -> mock(DataSource.class))
            .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
            .withPropertyValues(
                    "iam.token-hash-pepper=scheduler-registration-pepper-min-16",
                    "iam.escrow-secret=scheduler-registration-escrow-min-16",
                    "iam.cognito.issuer=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_real",
                    "iam.cognito.client-id=real-client-id",
                    "iam.cognito.user-pool-id=eu-central-1_real",
                    "iam.cognito.management-api.access-key-id=real-access-key",
                    "iam.cognito.management-api.secret-access-key=real-secret-key",
                    "iam.provider-command.worker.enabled=true");

    @Test
    void exactlyOneProviderCommandSchedulerBeanExistsWhenWorkerIsEnabled() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ProviderCommandScheduler.class))
                    .as("classpath component scan and IamInfrastructureConfiguration's @Bean method "
                            + "must not BOTH register a scheduler bean")
                    .hasSize(1);
            assertThat(context.getBeanNamesForType(ProviderCommandScheduler.class))
                    .containsExactly("providerCommandScheduler");
        });
    }

    @Test
    void noSchedulerBeanExistsWhenWorkerDisabled() {
        contextRunner.withPropertyValues("iam.provider-command.worker.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ProviderCommandScheduler.class)).isEmpty();
        });
    }

    @Test
    void noSchedulerBeanExistsWithoutADataSource() {
        ApplicationContextRunner runnerWithoutDataSource = new ApplicationContextRunner()
                .withUserConfiguration(IamCoreConfiguration.class, IamInfrastructureConfiguration.class,
                        PackageScanProbe.class, IamPropertiesServiceSettings.class)
                .withPropertyValues(
                        "iam.token-hash-pepper=scheduler-registration-pepper-min-16",
                        "iam.escrow-secret=scheduler-registration-escrow-min-16",
                        "iam.cognito.issuer=https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_real",
                        "iam.cognito.client-id=real-client-id",
                        "iam.cognito.user-pool-id=eu-central-1_real",
                        "iam.cognito.management-api.access-key-id=real-access-key",
                        "iam.cognito.management-api.secret-access-key=real-secret-key",
                        "iam.provider-command.worker.enabled=true");

        runnerWithoutDataSource.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ProviderCommandScheduler.class)).isEmpty();
        });
    }
}
