package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import jakarta.annotation.PreDestroy;
import javax.sql.DataSource;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * IAM-004 worker/scheduler: the single production path that turns PENDING USER_DISABLE /
 * USER_LOGOUT provider commands into real Cognito Admin API calls. Backed by {@link
 * ProviderCommandWorker#processOne} for the per-command claim→resolve→call→complete chain and by
 * {@link IamAuthRepository#findNextClaimableProviderCommandIds} for the poll query.
 *
 * <p>Design constraints (per IAM-004 fix round):
 * <ul>
 *   <li><b>Disabled by default.</b> Activated only when
 *       {@code iam.provider-command.worker.enabled=true} AND a {@link DataSource} bean exists, so a
 *       dev / CI / stub boot never polls and a deployment must consciously opt in per A-010's
 *       "production requires deliberate configuration" gate.</li>
 *   <li><b>Thin trigger, testable core.</b> {@link Scheduled} only calls {@link #pollOnce()};
 *       tests exercise {@link #pollOnce()} directly without waiting on a timer.</li>
 *   <li><b>Stable worker identity.</b> A single random UUID generated once per process — never
 *       re-derived per poll — so the lease_owner column is meaningful across an instance's lifetime
 *       and the fencing-token CAS in the repository is the sole guard against double-execution.
 *       Multi-instance safety comes from that DB CAS, NOT from this scheduler being a singleton.</li>
 *   <li><b>Graceful shutdown.</b> {@link #stop()} flips a volatile flag so the next scheduled tick
 *       takes no new work; in-flight {@code processOne} calls finish on their own (the DB lease
 *       expires if the JVM is killed mid-call, after which another instance reclaims it).</li>
 *   <li><b>Resilient.</b> A single command's failure (or even an exception out of the poll query)
 *       is caught and logged; it never tears down the scheduler — the next tick retries.</li>
 * </ul>
 *
 * <p><b>Single owner:</b> this class is deliberately a plain object, NOT a Spring stereotype
 * ({@code @Configuration}/{@code @Component}) — the {@code @ConditionalOnBean(DataSource.class)} /
 * {@code @ConditionalOnProperty(...)} gate lives solely on {@code
 * IamInfrastructureConfiguration.providerCommandScheduler(...)}. Annotating this class itself with
 * {@code @Configuration} in addition to that {@code @Bean} method previously made classpath
 * component scan (rooted at the application's root package) register a SECOND bean definition
 * under the same default name, which Spring Boot's bean-definition-overriding-disabled default
 * turns into a hard {@code BeanDefinitionOverrideException} at context startup the moment the
 * worker is enabled with a real {@link DataSource} present.</p>
 */
public class ProviderCommandScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderCommandScheduler.class);

    private final ProviderCommandWorker worker;
    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactionExecutor;
    private final IamServiceSettings settings;
    private final Clock clock;
    private final String workerId;

    private volatile boolean running = true;

    public ProviderCommandScheduler(ProviderCommandWorker worker,
                                    IamAuthRepository repository,
                                    IamTransactionExecutor transactionExecutor,
                                    IamServiceSettings settings,
                                    Clock clock,
                                    SecureRandom secureRandom) {
        this.worker = worker;
        this.repository = repository;
        this.transactionExecutor = transactionExecutor;
        this.settings = settings;
        this.clock = clock;
        // Per-process identity, generated ONCE. SecureRandom so two instances started in the same
        // second cannot collide; the value is opaque and carries no host information (hostname is
        // not required and would add an avoidable external dependency for tests/CI).
        byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);
        this.workerId = "iam-worker-" + UUID.nameUUIDFromBytes(bytes);
        LOG.info("IAM provider-command worker enabled: workerId={}, pollInterval={}, batchLimit={}",
                workerId, settings.providerCommandPollInterval(), settings.providerCommandBatchLimit());
    }

    /**
     * Spring's scheduler calls this on the configured {@code fixedDelay}. Delegates to {@link
     * #pollOnce()} but NEVER lets an exception escape — a single bad tick must not disable the
     * scheduler, since there is no human in the loop to restart it in production.
     */
    @Scheduled(fixedDelayString = "${iam.provider-command.worker.poll-interval:60s}")
    public void scheduledTick() {
        if (!running) {
            return;
        }
        try {
            pollOnce();
        } catch (Exception e) {
            // Log and move on; the next tick will retry. The DB CAS guarantees no double-execution
            // even if this tick partially ran before failing.
            LOG.warn("IAM provider-command worker poll failed; will retry next tick", e);
        }
    }

    /**
     * Processes up to {@link IamServiceSettings#providerCommandBatchLimit()} due PENDING commands
     * in a single pass. Each command is processed independently: one command's failure does not
     * stop the rest of the batch (the failure has already been recorded on the row itself by {@link
     * ProviderCommandService#reportProviderCallOutcome} — as FAILED, requeued-PENDING or left
     * CLAIMED-on-lease-loss — before it propagates here). Returns the number of commands processed
     * (success or failure) so callers/tests can assert deterministically without waiting on a timer.
     */
    public int pollOnce() {
        if (!running) {
            return 0;
        }
        // The poll query itself is RLS-gated by iam_provider_commands_select_worker, which requires
        // GLOBAL/PROVIDER_COMMAND_CLAIM to be set on the connection — the same scope/code
        // ProviderCommandService.claimCommand uses. Without opening this scope first, iam_runtime's
        // default-deny RLS would hide every row and the scheduler would silently never find any work.
        List<UUID> dueCommandIds = transactionExecutor.executeInGlobalScope(
                OperationCode.PROVIDER_COMMAND_CLAIM, IamAuthScopeContext.actorOnly(null),
                () -> repository.findNextClaimableProviderCommandIds(
                        settings.providerCommandBatchLimit(), clock.instant()));
        int processed = 0;
        for (UUID commandId : dueCommandIds) {
            if (!running) {
                // Shutdown requested mid-batch: stop taking new commands. The CAS on the next
                // pollOnce (by this instance after restart, or by another instance) will pick up
                // any row we did not get to — they remain PENDING with their lease_owner NULL.
                break;
            }
            try {
                worker.processOne(commandId, workerId);
            } catch (Exception e) {
                // processOne has already persisted whatever terminal/requeue outcome applied; any
                // exception reaching here is from the surrounding infra (e.g. a connection issue
                // during the fenced CAS). Log and continue with the next command — the row stays
                // CLAIMED until its lease expires, then another poll reclaims it.
                LOG.warn("IAM provider-command worker processOne failed for commandId={}", commandId, e);
            }
            processed++;
        }
        if (processed > 0) {
            LOG.debug("IAM provider-command worker processed {} command(s)", processed);
        }
        return processed;
    }

    /**
     * Graceful shutdown hook: future ticks take no new work. In-flight {@code processOne} calls
     * are NOT interrupted (interrupting a mid-CAS call could leave a half-applied state); they
     * finish on their own, and any lease they hold expires naturally if the JVM dies first.
     */
    @PreDestroy
    public void stop() {
        running = false;
        LOG.info("IAM provider-command worker shutting down: no new commands will be claimed");
    }
}
