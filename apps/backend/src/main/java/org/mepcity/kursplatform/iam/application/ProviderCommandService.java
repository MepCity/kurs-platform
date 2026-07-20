package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.application.IamTransactionExecutor.IamAuthScopeContext;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Every provider-command operation runs inside an explicit, narrow IAM transaction scope with a
 * dedicated operation code — never an ambient/autocommit connection and never a borrowed or
 * random operation code. Claim uses GLOBAL scope with the generic {@code PROVIDER_COMMAND_CLAIM}
 * code (the V1 RLS policies for claim/reclaim are intentionally type-agnostic); complete is scoped
 * per command type instead, matching the type-specific {@code ipc_complete}/{@code
 * ipc_complete_global} RLS policies exactly — {@code TEACHER_ACCOUNT_CREATE} completes under
 * {@code IAM_PROVISIONING}, the three lifecycle types complete under {@code GLOBAL} with their own
 * matching code. {@code app.iam_worker_id}/{@code app.iam_fencing_token} are set only inside
 * the JDBC repository's claim/reclaim/complete SQL itself (via a bind-param-safe {@code set_config})
 * from the caller-supplied worker
 * identity — never from any other source, so a worker can only ever assert its own identity.
 */
public class ProviderCommandService {

    private static final Duration DEFAULT_LEASE_TTL = Duration.ofMinutes(5);

    private final IamAuthRepository repository;
    private final Clock clock;
    private final IamTransactionExecutor transactionExecutor;
    private final IamAuditWriter auditWriter;
    private final ProviderCommandRetryPolicy retryPolicy;

    public ProviderCommandService(IamAuthRepository repository, Clock clock,
                                  IamTransactionExecutor transactionExecutor,
                                  IamAuditWriter auditWriter,
                                  ProviderCommandRetryPolicy retryPolicy) {
        this.repository = repository;
        this.clock = clock;
        this.transactionExecutor = transactionExecutor;
        this.auditWriter = auditWriter;
        this.retryPolicy = retryPolicy;
    }

    /**
     * @param resolvingUserId the internal user id that owns {@code targetIdentityId}, required
     *                        (and used only) for the RLS scope of USER_DISABLE/USER_LOGOUT/
     *                        PASSWORD_RESET commands — iam_provider_commands_insert_global's
     *                        WITH CHECK joins user_identities on both the identity and this user
     *                        id, but the row itself never stores target_user_id for these types
     *                        (table CHECK constraint), so the caller (who already resolved this
     *                        identity to a user before deciding to target it) must supply it
     *                        out-of-band. Ignored for TEACHER_ACCOUNT_CREATE (use targetUserId).
     */
    public ProviderCommandResult createCommand(ProviderCommandType commandType,
                                               UUID targetUserId,
                                               UUID targetIdentityId,
                                               UUID resolvingUserId,
                                               UUID organizationId,
                                               String usernameLookupHash,
                                               String payloadFingerprint,
                                               byte[] encryptedPayload,
                                               String payloadKeyId,
                                               String idempotencyKey) {
        Instant now = clock.instant();
        // Allow-list gate: only the worker-supported command types can ever be created via the
        // application. PASSWORD_RESET and TEACHER_ACCOUNT_CREATE both require decrypting
        // encrypted_command_payload before a provider call can be built; no KMS/payload-decryption
        // service exists in V1, so creating a PENDING row for them would only ever produce a poison
        // CLAIMED retry loop. Reject at the source rather than letting a row sit unreachable.
        if (!commandType.isSupportedInWorker()) {
            throw new IamException("BUSINESS_RULE_VIOLATION",
                    commandType + " bu sürümde desteklenmiyor; KMS/payload-decryption henüz uygulanmadı.");
        }
        validateCreateCommand(commandType, targetUserId, targetIdentityId, organizationId, usernameLookupHash,
                encryptedPayload, payloadKeyId);
        ProviderCommand command = new ProviderCommand(
                UUID.randomUUID(), idempotencyKey, "cognito", commandType,
                targetUserId, targetIdentityId, organizationId, usernameLookupHash,
                payloadFingerprint, encryptedPayload, payloadKeyId,
                ProviderCommandStatus.PENDING, 0, now, null, 0, null,
                now, null, null);

        if (commandType == ProviderCommandType.TEACHER_ACCOUNT_CREATE) {
            return transactionExecutor.executeInProvisioningScope(
                    OperationCode.TEACHER_ACCOUNT_CREATE,
                    IamAuthScopeContext.actorOnly(null).withTargetUserAndOrganization(targetUserId, organizationId),
                    () -> createOrFindExisting(command, payloadFingerprint));
        }
        if (resolvingUserId == null) {
            throw new IamException("VALIDATION_FAILED", "resolvingUserId zorunludur.");
        }
        return transactionExecutor.executeInGlobalScope(
                OperationCode.valueOf(commandType.name()),
                IamAuthScopeContext.actorOnly(null).withTargetIdentity(targetIdentityId, resolvingUserId),
                () -> createOrFindExisting(command, payloadFingerprint));
    }

    private ProviderCommandResult createOrFindExisting(ProviderCommand command, String expectedFingerprint) {
        Optional<ProviderCommand> existing = repository.findProviderCommandByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return toResultVerifyingFingerprint(existing.get(), expectedFingerprint);
        }
        ProviderCommand saved = repository.saveProviderCommand(command);
        if (!saved.id().equals(command.id())) {
            // Lost the create race to a concurrent request; saved is the DB's row, not ours.
            return toResultVerifyingFingerprint(saved, expectedFingerprint);
        }
        return toResult(saved);
    }

    private ProviderCommandResult toResultVerifyingFingerprint(ProviderCommand existing, String expectedFingerprint) {
        if (!existing.payloadFingerprint().equals(expectedFingerprint)) {
            throw new IamException("IDEMPOTENCY_KEY_REUSED",
                    "Aynı idempotency key farklı komut içeriğiyle kullanılmış.");
        }
        return toResult(existing);
    }

    /**
     * GLOBAL scope, generic {@code PROVIDER_COMMAND_CLAIM} code — matches
     * {@code iam_provider_commands_select_worker}/{@code ipc_claim}/{@code ipc_claim_renew}, which
     * are intentionally not command-type-specific. Tries a fresh claim first, then an expired-lease
     * reclaim; if RLS hides the row entirely (terminal, or claimed by someone else with a lease
     * still valid) both return empty and this reports STATE_CONFLICT without guessing which case
     * applied — RLS no longer lets this code see enough of the row to tell the difference, which
     * is the correct fail-closed outcome once row-level security is actually enforced.
     */
    public ProviderCommandResult claimCommand(UUID commandId, String workerId) {
        Instant now = clock.instant();
        Instant leaseExpiresAt = now.plus(DEFAULT_LEASE_TTL);
        return transactionExecutor.executeInGlobalScope(
                OperationCode.PROVIDER_COMMAND_CLAIM,
                IamAuthScopeContext.actorOnly(null),
                () -> {
                    Optional<ProviderCommand> claimed = repository.claimProviderCommandAtomically(
                            commandId, workerId, leaseExpiresAt, now);
                    if (claimed.isPresent()) {
                        return toResult(claimed.get());
                    }
                    Optional<ProviderCommand> reclaimed = repository.reclaimExpiredLeaseAtomically(
                            commandId, workerId, leaseExpiresAt, now);
                    if (reclaimed.isPresent()) {
                        return toResult(reclaimed.get());
                    }
                    throw new IamException("STATE_CONFLICT",
                            "Komut claim edilemedi (bulunamadı, terminal veya lease hâlâ geçerli).");
                });
    }

    /**
     * Scoped per command type, matching {@code ipc_complete} (IAM_PROVISIONING,
     * TEACHER_ACCOUNT_CREATE) or {@code ipc_complete_global} (GLOBAL, the matching lifecycle code)
     * exactly — never PROVIDER_COMMAND_CLAIM, which claim/reclaim use but complete's RLS policies do
     * not accept. The caller supplies {@code commandType}/{@code targetUserId}/
     * {@code organizationId} from the {@link ProviderCommandResult} it received from
     * {@link #claimCommand}, since RLS now genuinely hides rows this service can't otherwise
     * re-read without already knowing which scope to ask under.
     */
    public ProviderCommandResult completeCommand(UUID commandId, ProviderCommandType commandType,
                                                 UUID targetUserId, UUID organizationId,
                                                 String workerId, long fencingToken,
                                                 boolean success, String safeErrorCode) {
        Instant now = clock.instant();
        if (commandType == ProviderCommandType.TEACHER_ACCOUNT_CREATE) {
            return transactionExecutor.executeInProvisioningScope(
                    OperationCode.TEACHER_ACCOUNT_CREATE,
                    IamAuthScopeContext.actorOnly(null).withTargetUserAndOrganization(targetUserId, organizationId),
                    () -> completeAndMap(commandId, workerId, fencingToken, success, safeErrorCode, now));
        }
        // ipc_complete_global checks only operation_scope/code/status/lease/fencing — no
        // target_identity_id/target_user_id at all, unlike the create/insert policy. Audited: the
        // audit_logs_insert_provider_command RLS policy accepts exactly these three lifecycle codes.
        return transactionExecutor.executeInGlobalScope(
                OperationCode.valueOf(commandType.name()),
                IamAuthScopeContext.actorOnly(null),
                () -> {
                    ProviderCommandResult result = completeAndMap(
                            commandId, workerId, fencingToken, success, safeErrorCode, now);
                    auditWriter.write(new IamAuditEvent(
                            UUID.randomUUID(), null, null, MDC.get("requestId"),
                            "PROVIDER_COMMAND_COMPLETED", IamAuditEvent.EventScope.GLOBAL,
                            "PROVIDER_COMMAND", IamAuditEvent.EventKind.SECURITY, commandId,
                            Map.of("success", success, "safeErrorCode", safeErrorCode == null ? "" : safeErrorCode),
                            Map.of("operationCode", commandType.name())));
                    return result;
                });
    }

    /**
     * Worker-driven terminal decision: takes the raw {@link ProviderCommandOutcome} from the
     * adapter together with the claimed command's current {@code attemptCount} and decides:
     * <ul>
     *   <li>success → {@code COMPLETED} + {@code PROVIDER_COMMAND_COMPLETED} audit;</li>
     *   <li>non-retryable failure (4xx, auth) → {@code FAILED} + {@code PROVIDER_COMMAND_FAILED}
     *       audit — never retried;</li>
     *   <li>retryable failure (network, throttle, 5xx) and attempts still left → release the lease
     *       via {@code requeueClaimedForRetry} (CLAIMED → PENDING, lease cleared, fencing_token
     *       reset, next_attempt_at set with bounded exponential backoff + jitter) — no audit;
     *       retry attempts are not audited, only terminal outcomes (sözleşme §12.3);</li>
     *   <li>retryable failure and {@code attemptCount >= maxAttempts} → {@code FAILED} +
     *       {@code PROVIDER_COMMAND_EXHAUSTED} audit.</li>
     * </ul>
     * Caller passes the claim's own {@code attemptCount}/{@code fencingToken} so this method never
     * re-reads the row (RLS would hide a lease-lost row mid-call); the fenced CAS in the repository
     * is the source of truth and silently no-ops if the lease was already reclaimed out from under us.
     */
    public ProviderCommandResult reportProviderCallOutcome(UUID commandId, ProviderCommandType commandType,
                                                           String workerId, long fencingToken,
                                                           int attemptCount,
                                                           ProviderCommandOutcome outcome) {
        Instant now = clock.instant();
        if (outcome.success()) {
            return completeCommand(commandId, commandType, null, null,
                    workerId, fencingToken, true, null);
        }
        String safeErrorCode = outcome.safeErrorCode();
        boolean retryable = retryPolicy.isRetryable(safeErrorCode);
        boolean exhausted = retryPolicy.isExhausted(attemptCount);
        if (!retryable) {
            return completeAndAuditTerminal(commandId, commandType, workerId, fencingToken,
                    false, safeErrorCode, now, "PROVIDER_COMMAND_FAILED");
        }
        if (exhausted) {
            return completeAndAuditTerminal(commandId, commandType, workerId, fencingToken,
                    false, "PROVIDER_COMMAND_EXHAUSTED", now, "PROVIDER_COMMAND_EXHAUSTED");
        }
        // Retryable and not exhausted: release the lease back to PENDING with backoff. The trigger's
        // retry-requeue branch (V9) and the ipc_retry RLS policy require the SAME worker+fencing that
        // currently holds the CLAIMED lease — exactly what we pass here — so a lease lost to a
        // concurrent reclaim mid-call silently no-ops (returns empty) instead of clobbering the new
        // owner. We surface that as STATE_CONFLICT so the scheduler logs it and moves on; the row is
        // not lost, the new owner (or this worker on its next poll) will pick it up.
        Instant nextAttemptAt = retryPolicy.nextAttemptAt(now, attemptCount);
        return transactionExecutor.executeInGlobalScope(
                OperationCode.valueOf(commandType.name()),
                IamAuthScopeContext.actorOnly(null),
                () -> {
                    Optional<ProviderCommand> requeued = repository.requeueClaimedForRetry(
                            commandId, workerId, fencingToken, nextAttemptAt, now);
                    if (requeued.isEmpty()) {
                        throw new IamException("STATE_CONFLICT",
                                "Komut yeniden kuyruğa alınamadı (lease elden gitmiş olabilir).");
                    }
                    return toResult(requeued.get());
                });
    }

    private ProviderCommandResult completeAndAuditTerminal(UUID commandId, ProviderCommandType commandType,
                                                           String workerId, long fencingToken,
                                                           boolean success, String safeErrorCode,
                                                           Instant now, String auditActionType) {
        return transactionExecutor.executeInGlobalScope(
                OperationCode.valueOf(commandType.name()),
                IamAuthScopeContext.actorOnly(null),
                () -> {
                    ProviderCommandResult result = completeAndMap(
                            commandId, workerId, fencingToken, success, safeErrorCode, now);
                    auditWriter.write(new IamAuditEvent(
                            UUID.randomUUID(), null, null, MDC.get("requestId"),
                            auditActionType, IamAuditEvent.EventScope.GLOBAL,
                            "PROVIDER_COMMAND", IamAuditEvent.EventKind.SECURITY, commandId,
                            Map.of("success", success, "safeErrorCode", safeErrorCode == null ? "" : safeErrorCode),
                            Map.of("operationCode", commandType.name())));
                    return result;
                });
    }

    private ProviderCommandResult completeAndMap(UUID commandId, String workerId, long fencingToken,
                                                 boolean success, String safeErrorCode, Instant now) {
        Optional<ProviderCommand> completed = repository.completeProviderCommandAtomically(
                commandId, workerId, fencingToken, success, safeErrorCode, now);
        if (completed.isEmpty()) {
            throw new IamException("STATE_CONFLICT",
                    "Komut tamamlanamadı (bulunamadı, terminal, eski worker/fencing token veya lease süresi dolmuş).");
        }
        return toResult(completed.get());
    }

    private void validateCreateCommand(ProviderCommandType type, UUID targetUserId, UUID targetIdentityId,
                                       UUID organizationId, String usernameLookupHash,
                                       byte[] encryptedPayload, String payloadKeyId) {
        if (type == ProviderCommandType.TEACHER_ACCOUNT_CREATE) {
            if (targetUserId == null || organizationId == null || usernameLookupHash == null
                    || encryptedPayload == null || payloadKeyId == null || targetIdentityId != null) {
                throw new IamException("VALIDATION_FAILED", "TEACHER_ACCOUNT_CREATE alanları geçersiz.");
            }
        } else {
            if (targetIdentityId == null || targetUserId != null) {
                throw new IamException("VALIDATION_FAILED", "USER_DISABLE/USER_LOGOUT/PASSWORD_RESET alanları geçersiz.");
            }
        }
    }

    private ProviderCommandResult toResult(ProviderCommand command) {
        return new ProviderCommandResult(
                command.id(), command.idempotencyKey(), command.provider(), command.commandType(),
                command.targetUserId(), command.targetIdentityId(), command.organizationId(),
                command.status(), command.attemptCount(), command.nextAttemptAt(),
                command.leaseExpiresAt(), command.fencingToken(), command.leaseOwner(),
                command.createdAt(), command.completedAt(), command.lastSafeErrorCode());
    }
}
