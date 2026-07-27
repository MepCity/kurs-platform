enum AppEnvironment {
  development('development'),
  staging('staging'),
  production('production');

  const AppEnvironment(this.externalName);

  final String externalName;

  static AppEnvironment parse(String value) {
    for (final environment in values) {
      if (environment.externalName == value) {
        return environment;
      }
    }
    throw const AppConfigException(
      'KURS_PLATFORM_ENVIRONMENT must be development, staging or production',
    );
  }
}

class AppRuntimeConfig {
  const AppRuntimeConfig({
    required this.environment,
    required this.publicApiBaseUrl,
    required this.cognitoIssuerUri,
    required this.cognitoClientId,
    required this.cognitoRedirectUri,
  });

  factory AppRuntimeConfig.fromEnvironment() {
    return AppRuntimeConfig.fromValues(
      environment: const String.fromEnvironment('KURS_PLATFORM_ENVIRONMENT'),
      publicApiBaseUrl: const String.fromEnvironment(
        'KURS_PLATFORM_PUBLIC_API_BASE_URL',
      ),
      cognitoIssuerUri: const String.fromEnvironment(
        'KURS_PLATFORM_COGNITO_ISSUER_URI',
      ),
      cognitoClientId: const String.fromEnvironment(
        'KURS_PLATFORM_COGNITO_CLIENT_ID',
      ),
      cognitoRedirectUri: const String.fromEnvironment(
        'KURS_PLATFORM_COGNITO_REDIRECT_URI',
      ),
    );
  }

  factory AppRuntimeConfig.fromValues({
    required String environment,
    required String publicApiBaseUrl,
    required String cognitoIssuerUri,
    required String cognitoClientId,
    String cognitoRedirectUri = 'kursplatform://oauth2redirect',
  }) {
    final parsedEnvironment = AppEnvironment.parse(
      _required('KURS_PLATFORM_ENVIRONMENT', environment),
    );
    final apiBase = _parsePublicUri(
      'KURS_PLATFORM_PUBLIC_API_BASE_URL',
      publicApiBaseUrl,
      parsedEnvironment,
    );
    final issuer = _parsePublicUri(
      'KURS_PLATFORM_COGNITO_ISSUER_URI',
      cognitoIssuerUri,
      parsedEnvironment,
    );
    final redirect = Uri.tryParse(
      _required('KURS_PLATFORM_COGNITO_REDIRECT_URI', cognitoRedirectUri),
    );
    if (redirect == null ||
        redirect.scheme != 'kursplatform' ||
        redirect.host != 'oauth2redirect' ||
        redirect.hasQuery ||
        redirect.hasFragment ||
        redirect.path.isNotEmpty) {
      throw const AppConfigException(
        'KURS_PLATFORM_COGNITO_REDIRECT_URI must be '
        'kursplatform://oauth2redirect',
      );
    }
    return AppRuntimeConfig(
      environment: parsedEnvironment,
      publicApiBaseUrl: apiBase,
      cognitoIssuerUri: issuer,
      cognitoClientId: _required(
        'KURS_PLATFORM_COGNITO_CLIENT_ID',
        cognitoClientId,
      ),
      cognitoRedirectUri: redirect,
    );
  }

  final AppEnvironment environment;
  final Uri publicApiBaseUrl;
  final Uri cognitoIssuerUri;
  final String cognitoClientId;
  final Uri cognitoRedirectUri;

  static String _required(String key, String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) {
      throw AppConfigException('$key is required');
    }
    return trimmed;
  }

  static Uri _parsePublicUri(
    String key,
    String value,
    AppEnvironment environment,
  ) {
    final parsed = Uri.tryParse(_required(key, value));
    if (parsed == null || !parsed.hasScheme || parsed.host.isEmpty) {
      throw AppConfigException('$key must include scheme and host');
    }
    if (parsed.scheme == 'https') {
      return parsed;
    }
    final localDevelopmentHttp =
        environment == AppEnvironment.development &&
        parsed.scheme == 'http' &&
        (parsed.host == 'localhost' ||
            parsed.host == '127.0.0.1' ||
            parsed.host == '::1');
    if (!localDevelopmentHttp) {
      throw AppConfigException('$key must use HTTPS outside local development');
    }
    return parsed;
  }
}

class AppConfigException implements Exception {
  const AppConfigException(this.message);

  final String message;

  @override
  String toString() => message;
}
