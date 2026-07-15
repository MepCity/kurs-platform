import 'package:flutter/material.dart';
import 'package:kurs_platform_mobile/core/observability/app_observability.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';

void main() {
  AppObservability(
    logger: const DebugSafeEventLogger(),
    errorReporter: const NoopErrorReporter(),
  ).run(() => runApp(const KursPlatformApp()));
}
