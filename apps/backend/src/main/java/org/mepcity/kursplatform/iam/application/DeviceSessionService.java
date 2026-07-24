package org.mepcity.kursplatform.iam.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mepcity.kursplatform.iam.application.contract.ActiveSession;
import org.mepcity.kursplatform.iam.application.contract.ActiveSessionResolver;
import org.mepcity.kursplatform.iam.application.contract.CredentialResolution;
import org.mepcity.kursplatform.iam.application.contract.CredentialAuthenticationException;
import org.mepcity.kursplatform.iam.domain.IamAuditEvent;
import org.mepcity.kursplatform.iam.domain.IamException;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.MembershipRole;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.TokenHasher;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.slf4j.MDC;

/** IAM-006 device and organization-session revocation boundary. All mutation/audit pairs share one transaction. */
public final class DeviceSessionService {
    private static final ObjectMapper SNAPSHOTS = new ObjectMapper();
    private final IamAuthRepository repository;
    private final IamTransactionExecutor transactions;
    private final ActiveSessionResolver credentials;
    private final SessionInfoService sessionInfo;
    private final TokenHasher tokenHasher;
    private final IamAuditWriter audits;
    private final IamServiceSettings settings;
    private final Clock clock;
    private final IamDeviceRateLimiter rateLimiter;

    public DeviceSessionService(IamAuthRepository repository, IamTransactionExecutor transactions,
                                ActiveSessionResolver credentials, SessionInfoService sessionInfo, TokenHasher tokenHasher,
                                IamAuditWriter audits, IamServiceSettings settings, Clock clock) {
        this(repository, transactions, credentials, sessionInfo, tokenHasher, audits, settings, clock, (actor, scope, context, operation) -> { });
    }

    public DeviceSessionService(IamAuthRepository repository, IamTransactionExecutor transactions,
                                ActiveSessionResolver credentials, SessionInfoService sessionInfo, TokenHasher tokenHasher,
                                IamAuditWriter audits, IamServiceSettings settings, Clock clock, IamDeviceRateLimiter rateLimiter) {
        this.repository = repository; this.transactions = transactions; this.credentials = credentials;
        this.sessionInfo = sessionInfo; this.tokenHasher = tokenHasher; this.audits = audits;
        this.settings = settings; this.clock = clock; this.rateLimiter = rateLimiter;
    }

    public DevicePage list(String bearer, String cursor, int limit) {
        if (limit < 1 || limit > 100) throw new IamException("INVALID_REQUEST", "limit 1 ile 100 arasında olmalıdır.");
        ActiveSession actor = requirePlatformAccess(bearer);
        UUID currentDeviceId = sessionInfo.resolveSession(bearer).device().id();
        DeviceCursorCodec.Cursor position = cursor == null || cursor.isBlank() ? null : new DeviceCursorCodec(tokenHasher).decode(actor.userId(), cursor);
        return transactions.executeInIamAuthScope(OperationCode.DEVICE_LIST,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(actor.userId()),
                () -> {
                    rateLimiter.consume(actor.userId(), org.mepcity.kursplatform.iam.domain.OperationScope.IAM_AUTH, actor.userId(), OperationCode.DEVICE_LIST);
                    List<TrustedDevice> rows = repository.findActiveTrustedDevicesPage(actor.userId(),
                            position == null ? null : position.trustedAt(), position == null ? null : position.id(), limit + 1);
                    boolean hasNext = rows.size() > limit;
                    List<TrustedDevice> page = hasNext ? rows.subList(0, limit) : rows;
                    String next = hasNext ? new DeviceCursorCodec(tokenHasher).encode(actor.userId(), page.getLast().trustedAt(), page.getLast().id()) : null;
                    return new DevicePage(page.stream().map(device -> new DeviceListItem(device, device.id().equals(currentDeviceId))).toList(), next, hasNext);
                });
    }

    public DeviceRevokeResult revokeOwnDevice(String bearer, UUID deviceId, String key) {
        ActiveSession actor = requirePlatformAccess(bearer);
        UUID currentDeviceId = sessionInfo.resolveSession(bearer).device().id();
        String accessHash = tokenHasher.hash(bearer);
        return transactions.executeInIamAuthScope(OperationCode.DEVICE_SELF_REVOKE,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(actor.userId()).withDevice(currentDeviceId).withTargetDevice(deviceId),
                () -> revokeDevice(actor.userId(), actor.userId(), deviceId, currentDeviceId, key,
                        "DSR|" + actor.userId() + "|" + currentDeviceId + "|" + deviceId + "|" + accessHash,
                        IdempotencyScope.IAM_AUTH, null, OperationCode.DEVICE_SELF_REVOKE, false));
    }

    public MembershipRevokeResult revokeOrganizationSessions(String bearer, UUID organizationId, UUID targetMembershipId, String key) {
        ActiveSession actor = requirePlatformAccess(bearer);
        boolean support = actor.scope() == ActiveSession.Scope.GLOBAL_PLATFORM_ADMIN;
        if (!support && !organizationId.equals(actor.organizationId())) throw forbidden();
        return transactions.executeInOrganizationScope(OperationCode.DEVICE_SESSION_REVOKE,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(actor.userId()).withMembership(targetMembershipId, organizationId), support,
                () -> {
                    if (support) {
                        // This SELECT is RLS-visible only for the exact GLOBAL_PLATFORM_ADMIN actor/op pair.
                        repository.findActivePlatformAdministratorByUserId(actor.userId()).orElseThrow(this::forbidden);
                        transactions.enablePlatformAdminSupportAccess();
                    } else {
                        authorizeOrganizationActor(actor.userId(), organizationId);
                    }
                    OrganizationMembership target = repository.findOrganizationMembershipByIdForUpdate(targetMembershipId)
                            .filter(m -> organizationId.equals(m.organizationId())).orElseThrow(this::notFound);
                    String fingerprint = "DS|" + organizationId + "|" + actor.userId() + "|" + targetMembershipId;
                    Optional<IdempotencyKey> replay = replay(actor.userId(), key, IdempotencyScope.ORGANIZATION, organizationId,
                            OperationCode.DEVICE_SESSION_REVOKE, fingerprint);
                    if (replay.isPresent()) return membershipSnapshot(replay.get());
                    rateLimiter.consume(actor.userId(), org.mepcity.kursplatform.iam.domain.OperationScope.ORGANIZATION, organizationId, OperationCode.DEVICE_SESSION_REVOKE);
                    List<RefreshTokenFamily> families = repository.findActiveRefreshTokenFamiliesByOrganizationMembershipId(targetMembershipId);
                    for (RefreshTokenFamily family : families) { repository.lockActiveRefreshTokensInFamily(family.id()); repository.revokeRefreshTokensInFamily(family.id(), clock.instant()); repository.revokeRefreshTokenFamily(family.id(), clock.instant()); }
                    if (!families.isEmpty()) {
                        repository.advanceMembershipSessionBarrier(targetMembershipId);
                        target = repository.findOrganizationMembershipById(targetMembershipId).orElseThrow(this::notFound);
                    }
                    MembershipRevokeResult result = MembershipRevokeResult.from(target, families.size());
                    audit("DEVICE_SESSION_REVOKED", IamAuditEvent.EventScope.ORGANIZATION, organizationId, actor.userId(), target.userId(),
                            Map.of("operationCode", OperationCode.DEVICE_SESSION_REVOKE.name(), "organizationMembershipId", targetMembershipId.toString(), "revokedRefreshTokenFamilyCount", families.size()));
                    if (support) audits.write(new IamAuditEvent(UUID.randomUUID(), organizationId, actor.userId(), MDC.get("requestId"),
                            "PLATFORM_ADMIN_ORG_ACCESS", IamAuditEvent.EventScope.ORGANIZATION, "ORGANIZATION", IamAuditEvent.EventKind.ACCESS,
                            organizationId, Map.of(), Map.of("operationCode", OperationCode.DEVICE_SESSION_REVOKE.name(), "outcome", "SUCCESS")));
                    complete(actor.userId(), key, IdempotencyScope.ORGANIZATION, organizationId, OperationCode.DEVICE_SESSION_REVOKE, fingerprint, targetMembershipId, membershipSnapshot(result));
                    return result;
                });
    }

    public DeviceRevokeResult revokePlatformDevice(String bearer, UUID targetUserId, UUID deviceId, String key) {
        ActiveSession actor = requirePlatformAccess(bearer);
        if (actor.scope() != ActiveSession.Scope.GLOBAL_PLATFORM_ADMIN) throw forbidden();
        return transactions.executeInGlobalScope(OperationCode.PLATFORM_DEVICE_REVOKE,
                IamTransactionExecutor.IamAuthScopeContext.actorOnly(actor.userId()).withTargetUser(targetUserId).withTargetDevice(deviceId), () -> {
                    repository.findActivePlatformAdministratorByUserId(actor.userId()).orElseThrow(this::forbidden);
                    return revokeDevice(actor.userId(), targetUserId, deviceId, null, key,
                            "PDR|" + actor.userId() + "|" + targetUserId + "|" + deviceId,
                            IdempotencyScope.GLOBAL, null, OperationCode.PLATFORM_DEVICE_REVOKE, true);
                });
    }

    private DeviceRevokeResult revokeDevice(UUID actor, UUID targetUser, UUID deviceId, UUID currentDevice, String key,
                                             String fingerprint, IdempotencyScope scope, UUID organizationId,
                                             OperationCode operation, boolean platform) {
        Optional<IdempotencyKey> replay = replay(actor, key, scope, organizationId, operation, fingerprint);
        if (replay.isPresent()) return deviceSnapshot(replay.get());
        rateLimiter.consume(actor, scope == IdempotencyScope.GLOBAL ? org.mepcity.kursplatform.iam.domain.OperationScope.GLOBAL : org.mepcity.kursplatform.iam.domain.OperationScope.IAM_AUTH,
                organizationId == null ? actor : organizationId, operation);
        TrustedDevice discovered = repository.findTrustedDeviceById(targetUser, deviceId).orElseThrow(this::notFound);
        repository.acquireDeviceAdvisoryLock(targetUser, discovered.deviceIdentifier());
        TrustedDevice locked = repository.findTrustedDeviceByIdForUpdate(targetUser, deviceId).orElseThrow(this::notFound);
        if (!locked.deviceIdentifier().equals(discovered.deviceIdentifier())) throw notFound();
        List<RefreshTokenFamily> families = locked.isActive()
                ? repository.findActiveRefreshTokenFamiliesByTrustedDeviceId(deviceId) : List.of();
        if (locked.isActive()) {
            repository.revokeTrustedDeviceIfActive(targetUser, deviceId);
            for (RefreshTokenFamily family : families) { repository.lockActiveRefreshTokensInFamily(family.id()); repository.revokeRefreshTokensInFamily(family.id(), clock.instant()); repository.revokeRefreshTokenFamily(family.id(), clock.instant()); }
        }
        DeviceRevokeResult result = new DeviceRevokeResult(locked, families.size(), currentDevice != null && currentDevice.equals(deviceId), false);
        audit(platform ? "PLATFORM_DEVICE_REVOKED" : "DEVICE_SELF_REVOKED", IamAuditEvent.EventScope.GLOBAL, null, actor, targetUser,
                Map.of("operationCode", operation.name(), "trustedDeviceId", deviceId.toString(), "revokedRefreshTokenFamilyCount", families.size()));
        complete(actor, key, scope, organizationId, operation, fingerprint, deviceId, deviceSnapshot(result));
        return result;
    }

    private void authorizeOrganizationActor(UUID actor, UUID organization) {
        OrganizationMembership membership = repository.findActiveOrganizationMembershipsByUserId(actor).stream()
                .filter(m -> organization.equals(m.organizationId())).findFirst().orElseThrow(this::forbidden);
        boolean admin = repository.findActiveRolesByMembershipId(membership.id()).stream().anyMatch(r -> r.role() == MembershipRole.ORG_ADMIN);
        boolean delegatedTeacher = repository.findActiveRolesByMembershipId(membership.id()).stream().anyMatch(r -> r.role() == MembershipRole.TEACHER)
                && repository.findActivePermissionsByMembershipId(membership.id()).stream().anyMatch(p -> "DEVICE_SESSION_REVOKE".equals(p.permissionCode()));
        if (!admin && !delegatedTeacher) throw forbidden();
    }

    private Optional<IdempotencyKey> replay(UUID actor, String key, IdempotencyScope scope, UUID org, OperationCode op, String fingerprint) {
        repository.acquireIdempotencyAdvisoryLock(actor, key);
        Optional<IdempotencyKey> prior = repository.findIdempotencyKey(actor, key, scope, op);
        if (prior.isEmpty()) return Optional.empty();
        if (!fingerprint.equals(prior.get().requestFingerprint())) throw new IamException("IDEMPOTENCY_KEY_REUSED", "Anahtar farklı istek için kullanılmış.");
        if (!prior.get().isCompleted() || prior.get().resultPayload() == null) throw new IamException("IDEMPOTENCY_KEY_REUSED", "İşlem anahtarı tamamlanmamış.");
        return prior;
    }

    private void complete(UUID actor, String key, IdempotencyScope scope, UUID org, OperationCode op, String fingerprint, UUID result, String payload) {
        Instant now = clock.instant();
        IdempotencyKey record = new IdempotencyKey(UUID.randomUUID(), scope, org, actor, key, op.name(), fingerprint,
                IdempotencyStatus.COMPLETED, result, (short) 200, null, payload, "iam-006:" + result,
                null, null, null, now, now, null, now.plus(settings.idempotencyRetention()));
        if (repository.insertIdempotencyKeyOrFindExisting(record).isPresent()) throw new IamException("IDEMPOTENCY_KEY_REUSED", "Anahtar çakıştı.");
    }

    private String deviceSnapshot(DeviceRevokeResult value) {
        TrustedDevice d = value.device();
        try { return SNAPSHOTS.writeValueAsString(Map.ofEntries(Map.entry("version", 1), Map.entry("kind", "device-revoke"), Map.entry("id", d.id().toString()), Map.entry("userId", d.userId().toString()), Map.entry("deviceIdentifier", d.deviceIdentifier().toString()), Map.entry("deviceName", d.deviceName()), Map.entry("platform", d.platform().name()), Map.entry("trustedAt", d.trustedAt().toString()), Map.entry("lastSeenAt", d.lastSeenAt().toString()), Map.entry("revokedAt", d.revokedAt() == null ? "" : d.revokedAt().toString()), Map.entry("familyCount", value.revokedRefreshTokenFamilyCount()), Map.entry("currentDevice", value.currentDevice()))); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Idempotency sonucu yazılamadı.", e); }
    }

    private String membershipSnapshot(MembershipRevokeResult value) {
        try { return SNAPSHOTS.writeValueAsString(Map.of("version", 1, "kind", "membership-revoke", "membershipId", value.organizationMembershipId().toString(), "organizationId", value.organizationId().toString(), "sessionGeneration", value.sessionGeneration(), "reauthenticationRequiredAfter", value.reauthenticationRequiredAfter().toString(), "familyCount", value.revokedRefreshTokenFamilyCount())); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Idempotency sonucu yazılamadı.", e); }
    }

    private DeviceRevokeResult deviceSnapshot(IdempotencyKey replay) {
        try {
            JsonNode n = SNAPSHOTS.readTree(replay.resultPayload());
            if (n.path("version").asInt() != 1 || !"device-revoke".equals(n.path("kind").asText())) throw new IllegalArgumentException();
            TrustedDevice device = new TrustedDevice(UUID.fromString(n.path("id").asText()), UUID.fromString(n.path("userId").asText()), UUID.fromString(n.path("deviceIdentifier").asText()), n.path("deviceName").asText(), org.mepcity.kursplatform.iam.domain.DevicePlatform.valueOf(n.path("platform").asText()), Instant.parse(n.path("trustedAt").asText()), Instant.parse(n.path("lastSeenAt").asText()), n.path("revokedAt").asText().isEmpty() ? null : Instant.parse(n.path("revokedAt").asText()));
            return new DeviceRevokeResult(device, n.path("familyCount").asInt(), n.path("currentDevice").asBoolean(), true);
        }
        catch (Exception e) { throw new IamException("IDEMPOTENCY_KEY_REUSED", "İşlem sonucu okunamadı."); }
    }

    private MembershipRevokeResult membershipSnapshot(IdempotencyKey replay) {
        try {
            JsonNode n = SNAPSHOTS.readTree(replay.resultPayload());
            if (n.path("version").asInt() != 1 || !"membership-revoke".equals(n.path("kind").asText())) throw new IllegalArgumentException();
            return new MembershipRevokeResult(UUID.fromString(n.path("membershipId").asText()), UUID.fromString(n.path("organizationId").asText()), n.path("sessionGeneration").asInt(), Instant.parse(n.path("reauthenticationRequiredAfter").asText()), n.path("familyCount").asInt());
        } catch (Exception e) { throw new IamException("IDEMPOTENCY_KEY_REUSED", "İşlem sonucu okunamadı."); }
    }

    private ActiveSession requirePlatformAccess(String bearer) {
        try {
            CredentialResolution resolution = credentials.resolveCredential(bearer);
            if (resolution.kind() != CredentialResolution.Kind.PLATFORM_ACCESS) throw new IamException("UNAUTHENTICATED", "Platform erişim tokenı gerekli.");
            return resolution.activeSession();
        } catch (CredentialAuthenticationException e) { throw new IamException(e.code(), "Oturum doğrulanamadı."); }
    }
    private void audit(String action, IamAuditEvent.EventScope scope, UUID org, UUID actor, UUID target, Map<String, Object> metadata) {
        audits.write(new IamAuditEvent(UUID.randomUUID(), org, actor, MDC.get("requestId"), action, scope, "USER", IamAuditEvent.EventKind.SECURITY, target, Map.of(), metadata));
    }
    private IamException forbidden() { return new IamException("FORBIDDEN", "Bu işlem için yetkiniz yok."); }
    private IamException notFound() { return new IamException("RESOURCE_NOT_FOUND", "Kaynak bulunamadı."); }

    public record DeviceRevokeResult(TrustedDevice device, int revokedRefreshTokenFamilyCount, boolean currentDevice, boolean replayed) { }
    public record MembershipRevokeResult(UUID organizationMembershipId, UUID organizationId, int sessionGeneration,
                                         Instant reauthenticationRequiredAfter, int revokedRefreshTokenFamilyCount) {
        static MembershipRevokeResult from(OrganizationMembership membership, int count) {
            return new MembershipRevokeResult(membership.id(), membership.organizationId(), membership.sessionGeneration(),
                    membership.reauthenticationRequiredAfter(), count);
        }
    }
    public record DeviceListItem(TrustedDevice device, boolean currentDevice) { }
    public record DevicePage(List<DeviceListItem> items, String nextCursor, boolean hasNextPage) { }
}
