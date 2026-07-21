package org.mepcity.kursplatform.iam.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProviderCommandServiceTests {

    private IamAuthRepository repository;
    private IamAuditWriter auditWriter;
    private Clock clock;
    private ProviderCommandService service;
    private final Instant fixedNow = Instant.parse("2026-07-20T10:00:00Z");

    @BeforeEach
    void setUp() {
        repository = mock(IamAuthRepository.class);
        auditWriter = mock(IamAuditWriter.class);
        clock = Clock.fixed(fixedNow, ZoneOffset.UTC);
        service = new ProviderCommandService(repository, clock, new NoopTransactionExecutor(),
                auditWriter, newRetryPolicy());
    }

    @Test
    void createCommandSucceedsForUserDisable() {
        UUID targetIdentityId = UUID.randomUUID();
        when(repository.findProviderCommandByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(repository.saveProviderCommand(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderCommandResult result = service.createCommand(
                ProviderCommandType.USER_DISABLE, null, targetIdentityId, UUID.randomUUID(),
                null, null, "fp", new byte[]{1}, "key-1", "idem-1");

        assertThat(result.commandType()).isEqualTo(ProviderCommandType.USER_DISABLE);
        assertThat(result.status()).isEqualTo(ProviderCommandStatus.PENDING);
        assertThat(result.targetIdentityId()).isEqualTo(targetIdentityId);
        assertThat(result.targetUserId()).isNull();
    }

    @Test
    void createCommandFailsForUserDisableWithTargetUserId() {
        UUID targetIdentityId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createCommand(
                ProviderCommandType.USER_DISABLE, targetUserId, targetIdentityId, UUID.randomUUID(),
                null, null, "fp", new byte[]{1}, "key-1", "idem-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void createCommandRejectsTeacherAccountCreateAtTheAllowListGateBeforeFieldValidation() {
        // TEACHER_ACCOUNT_CREATE is outside ProviderCommandType.SUPPORTED_IN_WORKER (STAFF-002 will
        // widen it once KMS/payload-decryption exists), so createCommand's allow-list gate rejects
        // it with BUSINESS_RULE_VIOLATION before validateCreateCommand ever runs — even with fields
        // that would otherwise be valid — so no PENDING row for it can ever be created via the app.
        assertThatThrownBy(() -> service.createCommand(
                ProviderCommandType.TEACHER_ACCOUNT_CREATE, UUID.randomUUID(), null, null,
                UUID.randomUUID(), "hash", "fp", new byte[]{1}, "key-1", "idem-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("BUSINESS_RULE_VIOLATION");
    }

    @Test
    void createCommandFailsWhenExistingIdempotencyKeyHasDifferentFingerprint() {
        UUID targetIdentityId = UUID.randomUUID();
        ProviderCommand existing = new ProviderCommand(
                UUID.randomUUID(), "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, targetIdentityId, null, null, "different-fp", null, null,
                ProviderCommandStatus.PENDING, 0, fixedNow, null, 0, null, fixedNow, null, null);
        when(repository.findProviderCommandByIdempotencyKey("idem-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createCommand(
                ProviderCommandType.USER_DISABLE, null, targetIdentityId, UUID.randomUUID(),
                null, null, "fp", new byte[]{1}, "key-1", "idem-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void claimCommandTransitionsPendingToClaimed() {
        UUID commandId = UUID.randomUUID();
        ProviderCommand claimed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.CLAIMED, 1, fixedNow, fixedNow.plusSeconds(300),
                1, "worker-1", fixedNow, null, null);
        when(repository.claimProviderCommandAtomically(commandId, "worker-1", fixedNow.plusSeconds(300), fixedNow))
                .thenReturn(Optional.of(claimed));

        ProviderCommandResult result = service.claimCommand(commandId, "worker-1");

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.CLAIMED);
        assertThat(result.leaseOwner()).isEqualTo("worker-1");
        assertThat(result.fencingToken()).isEqualTo(1);
        assertThat(result.attemptCount()).isEqualTo(1);
        assertThat(result.leaseExpiresAt()).isNotNull();
    }

    @Test
    void claimCommandFallsBackToReclaimWhenNotFreshlyPending() {
        UUID commandId = UUID.randomUUID();
        ProviderCommand reclaimed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.CLAIMED, 1, fixedNow, fixedNow.plusSeconds(300),
                2, "worker-2", fixedNow, null, null);
        when(repository.claimProviderCommandAtomically(commandId, "worker-2", fixedNow.plusSeconds(300), fixedNow))
                .thenReturn(Optional.empty());
        when(repository.reclaimExpiredLeaseAtomically(commandId, "worker-2", fixedNow.plusSeconds(300), fixedNow))
                .thenReturn(Optional.of(reclaimed));

        ProviderCommandResult result = service.claimCommand(commandId, "worker-2");

        assertThat(result.fencingToken()).isEqualTo(2);
        assertThat(result.leaseOwner()).isEqualTo("worker-2");
    }

    @Test
    void claimCommandFailsWhenNeitherClaimNorReclaimMatch() {
        UUID commandId = UUID.randomUUID();
        when(repository.claimProviderCommandAtomically(any(), any(), any(), any())).thenReturn(Optional.empty());
        when(repository.reclaimExpiredLeaseAtomically(any(), any(), any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimCommand(commandId, "worker-1"))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("STATE_CONFLICT");
    }

    @Test
    void completeCommandTransitionsClaimedToCompleted() {
        UUID commandId = UUID.randomUUID();
        UUID targetIdentityId = UUID.randomUUID();
        ProviderCommand completed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, targetIdentityId, null, null, "fp", null, null,
                ProviderCommandStatus.COMPLETED, 1, fixedNow, null, 1, null,
                fixedNow, fixedNow, null);
        when(repository.completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any()))
                .thenReturn(Optional.of(completed));

        ProviderCommandResult result = service.completeCommand(commandId, ProviderCommandType.USER_DISABLE,
                null, null, "worker-1", 1, true, null);

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.COMPLETED);
        assertThat(result.leaseOwner()).isNull();
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    void completeCommandFailsWithWrongWorkerId() {
        UUID commandId = UUID.randomUUID();
        UUID targetIdentityId = UUID.randomUUID();
        when(repository.completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeCommand(commandId, ProviderCommandType.USER_DISABLE,
                null, null, "wrong-worker", 1, true, null))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("STATE_CONFLICT");
    }

    @Test
    void completeCommandFailsWithWrongFencingToken() {
        UUID commandId = UUID.randomUUID();
        UUID targetIdentityId = UUID.randomUUID();
        when(repository.completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeCommand(commandId, ProviderCommandType.USER_DISABLE,
                null, null, "worker-1", 99, true, null))
                .isInstanceOf(IamException.class)
                .extracting("errorCode").isEqualTo("STATE_CONFLICT");
    }

    @Test
    void completeCommandTransitionsClaimedToFailed() {
        UUID commandId = UUID.randomUUID();
        UUID targetIdentityId = UUID.randomUUID();
        ProviderCommand failed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, targetIdentityId, null, null, "fp", null, null,
                ProviderCommandStatus.FAILED, 1, fixedNow, null, 1, null,
                fixedNow, fixedNow, "PROVIDER_ERROR");
        when(repository.completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any()))
                .thenReturn(Optional.of(failed));

        ProviderCommandResult result = service.completeCommand(commandId, ProviderCommandType.USER_DISABLE,
                null, null, "worker-1", 1, false, "PROVIDER_ERROR");

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.FAILED);
        assertThat(result.lastSafeErrorCode()).isEqualTo("PROVIDER_ERROR");
    }

    @Test
    void completeCommandForTeacherAccountCreateUsesProvisioningScope() {
        UUID commandId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        ProviderCommand completed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.TEACHER_ACCOUNT_CREATE,
                targetUserId, null, organizationId, "hash", "fp", new byte[]{1}, "key-1",
                ProviderCommandStatus.COMPLETED, 1, fixedNow, null, 1, null, fixedNow, fixedNow, null);
        RecordingTransactionExecutor recordingExecutor = new RecordingTransactionExecutor();
        ProviderCommandService provisioningService =
                new ProviderCommandService(repository, clock, recordingExecutor, mock(IamAuditWriter.class),
                        newRetryPolicy());
        when(repository.completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any()))
                .thenReturn(Optional.of(completed));

        provisioningService.completeCommand(commandId, ProviderCommandType.TEACHER_ACCOUNT_CREATE,
                targetUserId, organizationId, "worker-1", 1, true, null);

        assertThat(recordingExecutor.lastProvisioningCode).isEqualTo(OperationCode.TEACHER_ACCOUNT_CREATE);
    }

    @Test
    void reportProviderCallOutcomeCompletesOnSuccess() {
        UUID commandId = UUID.randomUUID();
        ProviderCommand completed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.COMPLETED, 1, fixedNow, null, 1, null, fixedNow, fixedNow, null);
        when(repository.completeProviderCommandAtomically(commandId, "worker-1", 1L, true, null, fixedNow))
                .thenReturn(Optional.of(completed));

        ProviderCommandResult result = service.reportProviderCallOutcome(commandId, ProviderCommandType.USER_DISABLE,
                "worker-1", 1L, 1, ProviderCommandOutcome.ofSuccess());

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.COMPLETED);
    }

    @Test
    void reportProviderCallOutcomeRequeuesRetryableFailureWhenAttemptsRemain() {
        UUID commandId = UUID.randomUUID();
        ProviderCommand requeued = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.PENDING, 1, fixedNow.plusSeconds(10), null, 0, null, fixedNow, null, null);
        when(repository.requeueClaimedForRetry(eq(commandId), eq("worker-1"), eq(1L), any(), eq(fixedNow)))
                .thenReturn(Optional.of(requeued));

        ProviderCommandResult result = service.reportProviderCallOutcome(commandId, ProviderCommandType.USER_DISABLE,
                "worker-1", 1L, 1, ProviderCommandOutcome.ofFailure("PROVIDER_ERROR_5XX"));

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.PENDING);
        verify(repository, never()).completeProviderCommandAtomically(any(), any(), anyLong(), anyBoolean(), any(), any());
        verify(auditWriter, never()).write(any());
    }

    @Test
    void reportProviderCallOutcomeMarksExhaustedFailedAndAuditsWhenRetriesRunOut() {
        UUID commandId = UUID.randomUUID();
        ProviderCommandRetryPolicy singleAttemptPolicy = new ProviderCommandRetryPolicy(1,
                java.time.Duration.ofSeconds(10), java.time.Duration.ofMinutes(15), 0.25d, new java.security.SecureRandom());
        ProviderCommandService exhaustingService = new ProviderCommandService(repository, clock,
                new NoopTransactionExecutor(), auditWriter, singleAttemptPolicy);
        ProviderCommand failed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.FAILED, 1, fixedNow, null, 1, null, fixedNow, fixedNow, "PROVIDER_COMMAND_EXHAUSTED");
        when(repository.completeProviderCommandAtomically(commandId, "worker-1", 1L, false,
                "PROVIDER_COMMAND_EXHAUSTED", fixedNow)).thenReturn(Optional.of(failed));

        ProviderCommandResult result = exhaustingService.reportProviderCallOutcome(commandId, ProviderCommandType.USER_DISABLE,
                "worker-1", 1L, 1, ProviderCommandOutcome.ofFailure("PROVIDER_ERROR_5XX"));

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.FAILED);
        verify(repository, never()).requeueClaimedForRetry(any(), any(), anyLong(), any(), any());
        verify(auditWriter).write(org.mockito.ArgumentMatchers.argThat(event ->
                event.actionType().equals("PROVIDER_COMMAND_EXHAUSTED")));
    }

    @Test
    void reportProviderCallOutcomeMarksFailedImmediatelyAndAuditsForNonRetryableError() {
        UUID commandId = UUID.randomUUID();
        ProviderCommand failed = new ProviderCommand(
                commandId, "idem-1", "cognito", ProviderCommandType.USER_DISABLE,
                null, UUID.randomUUID(), null, null, "fp", null, null,
                ProviderCommandStatus.FAILED, 1, fixedNow, null, 1, null, fixedNow, fixedNow, "PROVIDER_ERROR_4XX");
        when(repository.completeProviderCommandAtomically(commandId, "worker-1", 1L, false,
                "PROVIDER_ERROR_4XX", fixedNow)).thenReturn(Optional.of(failed));

        ProviderCommandResult result = service.reportProviderCallOutcome(commandId, ProviderCommandType.USER_DISABLE,
                "worker-1", 1L, 1, ProviderCommandOutcome.ofFailure("PROVIDER_ERROR_4XX"));

        assertThat(result.status()).isEqualTo(ProviderCommandStatus.FAILED);
        assertThat(result.lastSafeErrorCode()).isEqualTo("PROVIDER_ERROR_4XX");
        verify(repository, never()).requeueClaimedForRetry(any(), any(), anyLong(), any(), any());
        verify(auditWriter).write(org.mockito.ArgumentMatchers.argThat(event ->
                event.actionType().equals("PROVIDER_COMMAND_FAILED")));
    }

    private static ProviderCommandRetryPolicy newRetryPolicy() {
        return new ProviderCommandRetryPolicy(10, java.time.Duration.ofSeconds(10),
                java.time.Duration.ofMinutes(15), 0.25d, new java.security.SecureRandom());
    }

    private static class NoopTransactionExecutor implements IamTransactionExecutor {
        @Override
        public <T> T executeInAuthenticationScope(UUID actorUserId, String issuer, String subject, Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInIamAuthScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInGlobalScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T executeInProvisioningScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action) {
            return action.get();
        }

        @Override
        public void refreshIamAuthScope(IamAuthScopeContext context) {
        }
    }

    private static class RecordingTransactionExecutor extends NoopTransactionExecutor {
        OperationCode lastProvisioningCode;

        @Override
        public <T> T executeInProvisioningScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action) {
            lastProvisioningCode = operationCode;
            return action.get();
        }
    }
}
