import 'dart:collection';

enum SafeLogSeverity { info, warning, error, fatal }

enum SafeLogOperation {
  httpRequest('http.request'),
  flutterFramework('flutter.framework'),
  flutterPlatform('flutter.platform'),
  dartZone('dart.zone');

  const SafeLogOperation(this.value);

  final String value;

  static SafeLogOperation parse(String value) {
    for (final operation in values) {
      if (operation.value == value) {
        return operation;
      }
    }
    throw ArgumentError.value(value, 'value', 'Unsupported operation');
  }
}

enum SafeRuntimeEnvironment {
  development('development'),
  staging('staging'),
  production('production');

  const SafeRuntimeEnvironment(this.value);

  final String value;

  static SafeRuntimeEnvironment parse(String value) {
    for (final environment in values) {
      if (environment.value == value) {
        return environment;
      }
    }
    throw ArgumentError.value(value, 'value', 'Unsupported environment');
  }
}

/// Provider-independent diagnostic event created only through typed factories.
final class SafeLogEvent {
  SafeLogEvent._({
    required this.name,
    required this.severity,
    required this.occurredAt,
    required Map<String, Object> fields,
  }) : _fields = Map.unmodifiable(fields);

  static final _requestId = RegExp(r'^[A-Za-z0-9._:-]{1,128}$');
  static final _errorType = RegExp(r'^[A-Za-z_$][A-Za-z0-9_.$]{0,127}$');
  static final _routePattern = RegExp(
    r"^/[A-Za-z0-9._~!$&'()*+,;=:@/{}`*\[\]-]{0,255}$",
  );
  static const _httpMethods = <String>{
    'GET',
    'HEAD',
    'POST',
    'PUT',
    'PATCH',
    'DELETE',
    'OPTIONS',
    'TRACE',
    'CONNECT',
  };

  final String name;
  final SafeLogSeverity severity;
  final DateTime occurredAt;
  final Map<String, Object> _fields;

  factory SafeLogEvent.httpRequestCompleted({
    required DateTime occurredAt,
    required String requestId,
    required String method,
    required String routePattern,
    required int status,
    required int durationMs,
    SafeRuntimeEnvironment? environment,
  }) {
    _validateRequestId(requestId);
    if (!_httpMethods.contains(method)) {
      throw ArgumentError.value(method, 'method', 'Invalid HTTP method');
    }
    if (routePattern != 'unmatched' && !_routePattern.hasMatch(routePattern)) {
      throw ArgumentError.value(
        routePattern,
        'routePattern',
        'Invalid route pattern',
      );
    }
    if (status < 100 || status > 599) {
      throw ArgumentError.value(status, 'status', 'Invalid HTTP status');
    }
    if (durationMs < 0) {
      throw ArgumentError.value(
        durationMs,
        'durationMs',
        'durationMs cannot be negative',
      );
    }
    return SafeLogEvent._(
      name: 'http.request.completed',
      severity: _severityForStatus(status),
      occurredAt: occurredAt,
      fields: <String, Object>{
        'requestId': requestId,
        'method': method,
        'path': routePattern,
        'status': status,
        'durationMs': durationMs,
        if (environment != null) 'environment': environment.value,
      },
    );
  }

  factory SafeLogEvent.unexpectedApplicationError({
    required DateTime occurredAt,
    required SafeLogOperation operation,
    required Object error,
    String? requestId,
    SafeRuntimeEnvironment? environment,
    SafeLogSeverity severity = SafeLogSeverity.error,
  }) {
    if (requestId != null) {
      _validateRequestId(requestId);
    }
    if (severity != SafeLogSeverity.error &&
        severity != SafeLogSeverity.fatal) {
      throw ArgumentError.value(
        severity,
        'severity',
        'Unexpected errors require error or fatal severity',
      );
    }
    final errorType = error.runtimeType.toString();
    validateErrorType(errorType);
    final environmentValue = environment?.value;
    return SafeLogEvent._(
      name: 'application.error.unexpected',
      severity: severity,
      occurredAt: occurredAt,
      fields: <String, Object>{
        'operation': operation.value,
        'errorType': errorType,
        'requestId': ?requestId,
        'environment': ?environmentValue,
      },
    );
  }

  Map<String, Object> toJson() => UnmodifiableMapView(<String, Object>{
    'event': name,
    'severity': severity.name,
    'occurredAt': occurredAt.toUtc().toIso8601String(),
    ..._fields,
  });

  static SafeLogSeverity _severityForStatus(int status) {
    if (status >= 500) {
      return SafeLogSeverity.error;
    }
    if (status >= 400) {
      return SafeLogSeverity.warning;
    }
    return SafeLogSeverity.info;
  }

  static void _validateRequestId(String requestId) {
    if (!_requestId.hasMatch(requestId)) {
      throw ArgumentError.value(requestId, 'requestId', 'Invalid requestId');
    }
  }

  /// Exposed for negative contract tests; event creation still requires a typed factory.
  static void validateErrorType(String errorType) {
    if (!_errorType.hasMatch(errorType)) {
      throw ArgumentError.value(errorType, 'errorType', 'Invalid errorType');
    }
  }
}
