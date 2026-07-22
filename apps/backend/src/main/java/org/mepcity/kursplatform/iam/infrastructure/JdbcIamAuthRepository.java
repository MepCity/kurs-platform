package org.mepcity.kursplatform.iam.infrastructure;

import org.mepcity.kursplatform.iam.application.IamAuthRepository;
import org.mepcity.kursplatform.iam.domain.AuthReplayEscrow;
import org.mepcity.kursplatform.iam.domain.AuthSession;
import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.DevicePlatform;
import org.mepcity.kursplatform.iam.domain.EscrowStatus;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.IdempotencyStatus;
import org.mepcity.kursplatform.iam.domain.MembershipRole;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.OrganizationMembershipRole;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.ProviderCommandStatus;
import org.mepcity.kursplatform.iam.domain.ProviderCommandType;
import org.mepcity.kursplatform.iam.domain.RefreshToken;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.SessionScope;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.domain.UserIdentity;
import org.mepcity.kursplatform.iam.domain.UserStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class JdbcIamAuthRepository implements IamAuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIamAuthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserIdentity> findUserIdentityByIssuerAndSubject(String issuer, String subject) {
        List<UserIdentity> results = jdbcTemplate.query(
                "SELECT id, user_id, issuer, subject, created_at, disabled_at FROM user_identities WHERE issuer = ? AND subject = ?",
                (rs, rowNum) -> new UserIdentity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("issuer"),
                        rs.getString("subject"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("disabled_at"))),
                issuer, subject);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<UserIdentity> findUserIdentityByTrustedDevice(UUID trustedDeviceId) {
        List<UserIdentity> results = jdbcTemplate.query(
                "SELECT ui.id, ui.user_id, ui.issuer, ui.subject, ui.created_at, ui.disabled_at " +
                        "FROM user_identities ui " +
                        "JOIN trusted_devices td ON td.user_id = ui.user_id " +
                        "WHERE td.id = ? AND ui.disabled_at IS NULL " +
                        "ORDER BY ui.created_at DESC LIMIT 1",
                (rs, rowNum) -> new UserIdentity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("issuer"),
                        rs.getString("subject"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("disabled_at"))),
                trustedDeviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<User> findUserById(UUID userId) {
        List<User> results = jdbcTemplate.query(
                "SELECT id, status, reauthentication_required_after, created_at, updated_at, row_version, created_by_user_id, updated_by_user_id FROM users WHERE id = ?",
                (rs, rowNum) -> new User(
                        rs.getObject("id", UUID.class),
                        UserStatus.valueOf(rs.getString("status")),
                        toInstant(rs.getTimestamp("reauthentication_required_after")),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at")),
                        rs.getInt("row_version"),
                        rs.getObject("created_by_user_id", UUID.class),
                        rs.getObject("updated_by_user_id", UUID.class)),
                userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<PlatformAdministrator> findActivePlatformAdministratorByUserId(UUID userId) {
        List<PlatformAdministrator> results = jdbcTemplate.query(
                "SELECT id, user_id, granted_by_user_id, granted_at, revoked_at FROM platform_administrators WHERE user_id = ? AND revoked_at IS NULL",
                (rs, rowNum) -> new PlatformAdministrator(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("granted_by_user_id", UUID.class),
                        toInstant(rs.getTimestamp("granted_at")),
                        toInstant(rs.getTimestamp("revoked_at"))),
                userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<TrustedDevice> findActiveTrustedDevice(UUID userId, UUID deviceIdentifier) {
        List<TrustedDevice> results = jdbcTemplate.query(
                "SELECT id, user_id, device_identifier, device_name, platform, trusted_at, last_seen_at, revoked_at FROM trusted_devices WHERE user_id = ? AND device_identifier = ? AND revoked_at IS NULL",
                (rs, rowNum) -> new TrustedDevice(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("device_identifier", UUID.class),
                        rs.getString("device_name"),
                        DevicePlatform.valueOf(rs.getString("platform")),
                        toInstant(rs.getTimestamp("trusted_at")),
                        toInstant(rs.getTimestamp("last_seen_at")),
                        toInstant(rs.getTimestamp("revoked_at"))),
                userId, deviceIdentifier);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<TrustedDevice> findTrustedDeviceById(UUID userId, UUID deviceId) {
        List<TrustedDevice> results = jdbcTemplate.query(
                "SELECT id, user_id, device_identifier, device_name, platform, trusted_at, last_seen_at, revoked_at FROM trusted_devices WHERE user_id = ? AND id = ?",
                (rs, rowNum) -> new TrustedDevice(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("device_identifier", UUID.class),
                        rs.getString("device_name"),
                        DevicePlatform.valueOf(rs.getString("platform")),
                        toInstant(rs.getTimestamp("trusted_at")),
                        toInstant(rs.getTimestamp("last_seen_at")),
                        toInstant(rs.getTimestamp("revoked_at"))),
                userId, deviceId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<Instant> getMaxRevokedAtForDevicePair(UUID userId, UUID deviceIdentifier) {
        List<Instant> results = jdbcTemplate.query(
                "SELECT MAX(revoked_at) AS max_revoked FROM trusted_devices WHERE user_id = ? AND device_identifier = ? AND revoked_at IS NOT NULL",
                (rs, rowNum) -> toInstant(rs.getTimestamp("max_revoked")),
                userId, deviceIdentifier);
        if (results.isEmpty() || results.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    @Override
    public TrustedDevice saveTrustedDevice(TrustedDevice device) {
        jdbcTemplate.update(
                "INSERT INTO trusted_devices (id, user_id, device_identifier, device_name, platform, trusted_at, last_seen_at, revoked_at) VALUES (?, ?, ?, ?, ?::device_platform_enum, ?, ?, ?)",
                device.id(), device.userId(), device.deviceIdentifier(), device.deviceName(),
                device.platform().name(), Timestamp.from(device.trustedAt()),
                device.lastSeenAt() != null ? Timestamp.from(device.lastSeenAt()) : null,
                device.revokedAt() != null ? Timestamp.from(device.revokedAt()) : null);
        return device;
    }

    @Override
    public ContextSelectionToken saveContextSelectionToken(ContextSelectionToken token) {
        jdbcTemplate.update(
                "INSERT INTO context_selection_tokens (id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at, consumed_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                token.id(), token.userId(), token.trustedDeviceId(), token.tokenHash(),
                Timestamp.from(token.authenticatedAt()), Timestamp.from(token.issuedAt()),
                Timestamp.from(token.expiresAt()),
                token.consumedAt() != null ? Timestamp.from(token.consumedAt()) : null,
                token.revokedAt() != null ? Timestamp.from(token.revokedAt()) : null);
        return token;
    }

    @Override
    public Optional<ContextSelectionToken> findContextSelectionTokenByHash(String tokenHash) {
        List<ContextSelectionToken> results = jdbcTemplate.query(
                "SELECT id, user_id, trusted_device_id, token_hash, authenticated_at, issued_at, expires_at, consumed_at, revoked_at FROM context_selection_tokens WHERE token_hash = ?",
                (rs, rowNum) -> new ContextSelectionToken(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("trusted_device_id", UUID.class),
                        rs.getString("token_hash"),
                        toInstant(rs.getTimestamp("authenticated_at")),
                        toInstant(rs.getTimestamp("issued_at")),
                        toInstant(rs.getTimestamp("expires_at")),
                        toInstant(rs.getTimestamp("consumed_at")),
                        toInstant(rs.getTimestamp("revoked_at"))),
                tokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean consumeContextSelectionTokenIfAvailable(UUID tokenId, Instant consumedAt) {
        int affected = jdbcTemplate.update(
                "UPDATE context_selection_tokens SET consumed_at = ? WHERE id = ? AND consumed_at IS NULL AND revoked_at IS NULL",
                Timestamp.from(consumedAt), tokenId);
        return affected == 1;
    }

    @Override
    public void markContextSelectionTokenRevoked(UUID tokenId, Instant revokedAt) {
        jdbcTemplate.update(
                "UPDATE context_selection_tokens SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL",
                Timestamp.from(revokedAt), tokenId);
    }

    @Override
    public void acquireDeviceAdvisoryLock(UUID userId, UUID deviceIdentifier) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext('trusted_device:' || ?::text), hashtext(?::text))",
                Object.class, userId.toString(), deviceIdentifier.toString());
    }

    @Override
    public void acquireIdempotencyAdvisoryLock(UUID actorUserId, String clientMutationId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_xact_lock(hashtext('idempotency:' || ?::text), hashtext(?::text))",
                Object.class, actorUserId.toString(), clientMutationId);
    }

    @Override
    public Optional<IdempotencyKey> insertIdempotencyKeyOrFindExisting(IdempotencyKey key) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, operation_type, request_fingerprint, status, result_entity_id, terminal_http_status, terminal_error_code, result_payload, result_reference, lease_owner, lease_generation, lease_expires_at, created_at, completed_at, result_expires_at, key_retention_expires_at) " +
                        "VALUES (?, ?::idempotency_scope_enum, ?, ?, ?, ?, ?, ?::idempotency_status_enum, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (user_id, client_mutation_id) WHERE scope_type = 'IAM_AUTH' DO NOTHING",
                key.id(), key.scopeType().name(),
                key.organizationId(), key.userId(), key.clientMutationId(),
                key.operationType(), key.requestFingerprint(), key.status().name(),
                key.resultEntityId(), key.terminalHttpStatus(), key.terminalErrorCode(),
                key.resultPayload(), key.resultReference(),
                key.leaseOwner(), key.leaseGeneration(),
                key.leaseExpiresAt() != null ? Timestamp.from(key.leaseExpiresAt()) : null,
                Timestamp.from(key.createdAt()),
                key.completedAt() != null ? Timestamp.from(key.completedAt()) : null,
                key.resultExpiresAt() != null ? Timestamp.from(key.resultExpiresAt()) : null,
                Timestamp.from(key.keyRetentionExpiresAt()));
        if (inserted == 1) {
            return Optional.empty();
        }
        return findIdempotencyKey(key.userId(), key.clientMutationId(), key.scopeType(),
                OperationCode.valueOf(key.operationType()));
    }

    @Override
    public List<OrganizationMembership> findActiveOrganizationMembershipsByUserId(UUID userId) {
        return jdbcTemplate.query(
                "SELECT id, organization_id, user_id, person_id, status, session_generation, reauthentication_required_after, granted_by_user_id, granted_at FROM organization_memberships WHERE user_id = ? AND status = 'ACTIVE'",
                (rs, rowNum) -> new OrganizationMembership(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("person_id", UUID.class),
                        UserStatus.valueOf(rs.getString("status")),
                        rs.getInt("session_generation"),
                        toInstant(rs.getTimestamp("reauthentication_required_after")),
                        rs.getObject("granted_by_user_id", UUID.class),
                        toInstant(rs.getTimestamp("granted_at"))),
                userId);
    }

    @Override
    public Optional<OrganizationMembership> findOrganizationMembershipByIdAndUserId(UUID membershipId, UUID userId) {
        List<OrganizationMembership> results = jdbcTemplate.query(
                "SELECT id, organization_id, user_id, person_id, status, session_generation, reauthentication_required_after, granted_by_user_id, granted_at FROM organization_memberships WHERE id = ? AND user_id = ?",
                (rs, rowNum) -> new OrganizationMembership(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("person_id", UUID.class),
                        UserStatus.valueOf(rs.getString("status")),
                        rs.getInt("session_generation"),
                        toInstant(rs.getTimestamp("reauthentication_required_after")),
                        rs.getObject("granted_by_user_id", UUID.class),
                        toInstant(rs.getTimestamp("granted_at"))),
                membershipId, userId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<OrganizationMembershipRole> findActiveRolesByMembershipId(UUID membershipId) {
        return jdbcTemplate.query(
                "SELECT id, organization_membership_id, organization_id, role, granted_by_user_id, granted_at, revoked_at FROM organization_membership_roles WHERE organization_membership_id = ? AND revoked_at IS NULL",
                (rs, rowNum) -> new OrganizationMembershipRole(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_membership_id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        MembershipRole.valueOf(rs.getString("role")),
                        rs.getObject("granted_by_user_id", UUID.class),
                        toInstant(rs.getTimestamp("granted_at")),
                        toInstant(rs.getTimestamp("revoked_at"))),
                membershipId);
    }

    @Override
    public Set<String> findRoleCodesByMembershipId(UUID membershipId) {
        List<String> roles = jdbcTemplate.queryForList(
                "SELECT role::text FROM organization_membership_roles WHERE organization_membership_id = ? AND revoked_at IS NULL",
                String.class, membershipId);
        return new HashSet<>(roles);
    }

    @Override
    public RefreshTokenFamily saveRefreshTokenFamily(RefreshTokenFamily family) {
        jdbcTemplate.update(
                "INSERT INTO refresh_token_families (id, user_id, trusted_device_id, organization_membership_id, authenticated_at, issued_at_session_generation, revoked_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                family.id(), family.userId(), family.trustedDeviceId(), family.organizationMembershipId(),
                Timestamp.from(family.authenticatedAt()),
                family.issuedAtSessionGeneration() != null ? family.issuedAtSessionGeneration() : null,
                family.revokedAt() != null ? Timestamp.from(family.revokedAt()) : null,
                Timestamp.from(family.createdAt()));
        return family;
    }

    @Override
    public RefreshToken saveRefreshToken(RefreshToken token) {
        jdbcTemplate.update(
                "INSERT INTO refresh_tokens (id, family_id, previous_refresh_token_id, token_hash, access_token_hash, access_expires_at, issued_at, used_at, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                token.id(), token.familyId(), token.previousRefreshTokenId(),
                token.tokenHash(), token.accessTokenHash(),
                Timestamp.from(token.accessExpiresAt()), Timestamp.from(token.issuedAt()),
                token.usedAt() != null ? Timestamp.from(token.usedAt()) : null,
                Timestamp.from(token.expiresAt()),
                token.revokedAt() != null ? Timestamp.from(token.revokedAt()) : null);
        return token;
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByHash(String tokenHash) {
        List<RefreshToken> results = jdbcTemplate.query(
                "SELECT id, family_id, previous_refresh_token_id, token_hash, access_token_hash, access_expires_at, issued_at, used_at, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ?",
                (rs, rowNum) -> mapRefreshToken(rs),
                tokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenById(UUID tokenId) {
        List<RefreshToken> results = jdbcTemplate.query(
                "SELECT id, family_id, previous_refresh_token_id, token_hash, access_token_hash, access_expires_at, issued_at, used_at, expires_at, revoked_at FROM refresh_tokens WHERE id = ?",
                (rs, rowNum) -> mapRefreshToken(rs), tokenId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByHashForUpdate(String tokenHash) {
        List<RefreshToken> results = jdbcTemplate.query(
                "SELECT id, family_id, previous_refresh_token_id, token_hash, access_token_hash, access_expires_at, issued_at, used_at, expires_at, revoked_at FROM refresh_tokens WHERE token_hash = ? FOR UPDATE",
                (rs, rowNum) -> mapRefreshToken(rs), tokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<RefreshToken> findRefreshTokenByAccessTokenHash(String accessTokenHash) {
        List<RefreshToken> results = jdbcTemplate.query(
                "SELECT id, family_id, previous_refresh_token_id, token_hash, access_token_hash, access_expires_at, issued_at, used_at, expires_at, revoked_at FROM refresh_tokens WHERE access_token_hash = ?",
                (rs, rowNum) -> mapRefreshToken(rs),
                accessTokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<RefreshTokenFamily> findRefreshTokenFamilyById(UUID familyId) {
        List<RefreshTokenFamily> results = jdbcTemplate.query(
                "SELECT id, user_id, trusted_device_id, organization_membership_id, authenticated_at, issued_at_session_generation, revoked_at, created_at FROM refresh_token_families WHERE id = ?",
                (rs, rowNum) -> new RefreshTokenFamily(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getObject("trusted_device_id", UUID.class),
                        rs.getObject("organization_membership_id", UUID.class),
                        toInstant(rs.getTimestamp("authenticated_at")),
                        rs.getObject("issued_at_session_generation", Integer.class),
                        toInstant(rs.getTimestamp("revoked_at")),
                        toInstant(rs.getTimestamp("created_at"))),
                familyId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void markRefreshTokenUsed(UUID tokenId, Instant usedAt) {
        jdbcTemplate.update("UPDATE refresh_tokens SET used_at = transaction_timestamp() WHERE id = ? AND used_at IS NULL AND revoked_at IS NULL",
                tokenId);
    }

    @Override
    public void revokeRefreshTokenFamily(UUID familyId, Instant revokedAt) {
        jdbcTemplate.update("UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ? AND revoked_at IS NULL",
                familyId);
    }

    @Override
    public boolean revokeRefreshTokenFamilyIfActive(UUID familyId) {
        return jdbcTemplate.update(
                "UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE id = ? AND revoked_at IS NULL",
                familyId) == 1;
    }

    @Override
    public void revokeRefreshTokensInFamily(UUID familyId, Instant revokedAt) {
        jdbcTemplate.update("UPDATE refresh_tokens SET revoked_at = transaction_timestamp() WHERE family_id = ? AND revoked_at IS NULL",
                familyId);
    }

    @Override
    public Optional<AuthSession> findAuthSessionByAccessTokenHash(String accessTokenHash) {
        List<AuthSession> results = jdbcTemplate.query(
                "SELECT rt.id AS rt_id, rt.family_id, rtf.user_id, rtf.trusted_device_id, rtf.organization_membership_id, rtf.authenticated_at, rt.access_expires_at, rt.revoked_at, rtf.revoked_at AS family_revoked_at " +
                        "FROM refresh_tokens rt JOIN refresh_token_families rtf ON rt.family_id = rtf.id " +
                        "WHERE rt.access_token_hash = ? AND rt.revoked_at IS NULL AND rtf.revoked_at IS NULL AND rt.used_at IS NULL",
                (rs, rowNum) -> {
                    UUID membershipId = rs.getObject("organization_membership_id", UUID.class);
                    SessionScope scope = membershipId == null ? SessionScope.GLOBAL_PLATFORM_ADMIN : SessionScope.ORGANIZATION;
                    return new AuthSession(
                            rs.getObject("user_id", UUID.class),
                            rs.getObject("trusted_device_id", UUID.class),
                            rs.getObject("family_id", UUID.class),
                            rs.getObject("rt_id", UUID.class),
                            scope,
                            membershipId,
                            toInstant(rs.getTimestamp("authenticated_at")),
                            toInstant(rs.getTimestamp("access_expires_at")));
                },
                accessTokenHash);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<IdempotencyKey> findIdempotencyKey(UUID userId, String clientMutationId,
                                                        IdempotencyScope scope, OperationCode operationCode) {
        String scopeFilter = switch (scope) {
            case IAM_AUTH -> "scope_type = 'IAM_AUTH'";
            case GLOBAL -> "scope_type = 'GLOBAL'";
            case ORGANIZATION -> "scope_type = 'ORGANIZATION'";
        };
        List<IdempotencyKey> results = jdbcTemplate.query(
                "SELECT id, scope_type, organization_id, user_id, client_mutation_id, operation_type, request_fingerprint, status, result_entity_id, terminal_http_status, terminal_error_code, result_payload, result_reference, lease_owner, lease_generation, lease_expires_at, created_at, completed_at, result_expires_at, key_retention_expires_at " +
                        "FROM idempotency_keys WHERE user_id = ? AND client_mutation_id = ? AND operation_type = ? AND " + scopeFilter,
                (rs, rowNum) -> mapIdempotencyKey(rs),
                userId, clientMutationId, operationCode.name());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public IdempotencyKey saveIdempotencyKey(IdempotencyKey key) {
        jdbcTemplate.update(
                "INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id, operation_type, request_fingerprint, status, result_entity_id, terminal_http_status, terminal_error_code, result_payload, result_reference, lease_owner, lease_generation, lease_expires_at, created_at, completed_at, result_expires_at, key_retention_expires_at) " +
                        "VALUES (?, ?::idempotency_scope_enum, ?, ?, ?, ?, ?, ?::idempotency_status_enum, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)",
                key.id(), key.scopeType().name(),
                key.organizationId(), key.userId(), key.clientMutationId(),
                key.operationType(), key.requestFingerprint(), key.status().name(),
                key.resultEntityId(), key.terminalHttpStatus(), key.terminalErrorCode(),
                key.resultPayload(), key.resultReference(),
                key.leaseOwner(), key.leaseGeneration(),
                key.leaseExpiresAt() != null ? Timestamp.from(key.leaseExpiresAt()) : null,
                Timestamp.from(key.createdAt()),
                key.completedAt() != null ? Timestamp.from(key.completedAt()) : null,
                key.resultExpiresAt() != null ? Timestamp.from(key.resultExpiresAt()) : null,
                Timestamp.from(key.keyRetentionExpiresAt()));
        return key;
    }

    @Override
    public void completeIdempotencyKey(UUID idempotencyKeyId, short httpStatus, String resultReference,
                                       Instant completedAt, Instant resultExpiresAt) {
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET status = 'COMPLETED', terminal_http_status = ?, result_reference = ?, completed_at = ?, result_expires_at = ?, lease_owner = NULL, lease_generation = NULL, lease_expires_at = NULL WHERE id = ? AND status = 'PENDING'",
                httpStatus, resultReference, Timestamp.from(completedAt),
                Timestamp.from(resultExpiresAt), idempotencyKeyId);
    }

    @Override
    public void failIdempotencyKey(UUID idempotencyKeyId, short httpStatus, String errorCode, Instant completedAt) {
        jdbcTemplate.update(
                "UPDATE idempotency_keys SET status = 'FAILED', terminal_http_status = ?, terminal_error_code = ?, completed_at = ?, lease_owner = NULL, lease_generation = NULL, lease_expires_at = NULL WHERE id = ? AND status = 'PENDING'",
                httpStatus, errorCode, Timestamp.from(completedAt), idempotencyKeyId);
    }

    @Override
    public void saveAuthReplayEscrow(AuthReplayEscrow escrow) {
        jdbcTemplate.update(
                "INSERT INTO iam_auth_response_escrows (id, idempotency_key_id, actor_user_id, operation_type, device_identifier, token_fingerprint, result_context_token_id, result_refresh_token_family_id, result_refresh_token_id, ciphertext, aead_key_reference, aead_nonce, aad_context, status, expires_at, created_at, deleted_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::escrow_status_enum, ?, ?, ?)",
                escrow.id(), escrow.idempotencyKeyId(), escrow.actorUserId(),
                escrow.operationType(), escrow.deviceIdentifier(), escrow.tokenFingerprint(),
                escrow.resultContextTokenId(), escrow.resultRefreshTokenFamilyId(), escrow.resultRefreshTokenId(),
                escrow.ciphertext(), escrow.aeadKeyReference(), escrow.aeadNonce(), escrow.aadContext(),
                escrow.status().name(), Timestamp.from(escrow.expiresAt()),
                Timestamp.from(escrow.createdAt()),
                escrow.deletedAt() != null ? Timestamp.from(escrow.deletedAt()) : null);
    }

    @Override
    public Optional<AuthReplayEscrow> findAuthReplayEscrowByIdempotencyKeyId(UUID idempotencyKeyId) {
        List<AuthReplayEscrow> results = jdbcTemplate.query(
                "SELECT id, idempotency_key_id, actor_user_id, operation_type, device_identifier, token_fingerprint, result_context_token_id, result_refresh_token_family_id, result_refresh_token_id, ciphertext, aead_key_reference, aead_nonce, aad_context, status, expires_at, created_at, deleted_at FROM iam_auth_response_escrows WHERE idempotency_key_id = ?",
                (rs, rowNum) -> new AuthReplayEscrow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("idempotency_key_id", UUID.class),
                        rs.getObject("actor_user_id", UUID.class),
                        rs.getString("operation_type"),
                        rs.getObject("device_identifier", UUID.class),
                        rs.getString("token_fingerprint"),
                        rs.getObject("result_context_token_id", UUID.class),
                        rs.getObject("result_refresh_token_family_id", UUID.class),
                        rs.getObject("result_refresh_token_id", UUID.class),
                        rs.getBytes("ciphertext"),
                        rs.getString("aead_key_reference"),
                        rs.getBytes("aead_nonce"),
                        rs.getString("aad_context"),
                        EscrowStatus.valueOf(rs.getString("status")),
                        toInstant(rs.getTimestamp("expires_at")),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("deleted_at"))),
                idempotencyKeyId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public boolean revokeAuthReplayEscrow(UUID escrowId) {
        // The RLS policy intentionally accepts this only after requireSecurityRevoke() has set the
        // transaction-local gate.  Keep all secret-bearing columns out of the terminal row.
        return jdbcTemplate.update(
                "UPDATE iam_auth_response_escrows SET status = 'REVOKED', ciphertext = NULL, "
                        + "aead_key_reference = NULL, aead_nonce = NULL, aad_context = NULL, "
                        + "deleted_at = transaction_timestamp() WHERE id = ? AND status = 'READY'",
                escrowId) == 1;
    }

    @Override
    public void revokeAllActorFamilies(UUID actorUserId, OperationCode operationCode, Instant now) {
        // revoked_at is set to transaction_timestamp() rather than the bound `now` parameter to
        // match refresh_token_families_update_actor_revoke / refresh_tokens_update_actor_revoke's
        // WITH CHECK (revoked_at = transaction_timestamp()) exactly — a Java clock value could
        // legitimately differ from the DB's clock by a few milliseconds, which would otherwise
        // make RLS silently reject the revoke on every call.
        jdbcTemplate.update(
                "UPDATE refresh_token_families SET revoked_at = transaction_timestamp() WHERE user_id = ? AND revoked_at IS NULL",
                actorUserId);
        jdbcTemplate.update(
                "UPDATE refresh_tokens SET revoked_at = transaction_timestamp() WHERE family_id IN (SELECT id FROM refresh_token_families WHERE user_id = ?) AND revoked_at IS NULL",
                actorUserId);
    }

    @Override
    public void elevateUserReauthenticationRequiredAfter(UUID userId, Instant now) {
        jdbcTemplate.update(
                "UPDATE users SET reauthentication_required_after = ? WHERE id = ?",
                Timestamp.from(now), userId);
    }

    @Override
    public ProviderCommand saveProviderCommand(ProviderCommand command) {
        int inserted = jdbcTemplate.update(
                "INSERT INTO iam_provider_commands (id, idempotency_key, provider, command_type, target_user_id, target_identity_id, organization_id, username_lookup_hash, payload_fingerprint, encrypted_command_payload, payload_key_id, status, attempt_count, next_attempt_at, lease_expires_at, fencing_token, lease_owner, created_at, completed_at, last_safe_error_code) " +
                        "VALUES (?, ?, ?, ?::provider_command_type_enum, ?, ?, ?, ?, ?, ?, ?, ?::provider_command_status_enum, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (idempotency_key) DO NOTHING",
                command.id(), command.idempotencyKey(), command.provider(), command.commandType().name(),
                command.targetUserId(), command.targetIdentityId(), command.organizationId(),
                command.usernameLookupHash(), command.payloadFingerprint(), command.encryptedCommandPayload(),
                command.payloadKeyId(), command.status().name(), command.attemptCount(),
                Timestamp.from(command.nextAttemptAt()),
                command.leaseExpiresAt() != null ? Timestamp.from(command.leaseExpiresAt()) : null,
                command.fencingToken(), command.leaseOwner(),
                Timestamp.from(command.createdAt()),
                command.completedAt() != null ? Timestamp.from(command.completedAt()) : null,
                command.lastSafeErrorCode());
        if (inserted == 1) {
            return command;
        }
        // ON CONFLICT DO NOTHING means another transaction already owns this idempotency_key;
        // the in-memory `command` object was never persisted, so returning it as-is would hand
        // callers a result that doesn't exist in the database. Return what's actually there.
        return findProviderCommandByIdempotencyKey(command.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException(
                        "Provider command insert conflict fakat idempotency_key ile kayıt bulunamadı."));
    }

    @Override
    public Optional<ProviderCommand> findProviderCommandById(UUID commandId) {
        List<ProviderCommand> results = jdbcTemplate.query(
                "SELECT id, idempotency_key, provider, command_type, target_user_id, target_identity_id, organization_id, username_lookup_hash, payload_fingerprint, encrypted_command_payload, payload_key_id, status, attempt_count, next_attempt_at, lease_expires_at, fencing_token, lease_owner, created_at, completed_at, last_safe_error_code FROM iam_provider_commands WHERE id = ?",
                (rs, rowNum) -> mapProviderCommand(rs),
                commandId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Sets an {@code app.*} GUC via {@code set_config(..., true)} (the bind-param-safe equivalent
     * of {@code SET LOCAL}) as its OWN statement, immediately before the CAS UPDATE that depends on
     * it. A same-statement CTE side effect (the original design here) satisfies {@code
     * trg_ipc_state()} — the BEFORE trigger clearly runs after the CTE materializes — but does
     * NOT reliably satisfy the iam_provider_commands RLS UPDATE policies' USING clause: under real
     * iam_runtime enforcement this was observed to silently match zero rows even though the row's
     * lease_owner/fencing_token/status genuinely matched, while the exact same setup as two
     * separate statements on the transaction-bound connection works correctly (the same pattern
     * SpringIamTransactionExecutor already relies on for app.iam_operation_scope/code). Postgres
     * does not guarantee a CTE's volatile side effect is visible to a policy qual evaluated for a
     * FROM-joined UPDATE in the same statement; a preceding statement removes the ambiguity.
     */
    private void setProviderCommandWorkerContext(UUID commandId, String workerId, long fencingToken) {
        jdbcTemplate.queryForObject("SELECT set_config('app.iam_target_provider_command_id', ?, true)",
                Object.class, commandId.toString());
        setWorkerIdContext(workerId);
        jdbcTemplate.queryForObject("SELECT set_config('app.iam_fencing_token', ?, true)", Object.class,
                String.valueOf(fencingToken));
    }

    private void setWorkerIdContext(String workerId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.iam_worker_id', ?, true)", Object.class, workerId);
    }

    /**
     * Resolves the provider identity a CLAIMED command targets, for {@link
     * org.mepcity.kursplatform.iam.application.ProviderCommandWorker} to build the real provider
     * call with. Gated by {@code user_identities_select_provider_command_worker} (V8), keyed on
     * {@code app.iam_target_identity_id} — set here from the id the caller already read back from
     * its own successful claim, never from external input.
     */
    @Override
    public Optional<UserIdentity> findClaimedCommandTargetIdentity(UUID targetIdentityId) {
        jdbcTemplate.queryForObject("SELECT set_config('app.iam_target_identity_id', ?, true)",
                Object.class, targetIdentityId.toString());
        List<UserIdentity> results = jdbcTemplate.query(
                "SELECT id, user_id, issuer, subject, created_at, disabled_at FROM user_identities WHERE id = ?",
                (rs, rowNum) -> new UserIdentity(
                        rs.getObject("id", UUID.class),
                        rs.getObject("user_id", UUID.class),
                        rs.getString("issuer"),
                        rs.getString("subject"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("disabled_at"))),
                targetIdentityId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * IAM-004 worker poll: returns the ids of PENDING USER_DISABLE / USER_LOGOUT commands whose
     * {@code next_attempt_at} has elapsed, oldest first, capped at {@code limit}. The WHERE clause
     * mirrors exactly what the {@code iam_provider_commands_select_worker} RLS policy (V9) already
     * enforces under real iam_runtime — supported types only, PENDING with no current lease, and
     * {@code next_attempt_at <= now} — so the query and the policy cannot drift apart. The worker
     * id GUC is set first because the policy's CLAIMED-by-me branch references it (not relevant for
     * this PENDING read, but the symmetric SELECT policy applies the same context for every branch).
     */
    @Override
    public List<UUID> findNextClaimableProviderCommandIds(int limit, Instant now) {
        return jdbcTemplate.queryForList(
                "SELECT id FROM iam_provider_commands " +
                        "WHERE command_type IN ('USER_DISABLE', 'USER_LOGOUT') " +
                        "AND status = 'PENDING' AND lease_owner IS NULL " +
                        "AND next_attempt_at <= ? " +
                        "ORDER BY next_attempt_at ASC, id ASC " +
                        "LIMIT ?",
                UUID.class, Timestamp.from(now), limit);
    }

    private static final String PROVIDER_COMMAND_COLUMNS =
            "id, idempotency_key, provider, command_type, target_user_id, target_identity_id, " +
                    "organization_id, username_lookup_hash, payload_fingerprint, encrypted_command_payload, " +
                    "payload_key_id, status, attempt_count, next_attempt_at, lease_expires_at, fencing_token, " +
                    "lease_owner, created_at, completed_at, last_safe_error_code";

    @Override
    public Optional<ProviderCommand> claimProviderCommandAtomically(UUID commandId, String workerId,
                                                                     Instant leaseExpiresAt, Instant now) {
        // A row only matches WHERE status='PENDING' AND lease_owner IS NULL when it was just
        // created (trg_ipc_state() never allows a transition back to PENDING), so fencing_token is
        // deterministically 0 and the new value is always 1 — no pre-read needed, unlike reclaim.
        setProviderCommandWorkerContext(commandId, workerId, 1);
        List<ProviderCommand> results = jdbcTemplate.query(
                "UPDATE iam_provider_commands SET status = 'CLAIMED', attempt_count = attempt_count + 1, " +
                        "fencing_token = fencing_token + 1, lease_owner = ?, lease_expires_at = ?, " +
                        "next_attempt_at = ? " +
                        "WHERE id = ? AND status = 'PENDING' AND lease_owner IS NULL " +
                        "RETURNING " + PROVIDER_COMMAND_COLUMNS,
                (rs, rowNum) -> mapProviderCommand(rs),
                workerId, Timestamp.from(leaseExpiresAt), Timestamp.from(now), commandId);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<ProviderCommand> reclaimExpiredLeaseAtomically(UUID commandId, String workerId,
                                                                    Instant leaseExpiresAt, Instant now) {
        // Unlike claim, the pre-reclaim fencing_token is whatever the last owner left it at, so it
        // must be read first (itself RLS-gated by iam_provider_commands_select_worker's "CLAIMED
        // and lease already expired" branch, matching this method's own WHERE) before the expected
        // new value can be published via set_config for trg_ipc_state()'s WITH CHECK.
        List<Long> currentFencing = jdbcTemplate.query(
                "SELECT fencing_token FROM iam_provider_commands " +
                        "WHERE id = ? AND status = 'CLAIMED' AND lease_expires_at IS NOT NULL AND lease_expires_at < ?",
                (rs, rowNum) -> rs.getLong("fencing_token"), commandId, Timestamp.from(now));
        if (currentFencing.isEmpty()) {
            return Optional.empty();
        }
        setProviderCommandWorkerContext(commandId, workerId, currentFencing.get(0) + 1);
        List<ProviderCommand> results = jdbcTemplate.query(
                "UPDATE iam_provider_commands SET fencing_token = fencing_token + 1, lease_owner = ?, " +
                        "lease_expires_at = ?, next_attempt_at = ? " +
                        "WHERE id = ? AND status = 'CLAIMED' AND lease_expires_at IS NOT NULL AND lease_expires_at < ? " +
                        "RETURNING " + PROVIDER_COMMAND_COLUMNS,
                (rs, rowNum) -> mapProviderCommand(rs),
                workerId, Timestamp.from(leaseExpiresAt), Timestamp.from(now),
                commandId, Timestamp.from(now));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<ProviderCommand> completeProviderCommandAtomically(UUID commandId, String workerId,
                                                                        long fencingToken, boolean success,
                                                                        String safeErrorCode, Instant completedAt) {
        String newStatus = success ? "COMPLETED" : "FAILED";
        setProviderCommandWorkerContext(commandId, workerId, fencingToken);
        List<ProviderCommand> results = jdbcTemplate.query(
                "UPDATE iam_provider_commands SET status = ?::provider_command_status_enum, completed_at = ?, last_safe_error_code = ?, " +
                        "lease_owner = NULL, lease_expires_at = NULL " +
                        "WHERE id = ? AND status = 'CLAIMED' AND lease_owner = ? AND fencing_token = ? " +
                        "AND lease_expires_at IS NOT NULL AND lease_expires_at >= ? " +
                        "RETURNING " + PROVIDER_COMMAND_COLUMNS,
                (rs, rowNum) -> mapProviderCommand(rs),
                newStatus, Timestamp.from(completedAt), success ? null : safeErrorCode,
                commandId, workerId, fencingToken, Timestamp.from(completedAt));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Releases a CLAIMED lease back to PENDING for a retryable failure, atomically verifying the
     * SAME worker+fencing that currently holds the lease. trg_ipc_state()'s retry-requeue branch
     * (V9) requires fencing_token to drop back to 0 (the value a fresh PENDING row has) and the
     * lease fields to be cleared; ipc_retry's USING requires the matching worker+fencing+commandId
     * context set above, so a lease lost to a concurrent reclaim mid-call (which would have bumped
     * fencing_token and changed lease_owner) silently no-ops — returns empty rather than clobbering
     * the new owner's in-flight attempt.
     */
    @Override
    public Optional<ProviderCommand> requeueClaimedForRetry(UUID commandId, String workerId, long fencingToken,
                                                            Instant nextAttemptAt, Instant now) {
        setProviderCommandWorkerContext(commandId, workerId, fencingToken);
        List<ProviderCommand> results = jdbcTemplate.query(
                "UPDATE iam_provider_commands SET status = 'PENDING', fencing_token = 0, " +
                        "lease_owner = NULL, lease_expires_at = NULL, next_attempt_at = ? " +
                        "WHERE id = ? AND status = 'CLAIMED' AND lease_owner = ? AND fencing_token = ? " +
                        "RETURNING " + PROVIDER_COMMAND_COLUMNS,
                (rs, rowNum) -> mapProviderCommand(rs),
                Timestamp.from(nextAttemptAt), commandId, workerId, fencingToken);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<ProviderCommand> findProviderCommandByIdempotencyKey(String idempotencyKey) {
        List<ProviderCommand> results = jdbcTemplate.query(
                "SELECT id, idempotency_key, provider, command_type, target_user_id, target_identity_id, organization_id, username_lookup_hash, payload_fingerprint, encrypted_command_payload, payload_key_id, status, attempt_count, next_attempt_at, lease_expires_at, fencing_token, lease_owner, created_at, completed_at, last_safe_error_code FROM iam_provider_commands WHERE idempotency_key = ?",
                (rs, rowNum) -> mapProviderCommand(rs),
                idempotencyKey);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<ContextSelectionSummary> findContextSelectionSummaries(UUID userId) {
        return jdbcTemplate.query(
                "SELECT om.id, om.organization_id, o.name AS org_name, o.status AS org_status, om.status AS membership_status, om.session_generation " +
                        "FROM organization_memberships om JOIN organizations o ON om.organization_id = o.id " +
                        "WHERE om.user_id = ? AND om.status = 'ACTIVE' AND o.status = 'ACTIVE' " +
                        "ORDER BY o.name ASC, om.id ASC",
                (rs, rowNum) -> {
                    UUID membershipId = rs.getObject("id", UUID.class);
                    Set<String> roleCodes = findRoleCodesByMembershipId(membershipId);
                    return new ContextSelectionSummary(
                            membershipId,
                            rs.getObject("organization_id", UUID.class),
                            rs.getString("org_name"),
                            rs.getString("org_status"),
                            rs.getString("membership_status"),
                            roleCodes,
                            rs.getInt("session_generation"));
                },
                userId);
    }

    private RefreshToken mapRefreshToken(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new RefreshToken(
                rs.getObject("id", UUID.class),
                rs.getObject("family_id", UUID.class),
                rs.getObject("previous_refresh_token_id", UUID.class),
                rs.getString("token_hash"),
                rs.getString("access_token_hash"),
                toInstant(rs.getTimestamp("access_expires_at")),
                toInstant(rs.getTimestamp("issued_at")),
                toInstant(rs.getTimestamp("used_at")),
                toInstant(rs.getTimestamp("expires_at")),
                toInstant(rs.getTimestamp("revoked_at")));
    }

    private IdempotencyKey mapIdempotencyKey(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new IdempotencyKey(
                rs.getObject("id", UUID.class),
                IdempotencyScope.valueOf(rs.getString("scope_type")),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("client_mutation_id"),
                rs.getString("operation_type"),
                rs.getString("request_fingerprint"),
                IdempotencyStatus.valueOf(rs.getString("status")),
                rs.getObject("result_entity_id", UUID.class),
                rs.getObject("terminal_http_status", Short.class),
                rs.getString("terminal_error_code"),
                rs.getString("result_payload"),
                rs.getString("result_reference"),
                rs.getString("lease_owner"),
                rs.getObject("lease_generation", Long.class),
                toInstant(rs.getTimestamp("lease_expires_at")),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("completed_at")),
                toInstant(rs.getTimestamp("result_expires_at")),
                toInstant(rs.getTimestamp("key_retention_expires_at")));
    }

    private ProviderCommand mapProviderCommand(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new ProviderCommand(
                rs.getObject("id", UUID.class),
                rs.getString("idempotency_key"),
                rs.getString("provider"),
                ProviderCommandType.valueOf(rs.getString("command_type")),
                rs.getObject("target_user_id", UUID.class),
                rs.getObject("target_identity_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("username_lookup_hash"),
                rs.getString("payload_fingerprint"),
                rs.getBytes("encrypted_command_payload"),
                rs.getString("payload_key_id"),
                ProviderCommandStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                toInstant(rs.getTimestamp("next_attempt_at")),
                toInstant(rs.getTimestamp("lease_expires_at")),
                rs.getLong("fencing_token"),
                rs.getString("lease_owner"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("completed_at")),
                rs.getString("last_safe_error_code"));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
