import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';

const _contractTestEnabled = bool.fromEnvironment(
  'KURS_PLATFORM_ALPHA_RUNTIME_CONTRACT_TEST',
);

void main() {
  test('ALPHA-001 dart defines are accepted by AppRuntimeConfig', () {
    if (!_contractTestEnabled) {
      expect(
        const String.fromEnvironment('KURS_PLATFORM_ENVIRONMENT'),
        isEmpty,
        reason:
            'The real-value contract is exercised by its dedicated CI step.',
      );
      return;
    }

    final config = AppRuntimeConfig.fromEnvironment();

    expect(config.environment, AppEnvironment.development);
    expect(
      config.publicApiBaseUrl.toString(),
      'https://kurs-platform-alpha-api-development.onrender.com',
    );
    expect(
      config.cognitoIssuerUri.toString(),
      'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_1GH5JivoG',
    );
    expect(config.cognitoClientId, '2c59dh2nf60fmk6chn6qq3eoqu');
    expect(
      config.cognitoRedirectUri.toString(),
      'kursplatform://oauth2redirect',
    );
  });
}
