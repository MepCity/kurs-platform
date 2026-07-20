package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.AuthSession;
import org.mepcity.kursplatform.iam.domain.ContextSelectionSummary;
import org.mepcity.kursplatform.iam.domain.ContextSelectionToken;
import org.mepcity.kursplatform.iam.domain.IdempotencyKey;
import org.mepcity.kursplatform.iam.domain.IdempotencyScope;
import org.mepcity.kursplatform.iam.domain.OperationCode;
import org.mepcity.kursplatform.iam.domain.OrganizationMembership;
import org.mepcity.kursplatform.iam.domain.OrganizationMembershipRole;
import org.mepcity.kursplatform.iam.domain.PlatformAdministrator;
import org.mepcity.kursplatform.iam.domain.ProviderCommand;
import org.mepcity.kursplatform.iam.domain.RefreshToken;
import org.mepcity.kursplatform.iam.domain.RefreshTokenFamily;
import org.mepcity.kursplatform.iam.domain.TrustedDevice;
import org.mepcity.kursplatform.iam.domain.User;
import org.mepcity.kursplatform.iam.domain.UserIdentity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface IamAuthRepository {

    Optional<UserIdentity> findUserIdentityByIssuerAndSubject(String issuer, String subject);

    Optional<UserIdentity> findUserIdentityByTrustedDevice(UUID trustedDeviceId);

    Optional<UserIdentity> findClaimedCommandTargetIdentity(UUID targetIdentityId);

    Optional<User> findUserById(UUID userId);

    Optional<PlatformAdministrator> findActivePlatformAdministratorByUserId(UUID userId);

    Optional<TrustedDevice> findActiveTrustedDevice(UUID userId, UUID deviceIdentifier);

    Optional<TrustedDevice> findTrustedDeviceById(UUID userId, UUID deviceId);

    Optional<Instant> getMaxRevokedAtForDevicePair(UUID userId, UUID deviceIdentifier);

    TrustedDevice saveTrustedDevice(TrustedDevice device);

    ContextSelectionToken saveContextSelectionToken(ContextSelectionToken token);

    Optional<ContextSelectionToken> findContextSelectionTokenByHash(String tokenHash);

    boolean consumeContextSelectionTokenIfAvailable(UUID tokenId, Instant consumedAt);

    void markContextSelectionTokenRevoked(UUID tokenId, Instant revokedAt);

    void acquireDeviceAdvisoryLock(UUID userId, UUID deviceIdentifier);

    /**
     * Transaction-scoped advisory lock keyed on (actorUserId, clientMutationId) so that concurrent
     * requests carrying the same idempotency logical key are serialized before either one reads or
     * writes the idempotency_keys row, closing the check-then-insert race.
     */
    void acquireIdempotencyAdvisoryLock(UUID actorUserId, String clientMutationId);

    Optional<IdempotencyKey> insertIdempotencyKeyOrFindExisting(IdempotencyKey key);

    List<OrganizationMembership> findActiveOrganizationMembershipsByUserId(UUID userId);

    Optional<OrganizationMembership> findOrganizationMembershipByIdAndUserId(UUID membershipId, UUID userId);

    List<OrganizationMembershipRole> findActiveRolesByMembershipId(UUID membershipId);

    Set<String> findRoleCodesByMembershipId(UUID membershipId);

    RefreshTokenFamily saveRefreshTokenFamily(RefreshTokenFamily family);

    RefreshToken saveRefreshToken(RefreshToken token);

    Optional<RefreshToken> findRefreshTokenByHash(String tokenHash);

    Optional<RefreshToken> findRefreshTokenByAccessTokenHash(String accessTokenHash);

    Optional<RefreshTokenFamily> findRefreshTokenFamilyById(UUID familyId);

    Optional<AuthSession> findAuthSessionByAccessTokenHash(String accessTokenHash);

    Optional<IdempotencyKey> findIdempotencyKey(UUID userId, String clientMutationId, IdempotencyScope scope, OperationCode operationCode);

    IdempotencyKey saveIdempotencyKey(IdempotencyKey key);

    void completeIdempotencyKey(UUID idempotencyKeyId, short httpStatus, String resultReference, Instant completedAt, Instant resultExpiresAt);

    void failIdempotencyKey(UUID idempotencyKeyId, short httpStatus, String errorCode, Instant completedAt);

    void saveAuthReplayEscrow(org.mepcity.kursplatform.iam.domain.AuthReplayEscrow escrow);

    Optional<org.mepcity.kursplatform.iam.domain.AuthReplayEscrow> findAuthReplayEscrowByIdempotencyKeyId(UUID idempotencyKeyId);

    void revokeAllActorFamilies(UUID actorUserId, OperationCode operationCode, Instant now);

    void elevateUserReauthenticationRequiredAfter(UUID userId, Instant now);

    ProviderCommand saveProviderCommand(ProviderCommand command);

    Optional<ProviderCommand> findProviderCommandById(UUID commandId);

    Optional<ProviderCommand> claimProviderCommandAtomically(UUID commandId, String workerId, Instant leaseExpiresAt, Instant now);

    Optional<ProviderCommand> reclaimExpiredLeaseAtomically(UUID commandId, String workerId, Instant leaseExpiresAt, Instant now);

    Optional<ProviderCommand> completeProviderCommandAtomically(UUID commandId, String workerId, long fencingToken, boolean success, String safeErrorCode, Instant completedAt);

    /**
     * Releases a CLAIMED lease back to PENDING so a retryable failure can be retried after bounded
     * exponential backoff. Atomically verifies the SAME worker+fencing that currently holds the
     * CLAIMED lease (so a lease lost to a concurrent reclaim mid-call silently no-ops rather than
     * clobbering the new owner), clears the lease fields, resets {@code fencing_token} to 0 (the
     * value trg_ipc_state requires for a fresh PENDING row) and sets {@code next_attempt_at} to
     * the supplied backoff time. RLS-gated by the {@code ipc_retry} policy (V9), which additionally
     * requires {@code app.iam_target_provider_command_id} to match the row id — set only by the
     * repository itself from the id the caller already holds, never from external input.
     */
    Optional<ProviderCommand> requeueClaimedForRetry(UUID commandId, String workerId, long fencingToken,
                                                    Instant nextAttemptAt, Instant now);

    /**
     * Returns the ids of PENDING provider commands the worker should attempt next, in {@code
     * next_attempt_at} order, capped at {@code limit}. Filters to the worker-supported allow-list
     * ({@code USER_DISABLE}, {@code USER_LOGOUT}) at the SQL level so PASSWORD_RESET /
     * TEACHER_ACCOUNT_CREATE rows — which can only exist via a hand-insert, since createCommand
     * rejects them — are never even considered. RLS hides rows whose {@code next_attempt_at} is
     * still in the future or that another worker currently holds a valid CLAIMED lease on.
     */
    List<UUID> findNextClaimableProviderCommandIds(int limit, Instant now);

    Optional<ProviderCommand> findProviderCommandByIdempotencyKey(String idempotencyKey);

    List<ContextSelectionSummary> findContextSelectionSummaries(UUID userId);
}
