import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';

import 'safe_log_event.dart';

abstract interface class SafeEventLogger {
  void log(SafeLogEvent event);
}

abstract interface class ErrorReporter {
  Future<void> capture(SafeLogEvent event);
}

final class NoopErrorReporter implements ErrorReporter {
  const NoopErrorReporter();

  @override
  Future<void> capture(SafeLogEvent event) async {}
}

/// Development adapter. It prints only the typed [SafeLogEvent] shape.
final class DebugSafeEventLogger implements SafeEventLogger {
  const DebugSafeEventLogger();

  @override
  void log(SafeLogEvent event) {
    if (kDebugMode) {
      debugPrint(jsonEncode(event.toJson()));
    }
  }
}

/// Connects framework, platform and zone failures without changing their behavior.
final class AppObservability {
  AppObservability({
    required this.logger,
    required this.errorReporter,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final SafeEventLogger logger;
  final ErrorReporter errorReporter;
  final DateTime Function() _now;

  void run(void Function() application) {
    final previousFlutterHandler = FlutterError.onError;
    final previousPlatformHandler = PlatformDispatcher.instance.onError;
    final parentZone = Zone.current;

    FlutterError.onError = (details) {
      _record(
        operation: SafeLogOperation.flutterFramework,
        error: details.exception,
        severity: SafeLogSeverity.fatal,
      );
      if (previousFlutterHandler != null) {
        previousFlutterHandler(details);
      } else {
        FlutterError.presentError(details);
      }
    };
    PlatformDispatcher.instance.onError = (error, stack) {
      _record(
        operation: SafeLogOperation.flutterPlatform,
        error: error,
        severity: SafeLogSeverity.fatal,
      );
      return previousPlatformHandler?.call(error, stack) ?? false;
    };
    runZonedGuarded(application, (error, stack) {
      _record(
        operation: SafeLogOperation.dartZone,
        error: error,
        severity: SafeLogSeverity.fatal,
      );
      parentZone.handleUncaughtError(error, stack);
    });
  }

  void _record({
    required SafeLogOperation operation,
    required Object error,
    required SafeLogSeverity severity,
  }) {
    SafeLogEvent event;
    try {
      event = SafeLogEvent.unexpectedApplicationError(
        occurredAt: _now(),
        operation: operation,
        error: error,
        severity: severity,
      );
    } on Object {
      return;
    }

    try {
      logger.log(event);
    } on Object {
      // A logger failure cannot become a new application or telemetry failure.
    }
    unawaited(_captureWithoutAffectingApplication(event));
  }

  Future<void> _captureWithoutAffectingApplication(SafeLogEvent event) async {
    try {
      await errorReporter.capture(event);
    } on Object {
      // Reporter failure cannot change product behavior or recurse into telemetry.
    }
  }
}
