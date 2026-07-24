/// Opaque platform tokens and their verified context, kept outside presentation.
///
/// Values of this type must never be included in logs, diagnostics, error
/// messages, navigation state, or normal local application storage.
enum SecureSessionScope { organization, globalPlatformAdministrator }

class SecureSession {
  SecureSession({
    required this.userId,
    required this.deviceId,
    required this.scope,
    required this.accessToken,
    required this.refreshToken,
    required this.expiresAt,
    required this.refreshExpiresAt,
    required this.authenticatedAt,
    this.organizationMembershipId,
    this.organizationId,
    this.sessionGeneration,
  }) {
    if (userId.trim().isEmpty ||
        deviceId.trim().isEmpty ||
        accessToken.trim().isEmpty ||
        refreshToken.trim().isEmpty) {
      throw ArgumentError('Güvenli oturum için zorunlu alan eksik.');
    }
    final organizationScope = scope == SecureSessionScope.organization;
    final hasOrganizationContext =
        organizationMembershipId != null && organizationId != null;
    if (organizationScope != hasOrganizationContext ||
        (!organizationScope &&
            (organizationMembershipId != null || organizationId != null)) ||
        (organizationMembershipId?.trim().isEmpty ?? false) ||
        (organizationId?.trim().isEmpty ?? false)) {
      throw ArgumentError('Oturum kapsamı bağlam alanlarıyla uyuşmuyor.');
    }
    if ((organizationScope && sessionGeneration == null) ||
        (!organizationScope && sessionGeneration != null) ||
        (sessionGeneration != null && sessionGeneration! < 0) ||
        authenticatedAt.isAfter(expiresAt) ||
        expiresAt.isAfter(refreshExpiresAt)) {
      throw ArgumentError('Geçersiz oturum nesli.');
    }
  }

  final String userId;
  final String deviceId;
  final SecureSessionScope scope;
  final String accessToken;
  final String refreshToken;
  final DateTime expiresAt;
  final DateTime refreshExpiresAt;
  final DateTime authenticatedAt;
  final String? organizationMembershipId;
  final String? organizationId;
  final int? sessionGeneration;
}

class SecureSessionWriteLease {
  const SecureSessionWriteLease(this.value);
  final int value;
}

/// Domain port for the one active platform session on this installation.
///
/// A stored value is only a refresh candidate. Bootstrap must still validate it
/// with IAM before opening an authenticated workspace.
abstract interface class SecureSessionStore {
  Future<SecureSession?> read();

  /// Invalidates the previously readable session before a new authentication
  /// attempt starts. Only the most recently obtained lease may commit.
  Future<SecureSessionWriteLease> beginActivation();

  /// Returns false when a newer attempt has superseded [lease].
  Future<bool> commit(SecureSessionWriteLease lease, SecureSession session);

  /// Invalidates a pending lease without making a token-bearing write.
  void abandonActivation(SecureSessionWriteLease lease);

  Future<void> clear();
}

enum SecureSessionStoreFailureReason { unavailable, corrupted }

/// Deliberately contains no underlying exception or persisted value, so a
/// caller cannot accidentally log an opaque platform token.
class SecureSessionStoreFailure implements Exception {
  const SecureSessionStoreFailure(this.reason);
  final SecureSessionStoreFailureReason reason;
}
