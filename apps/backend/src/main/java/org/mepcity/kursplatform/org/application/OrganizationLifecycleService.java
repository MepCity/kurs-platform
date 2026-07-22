package org.mepcity.kursplatform.org.application;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.domain.Organization;
import org.mepcity.kursplatform.org.domain.OrganizationRepository;
import org.mepcity.kursplatform.org.domain.OrganizationStatus;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns the ORG lifecycle transaction, server-set RLS context, contractual lock order, audit write
 * and idempotency terminal result.
 *
 * <p>This class is intentionally <strong>not</strong> a Spring bean. HTTP wiring, request parsing
 * and the choice of {@code clientMutationId}/{@code requestFingerprint} sources belong to the
 * ORG-004/ORG-005 controllers. The ORG-003 task delivers the transactional core so audit and
 * idempotency participate atomically; tests construct the service directly.
 *
 * <p>Binding transaction order: authorization (platform admin actor-only SELECT) → idempotency
 * lookup/claim + fingerprint compare → state/rowVersion + mutation → audit → idempotency
 * {@code COMPLETED} → commit. On audit/DB failure the transaction rolls back wholesale; the
 * implementation never attempts a {@code FAILED} terminal write on the same failing transaction,
 * so the claim and any partial audit row vanish with the rollback.
 */
public final class OrganizationLifecycleService {

    private static final String MEMBERSHIPS_LOCK_SQL =
            "SELECT id FROM organization_memberships WHERE organization_id = ? FOR UPDATE";
    private static final String FAMILIES_LOCK_SQL =
            "SELECT id FROM refresh_token_families WHERE organization_membership_id = ANY (?) "
                    + "AND revoked_at IS NULL FOR UPDATE";
    private static final String TOKENS_LOCK_SQL =
            "SELECT id FROM refresh_tokens WHERE family_id = ANY (?) AND revoked_at IS NULL FOR UPDATE";
    private static final String MEMBERSHIP_BARRIER_SQL =
            "UPDATE organization_memberships SET session_generation = session_generation + 1, "
                    + "reauthentication_required_after = transaction_timestamp() WHERE organization_id = ?";
    private static final String REVOKE_FAMILIES_SQL =
            "UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ANY (?)";
    private static final String REVOKE_TOKENS_SQL =
            "UPDATE refresh_tokens SET revoked_at = transaction_timestamp() WHERE id = ANY (?)";
    private static final String ADMIN_VERIFY_SQL =
            "SELECT user_id FROM platform_administrators WHERE user_id = ? AND revoked_at IS NULL";
    private static final String ORG_ACTOR_IDENTITY_ACCESS_SQL =
            "SELECT org_actor_has_identity_update_access(?, ?)";

    private final OrganizationRepository organizations;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final AuditWriter auditWriter;
    private final IdempotencyRecorder idempotencyRecorder;
    private final OrganizationResultSerializer resultSerializer;
    private final OrganizationCreateRateLimiter createRateLimiter;

    public OrganizationLifecycleService(
            OrganizationRepository organizations,
            DataSource dataSource,
            PlatformTransactionManager transactionManager,
            AuditWriter auditWriter,
            IdempotencyRecorder idempotencyRecorder,
            OrganizationResultSerializer resultSerializer,
            OrganizationCreateRateLimiter createRateLimiter) {
        this.organizations = organizations;
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(transactionManager);
        this.auditWriter = auditWriter;
        this.idempotencyRecorder = idempotencyRecorder;
        this.resultSerializer = resultSerializer;
        this.createRateLimiter = createRateLimiter;
    }

    public LifecycleResult suspend(LifecycleRequest request) {
        return changeStatus(request, OrganizationStatus.ACTIVE, OrganizationStatus.SUSPENDED, "ORG_SUSPEND", true);
    }

    public LifecycleResult archive(LifecycleRequest request) {
        return changeStatus(request, null, OrganizationStatus.ARCHIVED, "ORG_ARCHIVE", true);
    }

    /** Does not restore membership generations or revoked session material. */
    public LifecycleResult activate(LifecycleRequest request) {
        return changeStatus(request, OrganizationStatus.SUSPENDED, OrganizationStatus.ACTIVE, "ORG_ACTIVATE", false);
    }

    /**
     * Creates an ACTIVE organization in the GLOBAL platform-administrator scope.  The active
     * administrator check deliberately happens before idempotency lookup: an administrator whose
     * grant was revoked must not be able to replay an old successful create request.
     */
    public LifecycleResult create(LifecycleRequest request, String name, String shortName, String defaultTimezone) {
        return transactions.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);
            setGlobalCreateRuntimeContext(connection, request.actorId());
            if (!activeAdminExists(connection, request.actorId())) {
                throw new ForbiddenException();
            }

            IdempotencyOutcome outcome = idempotencyRecorder.resolveOrClaim(
                    "GLOBAL", null, request.actorId(), request.clientMutationId(), "ORG_CREATE",
                    request.requestFingerprint(), request.leaseOwner(), request.leaseExpiresAt(),
                    request.keyRetentionExpiresAt());
            if (outcome instanceof IdempotencyOutcome.Replay replay) {
                return new LifecycleResult.Replayed(replay.result());
            }
            if (outcome instanceof IdempotencyOutcome.Clash) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome instanceof IdempotencyOutcome.Pending) {
                throw new IdempotencyPendingException();
            }
            IdempotencyOutcome.Claimed claim = (IdempotencyOutcome.Claimed) outcome;
            // Replays and key-reuse conflicts returned above must remain available even when the
            // create quota is exhausted. Only a freshly claimed mutation consumes quota.
            createRateLimiter.check(request.actorId());

            Organization created = organizations.create(new Organization(
                    request.organizationId(), name, shortName, null, null, OrganizationStatus.ACTIVE,
                    defaultTimezone, null, null, 1, request.actorId(), request.actorId()));
            AuditEvent event = new AuditEvent.Factory(request.requestId()).orgCreated(
                    UUID.randomUUID(), created.id(), request.actorId(), created.id(), "ORG_CREATE", created.rowVersion());
            auditWriter.write(event);
            idempotencyRecorder.markCompleted(claim.claimId(), claim.leaseOwner(), claim.leaseGeneration(),
                    created.id(), (short) 201, resultSerializer.serialize(created), request.keyRetentionExpiresAt());
            return new LifecycleResult.Committed(created);
        });
    }

    /**
     * {@code ORG_UPDATE_IDENTITY}: either an org actor (active membership + un-revoked {@code
     * ORG_ADMIN} role, or un-revoked {@code TEACHER} role with a delegated, un-revoked {@code
     * BRAND_MANAGE} permission) or a platform admin (targeted support access, same as {@link
     * #changeStatus}). Authorization is verified in this same transaction, before idempotency
     * lookup/claim -- a request that fails authorization never touches the idempotency key or
     * writes an audit row. {@code SUSPENDED} orgs reject the org-actor path; {@code ARCHIVED}
     * rejects both (terminal).
     */
    public LifecycleResult updateIdentity(LifecycleRequest request, String newName, String newShortName, String newDefaultTimezone) {
        return transactions.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);

            // 1. Authorization: server-set RLS context, then branch on platform-admin vs org-actor.
            setOrgRuntimeContext(connection, request.actorId(), request.organizationId(), "ORG_UPDATE_IDENTITY");
            boolean platformAdmin = activeAdminExists(connection, request.actorId());
            if (platformAdmin) {
                set(connection, "app.iam_platform_admin_support_access", "true");
            } else {
                verifyOrgActorIdentityUpdateAccess(connection, request.actorId(), request.organizationId());
            }

            // 2. Idempotency lookup/claim + fingerprint compare -- only after authorization passes.
            IdempotencyOutcome outcome = idempotencyRecorder.resolveOrClaim(
                    "ORGANIZATION", request.organizationId(), request.actorId(),
                    request.clientMutationId(), "ORG_UPDATE_IDENTITY", request.requestFingerprint(),
                    request.leaseOwner(), request.leaseExpiresAt(), request.keyRetentionExpiresAt());
            if (outcome instanceof IdempotencyOutcome.Replay replay) {
                return new LifecycleResult.Replayed(replay.result());
            }
            if (outcome instanceof IdempotencyOutcome.Clash) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome instanceof IdempotencyOutcome.Pending) {
                throw new IdempotencyPendingException();
            }
            IdempotencyOutcome.Claimed claim = (IdempotencyOutcome.Claimed) outcome;

            // 3. State/rowVersion + status: ARCHIVED is terminal for both paths; SUSPENDED rejects
            // the org-actor path (matches ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md §2.7).
            Organization locked = organizations.findByIdForUpdate(request.organizationId())
                    .orElseThrow(() -> new OrganizationNotVisibleException());
            if (locked.rowVersion() != request.rowVersion()
                    || locked.status() == OrganizationStatus.ARCHIVED
                    || (!platformAdmin && locked.status() != OrganizationStatus.ACTIVE)) {
                throw new OrganizationConflictException();
            }

            // 4. Mutation: identity fields only; primary/secondaryColor and logo are ORG-002 fields,
            // untouched here.
            Organization desired = new Organization(locked.id(), newName, newShortName, locked.primaryColor(),
                    locked.secondaryColor(), locked.status(), newDefaultTimezone, locked.createdAt(), locked.updatedAt(),
                    request.rowVersion(), locked.createdByUserId(), request.actorId());
            Organization changed = organizations.updateIdentity(desired).orElseThrow(() -> new OrganizationConflictException());

            // 5. Audit: ORG_SETTING_CHANGED (only actually-changed identity fields) + mandatory
            // PLATFORM_ADMIN_ORG_ACCESS when platform-admin-targeted.
            writeIdentityAudit(request, locked, changed);
            if (platformAdmin) {
                writePlatformAdminAccessAudit(request, "ORG_UPDATE_IDENTITY", changed.id());
            }
            completeIdempotency(claim, changed, request.keyRetentionExpiresAt());
            return new LifecycleResult.Committed(changed);
        });
    }

    private LifecycleResult changeStatus(
            LifecycleRequest request,
            OrganizationStatus expected,
            OrganizationStatus target,
            String operation,
            boolean revokeSessions) {
        return transactions.execute(status -> {
            Connection connection = DataSourceUtils.getConnection(dataSource);

            // 1. Authorization: server-set RLS context + active platform admin actor-only SELECT.
            setPlatformAdminContext(connection, request.actorId(), request.organizationId(), operation);

            // 2. Idempotency lookup/claim + fingerprint compare.
            IdempotencyOutcome outcome = idempotencyRecorder.resolveOrClaim(
                    "ORGANIZATION", request.organizationId(), request.actorId(),
                    request.clientMutationId(), operation, request.requestFingerprint(),
                    request.leaseOwner(), request.leaseExpiresAt(), request.keyRetentionExpiresAt());
            if (outcome instanceof IdempotencyOutcome.Replay replay) {
                return new LifecycleResult.Replayed(replay.result());
            }
            if (outcome instanceof IdempotencyOutcome.Clash) {
                throw new IdempotencyKeyReusedException();
            }
            if (outcome instanceof IdempotencyOutcome.Pending) {
                throw new IdempotencyPendingException();
            }
            IdempotencyOutcome.Claimed claim = (IdempotencyOutcome.Claimed) outcome;

            // 3. State/rowVersion: lock the organization and revalidate status/version after the lock.
            Organization locked = organizations.findByIdForUpdate(request.organizationId())
                    .orElseThrow(() -> new OrganizationNotVisibleException());
            if (locked.rowVersion() != request.rowVersion()
                    || (expected != null && locked.status() != expected)
                    || (target == OrganizationStatus.ARCHIVED && locked.status() == OrganizationStatus.ARCHIVED)) {
                throw new OrganizationConflictException();
            }

            // 4. Contractual lock order (organizations above) -> memberships -> families -> tokens.
            int revokedMembershipCount = 0;
            int revokedFamilyCount = 0;
            int revokedTokenCount = 0;
            if (revokeSessions) {
                List<UUID> memberships = lockIds(connection, MEMBERSHIPS_LOCK_SQL, request.organizationId());
                revokedMembershipCount = memberships.size();
                List<UUID> families = memberships.isEmpty() ? List.of() : lockArray(connection, FAMILIES_LOCK_SQL, memberships);
                revokedFamilyCount = families.size();
                List<UUID> tokens = families.isEmpty() ? List.of() : lockArray(connection, TOKENS_LOCK_SQL, families);
                revokedTokenCount = tokens.size();

                Organization changed = organizations.transitionStatus(
                        request.organizationId(), locked.status(), target, request.rowVersion(), request.actorId())
                        .orElseThrow(() -> new OrganizationConflictException());
                updateMembershipBarrier(connection, request.organizationId());
                revoke(connection, REVOKE_FAMILIES_SQL, families);
                revoke(connection, REVOKE_TOKENS_SQL, tokens);
                writeStatusAudit(request, operation, locked, changed, revokedMembershipCount, revokedFamilyCount, revokedTokenCount);
                completeIdempotency(claim, changed, request.keyRetentionExpiresAt());
                return new LifecycleResult.Committed(changed);
            }

            // ACTIVATE path: no membership/family/token locks; rowVersion bump invalidates stale SUSPEND.
            Organization changed = organizations.transitionStatus(
                    request.organizationId(), locked.status(), target, request.rowVersion(), request.actorId())
                    .orElseThrow(() -> new OrganizationConflictException());
            writeStatusAudit(request, operation, locked, changed, 0, 0, 0);
            completeIdempotency(claim, changed, request.keyRetentionExpiresAt());
            return new LifecycleResult.Committed(changed);
        });
    }

    private void setPlatformAdminContext(Connection connection, UUID actorId, UUID organizationId, String operation) {
        setOrgRuntimeContext(connection, actorId, organizationId, operation);
        // Active platform admin actor-only SELECT before the support flag is set.
        if (!activeAdminExists(connection, actorId)) {
            throw new ForbiddenException();
        }
        set(connection, "app.iam_platform_admin_support_access", "true");
    }

    private static void setOrgRuntimeContext(Connection connection, UUID actorId, UUID organizationId, String operation) {
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL ROLE org_runtime");
            set(connection, "app.iam_operation_scope", "ORGANIZATION");
            set(connection, "app.iam_actor_user_id", actorId.toString());
            set(connection, "app.organization_id", organizationId.toString());
            set(connection, "app.iam_operation_code", operation);
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("ORG RLS bağlamı kurulamadı", exception);
        }
    }

    private static void setGlobalCreateRuntimeContext(Connection connection, UUID actorId) {
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL ROLE org_runtime");
            set(connection, "app.iam_operation_scope", "GLOBAL");
            set(connection, "app.iam_actor_user_id", actorId.toString());
            set(connection, "app.iam_operation_code", "ORG_CREATE");
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("ORG GLOBAL RLS bağlamı kurulamadı", exception);
        }
    }

    private static boolean activeAdminExists(Connection connection, UUID actorId) {
        try (PreparedStatement statement = connection.prepareStatement(ADMIN_VERIFY_SQL)) {
            statement.setObject(1, actorId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Aktif platform yöneticisi doğrulanamadı", exception);
        }
    }

    /**
     * Live org-actor authorization for {@code ORG_UPDATE_IDENTITY}, evaluated in this transaction
     * before idempotency: active membership + un-revoked {@code ORG_ADMIN} role (no permission-table
     * lookup), or un-revoked {@code TEACHER} role with a delegated, un-revoked {@code BRAND_MANAGE}
     * permission. Mirrors {@code org_actor_has_identity_update_access} in {@code V3__...} exactly;
     * this Java-side check and the DB RLS policies that also call the same SQL function are two
     * independent layers of the same rule, not a single point of enforcement.
     */
    private static void verifyOrgActorIdentityUpdateAccess(Connection connection, UUID actorId, UUID organizationId) {
        try (PreparedStatement statement = connection.prepareStatement(ORG_ACTOR_IDENTITY_ACCESS_SQL)) {
            statement.setObject(1, organizationId);
            statement.setObject(2, actorId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                if (!result.getBoolean(1)) {
                    throw new ForbiddenException();
                }
            }
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Kurum aktörü yetkisi doğrulanamadı", exception);
        }
    }

    private void writeIdentityAudit(LifecycleRequest request, Organization before, Organization after) {
        var change = new AuditEvent.SettingChange();
        if (!java.util.Objects.equals(before.name(), after.name())) {
            change.name(before.name(), after.name());
        }
        if (!java.util.Objects.equals(before.shortName(), after.shortName())) {
            change.shortName(before.shortName(), after.shortName());
        }
        if (!java.util.Objects.equals(before.defaultTimezone(), after.defaultTimezone())) {
            change.defaultTimezone(before.defaultTimezone(), after.defaultTimezone());
        }
        var factory = new AuditEvent.Factory(request.requestId());
        AuditEvent event = factory.orgSettingChanged(UUID.randomUUID(), after.id(), request.actorId(), after.id(),
                "ORG_UPDATE_IDENTITY", change, after.rowVersion());
        auditWriter.write(event);
    }

    private void writePlatformAdminAccessAudit(LifecycleRequest request, String operationCode, UUID targetOrganizationId) {
        var factory = new AuditEvent.Factory(request.requestId());
        AuditEvent event = factory.platformAdminOrgAccess(UUID.randomUUID(), targetOrganizationId, request.actorId(),
                targetOrganizationId, operationCode, "ALLOWED", null);
        auditWriter.write(event);
    }

    private void writeStatusAudit(
            LifecycleRequest request,
            String operation,
            Organization before,
            Organization after,
            int revokedMembershipCount,
            int revokedFamilyCount,
            int revokedTokenCount) {
        var factory = new AuditEvent.Factory(request.requestId());
        AuditEvent event = factory.orgStatusChanged(
                UUID.randomUUID(), after.id(), request.actorId(), after.id(), operation,
                before.status().name(), after.status().name(), before.rowVersion(), after.rowVersion(),
                revokedMembershipCount, revokedFamilyCount, revokedTokenCount);
        auditWriter.write(event);
    }

    private void completeIdempotency(IdempotencyOutcome.Claimed claim, Organization changed, Instant keyRetentionExpiresAt) {
        idempotencyRecorder.markCompleted(claim.claimId(), claim.leaseOwner(), claim.leaseGeneration(),
                changed.id(), (short) 200, keyRetentionExpiresAt);
    }

    private static void set(Connection connection, String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config(?, ?, true)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.execute();
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("RLS bağlam değeri kurulamadı: " + key, exception);
        }
    }

    private static List<UUID> lockIds(Connection connection, String sql, UUID organizationId) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, organizationId);
            return readIds(statement.executeQuery());
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Üyelik kilidi alınamadı", exception);
        }
    }

    private static List<UUID> lockArray(Connection connection, String sql, List<UUID> ids) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("uuid", ids.toArray()));
            return readIds(statement.executeQuery());
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Oturum kilidi alınamadı", exception);
        }
    }

    private static List<UUID> readIds(ResultSet result) throws SQLException {
        List<UUID> ids = new ArrayList<>();
        while (result.next()) {
            ids.add(result.getObject(1, UUID.class));
        }
        return ids;
    }

    private static void updateMembershipBarrier(Connection connection, UUID organizationId) {
        try (PreparedStatement statement = connection.prepareStatement(MEMBERSHIP_BARRIER_SQL)) {
            statement.setObject(1, organizationId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Üyelik bariyeri yazılamadı", exception);
        }
    }

    private static void revoke(Connection connection, String sql, List<UUID> ids) {
        if (ids.isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setArray(1, connection.createArrayOf("uuid", ids.toArray()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new OrganizationPersistenceStateException("Oturum iptali yazılamadı", exception);
        }
    }
}
