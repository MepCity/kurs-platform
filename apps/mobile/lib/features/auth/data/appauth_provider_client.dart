import 'package:flutter_appauth/flutter_appauth.dart';

import '../../../core/config/app_runtime_config.dart';
import '../domain/authentication_repository.dart';

abstract interface class ProviderAuthorizationClient {
  Future<String> authorize();
}

class AppAuthProviderAuthorizationClient
    implements ProviderAuthorizationClient {
  AppAuthProviderAuthorizationClient({
    required AppRuntimeConfig config,
    FlutterAppAuth? appAuth,
    // Public composition uses descriptive names; private initializing formals
    // would make those named arguments inaccessible outside this library.
    // ignore: prefer_initializing_formals
  }) : _config = config,
       _appAuth = appAuth ?? const FlutterAppAuth();

  final AppRuntimeConfig _config;
  final FlutterAppAuth _appAuth;

  @override
  Future<String> authorize() async {
    try {
      final response = await _appAuth.authorizeAndExchangeCode(
        AuthorizationTokenRequest(
          _config.cognitoClientId,
          _config.cognitoRedirectUri.toString(),
          issuer: _config.cognitoIssuerUri.toString(),
          scopes: const <String>['openid'],
          promptValues: const <String>['login'],
          externalUserAgent:
              ExternalUserAgent.ephemeralAsWebAuthenticationSession,
        ),
      );
      final token = response.accessToken;
      if (token == null || token.trim().isEmpty) throw const FormatException();
      return token;
    } on FlutterAppAuthUserCancelledException {
      throw const AuthenticationFailure(
        AuthenticationFailureCode.cancelled,
        'Giriş iptal edildi.',
      );
    } on AuthenticationFailure {
      rethrow;
    } on Object {
      throw const AuthenticationFailure(
        AuthenticationFailureCode.providerUnavailable,
        'Kimlik sağlayıcısına şu anda ulaşılamıyor.',
      );
    }
  }
}
