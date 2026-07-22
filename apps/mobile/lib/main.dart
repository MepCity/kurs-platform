import 'package:flutter/material.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/core/observability/app_observability.dart';
import 'package:kurs_platform_mobile/features/auth/data/unavailable_authentication_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';

void main() {
  AppRuntimeConfig.fromEnvironment();
  AppObservability(
    logger: const DebugSafeEventLogger(),
    errorReporter: const NoopErrorReporter(),
  ).run(
    () => runApp(
      const KursPlatformApp(
        authenticationRepository: UnavailableAuthenticationRepository(),
      ),
    ),
  );
}
