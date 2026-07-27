import 'package:flutter/material.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/core/observability/app_observability.dart';
import 'package:kurs_platform_mobile/features/auth/data/appauth_provider_client.dart';
import 'package:kurs_platform_mobile/features/auth/data/device_identity.dart';
import 'package:kurs_platform_mobile/features/auth/data/flutter_secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/data/iam_http_client.dart';
import 'package:kurs_platform_mobile/features/auth/data/production_iam_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  final config = AppRuntimeConfig.fromEnvironment();
  final sessionStore = FlutterSecureSessionStore();
  final repository = ProductionIamRepository(
    provider: AppAuthProviderAuthorizationClient(config: config),
    client: IamHttpClient(config: config),
    deviceIdentity: InstallationDeviceIdentity(sessionStore),
  );
  AppObservability(
    logger: const DebugSafeEventLogger(),
    errorReporter: const NoopErrorReporter(),
  ).run(
    () => runApp(
      KursPlatformApp(
        authenticationRepository: repository,
        sessionRepository: repository,
        secureSessionStore: sessionStore,
      ),
    ),
  );
}
