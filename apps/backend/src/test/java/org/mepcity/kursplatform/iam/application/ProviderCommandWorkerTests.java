package org.mepcity.kursplatform.iam.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ProviderCommandWorker#processOne} always routes both success and failure outcomes
 * through {@link ProviderCommandService#reportProviderCallOutcome} — never {@code completeCommand}
 * directly — since that is the one method that knows how to classify retryable vs terminal vs
 * exhausted. Unsupported command types ({@code isSupportedInWorker() == false}) are rejected
 * BEFORE any identity resolution or adapter call, defensively, in case one ever reaches a claim
 * despite the scheduler/RLS/createCommand allow-list already excluding them.
 */
class ProviderCommandWorkerTests {

    private ProviderCommandService providerCommandService;
    private IamAuthRepository repository;
    private ProviderCommandWorkerAdapter adapter;
    private ProviderCommandWorker worker;

    private final UUID commandId = UUID.randomUUID();
    private final UUID targetIdentityId = UUID.randomUUID();
    private final UUID targetUserId = UUID.randomUUID();
    private final String workerId = "worker-1";

    @BeforeEach
    void setUp() {
        providerCommandService = mock(ProviderCommandService.class);
        repository = mock(IamAuthRepository.class);
        adapter = mock(ProviderCommandWorkerAdapter.class);
        worker = new ProviderCommandWorker(providerCommandService, repository, new NoopTransactionExecutor(), adapter);
    }

    private ProviderCommandResult claimedResult(ProviderCommandType type, UUID targetIdentity, UUID targetUser) {
        return new ProviderCommandResult(
                commandId, "idem-1", "cognito", type, targetUser, targetIdentity, null,
                ProviderCommandStatus.CLAIMED, 1, null, Instant.now().plusSeconds(300), 1, workerId,
                Instant.now(), null, null);
    }

    @Test
    void processOneResolvesIdentityCallsAdapterAndReportsSuccessOutcome() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.USER_DISABLE, targetIdentityId, null));
        when(repository.findClaimedCommandTargetIdentity(targetIdentityId))
                .thenReturn(Optional.of(new UserIdentity(targetIdentityId, targetUserId, "iss-1", "sub-1", Instant.now(), null)));
        when(adapter.execute(ProviderCommandType.USER_DISABLE, "sub-1", "iss-1"))
                .thenReturn(ProviderCommandOutcome.ofSuccess());
        when(providerCommandService.reportProviderCallOutcome(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(claimedResult(ProviderCommandType.USER_DISABLE, targetIdentityId, null));

        worker.processOne(commandId, workerId);

        verify(providerCommandService).reportProviderCallOutcome(
                eq(commandId), eq(ProviderCommandType.USER_DISABLE), eq(workerId), eq(1L), eq(1),
                eq(ProviderCommandOutcome.ofSuccess()));
    }

    @Test
    void processOneReportsFailureOutcomeFromAdapter() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.USER_LOGOUT, targetIdentityId, null));
        when(repository.findClaimedCommandTargetIdentity(targetIdentityId))
                .thenReturn(Optional.of(new UserIdentity(targetIdentityId, targetUserId, "iss-1", "sub-1", Instant.now(), null)));
        when(adapter.execute(ProviderCommandType.USER_LOGOUT, "sub-1", "iss-1"))
                .thenReturn(ProviderCommandOutcome.ofFailure("PROVIDER_ERROR_5XX"));
        when(providerCommandService.reportProviderCallOutcome(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(claimedResult(ProviderCommandType.USER_LOGOUT, targetIdentityId, null));

        worker.processOne(commandId, workerId);

        verify(providerCommandService).reportProviderCallOutcome(
                eq(commandId), eq(ProviderCommandType.USER_LOGOUT), eq(workerId), eq(1L), eq(1),
                eq(ProviderCommandOutcome.ofFailure("PROVIDER_ERROR_5XX")));
    }

    @Test
    void processOneRejectsTeacherAccountCreateWithoutResolvingIdentityOrCallingAdapter() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.TEACHER_ACCOUNT_CREATE, null, targetUserId));
        when(providerCommandService.reportProviderCallOutcome(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(claimedResult(ProviderCommandType.TEACHER_ACCOUNT_CREATE, null, targetUserId));

        worker.processOne(commandId, workerId);

        verify(repository, never()).findClaimedCommandTargetIdentity(any());
        verify(adapter, never()).execute(any(), any(), any());
        verify(providerCommandService).reportProviderCallOutcome(
                eq(commandId), eq(ProviderCommandType.TEACHER_ACCOUNT_CREATE), eq(workerId), eq(1L), eq(1),
                eq(ProviderCommandOutcome.ofFailure("BUSINESS_RULE_VIOLATION")));
    }

    @Test
    void processOneRejectsPasswordResetWithoutResolvingIdentityOrCallingAdapter() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.PASSWORD_RESET, targetIdentityId, null));
        when(providerCommandService.reportProviderCallOutcome(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(claimedResult(ProviderCommandType.PASSWORD_RESET, targetIdentityId, null));

        worker.processOne(commandId, workerId);

        verify(repository, never()).findClaimedCommandTargetIdentity(any());
        verify(adapter, never()).execute(any(), any(), any());
        verify(providerCommandService).reportProviderCallOutcome(
                eq(commandId), eq(ProviderCommandType.PASSWORD_RESET), eq(workerId), eq(1L), eq(1),
                eq(ProviderCommandOutcome.ofFailure("BUSINESS_RULE_VIOLATION")));
    }

    @Test
    void processOneMapsAdapterUnsupportedOperationExceptionToBusinessRuleViolationInsteadOfPropagating() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.USER_DISABLE, targetIdentityId, null));
        when(repository.findClaimedCommandTargetIdentity(targetIdentityId))
                .thenReturn(Optional.of(new UserIdentity(targetIdentityId, targetUserId, "iss-1", "sub-1", Instant.now(), null)));
        when(adapter.execute(ProviderCommandType.USER_DISABLE, "sub-1", "iss-1"))
                .thenThrow(new UnsupportedOperationException("adapter does not implement this type"));
        when(providerCommandService.reportProviderCallOutcome(any(), any(), any(), anyLong(), anyInt(), any()))
                .thenReturn(claimedResult(ProviderCommandType.USER_DISABLE, targetIdentityId, null));

        ProviderCommandResult result = worker.processOne(commandId, workerId);

        assertThat(result).isNotNull();
        verify(providerCommandService).reportProviderCallOutcome(
                eq(commandId), eq(ProviderCommandType.USER_DISABLE), eq(workerId), eq(1L), eq(1),
                eq(ProviderCommandOutcome.ofFailure("BUSINESS_RULE_VIOLATION")));
    }

    @Test
    void processOneFailsClosedWhenTargetIdentityNoLongerVisible() {
        when(providerCommandService.claimCommand(commandId, workerId))
                .thenReturn(claimedResult(ProviderCommandType.USER_DISABLE, targetIdentityId, null));
        when(repository.findClaimedCommandTargetIdentity(targetIdentityId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> worker.processOne(commandId, workerId))
                .isInstanceOf(IamException.class);

        verify(providerCommandService, never()).reportProviderCallOutcome(
                any(), any(), any(), anyLong(), anyInt(), any());
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
}
