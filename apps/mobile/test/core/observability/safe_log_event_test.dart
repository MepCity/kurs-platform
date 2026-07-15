import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/observability/safe_log_event.dart';

void main() {
  final now = DateTime.utc(2026, 7, 15, 10);

  test('only typed factories are externally available', () {
    final source = File(
      'lib/core/observability/safe_log_event.dart',
    ).readAsStringSync();

    expect(source, isNot(contains('SafeLogEvent({')));
    expect(source, contains('SafeLogEvent._({'));
    expect(RegExp(r'factory SafeLogEvent\.').allMatches(source), hasLength(2));
  });

  test('HTTP event contains only typed diagnostic fields', () {
    final event = SafeLogEvent.httpRequestCompleted(
      occurredAt: now,
      requestId: 'req-1',
      method: 'POST',
      routePattern: '/api/v1/students/{studentId}',
      status: 201,
      durationMs: 42,
      environment: SafeRuntimeEnvironment.staging,
    );

    expect(event.severity, SafeLogSeverity.info);
    expect(event.toJson(), {
      'event': 'http.request.completed',
      'severity': 'info',
      'occurredAt': '2026-07-15T10:00:00.000Z',
      'requestId': 'req-1',
      'method': 'POST',
      'path': '/api/v1/students/{studentId}',
      'status': 201,
      'durationMs': 42,
      'environment': 'staging',
    });
    expect(() => event.toJson()['unknown'] = 'value', throwsUnsupportedError);
  });

  test('free-text operation and environment are rejected', () {
    expect(
      () =>
          SafeLogOperation.parse('telefon=05551234567 token=secret parola=abc'),
      throwsArgumentError,
    );
    expect(
      () => SafeRuntimeEnvironment.parse('production\nforged=true'),
      throwsArgumentError,
    );
  });

  test('request fields reject forging and invalid ranges', () {
    expect(() => _request(now, requestId: 'req\nforged'), throwsArgumentError);
    for (final invalidRoute in <String>[
      '/health?token=secret',
      '/students?phone=05551234567',
      '/health#fragment',
      '/health\r\nforged=true',
    ]) {
      expect(
        () => _request(now, routePattern: invalidRoute),
        throwsArgumentError,
        reason: invalidRoute,
      );
    }
    expect(() => _request(now, method: 'BREW\nforged'), throwsArgumentError);
    expect(() => _request(now, status: 99), throwsArgumentError);
    expect(() => _request(now, status: 600), throwsArgumentError);
    expect(() => _request(now, durationMs: -1), throwsArgumentError);
  });

  test('valid Spring routes and safe methods are accepted', () {
    for (final route in <String>[
      '/health',
      '/api/v1/students/{studentId}',
      '/api/v1/classes/*',
    ]) {
      expect(_request(now, routePattern: route).toJson()['path'], route);
    }
    expect(_request(now, method: 'TRACE').toJson()['method'], 'TRACE');
    expect(_request(now, method: 'CONNECT').toJson()['method'], 'CONNECT');
  });

  test('unexpected error drops message and stack', () {
    final event = SafeLogEvent.unexpectedApplicationError(
      occurredAt: now,
      operation: SafeLogOperation.flutterFramework,
      error: StateError('telefon=05551234567 token=secret parola=abc'),
      severity: SafeLogSeverity.fatal,
    );

    expect(event.toJson()['errorType'], 'StateError');
    expect(event.toJson()['severity'], 'fatal');
    expect(
      event.toJson().values,
      isNot(contains('telefon=05551234567 token=secret parola=abc')),
    );
    expect(event.toJson().keys, isNot(contains('stackTrace')));
  });

  test('unexpected error rejects an invalid severity', () {
    expect(
      () => SafeLogEvent.unexpectedApplicationError(
        occurredAt: now,
        operation: SafeLogOperation.dartZone,
        error: StateError('failure'),
        severity: SafeLogSeverity.info,
      ),
      throwsArgumentError,
    );
  });

  test('empty and forged error types are rejected', () {
    expect(() => SafeLogEvent.validateErrorType(''), throwsArgumentError);
    expect(
      () => SafeLogEvent.validateErrorType('StateError\nforged=true'),
      throwsArgumentError,
    );
  });

  test('typed factories cover every severity', () {
    expect(_request(now, status: 200).severity, SafeLogSeverity.info);
    expect(_request(now, status: 400).severity, SafeLogSeverity.warning);
    expect(_request(now, status: 500).severity, SafeLogSeverity.error);
    expect(
      SafeLogEvent.unexpectedApplicationError(
        occurredAt: now,
        operation: SafeLogOperation.dartZone,
        error: StateError('failure'),
        severity: SafeLogSeverity.fatal,
      ).severity,
      SafeLogSeverity.fatal,
    );
  });
}

SafeLogEvent _request(
  DateTime now, {
  String requestId = 'req-1',
  String method = 'GET',
  String routePattern = '/health',
  int status = 200,
  int durationMs = 1,
}) => SafeLogEvent.httpRequestCompleted(
  occurredAt: now,
  requestId: requestId,
  method: method,
  routePattern: routePattern,
  status: status,
  durationMs: durationMs,
);
