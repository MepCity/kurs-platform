package org.mepcity.kursplatform.iam.application;

import org.mepcity.kursplatform.iam.domain.OperationCode;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

public interface IamTransactionExecutor {

    <T> T executeInAuthenticationScope(UUID actorUserId, String issuer, String subject, Supplier<T> action);

    <T> T executeInIamAuthScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action);

    <T> T executeInGlobalScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action);

    /** IAM_PROVISIONING scope: TEACHER_ACCOUNT_CREATE / TEACHER_ACCOUNT_FINALIZE provider commands. */
    <T> T executeInProvisioningScope(OperationCode operationCode, IamAuthScopeContext context, Supplier<T> action);

    /**
     * Applies additional {@code SET LOCAL app.*} variables to the connection bound to the
     * currently active {@code executeInIamAuthScope}/{@code executeInGlobalScope} transaction
     * on this thread, without opening a new transaction. Used to progressively reveal identity
     * (actor/device/membership) learned mid-transaction from a bootstrap read (e.g. a context
     * token looked up by hash) so later statements in the SAME transaction can rely on RLS
     * policies keyed on that identity. Throws IllegalStateException if called outside an active
     * IAM_AUTH/GLOBAL scope on this thread.
     */
    void refreshIamAuthScope(IamAuthScopeContext context);

    record IamAuthScopeContext(
            UUID actorUserId,
            UUID currentTrustedDeviceId,
            UUID currentFamilyId,
            UUID targetMembershipId,
            UUID targetOrganizationId,
            UUID targetDeviceId,
            UUID targetUserId,
            UUID targetIdentityId,
            UUID providerDeviceIdentifier,
            Instant verifiedAuthTime,
            String contextTokenHash,
            String accessTokenHash
    ) {
        public static IamAuthScopeContext actorOnly(UUID actorUserId) {
            return new IamAuthScopeContext(actorUserId, null, null, null, null, null, null, null, null, null, null, null);
        }

        public static IamAuthScopeContext bootstrapContextToken(String contextTokenHash) {
            return new IamAuthScopeContext(null, null, null, null, null, null, null, null, null, null, contextTokenHash, null);
        }

        public static IamAuthScopeContext bootstrapAccessToken(String accessTokenHash) {
            return new IamAuthScopeContext(null, null, null, null, null, null, null, null, null, null, null, accessTokenHash);
        }

        public static IamAuthScopeContext bootstrapFamily(UUID familyId) {
            return new IamAuthScopeContext(null, null, familyId, null, null, null, null, null, null, null, null, null);
        }

        public IamAuthScopeContext withDevice(UUID trustedDeviceId) {
            return new IamAuthScopeContext(actorUserId, trustedDeviceId, currentFamilyId,
                    targetMembershipId, targetOrganizationId, targetDeviceId, targetUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }

        public IamAuthScopeContext withFamily(UUID familyId) {
            return new IamAuthScopeContext(actorUserId, currentTrustedDeviceId, familyId,
                    targetMembershipId, targetOrganizationId, targetDeviceId, targetUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }

        public IamAuthScopeContext withProviderDevice(UUID providerDeviceIdentifier, Instant verifiedAuthTime) {
            return new IamAuthScopeContext(actorUserId, currentTrustedDeviceId, currentFamilyId,
                    targetMembershipId, targetOrganizationId, targetDeviceId, targetUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }

        public IamAuthScopeContext withMembership(UUID membershipId, UUID organizationId) {
            return new IamAuthScopeContext(actorUserId, currentTrustedDeviceId, currentFamilyId,
                    membershipId, organizationId, targetDeviceId, targetUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }

        /** Actor and device now known; used with refreshIamAuthScope after a bootstrap read. */
        public static IamAuthScopeContext actorAndDevice(UUID actorUserId, UUID trustedDeviceId) {
            return new IamAuthScopeContext(actorUserId, trustedDeviceId, null, null, null, null, null, null, null, null, null, null);
        }

        /**
         * GLOBAL-scope provider-command lifecycle target (USER_DISABLE/USER_LOGOUT/PASSWORD_RESET).
         * Both vars are required: iam_provider_commands_select_global/insert_global match on
         * target_user_id directly OR via a user_identities join keyed on BOTH
         * app.iam_target_identity_id and app.iam_target_user_id — the row itself never stores
         * target_user_id for these command types (CHECK constraint), so the resolving user id is
         * scope-only, supplied by the caller who already knows which user owns this identity.
         */
        public IamAuthScopeContext withTargetIdentity(UUID targetIdentityId, UUID resolvingUserId) {
            return new IamAuthScopeContext(actorUserId, currentTrustedDeviceId, currentFamilyId,
                    targetMembershipId, targetOrganizationId, targetDeviceId, resolvingUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }

        /** IAM_PROVISIONING-scope provider-command target (TEACHER_ACCOUNT_CREATE/_FINALIZE). */
        public IamAuthScopeContext withTargetUserAndOrganization(UUID targetUserId, UUID organizationId) {
            return new IamAuthScopeContext(actorUserId, currentTrustedDeviceId, currentFamilyId,
                    targetMembershipId, organizationId, targetDeviceId, targetUserId,
                    targetIdentityId, providerDeviceIdentifier, verifiedAuthTime, contextTokenHash, accessTokenHash);
        }
    }
}
