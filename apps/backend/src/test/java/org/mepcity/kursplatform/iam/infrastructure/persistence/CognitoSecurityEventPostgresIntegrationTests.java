package org.mepcity.kursplatform.iam.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.CognitoSecurityEventService;
import org.mepcity.kursplatform.iam.application.CognitoReconciliationService;
import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.application.IamTransactionExecutor;
import org.mepcity.kursplatform.iam.application.SecurityAlertSink;
import org.mepcity.kursplatform.iam.application.ReconciliationLagMonitor;
import org.mepcity.kursplatform.iam.domain.CognitoSecurityEvent;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IamAuditWriteException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderUserStatus;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuditWriter;
import org.mepcity.kursplatform.iam.infrastructure.JdbcIamAuthRepository;
import org.mepcity.kursplatform.iam.infrastructure.SpringIamTransactionExecutor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

class CognitoSecurityEventPostgresIntegrationTests {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final String RUNTIME_PASSWORD = "iam-event-runtime-password";
    static JdbcTemplate owner;
    static JdbcTemplate runtime;
    static IamAuthRepository repository;
    static IamTransactionExecutor transactions;
    static DriverManagerDataSource runtimeDataSource;
    static final Instant NOW = Instant.parse("2026-07-27T12:00:00Z");

    @BeforeAll static void start() {
        POSTGRES.start();
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration").cleanDisabled(false).load().migrate();
        owner = new JdbcTemplate(new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        owner.execute("ALTER ROLE iam_runtime WITH PASSWORD '" + RUNTIME_PASSWORD + "'");
        runtimeDataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), "iam_runtime", RUNTIME_PASSWORD);
        runtime = new JdbcTemplate(runtimeDataSource);
        repository = new JdbcIamAuthRepository(runtime);
        transactions = new SpringIamTransactionExecutor(new DataSourceTransactionManager(runtimeDataSource), runtimeDataSource);
    }

    @AfterAll static void stop() { POSTGRES.stop(); }

    @BeforeEach void clean() {
        owner.execute("""
                TRUNCATE iam_cognito_security_events, iam_cognito_reconciliation_targets,
                    iam_cognito_lag_alarm_state, audit_logs, refresh_tokens,
                    refresh_token_families, trusted_devices, user_identities, users
                RESTART IDENTITY CASCADE
                """);
    }

    @Test void duplicateDeliveryRevokesOnlyMappedUserAndCompletesOnce() {
        UUID target = seedIdentityWithFamily("subject-a", "target");
        UUID other = seedIdentityWithFamily("subject-b", "other");
        var event = event("event-1", "subject-a");
        SecurityAlertSink alerts = alert -> { };
        var service = new CognitoSecurityEventService(repository, transactions,
                new JdbcIamAuditWriter(runtimeDataSource), alerts, Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(), "eu-central-1_pool");

        service.ingest(event, "worker-a");
        service.ingest(event, "worker-b");

        assertThat(count("refresh_token_families WHERE user_id='" + target + "' AND revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("refresh_token_families WHERE user_id='" + other + "' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(count("iam_cognito_security_events WHERE event_id='event-1' AND status='COMPLETED'")).isEqualTo(1);
        assertThat(count("audit_logs WHERE action_type='IAM_PROVIDER_SESSION_REVOKED'"
                + " AND actor_user_id IS NULL AND target_entity_id='" + target + "'"))
                .isEqualTo(1);
    }

    @Test
    void injectedClockOwnsInitialDueTimesWhenDatabaseClockIsLater() {
        Instant applicationNow = Instant.parse("2000-01-01T00:00:00Z");
        var event = event("event-clock-source", "subject-clock-source");

        assertThat(owner.queryForObject(
                "SELECT transaction_timestamp() > ?",
                Boolean.class,
                Timestamp.from(applicationNow)))
                .isTrue();
        assertThat(owner.queryForObject("""
                SELECT column_default
                FROM information_schema.columns
                WHERE table_schema='public'
                  AND table_name='iam_cognito_security_events'
                  AND column_name='next_attempt_at'
                """, String.class))
                .isNull();
        assertThat(inEventScope(() -> repository.recordCognitoSecurityEvent(event, applicationNow)))
                .isTrue();
        assertThat(owner.queryForObject(
                "SELECT next_attempt_at FROM iam_cognito_security_events WHERE event_id=?",
                Timestamp.class,
                event.eventId()).toInstant())
                .isEqualTo(applicationNow);
        assertThat(inEventScope(() -> repository.claimCognitoSecurityEvent(
                event,
                "clock-source-event-worker",
                applicationNow,
                applicationNow.plusSeconds(120))))
                .isPresent();

        UUID userId = seedIdentityWithFamily("subject-clock-source", "clock-source");
        inSweepScope(() -> {
            repository.discoverCognitoReconciliationTargets(
                    issuer(), "eu-central-1_pool", applicationNow);
            return null;
        });
        assertThat(owner.queryForObject(
                "SELECT next_check_at FROM iam_cognito_reconciliation_targets WHERE user_id=?",
                Timestamp.class,
                userId).toInstant())
                .isEqualTo(applicationNow);
        assertThat(inSweepScope(() -> repository.claimCognitoReconciliationTarget(
                "eu-central-1_pool",
                "clock-source-reconciliation-worker",
                applicationNow,
                applicationNow.plusSeconds(120))))
                .isPresent();
    }

    @Test void expiredLeaseCanBeReclaimedAndStaleWorkerCannotComplete() {
        var event = event("event-lease", "subject-a");
        var first = inEventScope(() -> repository.claimCognitoSecurityEvent(
                event, "worker-a", NOW, NOW.plusSeconds(120))).orElseThrow();
        assertThat(inEventScope(() -> repository.claimCognitoSecurityEvent(
                event, "worker-b", NOW, NOW.plusSeconds(120)))).isEmpty();
        owner.update("UPDATE iam_cognito_security_events SET lease_expires_at=? WHERE event_id=?",
                Timestamp.from(NOW.minusSeconds(1)), event.eventId());
        var second = inEventScope(() -> repository.claimCognitoSecurityEvent(
                event, "worker-b", NOW, NOW.plusSeconds(120))).orElseThrow();
        assertThat(second.fencingToken()).isGreaterThan(first.fencingToken());
        assertThatThrownBy(() -> inEventScope(() -> {
            repository.completeCognitoSecurityEvent(first, NOW); return null;
        })).isInstanceOf(IllegalStateException.class);
        inEventScope(() -> { repository.completeCognitoSecurityEvent(second, NOW); return null; });
    }

    @Test void concurrentWorkersProduceSingleClaim() throws Exception {
        var event=event("event-race","subject-a");
        var gate=new CountDownLatch(1);
        try (var executor=Executors.newFixedThreadPool(2)) {
            var first=executor.submit(() -> { gate.await(); return inEventScope(() -> repository.claimCognitoSecurityEvent(event,"race-a",NOW,NOW.plusSeconds(120))); });
            var second=executor.submit(() -> { gate.await(); return inEventScope(() -> repository.claimCognitoSecurityEvent(event,"race-b",NOW,NOW.plusSeconds(120))); });
            gate.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)))
                    .filteredOn(java.util.Optional::isPresent).hasSize(1);
        }
    }

    @Test void auditFailureRollsBackRevocationAndCompletion() {
        UUID target=seedIdentityWithFamily("subject-rollback","rollback");
        var event=event("event-rollback","subject-rollback");
        var service=new CognitoSecurityEventService(repository,transactions,
                ignored -> { throw new IllegalStateException("synthetic audit failure"); },
                ignored -> { },Clock.fixed(NOW,ZoneOffset.UTC), issuer(), "eu-central-1_pool");
        assertThatThrownBy(() -> service.ingest(event,"rollback-worker"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(count("refresh_token_families WHERE user_id='"+target+"' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(count("iam_cognito_security_events WHERE event_id='event-rollback' AND status='PENDING_MAPPING'")).isEqualTo(1);
        assertThat(count("audit_logs WHERE target_entity_id='"+target+"'")).isZero();
    }

    @Test
    void unknownSubjectIsAckSafePersistentWorkAndCompletesAfterIdentityAppears() {
        String subject = "federated|öğrenci/例-42";
        var event = event("event-late-mapping", subject);
        var service = new CognitoSecurityEventService(
                repository,
                transactions,
                new JdbcIamAuditWriter(runtimeDataSource),
                ignored -> { },
                Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(),
                "eu-central-1_pool");

        assertThat(service.ingest(event, "delivery-worker"))
                .isEqualTo(org.mepcity.kursplatform.iam.application.CognitoEventProcessingResult.PERSISTED_PENDING);
        assertThat(count("iam_cognito_security_events WHERE event_id='event-late-mapping'"
                + " AND status='PENDING_MAPPING' AND lease_owner IS NULL")).isEqualTo(1);

        UUID target = seedIdentityWithFamily(subject, "late");
        owner.update("UPDATE iam_cognito_security_events SET next_attempt_at=? WHERE event_id=?",
                Timestamp.from(NOW), event.eventId());

        assertThat(service.processNextPending("database-worker")).isTrue();
        assertThat(count("iam_cognito_security_events WHERE event_id='event-late-mapping'"
                + " AND status='COMPLETED'")).isEqualTo(1);
        assertThat(count("refresh_token_families WHERE user_id='" + target
                + "' AND revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("audit_logs WHERE actor_user_id IS NULL"
                + " AND target_entity_id='" + target + "'")).isEqualTo(1);
    }

    @Test
    void persistentLagCooldownEmitsAtExactTwoAndFiveMinuteThresholdsWithoutStorm() {
        var lagged = new CognitoSecurityEvent(
                "eu-central-1_pool",
                "event-lag",
                "AdminDisableUser",
                "subject-lag",
                NOW.minusSeconds(120));
        inEventScope(() -> repository.recordCognitoSecurityEvent(lagged, NOW));
        var alerts = new java.util.ArrayList<SecurityAlertSink.SecurityAlert>();
        var warningMonitor = new ReconciliationLagMonitor(
                repository,
                transactions,
                Clock.fixed(NOW, ZoneOffset.UTC),
                alerts::add,
                "eu-central-1_pool");

        warningMonitor.inspectEvents();
        warningMonitor.inspectEvents();

        var criticalMonitor = new ReconciliationLagMonitor(
                repository,
                transactions,
                Clock.fixed(NOW.plusSeconds(180), ZoneOffset.UTC),
                alerts::add,
                "eu-central-1_pool");
        criticalMonitor.inspectEvents();
        criticalMonitor.inspectEvents();

        assertThat(alerts).extracting(SecurityAlertSink.SecurityAlert::severity)
                .containsExactly(
                        SecurityAlertSink.Severity.WARNING,
                        SecurityAlertSink.Severity.CRITICAL);
        assertThat(count("iam_cognito_lag_alarm_state WHERE user_pool_id='eu-central-1_pool'"))
                .isEqualTo(2);
        assertThat(alerts).allSatisfy(alert -> assertThat(alert.attributes())
                .containsOnlyKeys("checkpoint", "lagSeconds"));
    }

    @Test void runtimeRolesAndPoolContextRemainFailClosed() {
        assertThat(owner.queryForObject("SELECT rolcanlogin AND NOT rolsuper AND NOT rolbypassrls FROM pg_roles WHERE rolname='iam_runtime'", Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT tableowner <> 'iam_runtime' FROM pg_tables WHERE tablename='iam_cognito_security_events'", Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT relforcerowsecurity FROM pg_class WHERE relname='iam_cognito_security_events'", Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT relforcerowsecurity FROM pg_class WHERE relname='iam_cognito_reconciliation_targets'", Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT relforcerowsecurity FROM pg_class WHERE relname='iam_cognito_lag_alarm_state'", Boolean.class)).isTrue();
        assertThat(owner.queryForObject("SELECT has_table_privilege('app_runtime','iam_cognito_security_events','SELECT')", Boolean.class)).isFalse();
        assertThat(owner.queryForObject("SELECT has_table_privilege('app_runtime','iam_cognito_reconciliation_targets','SELECT')", Boolean.class)).isFalse();
        assertThat(owner.queryForObject("SELECT has_table_privilege('app_runtime','iam_cognito_lag_alarm_state','SELECT')", Boolean.class)).isFalse();
        assertThat(runtime.queryForObject("SELECT count(*) FROM iam_cognito_security_events", Long.class)).isZero();
    }

    @Test
    void automatedAuditRlsRejectsActorGucInsteadOfEquatingActorAndTarget() {
        UUID target = seedIdentityWithFamily("subject-system-audit", "system-audit");
        var auditWriter = new JdbcIamAuditWriter(runtimeDataSource);

        assertThatThrownBy(() -> transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID())
                        .withTargetUser(target),
                () -> {
                    auditWriter.write(systemRevocationAudit(target));
                    return null;
                }))
                .isInstanceOf(IamAuditWriteException.class);
        assertThat(count("audit_logs WHERE target_entity_id='" + target + "'")).isZero();
    }

    @Test
    void reconciliationRlsRejectsWrongOperationAndHidesAnotherPool() {
        UUID correct = seedIdentityWithFamily("subject-correct-pool", "correct-pool");
        UUID other = seedIdentityWithFamily("subject-other-pool", "other-pool");
        UUID otherIdentity = owner.queryForObject(
                "SELECT id FROM user_identities WHERE user_id=?",
                UUID.class,
                other);
        owner.update("""
                INSERT INTO iam_cognito_reconciliation_targets
                    (identity_id,user_id,issuer,subject,user_pool_id,next_check_at)
                VALUES (?,?,?,?,?,?)
                """, otherIdentity, other,
                "https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_other",
                "subject-other-pool", "eu-central-1_other", Timestamp.from(NOW));
        inSweepScope(() -> {
            repository.discoverCognitoReconciliationTargets(issuer(), "eu-central-1_pool", NOW);
            return null;
        });

        var wrongOperation = transactions.executeInGlobalScope(
                OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(UUID.randomUUID()),
                () -> repository.claimCognitoReconciliationTarget(
                        "eu-central-1_pool", "wrong-operation", NOW, NOW.plusSeconds(120)));
        assertThat(wrongOperation).isEmpty();

        var claimed = inSweepScope(() -> repository.claimCognitoReconciliationTarget(
                "eu-central-1_pool", "correct-operation", NOW, NOW.plusSeconds(120)))
                .orElseThrow();
        assertThat(claimed.userId()).isEqualTo(correct);
        assertThat(claimed.userPoolId()).isEqualTo("eu-central-1_pool");
        assertThat(runtime.queryForObject(
                "SELECT count(*) FROM iam_cognito_reconciliation_targets",
                Long.class)).isZero();
    }

    @Test void canonicalSweepFindsMissedDisableAndPersistentCheckpointPreventsDoubleWork() {
        UUID target = seedIdentityWithFamily("subject-sweep", "sweep");
        var alerts = new java.util.ArrayList<SecurityAlertSink.SecurityAlert>();
        var service = new CognitoReconciliationService(repository, transactions,
                (user, issuer, subject) -> ProviderUserStatus.DISABLED,
                new JdbcIamAuditWriter(runtimeDataSource), alerts::add,
                Clock.fixed(NOW, ZoneOffset.UTC), issuer(), "eu-central-1_pool");

        assertThat(service.pollOne("sweep-worker-a")).isTrue();
        assertThat(service.pollOne("sweep-worker-b")).isFalse();

        assertThat(count("refresh_token_families WHERE user_id='" + target + "' AND revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("iam_cognito_reconciliation_targets WHERE user_id='" + target + "' AND last_provider_status='DISABLED' AND lease_owner IS NULL")).isEqualTo(1);
        assertThat(count("audit_logs WHERE action_type='IAM_PROVIDER_SESSION_REVOKED'"
                + " AND actor_user_id IS NULL AND target_entity_id='" + target + "'"))
                .isEqualTo(1);
        assertThat(alerts).isEmpty();
    }

    @Test
    void reconciliationWorkersCannotClaimSameTargetConcurrently() throws Exception {
        seedIdentityWithFamily("subject-recon-race", "recon-race");
        inSweepScope(() -> {
            repository.discoverCognitoReconciliationTargets(issuer(), "eu-central-1_pool", NOW);
            return null;
        });
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                gate.await();
                return inSweepScope(() -> repository.claimCognitoReconciliationTarget(
                        "eu-central-1_pool", "recon-a", NOW, NOW.plusSeconds(120)));
            });
            var second = executor.submit(() -> {
                gate.await();
                return inSweepScope(() -> repository.claimCognitoReconciliationTarget(
                        "eu-central-1_pool", "recon-b", NOW, NOW.plusSeconds(120)));
            });
            gate.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .filteredOn(java.util.Optional::isPresent)
                    .hasSize(1);
        }
    }

    @Test
    void expiredReconciliationLeaseUsesHigherFenceAndRejectsStaleCompletion() {
        seedIdentityWithFamily("subject-recon-lease", "recon-lease");
        var first = inSweepScope(() -> {
            repository.discoverCognitoReconciliationTargets(issuer(), "eu-central-1_pool", NOW);
            return repository.claimCognitoReconciliationTarget(
                    "eu-central-1_pool", "recon-a", NOW, NOW.plusSeconds(120));
        }).orElseThrow();
        owner.update("""
                UPDATE iam_cognito_reconciliation_targets
                SET lease_expires_at=?
                WHERE identity_id=?
                """, Timestamp.from(NOW.minusSeconds(1)), first.identityId());
        var second = inSweepScope(() -> repository.claimCognitoReconciliationTarget(
                "eu-central-1_pool", "recon-b", NOW, NOW.plusSeconds(120))).orElseThrow();

        assertThat(second.fencingToken()).isGreaterThan(first.fencingToken());
        assertThatThrownBy(() -> inSweepScope(() -> {
            repository.completeCognitoReconciliationTarget(
                    first, "ACTIVE", NOW, NOW.plusSeconds(60));
            return null;
        })).isInstanceOf(IllegalStateException.class);
        inSweepScope(() -> {
            repository.completeCognitoReconciliationTarget(
                    second, "ACTIVE", NOW, NOW.plusSeconds(60));
            return null;
        });
    }

    @Test
    void providerExceptionReleasesTargetForPersistentRetry() {
        UUID target = seedIdentityWithFamily("subject-provider-down", "provider-down");
        var alerts = new java.util.ArrayList<SecurityAlertSink.SecurityAlert>();
        var service = new CognitoReconciliationService(
                repository,
                transactions,
                (user, issuer, subject) -> {
                    throw new IllegalStateException("provider unavailable");
                },
                new JdbcIamAuditWriter(runtimeDataSource),
                alerts::add,
                Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(),
                "eu-central-1_pool");

        assertThat(service.pollOne("provider-down-worker")).isTrue();

        assertThat(count("iam_cognito_reconciliation_targets WHERE user_id='" + target
                + "' AND lease_owner IS NULL AND last_checked_at IS NULL")).isEqualTo(1);
        assertThat(count("refresh_token_families WHERE user_id='" + target
                + "' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(alerts).extracting(SecurityAlertSink.SecurityAlert::type)
                .containsExactly(SecurityAlertSink.Type.PROVIDER_UNAVAILABLE);
    }

    @Test
    void reconciliationAuditFailureRollsBackFamilyBarrierAndCompletion() {
        UUID target = seedIdentityWithFamily("subject-recon-rollback", "recon-rollback");
        Instant barrierBefore = owner.queryForObject(
                "SELECT reauthentication_required_after FROM users WHERE id=?",
                Timestamp.class,
                target).toInstant();
        var service = new CognitoReconciliationService(
                repository,
                transactions,
                (user, issuer, subject) -> ProviderUserStatus.DISABLED,
                ignored -> {
                    throw new IllegalStateException("synthetic audit failure");
                },
                ignored -> { },
                Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(),
                "eu-central-1_pool");

        assertThatThrownBy(() -> service.pollOne("recon-rollback-worker"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(count("refresh_token_families WHERE user_id='" + target
                + "' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(owner.queryForObject(
                "SELECT reauthentication_required_after FROM users WHERE id=?",
                Timestamp.class,
                target).toInstant()).isEqualTo(barrierBefore);
        assertThat(count("iam_cognito_reconciliation_targets WHERE user_id='" + target
                + "' AND last_checked_at IS NULL AND last_provider_status IS NULL")).isEqualTo(1);
        assertThat(count("audit_logs WHERE target_entity_id='" + target + "'")).isZero();
    }

    @Test
    void activeStatusDoesNotRevokeOrAuditAndRepeatedDisabledStatusHasOneSideEffect() {
        UUID active = seedIdentityWithFamily("subject-active", "active");
        var activeService = new CognitoReconciliationService(
                repository,
                transactions,
                (user, issuer, subject) -> ProviderUserStatus.ACTIVE,
                new JdbcIamAuditWriter(runtimeDataSource),
                ignored -> { },
                Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(),
                "eu-central-1_pool");
        assertThat(activeService.pollOne("active-worker")).isTrue();
        assertThat(count("refresh_token_families WHERE user_id='" + active
                + "' AND revoked_at IS NULL")).isEqualTo(1);
        assertThat(count("audit_logs WHERE target_entity_id='" + active + "'")).isZero();

        owner.execute("TRUNCATE iam_cognito_reconciliation_targets, audit_logs CASCADE");
        owner.update("UPDATE refresh_token_families SET revoked_at=? WHERE user_id=?",
                Timestamp.from(NOW), active);
        UUID disabled = seedIdentityWithFamily("subject-disabled-once", "disabled-once");
        var disabledService = new CognitoReconciliationService(
                repository,
                transactions,
                (user, issuer, subject) -> ProviderUserStatus.DISABLED,
                new JdbcIamAuditWriter(runtimeDataSource),
                ignored -> { },
                Clock.fixed(NOW, ZoneOffset.UTC),
                issuer(),
                "eu-central-1_pool");
        assertThat(disabledService.pollOne("disabled-worker-a")).isTrue();
        owner.update("""
                UPDATE iam_cognito_reconciliation_targets
                SET next_check_at=?
                WHERE user_id=?
                """, Timestamp.from(NOW), disabled);
        assertThat(disabledService.pollOne("disabled-worker-b")).isTrue();

        assertThat(count("refresh_token_families WHERE user_id='" + disabled
                + "' AND revoked_at IS NOT NULL")).isEqualTo(1);
        assertThat(count("audit_logs WHERE actor_user_id IS NULL"
                + " AND target_entity_id='" + disabled + "'")).isEqualTo(1);
    }

    private <T> T inEventScope(java.util.function.Supplier<T> work) {
        return transactions.executeInGlobalScope(OperationCode.COGNITO_SECURITY_EVENT_PROCESS,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(null), work);
    }

    private <T> T inSweepScope(java.util.function.Supplier<T> work) {
        return transactions.executeInGlobalScope(
                OperationCode.COGNITO_RECONCILIATION_SWEEP,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(null),
                work);
    }

    private UUID seedIdentityWithFamily(String subject, String suffix) {
        UUID user = UUID.randomUUID(), device = UUID.randomUUID(), family = UUID.randomUUID();
        owner.update("INSERT INTO users(id,status,reauthentication_required_after) VALUES (?,'ACTIVE',?)", user, Timestamp.from(Instant.EPOCH));
        owner.update("INSERT INTO user_identities(id,user_id,issuer,subject) VALUES (?,?,?,?)", UUID.randomUUID(), user, issuer(), subject);
        owner.update("INSERT INTO trusted_devices(id,user_id,device_identifier,platform) VALUES (?,?,?,'ANDROID')", device, user, UUID.randomUUID());
        owner.update("INSERT INTO refresh_token_families(id,user_id,trusted_device_id,authenticated_at) VALUES (?,?,?,?)", family, user, device, Timestamp.from(NOW.minus(Duration.ofHours(1))));
        owner.update("INSERT INTO refresh_tokens(id,family_id,token_hash,access_token_hash,access_expires_at,issued_at,expires_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), family, "refresh-" + suffix, "access-" + suffix,
                Timestamp.from(Instant.now().plusSeconds(600)), Timestamp.from(Instant.now().minusSeconds(60)),
                Timestamp.from(Instant.now().plus(Duration.ofDays(30))));
        return user;
    }

    private CognitoSecurityEvent event(String id, String subject) {
        return new CognitoSecurityEvent("eu-central-1_pool", id, "AdminDisableUser", subject, NOW.minusSeconds(30));
    }

    private IamAuditEvent systemRevocationAudit(UUID target) {
        return new IamAuditEvent(
                UUID.randomUUID(),
                null,
                null,
                null,
                "IAM_PROVIDER_SESSION_REVOKED",
                IamAuditEvent.EventScope.GLOBAL,
                "USER",
                IamAuditEvent.EventKind.SECURITY,
                target,
                java.util.Map.of("providerStatus", "REVOKED"),
                java.util.Map.of(
                        "operationCode",
                        OperationCode.COGNITO_SECURITY_EVENT_PROCESS.name()));
    }

    private String issuer() { return "https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_pool"; }
    private long count(String tail) { return owner.queryForObject("SELECT count(*) FROM " + tail, Long.class); }
}
