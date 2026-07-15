import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/observability/app_observability.dart';
import 'package:kurs_platform_mobile/core/observability/safe_log_event.dart';

void main() {
  late FlutterExceptionHandler? originalFlutterHandler;
  late bool Function(Object, StackTrace)? originalPlatformHandler;

  setUp(() {
    originalFlutterHandler = FlutterError.onError;
    originalPlatformHandler = PlatformDispatcher.instance.onError;
  });

  tearDown(() {
    FlutterError.onError = originalFlutterHandler;
    PlatformDispatcher.instance.onError = originalPlatformHandler;
  });

  test('zone failure is recorded and forwarded to its parent zone', () async {
    final logger = _RecordingLogger();
    final reporter = _RecordingReporter();
    final observability = _observability(logger, reporter);
    final original = StateError('telefon=05551234567 token=secret');

    final forwarded = _runAndCaptureParentZone(observability, original);
    await Future<void>.delayed(Duration.zero);

    expect(forwarded, same(original));
    expect(logger.events, hasLength(1));
    expect(reporter.events, hasLength(1));
    expect(logger.events.single.toJson()['errorType'], 'StateError');
    expect(
      logger.events.single.toJson().values,
      isNot(contains('telefon=05551234567 token=secret')),
    );
  });

  test('logger failure does not recurse or replace zone failure', () async {
    final logger = _FailingLogger();
    final reporter = _RecordingReporter();
    final observability = _observability(logger, reporter);
    final original = StateError('product failure');

    final forwarded = _runAndCaptureParentZone(observability, original);
    await Future<void>.delayed(Duration.zero);

    expect(forwarded, same(original));
    expect(logger.calls, 1);
    expect(reporter.events, hasLength(1));
  });

  test('reporter failure does not change zone behavior', () async {
    final logger = _RecordingLogger();
    final reporter = _FailingReporter();
    final observability = _observability(logger, reporter);
    final original = StateError('product failure');

    final forwarded = _runAndCaptureParentZone(observability, original);
    await Future<void>.delayed(Duration.zero);
    await Future<void>.delayed(Duration.zero);

    expect(forwarded, same(original));
    expect(logger.events, hasLength(1));
    expect(reporter.calls, 1);
  });

  test('existing Flutter error handler is preserved after telemetry', () {
    FlutterErrorDetails? presented;
    FlutterError.onError = (details) => presented = details;
    final logger = _FailingLogger();
    final observability = _observability(logger, _RecordingReporter());
    observability.run(() {});
    final details = FlutterErrorDetails(exception: StateError('framework'));

    FlutterError.onError!(details);

    expect(presented, same(details));
    expect(logger.calls, 1);
  });

  test('existing platform handler result is preserved after telemetry', () {
    Object? presented;
    PlatformDispatcher.instance.onError = (error, stack) {
      presented = error;
      return true;
    };
    final logger = _FailingLogger();
    final observability = _observability(logger, _RecordingReporter());
    observability.run(() {});
    final original = StateError('platform');

    final handled = PlatformDispatcher.instance.onError!(
      original,
      StackTrace.current,
    );

    expect(handled, isTrue);
    expect(presented, same(original));
    expect(logger.calls, 1);
  });

  test('platform errors remain unhandled when there was no prior handler', () {
    PlatformDispatcher.instance.onError = null;
    final observability = _observability(
      _RecordingLogger(),
      _RecordingReporter(),
    );
    observability.run(() {});

    final handled = PlatformDispatcher.instance.onError!(
      StateError('platform'),
      StackTrace.current,
    );

    expect(handled, isFalse);
  });
}

AppObservability _observability(
  SafeEventLogger logger,
  ErrorReporter reporter,
) => AppObservability(
  logger: logger,
  errorReporter: reporter,
  now: () => DateTime.utc(2026, 7, 15, 10),
);

Object? _runAndCaptureParentZone(
  AppObservability observability,
  Object original,
) {
  Object? forwarded;
  runZonedGuarded(
    () => observability.run(() => throw original),
    (error, stack) => forwarded = error,
  );
  return forwarded;
}

final class _RecordingLogger implements SafeEventLogger {
  final events = <SafeLogEvent>[];

  @override
  void log(SafeLogEvent event) => events.add(event);
}

final class _FailingLogger implements SafeEventLogger {
  int calls = 0;

  @override
  void log(SafeLogEvent event) {
    calls++;
    throw StateError('logger unavailable');
  }
}

final class _RecordingReporter implements ErrorReporter {
  final events = <SafeLogEvent>[];

  @override
  Future<void> capture(SafeLogEvent event) async => events.add(event);
}

final class _FailingReporter implements ErrorReporter {
  int calls = 0;

  @override
  Future<void> capture(SafeLogEvent event) async {
    calls++;
    throw StateError('reporter unavailable');
  }
}
