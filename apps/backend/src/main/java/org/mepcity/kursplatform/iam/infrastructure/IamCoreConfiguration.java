package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.time.Clock;

/**
 * Beans the IAM module needs that do NOT require a DataSource — split out from
 * {@link IamInfrastructureConfiguration} (which is {@code @ConditionalOnBean(DataSource.class)})
 * so that {@link IamLocalStubConfiguration}'s local-stub/test beans (which also need {@link
 * IamProperties} and {@link HmacSha256TokenHasher} but never a real database) don't depend on a
 * DataSource existing either. Always active, in every profile.
 */
@Configuration
@EnableConfigurationProperties(IamProperties.class)
public class IamCoreConfiguration {

    @Bean
    HmacSha256TokenHasher tokenHasher(IamProperties properties) {
        return new HmacSha256TokenHasher(properties.getTokenHashPepper());
    }

    @Bean
    AeadEscrowService aeadEscrowService(IamProperties properties) {
        return new AeadEscrowServiceImpl(properties.getEscrowSecret());
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
