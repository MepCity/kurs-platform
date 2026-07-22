/// IAM-001 mobile authentication boundary.
///
/// Implementations start the system-browser OIDC Code + PKCE flow, exchange
/// only the provider access token with IAM, and activate one server-provided
/// context. They must not persist provider or platform tokens; IAM-008 owns
/// secure session storage.
abstract interface class AuthenticationRepository {
  Future<AuthContextChoices> beginSignIn();

  Future<ActivatedSession> activateOrganization(String membershipId);

  Future<ActivatedSession> activatePlatformAdministrator();
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
