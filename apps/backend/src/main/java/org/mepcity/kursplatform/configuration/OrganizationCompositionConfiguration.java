package org.mepcity.kursplatform.configuration;

import java.time.Clock;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.org.application.OrganizationCreationService;
import org.mepcity.kursplatform.org.application.OrganizationLifecycleService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/** Composition root wiring across the published IAM authentication contract. */
@Configuration
@ConditionalOnBean({OrganizationLifecycleService.class, ActiveSessionResolver.class})
public class OrganizationCompositionConfiguration {
    @Bean
    OrganizationCreationService organizationCreationService(OrganizationLifecycleService lifecycleService,
                                                            ActiveSessionResolver sessionResolver, Clock clock) {
        return new OrganizationCreationService(lifecycleService, sessionResolver, clock);
    }
}
