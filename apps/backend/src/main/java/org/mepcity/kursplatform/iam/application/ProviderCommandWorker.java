package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

import java.util.UUID;

/**
 * The real create→claim→provider-call→fenced-completion chain, reachable and testable from
 * application code without any HTTP controller. The only production caller is {@link
 * ProviderCommandScheduler#pollOnce}, which finds PENDING USER_DISABLE / USER_LOGOUT rows whose
 * {@code next_attempt_at} has elapsed and feeds their ids to {@link #processOne}.
 *
 * <p>{@link ProviderCommandType#TEACHER_ACCOUNT_CREATE} and {@link ProviderCommandType#PASSWORD_RESET}
 * are NOT supported in V1 (see {@link ProviderCommandType#SUPPORTED_IN_WORKER}); the scheduler never
 * returns them, the RLS claim policy hides them, and {@link ProviderCommandWorkerAdapter}
 * implementations reject them defensively with {@code BUSINESS_RULE_VIOLATION} — they are never
 * left CLAIMED, unlike the pre-{@code reportProviderCallOutcome} behavior, so no poison-queue loop.
 */
public class ProviderCommandWorker {

    private final ProviderCommandService providerCommandService;
    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactionExecutor;
    private final ProviderCommandWorkerAdapter adapter;

    public ProviderCommandWorker(ProviderCommandService providerCommandService,
                                 IamAuthRepository repository,
                                 IamTransactionExecutor transactionExecutor,
                                 ProviderCommandWorkerAdapter adapter) {
        this.providerCommandService = providerCommandService;
        this.repository = repository;
        this.transactionExecutor = transactionExecutor;
        this.adapter = adapter;
    }

    /**
     * Claims {@code commandId} under {@code workerId}, resolves the target provider identity,
     * makes the real provider call, and reports the fenced completion — all three steps use the
     * SAME claimed lease (worker id + fencing token), so a lease lost to a reclaim mid-call is
     * rejected by {@code completeCommand}'s CAS exactly as it is for any other stale-worker
     * completion attempt.
     *
     * <p>Failures are routed through {@link ProviderCommandService#reportProviderCallOutcome}, which
     * decides retryable vs terminal vs exhausted and either requeues the command with bounded
     * exponential backoff or marks it {@code FAILED} with a safe terminal audit — never leaves it
     * CLAIMED. An adapter throwing on an unsupported command type fails the command as terminal
     * {@code BUSINESS_RULE_VIOLATION} (treated as non-retryable) rather than aborting the worker.
     */
    public ProviderCommandResult processOne(UUID commandId, String workerId) {
        ProviderCommandResult claimed = providerCommandService.claimCommand(commandId, workerId);

        // Defensive: the scheduler + RLS + createCommand already gate unsupported types, but if an
        // unsupported command somehow reached here (e.g. a hand-inserted row before V9 hardened the
        // claim policy), fail it as terminal instead of leaving it CLAIMED for a poison loop.
        if (!claimed.commandType().isSupportedInWorker()) {
            return providerCommandService.reportProviderCallOutcome(
                    claimed.id(), claimed.commandType(), workerId, claimed.fencingToken(),
                    claimed.attemptCount(),
                    ProviderCommandOutcome.ofFailure("BUSINESS_RULE_VIOLATION"));
        }

        // USER_DISABLE / USER_LOGOUT always store target_identity_id (table CHECK constraint);
        // TEACHER_ACCOUNT_CREATE never does, but is rejected above so it never reaches this resolve.
        UserIdentity identity = transactionExecutor.executeInGlobalScope(
                        OperationCode.PROVIDER_COMMAND_CLAIM,
                        IamAuthScopeContext.actorOnly(null),
                        () -> repository.findClaimedCommandTargetIdentity(claimed.targetIdentityId()))
                .orElseThrow(() -> new IamException("STATE_CONFLICT",
                        "Claim edilen komutun hedef identity'si artık görünür değil (lease elden gitmiş olabilir)."));

        ProviderCommandOutcome outcome;
        try {
            outcome = adapter.execute(claimed.commandType(), identity.subject(), identity.issuer());
        } catch (UnsupportedOperationException e) {
            // Adapter genuinely does not implement this command type — treat as terminal business
            // violation so the row goes FAILED (with audit) and the scheduler stops retrying it.
            outcome = ProviderCommandOutcome.ofFailure("BUSINESS_RULE_VIOLATION");
        }

        return providerCommandService.reportProviderCallOutcome(
                claimed.id(), claimed.commandType(), workerId, claimed.fencingToken(),
                claimed.attemptCount(), outcome);
    }
}
