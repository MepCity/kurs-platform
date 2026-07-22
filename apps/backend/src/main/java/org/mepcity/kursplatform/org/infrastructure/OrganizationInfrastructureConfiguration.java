package org.mepcity.kursplatform.org.infrastructure;

import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.AuditWriter;
import org.mepcity.kursplatform.org.application.IdempotencyRecorder;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcAuditWriter;
import org.mepcity.kursplatform.org.infrastructure.persistence.JdbcIdempotencyRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Runtime wiring for the ORG transactional command service. */
@Configuration
@ConditionalOnBean(DataSource.class)
public class OrganizationInfrastructureConfiguration {

    @Bean
    AuditWriter organizationAuditWriter(DataSource dataSource) {
        return new JdbcAuditWriter(dataSource);
    }

    @Bean
    IdempotencyRecorder organizationIdempotencyRecorder(DataSource dataSource) {
        return new JdbcIdempotencyRecorder(dataSource);
    }

    @Bean
    OrganizationLifecycleService organizationLifecycleService(
            OrganizationRepository repository, DataSource dataSource,
            PlatformTransactionManager transactionManager, AuditWriter organizationAuditWriter,
            IdempotencyRecorder organizationIdempotencyRecorder) {
        return new OrganizationLifecycleService(repository, dataSource, transactionManager,
                organizationAuditWriter, organizationIdempotencyRecorder);
    }

}
