package org.mepcity.kursplatform.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.UserStatus;

class DeviceSessionServiceTests {
    private IamAuthRepository repository;
    private IamTransactionExecutor transactions;
    private ActiveSessionResolver credentials;
    private IamAuditWriter audits;
    private DeviceSessionService service;
    private UUID actor;

    @BeforeEach
    void setUp() {
        repository = mock(IamAuthRepository.class); transactions = mock(IamTransactionExecutor.class);
        credentials = mock(ActiveSessionResolver.class); audits = mock(IamAuditWriter.class); actor = UUID.randomUUID();
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(3)).get()).when(transactions)
                .executeInOrganizationScope(any(), any(), anyBoolean(), any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get()).when(transactions)
                .executeInGlobalScope(any(), any(), any());
        doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get()).when(transactions)
                .executeInIamAuthScope(any(), any(), any());
        service = new DeviceSessionService(repository, transactions, credentials, mock(SessionInfoService.class),
                new org.mepcity.kursplatform.iam.domain.TokenHasher() { public String hash(String v) { return "h:" + v; } public String hashWithPepper(String a, String b) { return hash(a); } },
                audits, settings(), Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC),
                mock(IamDeviceRateLimiter.class), mock(DeviceSessionSnapshotSerializer.class));
    }

    @Test
    void globalPlatformAdminSupportRechecksAdminBeforeEnablingFlagAndWritesDoubleAudit() {
        UUID org = UUID.randomUUID(), targetMembership = UUID.randomUUID(), targetUser = UUID.randomUUID();
        when(credentials.resolveCredential("token")).thenReturn(CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(actor)));
        when(repository.findActivePlatformAdministratorByUserId(actor)).thenReturn(Optional.of(new PlatformAdministrator(UUID.randomUUID(), actor, actor, Instant.now(), null)));
        when(repository.findOrganizationMembershipById(targetMembership)).thenReturn(Optional.of(membership(targetMembership, org, targetUser)));
        when(repository.findOrganizationMembershipByIdForUpdate(targetMembership)).thenReturn(Optional.of(membership(targetMembership, org, targetUser)));
        when(repository.findIdempotencyKey(any(), anyString(), any(), any())).thenReturn(Optional.empty());
        when(repository.insertIdempotencyKeyOrFindExisting(any())).thenReturn(Optional.empty());
        when(repository.findActiveRefreshTokenFamiliesByOrganizationMembershipId(targetMembership)).thenReturn(List.of(new RefreshTokenFamily(UUID.randomUUID(), targetUser, null, targetMembership, Instant.EPOCH, 1, null, Instant.EPOCH)));

        service.revokeOrganizationSessions("token", org, targetMembership, "key-12345678");

        verify(transactions).enablePlatformAdminSupportAccess();
        verify(audits, times(2)).write(any());
        verify(repository).advanceMembershipSessionBarrier(targetMembership);
    }

    @Test
    void organizationScopeCannotUseAnotherTenantAndNeverOpensSupportFlag() {
        UUID ownOrg = UUID.randomUUID();
        when(credentials.resolveCredential("token")).thenReturn(CredentialResolution.platformAccess(ActiveSession.organization(actor, ownOrg)));

        assertThatThrownBy(() -> service.revokeOrganizationSessions("token", UUID.randomUUID(), UUID.randomUUID(), "key-12345678"))
                .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("FORBIDDEN");
        verify(transactions, never()).enablePlatformAdminSupportAccess();
    }

    @Test
    void platformDeviceRevokeRejectsOrganizationScopedAdminBeforeDatabaseMutation() {
        when(credentials.resolveCredential("token")).thenReturn(CredentialResolution.platformAccess(ActiveSession.organization(actor, UUID.randomUUID())));
        assertThatThrownBy(() -> service.revokePlatformDevice("token", UUID.randomUUID(), UUID.randomUUID(), "key-12345678"))
                .isInstanceOf(IamException.class).extracting("errorCode").isEqualTo("FORBIDDEN");
        verify(repository, never()).revokeTrustedDeviceIfActive(any(), any());
    }

    @Test
    void organizationReplayReturnsStoredSnapshotWithoutReadingCurrentTargetOrConsumingRateLimit() {
        UUID org = UUID.randomUUID(), targetMembership = UUID.randomUUID();
        var stored = new DeviceSessionService.MembershipRevokeResult(
                targetMembership, org, 7, Instant.parse("2026-07-24T11:30:00Z"), 2);
        var snapshot = mock(DeviceSessionSnapshotSerializer.class);
        var limiter = mock(IamDeviceRateLimiter.class);
        service = new DeviceSessionService(repository, transactions, credentials, mock(SessionInfoService.class),
                new org.mepcity.kursplatform.iam.domain.TokenHasher() {
                    public String hash(String v) { return "h:" + v; }
                    public String hashWithPepper(String a, String b) { return hash(a); }
                }, audits, settings(), Clock.fixed(Instant.parse("2026-07-24T12:00:00Z"), ZoneOffset.UTC),
                limiter, snapshot);
        when(credentials.resolveCredential("token")).thenReturn(
                CredentialResolution.platformAccess(ActiveSession.globalPlatformAdmin(actor)));
        when(repository.findActivePlatformAdministratorByUserId(actor)).thenReturn(Optional.of(
                new PlatformAdministrator(UUID.randomUUID(), actor, actor, Instant.now(), null)));
        IdempotencyKey prior = completedKey(
                IdempotencyScope.ORGANIZATION, org, OperationCode.DEVICE_SESSION_REVOKE,
                "DS|" + org + "|" + actor + "|" + targetMembership, "{\"snapshot\":true}");
        when(repository.findIdempotencyKey(
                actor, "key-12345678", IdempotencyScope.ORGANIZATION,
                OperationCode.DEVICE_SESSION_REVOKE)).thenReturn(Optional.of(prior));
        when(snapshot.readMembershipRevoke("{\"snapshot\":true}")).thenReturn(stored);

        assertThat(service.revokeOrganizationSessions(
                "token", org, targetMembership, "key-12345678")).isEqualTo(stored);

        verify(repository, never()).findOrganizationMembershipByIdForUpdate(any());
        verify(limiter, never()).consume(any(), any(), any(), any());
        verify(audits, never()).write(any());
    }

    private IdempotencyKey completedKey(
            IdempotencyScope scope, UUID org, OperationCode operation, String fingerprint,
            String payload) {
        Instant now = Instant.parse("2026-07-24T12:00:00Z");
        return new IdempotencyKey(
                UUID.randomUUID(), scope, org, actor, "key-12345678", operation.name(),
                fingerprint, IdempotencyStatus.COMPLETED, UUID.randomUUID(), (short) 200,
                null, payload, null, null, null, null, now, now, now.plusSeconds(60),
                now.plusSeconds(60));
    }

    private static OrganizationMembership membership(UUID id, UUID org, UUID user) {
        return new OrganizationMembership(id, org, user, null, UserStatus.ACTIVE, 1, Instant.EPOCH, null, Instant.EPOCH);
    }
    private static IamServiceSettings settings() { return new IamServiceSettings() {
        public Duration accessTokenTtl(){return Duration.ofMinutes(1);} public Duration refreshTokenTtl(){return Duration.ofMinutes(1);} public Duration contextSelectionTokenTtl(){return Duration.ofMinutes(1);} public Duration activationEscrowTtl(){return Duration.ofMinutes(1);} public Duration idempotencyRetention(){return Duration.ofDays(1);} public boolean providerCommandWorkerEnabled(){return false;} public Duration providerCommandPollInterval(){return Duration.ofSeconds(1);} public int providerCommandBatchLimit(){return 1;} public Duration providerCommandLeaseTtl(){return Duration.ofSeconds(1);} public int providerCommandMaxAttempts(){return 1;} public Duration providerCommandBackoffBase(){return Duration.ofSeconds(1);} public Duration providerCommandBackoffMax(){return Duration.ofSeconds(1);} public double providerCommandJitter(){return 0;}
    }; }
}
