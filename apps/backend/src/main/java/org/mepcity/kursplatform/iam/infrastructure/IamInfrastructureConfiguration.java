package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.AeadEscrowService;
import org.mepcity.kursplatform.iam.application.CognitoTokenVerifier;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.CognitoReconciliationService;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventParser;
import org.mepcity.kursplatform.iam.application.CognitoUserStatusChecker;
import org.mepcity.kursplatform.iam.application.ContextSelectionService;
import org.mepcity.kursplatform.iam.application.DeviceSessionService;
import org.mepcity.kursplatform.iam.application.DeviceCursorCodec;
import org.mepcity.kursplatform.iam.application.DeviceSessionSnapshotSerializer;
import org.mepcity.kursplatform.iam.application.IamAuditWriter;
import org.mepcity.kursplatform.iam.application.IamDeviceRateLimiter;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.application.IamServiceSettings;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.ProviderCommandRetryPolicy;
import org.mepcity.kursplatform.iam.application.ProviderCommandService;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorker;
import org.mepcity.kursplatform.iam.application.ProviderCommandWorkerAdapter;
import org.mepcity.kursplatform.iam.application.ProviderTokenExchangeService;
import org.mepcity.kursplatform.iam.application.SessionActivationService;
import org.mepcity.kursplatform.iam.application.SessionInfoService;
import org.mepcity.kursplatform.iam.application.SessionRefreshService;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.mepcity.kursplatform.core.observability.SafeEventLogger;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;

/** Runtime wiring for IAM services backed by the application DataSource.
 *
 * <p>This is intentionally unconditional in the production component graph. A
 * {@code ConditionalOnBean(DataSource.class)} guard on a regular scanned configuration is order
 * dependent: it can be evaluated before DataSource auto-configuration and silently remove every
 * IAM HTTP endpoint. DB-free tests use an explicit configuration that does not component-scan this
 * class. DataSource-independent beans remain in {@link IamCoreConfiguration}.</p>
 */
@Configuration
public class IamInfrastructureConfiguration {

    @Bean
    SecurityAlertSink securityAlertSink(SafeEventLogger logger) {
        return new ObservabilitySecurityAlertSink(logger);
    }

    @Bean
    CognitoSecurityEventService cognitoSecurityEventService(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            IamAuditWriter audit,
            SecurityAlertSink alerts,
            Clock clock,
            IamProperties properties) {
        return new CognitoSecurityEventService(
                repository,
                transactions,
                audit,
                alerts,
                clock,
                properties.getCognito().getIssuer(),
                properties.getCognito().getUserPoolId());
    }

    @Bean
    CognitoReconciliationService cognitoReconciliationService(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            CognitoUserStatusChecker provider,
            IamAuditWriter audit,
            SecurityAlertSink alerts,
            Clock clock,
            IamProperties properties) {
        return new CognitoReconciliationService(
                repository,
                transactions,
                provider,
                audit,
                alerts,
                clock,
                properties.getCognito().getIssuer(),
                properties.getCognito().getUserPoolId());
    }

    @Bean
    CognitoSecurityEventParser cognitoSecurityEventParser(IamProperties properties) {
        IamProperties.Cognito cognito = properties.getCognito();
        return new CognitoSecurityEventParser(
                new ObjectMapper(),
                cognito.getAccountId(),
                cognito.getRegion(),
                cognito.getUserPoolId());
    }

    @Bean
    ReconciliationLagMonitor reconciliationLagMonitor(
            IamAuthRepository repository,
            IamTransactionExecutor transactions,
            Clock clock,
            SecurityAlertSink alerts,
            IamProperties properties) {
        return new ReconciliationLagMonitor(
                repository,
                transactions,
                clock,
                alerts,
                properties.getCognito().getUserPoolId());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "iam.security-events",
            name = "enabled",
            havingValue = "true")
    CognitoSecurityEventScheduler cognitoSecurityEventScheduler(
            CognitoEventQueueClient queue,
            CognitoSecurityEventParser parser,
            CognitoSecurityEventService service,
            ReconciliationLagMonitor lagMonitor,
            SecurityAlertSink alerts,
            Clock clock) {
        var consumer = new CognitoSecurityEventConsumer(
                queue, parser, service, lagMonitor, alerts, clock, 5);
        return new CognitoSecurityEventScheduler(consumer, 20);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "iam.security-events",
            name = "enabled",
            havingValue = "true")
    CognitoPendingEventScheduler cognitoPendingEventScheduler(
            CognitoSecurityEventService service, ReconciliationLagMonitor lagMonitor) {
        return new CognitoPendingEventScheduler(service, lagMonitor, 20);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "iam.reconciliation",
            name = "enabled",
            havingValue = "true")
    CognitoReconciliationScheduler cognitoReconciliationScheduler(
            CognitoReconciliationService service, ReconciliationLagMonitor lagMonitor) {
        return new CognitoReconciliationScheduler(service, lagMonitor, 20);
    }

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
    IamDeviceRateLimiter iamDeviceRateLimiter(DataSource dataSource) {
        return new JdbcIamDeviceRateLimiter(dataSource);
    }

    @Bean
    DeviceSessionSnapshotSerializer deviceSessionSnapshotSerializer() {
        return new JacksonDeviceSessionSnapshotSerializer(new ObjectMapper().findAndRegisterModules());
    }

    @Bean
    DeviceCursorCodec deviceCursorCodec(TokenHasher tokenHasher, Clock clock, IamServiceSettings settings,
                                        SecureRandom secureRandom) {
        return new AesGcmDeviceCursorCodec(tokenHasher, clock, settings.deviceCursorTtl(), secureRandom);
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
    DeviceSessionService deviceSessionService(IamAuthRepository repository, IamTransactionExecutor transactions,
                                              ActiveSessionResolver credentials, SessionInfoService sessionInfoService,
                                              TokenHasher tokenHasher, IamAuditWriter auditWriter,
                                              IamServiceSettings settings, Clock clock, IamDeviceRateLimiter rateLimiter,
                                              DeviceSessionSnapshotSerializer snapshots, DeviceCursorCodec cursorCodec) {
        return new DeviceSessionService(repository, transactions, credentials, sessionInfoService, tokenHasher,
                auditWriter, settings, clock, rateLimiter, snapshots, cursorCodec);
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
