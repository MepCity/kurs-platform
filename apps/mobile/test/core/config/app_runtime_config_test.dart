import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';

void main() {
  test('fromEnvironment fails when dart defines are missing', () {
    expect(
      AppRuntimeConfig.fromEnvironment,
      throwsA(isA<AppConfigException>()),
    );
  });

  test('accepts synthetic development config', () {
    final config = AppRuntimeConfig.fromValues(
      environment: 'development',
      publicApiBaseUrl: 'https://api-development.example.invalid',
      cognitoIssuerUri:
          'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
      cognitoClientId: 'examplepublicclientid',
    );

    expect(config.environment, AppEnvironment.development);
    expect(config.publicApiBaseUrl.host, 'api-development.example.invalid');
  });

  test('rejects short environment alias', () {
    expect(
      () => AppRuntimeConfig.fromValues(
        environment: 'prod',
        publicApiBaseUrl: 'https://api.example.invalid',
        cognitoIssuerUri:
            'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
        cognitoClientId: 'examplepublicclientid',
      ),
      throwsA(isA<AppConfigException>()),
    );
  });

  test('rejects production http url', () {
    expect(
      () => AppRuntimeConfig.fromValues(
        environment: 'production',
        publicApiBaseUrl: 'http://api.example.invalid',
        cognitoIssuerUri:
            'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
        cognitoClientId: 'examplepublicclientid',
      ),
      throwsA(isA<AppConfigException>()),
    );
  });

  test('allows only local http in development', () {
    final config = AppRuntimeConfig.fromValues(
      environment: 'development',
      publicApiBaseUrl: 'http://localhost:8080',
      cognitoIssuerUri:
          'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
      cognitoClientId: 'examplepublicclientid',
    );

    expect(config.publicApiBaseUrl.host, 'localhost');
  });

  test('does not model backend secret references on mobile', () {
    final fields = AppRuntimeConfig.fromValues(
      environment: 'staging',
      publicApiBaseUrl: 'https://api-staging.example.invalid',
      cognitoIssuerUri:
          'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
      cognitoClientId: 'examplepublicclientid',
    ).toString();

    expect(fields, isNot(contains('DATABASE_URL_SECRET_REF')));
    expect(fields, isNot(contains('TOKEN_PEPPER_SECRET_REF')));
  });
}
