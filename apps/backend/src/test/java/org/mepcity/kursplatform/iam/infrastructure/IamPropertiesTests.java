package org.mepcity.kursplatform.iam.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A-010 / ADR-004 gate: V1 runs a native Cognito Essentials user pool (sub == Username); enabling
 * {@code allow-federated-idp} without the matching ADR revision + verified-Username storage would
 * silently make the status-checker / worker adapter mis-resolve federated users. These tests prove
 * {@link IamProperties#validate()} hard-fails that combination rather than letting it half-work.
 */
class IamPropertiesTests {

    private static IamProperties newProperties(String... activeProfiles) throws Exception {
        IamProperties properties = new IamProperties();
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles(activeProfiles);
        Field field = IamProperties.class.getDeclaredField("environment");
        field.setAccessible(true);
        field.set(properties, environment);
        return properties;
    }

    private static void configureRealCognito(IamProperties properties) {
        properties.setLocalStubProfile(false);
        properties.setTokenHashPepper("a-real-pepper-value-1234");
        properties.setEscrowSecret("a-real-escrow-secret-1234");
        properties.getCognito().setIssuer("https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_real");
        properties.getCognito().setClientId("real-client-id");
        properties.getCognito().setUserPoolId("eu-central-1_real");
        properties.getCognito().getManagementApi().setAccessKeyId("AKIA-real");
        properties.getCognito().getManagementApi().setSecretAccessKey("real-secret");
    }

    @Test
    void validateAcceptsLocalStubProfileWithMatchingFlag() throws Exception {
        IamProperties properties = newProperties("local-stub");
        properties.setLocalStubProfile(true);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsLocalStubFlagMismatchedWithActiveProfile() throws Exception {
        IamProperties properties = newProperties("prod");
        properties.setLocalStubProfile(true);

        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validatePassesForRealCognitoConfigWithFederationDisabled() throws Exception {
        IamProperties properties = newProperties("prod");
        configureRealCognito(properties);

        assertThatCode(properties::validate).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsRealCognitoConfigWithFederatedIdpEnabled() throws Exception {
        IamProperties properties = newProperties("prod");
        configureRealCognito(properties);
        properties.getCognito().setAllowFederatedIdp(true);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("allow-federated-idp");
    }
}
