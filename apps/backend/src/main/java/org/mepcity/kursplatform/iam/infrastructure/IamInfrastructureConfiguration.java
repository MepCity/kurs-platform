package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.application.ContextSelectionService;
import org.mepcity.kursplatform.iam.application.IamAuditWriter;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.ProviderCommandRetryPolicy;
import org.mepcity.kursplatform.iam.application.ProviderCommandScheduler;
import org.mepcity.kursplatform.iam.application.ProviderCommandService;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorker;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorkerAdapter;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeService;
import org.mepcity.kursplatform.iam.application.SessionActivationService;
import org.mepcity.kursplatform.iam.application.SessionInfoService;
import org.mepcity.kursplatform.iam.application.SessionRefreshService;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;

/** Same {@code @ConditionalOnBean(DataSource.class)} pattern as {@code JdbcOrganizationRepository}
 *  (ORG-003) — every bean here needs a real DataSource, so the whole configuration backs off
 *  cleanly when one isn't provided (e.g. KursPlatformBackendApplicationTests deliberately excludes
 *  DataSourceAutoConfiguration for a DB-free wiring smoke test). DataSource-independent beans
 *  (tokenHasher, aeadEscrowService, secureRandom, clock, IamProperties itself) live in
 *  IamCoreConfiguration instead, since IamLocalStubConfiguration's beans need them too without
 *  ever needing a DataSource. */
@Configuration
@ConditionalOnBean(DataSource.class)
public class IamInfrastructureConfiguration {

    @Bean
    IamAuthRepository iamAuthRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIamAuthRepository(jdbcTemplate);
    }

    @Bean
    IamTransactionExecutor iamTransactionExecutor(PlatformTransactionManager transactionManager, DataSource dataSource) {
        return new SpringIamTransactionExecutor(transactionManager, dataSource);
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    IamAuditWriter iamAuditWriter(DataSource dataSource) {
        return new JdbcIamAuditWriter(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(CognitoTokenVerifier.class)
    @Profile("!local-stub & !test")
    CognitoTokenVerifier cognitoTokenVerifier(IamProperties properties, HmacSha256TokenHasher hasher) {
        return new CognitoJwksTokenVerifier(
                properties.getCognito().getIssuer(),
                properties.getCognito().getClientId(),
                hasher);
    }

    @Bean
    @ConditionalOnMissingBean(CognitoUserStatusChecker.class)
    @Profile("!local-stub & !test")
    CognitoUserStatusChecker cognitoUserStatusChecker(IamProperties properties) {
        IamProperties.Cognito cognito = properties.getCognito();
        IamProperties.ManagementApi mgmt = cognito.getManagementApi();
        return new CognitoManagementApiUserStatusChecker(
                cognito.getRegion(),
                cognito.getUserPoolId(),
                mgmt.getAccessKeyId(),
                mgmt.getSecretAccessKey(),
                mgmt.getSessionToken());
    }

    @Bean
    ProviderTokenExchangeService providerTokenExchangeService(IamAuthRepository repository,
                                                              CognitoTokenVerifier cognitoTokenVerifier,
                                                              TokenHasher tokenHasher,
                                                              AeadEscrowService escrowService,
                                                              SecureRandom secureRandom,
                                                              Clock clock,
                                                              IamTransactionExecutor transactionExecutor,
                                                              IamServiceSettings settings,
                                                              IamAuditWriter auditWriter) {
        return new ProviderTokenExchangeService(repository, cognitoTokenVerifier, tokenHasher,
                escrowService, secureRandom, clock, transactionExecutor, settings, auditWriter);
    }

    @Bean
    ContextSelectionService contextSelectionService(IamAuthRepository repository, TokenHasher tokenHasher,
                                                    Clock clock, IamTransactionExecutor transactionExecutor) {
        return new ContextSelectionService(repository, tokenHasher, clock, transactionExecutor);
    }

    @Bean
    SessionActivationService sessionActivationService(IamAuthRepository repository,
                                                      CognitoUserStatusChecker cognitoUserStatusChecker,
                                                      TokenHasher tokenHasher,
                                                      AeadEscrowService escrowService,
                                                      SecureRandom secureRandom,
                                                      Clock clock,
                                                      IamTransactionExecutor transactionExecutor,
                                                      IamServiceSettings settings,
                                                      IamAuditWriter auditWriter,
                                                      ProviderCommandService providerCommandService) {
        return new SessionActivationService(repository, cognitoUserStatusChecker, tokenHasher,
                escrowService, secureRandom, clock, transactionExecutor, settings, auditWriter,
                providerCommandService);
    }

    @Bean
    SessionInfoService sessionInfoService(IamAuthRepository repository, TokenHasher tokenHasher,
                                          Clock clock, IamTransactionExecutor transactionExecutor) {
        return new SessionInfoService(repository, tokenHasher, clock, transactionExecutor);
    }

    @Bean
    SessionRefreshService sessionRefreshService(IamAuthRepository repository, TokenHasher tokenHasher,
                                                SecureRandom secureRandom, Clock clock,
                                                IamTransactionExecutor transactionExecutor, IamServiceSettings settings,
                                                AeadEscrowService escrowService, IamAuditWriter auditWriter) {
        return new SessionRefreshService(repository, tokenHasher, secureRandom, clock, transactionExecutor, settings, escrowService, auditWriter);
    }

    @Bean
    ActiveSessionResolver activeSessionResolver(SessionInfoService sessionInfoService,
                                                ContextSelectionService contextSelectionService) {
        return credential -> {
            try {
                return CredentialResolution.platformAccess(sessionInfoService.resolveActiveSession(credential));
            } catch (org.mepcity.kursplatform.iam.domain.IamException accessFailure) {
                try {
                    contextSelectionService.listContextSelections(credential);
                    return CredentialResolution.contextSelection();
                } catch (org.mepcity.kursplatform.iam.domain.IamException ignored) {
                    throw new CredentialAuthenticationException(accessFailure.errorCode());
                }
            }
        };
    }

    @Bean
    ProviderCommandRetryPolicy providerCommandRetryPolicy(IamServiceSettings settings, SecureRandom secureRandom) {
        return new ProviderCommandRetryPolicy(
                settings.providerCommandMaxAttempts(),
                settings.providerCommandBackoffBase(),
                settings.providerCommandBackoffMax(),
                settings.providerCommandJitter(),
                secureRandom);
    }

    @Bean
    ProviderCommandService providerCommandService(IamAuthRepository repository, Clock clock,
                                                   IamTransactionExecutor transactionExecutor,
                                                   IamAuditWriter auditWriter,
                                                   ProviderCommandRetryPolicy retryPolicy) {
        return new ProviderCommandService(repository, clock, transactionExecutor, auditWriter, retryPolicy);
    }

    @Bean
    @ConditionalOnMissingBean(ProviderCommandWorkerAdapter.class)
    @Profile("!local-stub & !test")
    ProviderCommandWorkerAdapter providerCommandWorkerAdapter(IamProperties properties) {
        IamProperties.Cognito cognito = properties.getCognito();
        IamProperties.ManagementApi mgmt = cognito.getManagementApi();
        return new CognitoProviderCommandWorkerAdapter(
                cognito.getRegion(), cognito.getUserPoolId(),
                mgmt.getAccessKeyId(), mgmt.getSecretAccessKey(), mgmt.getSessionToken());
    }

    @Bean
    ProviderCommandWorker providerCommandWorker(ProviderCommandService providerCommandService,
                                                IamAuthRepository repository,
                                                IamTransactionExecutor transactionExecutor,
                                                ProviderCommandWorkerAdapter adapter) {
        return new ProviderCommandWorker(providerCommandService, repository, transactionExecutor, adapter);
    }

    /**
     * Activated only when {@code iam.provider-command.worker.enabled=true} AND a DataSource exists.
     * Disabled by default (and in test/local-stub profiles, where the property never flips on) so a
     * dev/CI boot never polls Cognito and a production deployment must consciously opt in per
     * A-010's "production requires deliberate configuration" gate.
     */
    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "iam.provider-command.worker", name = "enabled", havingValue = "true")
    ProviderCommandScheduler providerCommandScheduler(ProviderCommandWorker providerCommandWorker,
                                                      IamAuthRepository repository,
                                                      IamTransactionExecutor transactionExecutor,
                                                      IamServiceSettings settings,
                                                      Clock clock,
                                                      SecureRandom secureRandom) {
        return new ProviderCommandScheduler(providerCommandWorker, repository, transactionExecutor,
                settings, clock, secureRandom);
    }
}
