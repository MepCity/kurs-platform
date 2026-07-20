package org.mepcity.kursplatform.org.infrastructure.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.mepcity.kursplatform.org.application.IdempotencyOutcome;
import org.mepcity.kursplatform.org.application.IdempotencyRecorder;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * JDBC adapter for {@link IdempotencyRecorder}. All operations use the caller's Spring transaction
 * and PostgreSQL RLS context.
 *
 * <p>The claim path is race-safe: a brand-new claim is inserted with
 * {@code INSERT ... ON CONFLICT DO NOTHING}. If the insert succeeds, this transaction owns the
 * lease. If the insert is skipped (a row already exists), the row is locked with
 * {@code SELECT ... FOR UPDATE} and re-evaluated:
 *
 * <ul>
 *   <li>Any fingerprint mismatch (terminal <em>or</em> still {@code PENDING}) →
 *       {@link IdempotencyOutcome.Clash} ({@code 409 IDEMPOTENCY_KEY_REUSED}). A mismatched request
 *       can never succeed by waiting, so it fails closed immediately instead of returning
 *       {@code Pending} for a lease that belongs to an unrelated request.
 *   <li>Terminal {@code COMPLETED}/{@code FAILED} + matching fingerprint → {@link IdempotencyOutcome.Replay}.
 *   <li>{@code PENDING} + matching fingerprint → an unconditional attempt to re-claim via a
 *       conditional {@code UPDATE} gated on {@code lease_expires_at <= clock_timestamp()}.
 *       {@code clock_timestamp()} (the real wall-clock time at statement execution, unlike
 *       {@code transaction_timestamp()} which is frozen at {@code BEGIN}) is the sole authority on
 *       expiry, evaluated under the row lock already held — there is no comparison between a JVM
 *       wall-clock reading and a DB timestamp, and a long-running transaction cannot see a stale
 *       "now". If the lease is still valid the {@code UPDATE} affects no rows and this transaction
 *       fails closed with {@link IdempotencyOutcome.Pending}; if it is expired, the {@code UPDATE}
 *       re-claims the lease, incrementing {@code lease_generation} exactly once, and returns
 *       {@link IdempotencyOutcome.Claimed}.
 * </ul>
 *
 * <p>{@link #markCompleted} matches the record id <em>and</em> the lease owner + lease generation +
 * a still-valid ({@code lease_expires_at > clock_timestamp()}) lease; a stale owner cannot complete
 * the record once the lease has expired in real time, even if its own transaction began before that
 * expiry.
 *
 * <p>No {@code FAILED} terminal write is attempted on the failing transaction: the claim (and any
 * partial audit row) vanish with the rollback.
 */
public final class JdbcIdempotencyRecorder implements IdempotencyRecorder {

    private static final String INSERT_CLAIM_SQL = """
            INSERT INTO idempotency_keys (id, scope_type, organization_id, user_id, client_mutation_id,
                operation_type, request_fingerprint, status, lease_owner, lease_generation,
                lease_expires_at, key_retention_expires_at)
            VALUES (?, ?::idempotency_scope_enum, ?, ?, ?, ?, ?, 'PENDING'::idempotency_status_enum, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            RETURNING id, lease_owner, lease_generation
            """;

    private static final String SELECT_EXISTING_SQL = """
            SELECT id, status, request_fingerprint, terminal_http_status, terminal_error_code,
                   result_entity_id, lease_owner, lease_generation, lease_expires_at
            FROM idempotency_keys
            WHERE scope_type = ?::idempotency_scope_enum
              AND COALESCE(organization_id::text, '') = COALESCE((?::uuid)::text, '')
              AND user_id = ?
              AND client_mutation_id = ?
            FOR UPDATE
            """;

    private static final String RECLAIM_EXPIRED_LEASE_SQL = """
            UPDATE idempotency_keys
            SET lease_owner = ?, lease_generation = lease_generation + 1, lease_expires_at = ?
            WHERE id = ?
              AND status = 'PENDING'::idempotency_status_enum
              AND lease_expires_at <= clock_timestamp()
            RETURNING lease_owner, lease_generation
            """;

    private static final String MARK_COMPLETED_SQL = """
            UPDATE idempotency_keys
            SET status = 'COMPLETED'::idempotency_status_enum,
                completed_at = transaction_timestamp(),
                lease_owner = NULL,
                lease_generation = NULL,
                lease_expires_at = NULL,
                terminal_http_status = ?,
                terminal_error_code = NULL,
                result_entity_id = ?,
                result_payload = NULL,
                result_reference = NULL,
                result_expires_at = NULL,
                key_retention_expires_at = ?
            WHERE id = ?
              AND status = 'PENDING'::idempotency_status_enum
              AND lease_owner = ?
              AND lease_generation = ?
              AND lease_expires_at > clock_timestamp()
            """;

    private final DataSource dataSource;

    public JdbcIdempotencyRecorder(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IdempotencyOutcome resolveOrClaim(
            String scopeType,
            UUID organizationId,
            UUID actorUserId,
            String clientMutationId,
            String operationType,
            String requestFingerprint,
            String leaseOwner,
            Instant leaseExpiresAt,
            Instant keyRetentionExpiresAt) {
        Connection connection = DataSourceUtils.getConnection(dataSource);

        // Step 1: attempt an atomic INSERT ON CONFLICT DO NOTHING. If the row is created, this
        // transaction owns a fresh PENDING claim and can proceed.
        UUID claimId = UUID.randomUUID();
        InsertResult insertResult = tryInsertClaim(connection, claimId, scopeType, organizationId, actorUserId,
                clientMutationId, operationType, requestFingerprint, leaseOwner, leaseExpiresAt, keyRetentionExpiresAt);
        if (insertResult.inserted) {
            return new IdempotencyOutcome.Claimed(insertResult.id, leaseOwner, insertResult.leaseGeneration);
        }

        // Step 2: a row already exists. Lock it for the remainder of this transaction and re-evaluate.
        ExistingRecord record = lockExisting(connection, scopeType, organizationId, actorUserId, clientMutationId);

        // A fingerprint mismatch is key reuse regardless of whether the prior record is terminal or
        // still in flight: a second request with a different body/target reusing the same key can
        // never legitimately succeed by retrying, so it fails closed immediately rather than
        // returning Pending for a lease that belongs to an unrelated request.
        if (!requestFingerprint.equals(record.requestFingerprint)) {
            return new IdempotencyOutcome.Clash();
        }

        // Terminal record with a matching fingerprint: replay the prior result.
        if ("COMPLETED".equals(record.status) || "FAILED".equals(record.status)) {
            var status = "COMPLETED".equals(record.status)
                    ? IdempotencyOutcome.TerminalStatus.COMPLETED
                    : IdempotencyOutcome.TerminalStatus.FAILED;
            short httpStatus = record.terminalHttpStatus == null ? 0 : record.terminalHttpStatus;
            var result = new IdempotencyOutcome.IdempotencyResult(
                    record.id, status, httpStatus, record.terminalErrorCode, record.resultEntityId);
            return new IdempotencyOutcome.Replay(result);
        }

        // PENDING with a matching fingerprint: unconditionally attempt to re-claim. The conditional
        // UPDATE (gated on transaction_timestamp(), evaluated under the row lock already held) is the
        // sole authority on whether the lease is expired — if it is still valid the UPDATE affects no
        // rows and this transaction fails closed; only the winning updater gets a result row back.
        ReclaimResult reclaim = reclaimExpiredLease(connection, record.id, leaseOwner, leaseExpiresAt);
        if (reclaim.reclaimed) {
            return new IdempotencyOutcome.Claimed(record.id, reclaim.leaseOwner, reclaim.leaseGeneration);
        }
        // Lease is still valid per the database's own clock: an in-flight transaction owns it.
        return new IdempotencyOutcome.Pending();
    }

    @Override
    public void markCompleted(
            UUID idempotencyKeyId,
            String leaseOwner,
            long leaseGeneration,
            UUID resultEntityId,
            short terminalHttpStatus,
            Instant keyRetentionExpiresAt) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try (PreparedStatement statement = connection.prepareStatement(MARK_COMPLETED_SQL)) {
            statement.setShort(1, terminalHttpStatus);
            statement.setObject(2, resultEntityId);
            statement.setTimestamp(3, Timestamp.from(keyRetentionExpiresAt));
            statement.setObject(4, idempotencyKeyId);
            statement.setString(5, leaseOwner);
            statement.setLong(6, leaseGeneration);
            if (statement.executeUpdate() != 1) {
                throw new IdempotencyRecordException(
                        "İdempotency kaydı COMPLETED'a güncellenemedi (lease sahipliği doğrulanamadı)", null);
            }
        } catch (SQLException exception) {
            throw new IdempotencyRecordException("İdempotency kaydı COMPLETED'a güncellenemedi", exception);
        }
    }

    private InsertResult tryInsertClaim(
            Connection connection, UUID claimId, String scopeType, UUID organizationId, UUID actorUserId,
            String clientMutationId, String operationType, String requestFingerprint, String leaseOwner,
            Instant leaseExpiresAt, Instant keyRetentionExpiresAt) {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_CLAIM_SQL)) {
            statement.setObject(1, claimId);
            statement.setString(2, scopeType);
            statement.setObject(3, organizationId);
            statement.setObject(4, actorUserId);
            statement.setString(5, clientMutationId);
            statement.setString(6, operationType);
            statement.setString(7, requestFingerprint);
            statement.setString(8, leaseOwner);
            statement.setLong(9, 1L);
            statement.setTimestamp(10, Timestamp.from(leaseExpiresAt));
            statement.setTimestamp(11, Timestamp.from(keyRetentionExpiresAt));
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new InsertResult(true, result.getObject("id", UUID.class), result.getLong("lease_generation"));
                }
                return new InsertResult(false, null, 0L);
            }
        } catch (SQLException exception) {
            throw new IdempotencyRecordException("İdempotency claim kaydı oluşturulamadı", exception);
        }
    }

    private ExistingRecord lockExisting(
            Connection connection, String scopeType, UUID organizationId, UUID actorUserId, String clientMutationId) {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_EXISTING_SQL)) {
            statement.setString(1, scopeType);
            statement.setObject(2, organizationId);
            statement.setObject(3, actorUserId);
            statement.setString(4, clientMutationId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    // Row vanished between the conflicting INSERT and this SELECT (extreme race under RLS).
                    // Treat as fail-closed pending rather than silently retrying.
                    throw new IdempotencyRecordException("İdempotency kaydı kilitleme sonrası bulunamadı", null);
                }
                Object httpStatusObject = result.getObject("terminal_http_status");
                Short httpStatus = httpStatusObject == null ? null : ((Number) httpStatusObject).shortValue();
                Object leaseGenObject = result.getObject("lease_generation");
                Long leaseGeneration = leaseGenObject == null ? null : ((Number) leaseGenObject).longValue();
                Timestamp leaseExpiry = result.getTimestamp("lease_expires_at");
                return new ExistingRecord(
                        result.getObject("id", UUID.class),
                        result.getString("status"),
                        result.getString("request_fingerprint"),
                        httpStatus,
                        result.getString("terminal_error_code"),
                        result.getObject("result_entity_id", UUID.class),
                        leaseGeneration,
                        leaseExpiry == null ? null : leaseExpiry.toInstant());
            }
        } catch (SQLException exception) {
            throw new IdempotencyRecordException("İdempotency kaydı okunamadı", exception);
        }
    }

    private ReclaimResult reclaimExpiredLease(Connection connection, UUID recordId, String leaseOwner, Instant leaseExpiresAt) {
        try (PreparedStatement statement = connection.prepareStatement(RECLAIM_EXPIRED_LEASE_SQL)) {
            statement.setString(1, leaseOwner);
            statement.setTimestamp(2, Timestamp.from(leaseExpiresAt));
            statement.setObject(3, recordId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new ReclaimResult(true, result.getString("lease_owner"), result.getLong("lease_generation"));
                }
                return new ReclaimResult(false, null, 0L);
            }
        } catch (SQLException exception) {
            throw new IdempotencyRecordException("Süresi geçmiş lease yeniden sahiplenilemedi", exception);
        }
    }

    private record InsertResult(boolean inserted, UUID id, long leaseGeneration) {}

    private record ExistingRecord(
            UUID id,
            String status,
            String requestFingerprint,
            Short terminalHttpStatus,
            String terminalErrorCode,
            UUID resultEntityId,
            Long leaseGeneration,
            Instant leaseExpiresAt) {}

    private record ReclaimResult(boolean reclaimed, String leaseOwner, long leaseGeneration) {}
}
