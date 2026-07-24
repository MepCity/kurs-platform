import 'secure_session_store.dart';

/// IAM-001 mobile authentication boundary.
///
/// Implementations start the system-browser OIDC Code + PKCE flow, exchange
/// only the provider access token with IAM, and activate one server-provided
/// context. Provider tokens are discarded after that exchange. The resulting
/// platform session is handed to the application layer, which persists it via
/// the IAM-008 secure-session port without exposing its secrets to UI.
abstract interface class AuthenticationRepository {
  Future<AuthContextChoices> beginSignIn();

  Future<AuthenticatedSessionActivation> activateOrganization(
    String membershipId,
  );

  Future<AuthenticatedSessionActivation> activatePlatformAdministrator();
}

class AuthContextChoices {
  const AuthContextChoices({
    required this.displayName,
    required this.memberships,
    required this.canActivatePlatformAdministrator,
  });

  final String displayName;
  final List<AuthOrganizationMembership> memberships;
  final bool canActivatePlatformAdministrator;

  int get selectableCount =>
      memberships.length + (canActivatePlatformAdministrator ? 1 : 0);
}

class AuthOrganizationMembership {
  const AuthOrganizationMembership({
    required this.id,
    required this.organizationName,
    required this.roleCodes,
  });

  final String id;
  final String organizationName;
  final List<String> roleCodes;
}

enum ActivatedSessionScope { organization, globalPlatformAdministrator }

class ActivatedSession {
  const ActivatedSession({
    required this.scope,
    required this.displayName,
    this.organizationMembership,
  });

  final ActivatedSessionScope scope;
  final String displayName;
  final AuthOrganizationMembership? organizationMembership;
}

/// The activation result before the application layer persists its opaque
/// platform tokens. Presentation consumes only [session].
class AuthenticatedSessionActivation {
  AuthenticatedSessionActivation({
    required this.session,
    required this.secureSession,
  }) {
    final expectedScope = session.scope == ActivatedSessionScope.organization
        ? SecureSessionScope.organization
        : SecureSessionScope.globalPlatformAdministrator;
    if (secureSession.scope != expectedScope) {
      throw ArgumentError('Aktivasyon ve güvenli oturum kapsamı uyuşmuyor.');
    }
  }

  final ActivatedSession session;
  final SecureSession secureSession;
}

enum AuthenticationFailureCode {
  cancelled,
  unauthenticated,
  accountNotReady,
  reauthenticationRequired,
  stateConflict,
  providerUnavailable,
  rateLimited,
  unavailable,
}

class AuthenticationFailure implements Exception {
  const AuthenticationFailure(this.code, this.message);

  final AuthenticationFailureCode code;
  final String message;
}
