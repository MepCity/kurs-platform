import 'package:flutter_appauth/flutter_appauth.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/features/auth/data/appauth_provider_client.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';

class _FakeAppAuth extends FlutterAppAuth {
  _FakeAppAuth(this.result);

  Object result;
  AuthorizationTokenRequest? request;

  @override
  Future<AuthorizationTokenResponse> authorizeAndExchangeCode(
    AuthorizationTokenRequest request,
  ) async {
    this.request = request;
    if (result is AuthorizationTokenResponse) {
      return result as AuthorizationTokenResponse;
    }
    throw result;
  }
}

final _config = AppRuntimeConfig.fromValues(
  environment: 'production',
  publicApiBaseUrl: 'https://api.example.invalid',
  cognitoIssuerUri:
      'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
  cognitoClientId: 'public-client',
);

AuthorizationTokenResponse _response(String? accessToken) =>
    AuthorizationTokenResponse(
      accessToken,
      'provider-refresh-must-be-discarded',
      DateTime.utc(2026, 7, 27, 10),
      'provider-id-must-be-discarded',
      'Bearer',
      const <String>['openid', 'profile'],
      null,
      null,
    );

void main() {
  test(
    'uses system AppAuth code exchange with the production OIDC config',
    () async {
      final appAuth = _FakeAppAuth(_response('provider-access'));
      final client = AppAuthProviderAuthorizationClient(
        config: _config,
        appAuth: appAuth,
      );

      expect(await client.authorize(), 'provider-access');
      expect(appAuth.request?.clientId, 'public-client');
      expect(appAuth.request?.redirectUrl, 'kursplatform://oauth2redirect');
      expect(appAuth.request?.issuer, _config.cognitoIssuerUri.toString());
      expect(appAuth.request?.grantType, GrantType.authorizationCode);
      expect(appAuth.request?.scopes, <String>['openid', 'profile']);
      expect(
        appAuth.request?.externalUserAgent,
        ExternalUserAgent.ephemeralAsWebAuthenticationSession,
      );
      expect(appAuth.request?.clientSecret, isNull);
    },
  );

  test('cancel, malformed response and provider error fail closed', () async {
    final cancelled = _FakeAppAuth(
      FlutterAppAuthUserCancelledException(
        code: 'user_cancelled',
        platformErrorDetails: FlutterAppAuthPlatformErrorDetails(
          error: 'user_cancelled',
        ),
      ),
    );
    await expectLater(
      AppAuthProviderAuthorizationClient(
        config: _config,
        appAuth: cancelled,
      ).authorize(),
      throwsA(
        isA<AuthenticationFailure>().having(
          (failure) => failure.code,
          'code',
          AuthenticationFailureCode.cancelled,
        ),
      ),
    );

    for (final result in <Object>[_response(null), StateError('provider')]) {
      await expectLater(
        AppAuthProviderAuthorizationClient(
          config: _config,
          appAuth: _FakeAppAuth(result),
        ).authorize(),
        throwsA(
          isA<AuthenticationFailure>().having(
            (failure) => failure.code,
            'code',
            AuthenticationFailureCode.providerUnavailable,
          ),
        ),
      );
    }
  });
}
