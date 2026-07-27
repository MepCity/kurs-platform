import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';

class TestAuthenticationRepository implements AuthenticationRepository {
  const TestAuthenticationRepository();

  @override
  Future<AuthContextChoices> beginSignIn() => throw UnimplementedError();

  @override
  Future<AuthenticatedSessionActivation> activateOrganization(String id) =>
      throw UnimplementedError();

  @override
  Future<AuthenticatedSessionActivation> activatePlatformAdministrator() =>
      throw UnimplementedError();
}
