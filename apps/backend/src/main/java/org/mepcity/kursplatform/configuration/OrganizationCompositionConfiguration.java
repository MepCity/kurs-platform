package org.mepcity.kursplatform.configuration;

import java.time.Clock;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.org.application.OrganizationBrandAuthentication;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.mepcity.kursplatform.org.application.OrganizationListService;
import org.mepcity.kursplatform.org.application.AuditWriter;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Composition root wiring across the published IAM authentication contract. */
@Configuration
public class OrganizationCompositionConfiguration {
    @Bean
    OrganizationCreationService organizationCreationService(OrganizationLifecycleService lifecycleService,
                                                            ActiveSessionResolver sessionResolver, Clock clock) {
        return new OrganizationCreationService(lifecycleService, sessionResolver, clock);
    }

    @Bean
    OrganizationBrandAuthentication organizationBrandAuthentication(ActiveSessionResolver sessionResolver) {
        return new OrganizationBrandAuthentication(sessionResolver);
    }

    @Bean
    OrganizationListService organizationListService(OrganizationRepository organizations, ActiveSessionResolver sessions,
            AuditWriter audits, DataSource dataSource, PlatformTransactionManager transactionManager, Clock clock) {
        return new OrganizationListService(organizations, sessions, audits, dataSource, transactionManager, clock);
    }
}
