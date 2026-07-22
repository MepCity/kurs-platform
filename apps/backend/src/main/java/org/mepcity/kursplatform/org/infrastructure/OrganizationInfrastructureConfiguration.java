package org.mepcity.kursplatform.org.infrastructure;

import javax.sql.DataSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.org.application.AuditWriter;
import org.mepcity.kursplatform.org.application.IdempotencyRecorder;
import org.mepcity.kursplatform.org.application.OrganizationCreateRateLimiter;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.application.OrganizationResultSerializer;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcAuditWriter;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcIdempotencyRecorder;
import org.mepcity.kursplatform.org.infrastructure.persistence.JacksonOrganizationResultSerializer;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcOrganizationCreateRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Runtime wiring for the ORG transactional command service. */
@Configuration
@EnableConfigurationProperties(OrganizationRateLimitProperties.class)
public class OrganizationInfrastructureConfiguration {

    /** Production JSON dependency for HTTP representation and persisted idempotency payloads. */
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper organizationObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    AuditWriter organizationAuditWriter(DataSource dataSource) {
        return new JdbcAuditWriter(dataSource);
    }

    @Bean
    IdempotencyRecorder organizationIdempotencyRecorder(DataSource dataSource) {
        return new JdbcIdempotencyRecorder(dataSource);
    }

    @Bean
    OrganizationResultSerializer organizationResultSerializer(ObjectMapper objectMapper) {
        return new JacksonOrganizationResultSerializer(objectMapper);
    }

    @Bean
    OrganizationCreateRateLimiter organizationCreateRateLimiter(DataSource dataSource,
            PlatformTransactionManager transactionManager,
            OrganizationRateLimitProperties properties) {
        properties.validate();
        return new JdbcOrganizationCreateRateLimiter(dataSource, transactionManager,
                properties.getLimit(), properties.getWindow());
    }

    @Bean
    OrganizationLifecycleService organizationLifecycleService(
            OrganizationRepository repository, DataSource dataSource,
            PlatformTransactionManager transactionManager, AuditWriter organizationAuditWriter,
            IdempotencyRecorder organizationIdempotencyRecorder, OrganizationResultSerializer organizationResultSerializer,
            OrganizationCreateRateLimiter organizationCreateRateLimiter) {
        return new OrganizationLifecycleService(repository, dataSource, transactionManager,
                organizationAuditWriter, organizationIdempotencyRecorder, organizationResultSerializer, organizationCreateRateLimiter);
    }

}
