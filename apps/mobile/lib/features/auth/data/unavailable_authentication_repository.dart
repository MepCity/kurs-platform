import '../domain/authentication_repository.dart';

/// Composition-root placeholder until the OIDC/API adapter is wired with its
/// deployment redirect URI. It intentionally never simulates a login.
class UnavailableAuthenticationRepository implements AuthenticationRepository {
  const UnavailableAuthenticationRepository();

  static const _failure = AuthenticationFailure(
    AuthenticationFailureCode.unavailable,
    'Giriş hizmeti bu uygulama yapılandırmasında henüz kullanıma hazır değil.',
  );

  @override
  Future<ActivatedSession> activateOrganization(String membershipId) =>
      Future<ActivatedSession>.error(_failure);

  @override
  Future<ActivatedSession> activatePlatformAdministrator() =>
      Future<ActivatedSession>.error(_failure);

  @override
  Future<AuthContextChoices> beginSignIn() =>
      Future<AuthContextChoices>.error(_failure);
}
