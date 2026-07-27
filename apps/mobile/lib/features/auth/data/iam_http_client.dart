import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import '../../../core/config/app_runtime_config.dart';

enum IamErrorCode {
  invalidRequest,
  unauthenticated,
  forbidden,
  organizationContextRequired,
  sessionRevoked,
  accountNotReady,
  reauthenticationRequired,
  resourceNotFound,
  stateConflict,
  idempotencyKeyReused,
  rateLimited,
  providerUnavailable,
  internalError,
}

class IamApiException implements Exception {
  const IamApiException({
    required this.code,
    required this.statusCode,
    this.retryAfter,
  });
  final IamErrorCode code;
  final int statusCode;
  final Duration? retryAfter;

  bool get isTerminalSession =>
      code == IamErrorCode.unauthenticated ||
      code == IamErrorCode.sessionRevoked ||
      code == IamErrorCode.accountNotReady ||
      code == IamErrorCode.forbidden ||
      code == IamErrorCode.organizationContextRequired ||
      code == IamErrorCode.resourceNotFound ||
      code == IamErrorCode.stateConflict;
}

class IamTransportException implements Exception {
  const IamTransportException();
}

class IamRequest {
  const IamRequest({
    required this.method,
    required this.uri,
    required this.headers,
    this.body,
  });
  final String method;
  final Uri uri;
  final Map<String, String> headers;
  final String? body;
}

class IamResponse {
  const IamResponse({
    required this.statusCode,
    required this.headers,
    required this.body,
  });
  final int statusCode;
  final Map<String, String> headers;
  final String body;
}

abstract interface class IamTransport {
  Future<IamResponse> send(IamRequest request);
}

class DartIoIamTransport implements IamTransport {
  DartIoIamTransport({
    this.connectionTimeout = const Duration(seconds: 10),
    this.responseTimeout = const Duration(seconds: 20),
  });
  final Duration connectionTimeout;
  final Duration responseTimeout;

  @override
  Future<IamResponse> send(IamRequest request) async {
    final client = HttpClient()..connectionTimeout = connectionTimeout;
    try {
      final outgoing = await client
          .openUrl(request.method, request.uri)
          .timeout(connectionTimeout);
      outgoing.followRedirects = false;
      request.headers.forEach(outgoing.headers.set);
      if (request.body != null) outgoing.write(request.body);
      final incoming = await outgoing.close().timeout(responseTimeout);
      if (incoming.isRedirect) throw const IamTransportException();
      final body = await utf8.decoder
          .bind(incoming)
          .join()
          .timeout(responseTimeout);
      final headers = <String, String>{};
      incoming.headers.forEach((name, values) {
        headers[name.toLowerCase()] = values.join(',');
      });
      return IamResponse(
        statusCode: incoming.statusCode,
        headers: headers,
        body: body,
      );
    } on IamTransportException {
      rethrow;
    } on Object {
      throw const IamTransportException();
    } finally {
      client.close(force: true);
    }
  }
}

class DeviceRegistration {
  const DeviceRegistration({
    required this.identifier,
    required this.platform,
    this.name,
  });
  final String identifier;
  final String platform;
  final String? name;
}

class ProviderExchange {
  const ProviderExchange({
    required this.contextToken,
    required this.expiresAt,
    required this.userId,
    required this.displayName,
    required this.deviceIdentifier,
    required this.canActivatePlatformAdministrator,
  });
  final String contextToken;
  final DateTime expiresAt;
  final String userId;
  final String displayName;
  final String deviceIdentifier;
  final bool canActivatePlatformAdministrator;
}

class MembershipDto {
  const MembershipDto({
    required this.id,
    required this.organizationId,
    required this.organizationName,
    required this.roleCodes,
    required this.sessionGeneration,
  });
  final String id;
  final String organizationId;
  final String organizationName;
  final List<String> roleCodes;
  final int sessionGeneration;
}

class PlatformSessionDto {
  const PlatformSessionDto({
    required this.userId,
    required this.displayName,
    required this.deviceIdentifier,
    required this.scope,
    required this.expiresAt,
    required this.authenticatedAt,
    this.accessToken,
    this.refreshToken,
    this.refreshExpiresAt,
    this.membership,
  });
  final String userId;
  final String displayName;
  final String deviceIdentifier;
  final String scope;
  final String? accessToken;
  final String? refreshToken;
  final DateTime expiresAt;
  final DateTime? refreshExpiresAt;
  final DateTime authenticatedAt;
  final MembershipDto? membership;
}

class IamHttpClient {
  IamHttpClient({
    required AppRuntimeConfig config,
    IamTransport? transport,
    DateTime Function()? now,
    Future<void> Function(Duration)? delay,
  }) : _baseUri = config.publicApiBaseUrl,
       _transport = transport ?? DartIoIamTransport(),
       _now = now ?? DateTime.now,
       _delay = delay ?? Future<void>.delayed;

  final Uri _baseUri;
  final IamTransport _transport;
  final DateTime Function() _now;
  final Future<void> Function(Duration) _delay;

  Future<ProviderExchange> exchangeProviderToken(
    String providerAccessToken,
    DeviceRegistration device,
  ) async {
    final json = await _jsonCommand(
      '/api/v1/iam/auth/provider-token-exchange',
      bearer: providerAccessToken,
      body: <String, Object?>{
        'deviceIdentifier': device.identifier,
        'platform': device.platform,
        if (device.name != null) 'deviceName': device.name,
      },
    );
    final user = _map(json, 'user');
    final remoteDevice = _map(json, 'device');
    final scopes = _stringList(json, 'availableScopes');
    if (scopes.any(
          (scope) =>
              scope != 'ORGANIZATION_SELECTION' &&
              scope != 'GLOBAL_PLATFORM_ADMIN',
        ) ||
        _string(user, 'status') != 'ACTIVE' ||
        (_string(remoteDevice, 'platform') != 'IOS' &&
            _string(remoteDevice, 'platform') != 'ANDROID') ||
        (json['platformAdministrator'] != null &&
            _string(_map(json, 'platformAdministrator'), 'status') !=
                'ACTIVE')) {
      throw const FormatException();
    }
    return ProviderExchange(
      contextToken: _string(json, 'contextSelectionToken'),
      expiresAt: _date(json, 'contextSelectionTokenExpiresAt'),
      userId: _string(user, 'id'),
      displayName: _string(user, 'displayName'),
      deviceIdentifier: _string(remoteDevice, 'deviceIdentifier'),
      canActivatePlatformAdministrator: scopes.contains(
        'GLOBAL_PLATFORM_ADMIN',
      ),
    );
  }

  Future<List<MembershipDto>> contextSelections(String contextToken) async {
    final json = await _jsonGet(
      '/api/v1/iam/auth/context-selections',
      bearer: contextToken,
    );
    final items = json['items'];
    final page = _map(json, 'page');
    if (items is! List ||
        page['hasNextPage'] != false ||
        page['nextCursor'] != null) {
      throw const FormatException();
    }
    return List<MembershipDto>.unmodifiable(
      items.map((item) {
        if (item is! Map<String, dynamic>) throw const FormatException();
        if (_string(item, 'organizationStatus') != 'ACTIVE' ||
            _string(item, 'membershipStatus') != 'ACTIVE') {
          throw const FormatException();
        }
        return MembershipDto(
          id: _uuid(item, 'id'),
          organizationId: _uuid(item, 'organizationId'),
          organizationName: _string(item, 'organizationName'),
          roleCodes: _stringList(item, 'roleCodes'),
          sessionGeneration: _nonNegativeInt(item, 'sessionGeneration'),
        );
      }),
    );
  }

  Future<PlatformSessionDto> activateOrganization(
    String contextToken,
    String membershipId, {
    String? idempotencyKey,
  }) => _sessionCommand(
    '/api/v1/iam/auth/context-selections/${Uri.encodeComponent(membershipId)}/activate',
    bearer: contextToken,
    tokensRequired: true,
    identityRequired: true,
    idempotencyKey: idempotencyKey,
  );

  Future<PlatformSessionDto> activatePlatformAdministrator(
    String contextToken, {
    String? idempotencyKey,
  }) => _sessionCommand(
    '/api/v1/iam/auth/platform-admin/activate',
    bearer: contextToken,
    tokensRequired: true,
    identityRequired: true,
    idempotencyKey: idempotencyKey,
  );

  Future<PlatformSessionDto> sessionMe(String accessToken) async =>
      _parseSession(
        await _jsonGet('/api/v1/iam/sessions/me', bearer: accessToken),
        tokensRequired: false,
        identityRequired: true,
      );

  Future<PlatformSessionDto> refresh(
    String refreshToken, {
    String? idempotencyKey,
  }) => _sessionCommand(
    '/api/v1/iam/sessions/refresh',
    body: <String, Object?>{'refreshToken': refreshToken},
    tokensRequired: true,
    identityRequired: false,
    idempotencyKey: idempotencyKey,
  );

  Future<void> logout(
    String accessToken,
    String refreshToken, {
    String? idempotencyKey,
  }) async {
    final response = await _command(
      '/api/v1/iam/sessions/logout',
      bearer: accessToken,
      body: <String, Object?>{'refreshToken': refreshToken},
      idempotencyKey: idempotencyKey,
    );
    if (response.statusCode != HttpStatus.noContent) {
      _throwError(response);
    }
  }

  Future<PlatformSessionDto> _sessionCommand(
    String path, {
    String? bearer,
    Map<String, Object?>? body,
    required bool tokensRequired,
    required bool identityRequired,
    String? idempotencyKey,
  }) async => _parseSession(
    await _jsonCommand(
      path,
      bearer: bearer,
      body: body,
      idempotencyKey: idempotencyKey,
    ),
    tokensRequired: tokensRequired,
    identityRequired: identityRequired,
  );

  PlatformSessionDto _parseSession(
    Map<String, dynamic> json, {
    required bool tokensRequired,
    required bool identityRequired,
  }) {
    final user = _map(json, 'user', required: identityRequired);
    final session = _map(json, 'session');
    final device = json['device'];
    final membershipJson = json['organizationMembership'];
    final scope = _string(session, 'scope');
    if (scope != 'ORGANIZATION' && scope != 'GLOBAL_PLATFORM_ADMIN') {
      throw const FormatException();
    }
    MembershipDto? membership;
    if (membershipJson != null) {
      if (membershipJson is! Map<String, dynamic> ||
          _string(membershipJson, 'organizationStatus') != 'ACTIVE' ||
          _string(membershipJson, 'membershipStatus') != 'ACTIVE') {
        throw const FormatException();
      }
      membership = MembershipDto(
        id: _uuid(membershipJson, 'id'),
        organizationId: _uuid(membershipJson, 'organizationId'),
        organizationName: _string(membershipJson, 'organizationName'),
        roleCodes: _stringList(membershipJson, 'roleCodes'),
        sessionGeneration: _nonNegativeInt(membershipJson, 'sessionGeneration'),
      );
    }
    if ((scope == 'ORGANIZATION') != (membership != null)) {
      throw const FormatException();
    }
    if (identityRequired) {
      if (device is! Map<String, dynamic> ||
          _string(user, 'status') != 'ACTIVE' ||
          (_string(device, 'platform') != 'IOS' &&
              _string(device, 'platform') != 'ANDROID')) {
        throw const FormatException();
      }
      if (scope == 'GLOBAL_PLATFORM_ADMIN') {
        if (_string(_map(json, 'platformAdministrator'), 'status') !=
            'ACTIVE') {
          throw const FormatException();
        }
      } else if (json['platformAdministrator'] != null) {
        throw const FormatException();
      }
    }
    if (tokensRequired && _string(session, 'tokenType') != 'Bearer') {
      throw const FormatException();
    }
    final access = tokensRequired ? _string(session, 'accessToken') : null;
    final refresh = tokensRequired ? _string(session, 'refreshToken') : null;
    final refreshExpiry = tokensRequired
        ? _date(session, 'refreshExpiresAt')
        : null;
    return PlatformSessionDto(
      userId: user.isEmpty ? '' : _uuid(user, 'id'),
      displayName: user.isEmpty ? '' : _string(user, 'displayName'),
      deviceIdentifier: device is Map<String, dynamic>
          ? _uuid(device, 'deviceIdentifier')
          : '',
      scope: scope,
      accessToken: access,
      refreshToken: refresh,
      expiresAt: _date(session, 'expiresAt'),
      refreshExpiresAt: refreshExpiry,
      authenticatedAt: _date(session, 'authenticatedAt'),
      membership: membership,
    );
  }

  Future<Map<String, dynamic>> _jsonGet(
    String path, {
    required String bearer,
  }) async => _decode(await _send('GET', path, bearer: bearer));

  Future<Map<String, dynamic>> _jsonCommand(
    String path, {
    String? bearer,
    Map<String, Object?>? body,
    String? idempotencyKey,
  }) async => _decode(
    await _command(
      path,
      bearer: bearer,
      body: body,
      idempotencyKey: idempotencyKey,
    ),
  );

  Future<IamResponse> _command(
    String path, {
    String? bearer,
    Map<String, Object?>? body,
    String? idempotencyKey,
  }) => _send(
    'POST',
    path,
    bearer: bearer,
    body: body,
    idempotencyKey: idempotencyKey ?? createIdempotencyKey(),
  );

  String createIdempotencyKey() => _randomUuid();

  Future<IamResponse> _send(
    String method,
    String path, {
    String? bearer,
    Map<String, Object?>? body,
    String? idempotencyKey,
  }) async {
    if (bearer != null && bearer.trim().isEmpty) throw const FormatException();
    final uri = _baseUri.resolve(path);
    if (!_sameOrigin(_baseUri, uri)) throw const IamTransportException();
    final requestId = _randomUuid();
    final headers = <String, String>{
      'Accept': 'application/json',
      'X-Request-Id': requestId,
      if (bearer != null) 'Authorization': 'Bearer $bearer',
      // ignore: use_null_aware_elements
      if (idempotencyKey != null) 'Idempotency-Key': idempotencyKey,
      if (body != null) 'Content-Type': 'application/json',
    };
    final request = IamRequest(
      method: method,
      uri: uri,
      headers: Map<String, String>.unmodifiable(headers),
      body: body == null ? null : jsonEncode(body),
    );
    for (var attempt = 0; attempt < 2; attempt++) {
      try {
        final response = await _transport.send(request);
        if (response.statusCode >= 200 && response.statusCode < 300) {
          return response;
        }
        final retryAfter = _retryAfter(response.headers['retry-after']);
        if (attempt == 0 &&
            (response.statusCode == 429 || response.statusCode == 503)) {
          if (retryAfter != null) await _delay(retryAfter);
          continue;
        }
        _throwError(response);
      } on IamTransportException {
        if (attempt == 1) rethrow;
      }
    }
    throw const IamTransportException();
  }

  Map<String, dynamic> _decode(IamResponse response) {
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      return decoded;
    } on FormatException {
      throw const IamApiException(
        code: IamErrorCode.internalError,
        statusCode: 500,
      );
    }
  }

  Never _throwError(IamResponse response) {
    IamErrorCode code = IamErrorCode.internalError;
    try {
      final decoded = jsonDecode(response.body);
      if (decoded is Map<String, dynamic> &&
          decoded['error'] is Map<String, dynamic>) {
        code = _errorCode(
          _string(decoded['error'] as Map<String, dynamic>, 'code'),
        );
      }
    } on Object {
      code = IamErrorCode.internalError;
    }
    throw IamApiException(
      code: code,
      statusCode: response.statusCode,
      retryAfter: _retryAfter(response.headers['retry-after']),
    );
  }

  Duration? _retryAfter(String? value) {
    if (value == null) return null;
    final seconds = int.tryParse(value.trim());
    if (seconds != null && seconds >= 0 && seconds <= 60) {
      return Duration(seconds: seconds);
    }
    DateTime date;
    try {
      date = HttpDate.parse(value);
    } on Object {
      return null;
    }
    final duration = date.difference(_now().toUtc());
    if (duration.isNegative || duration > const Duration(seconds: 60)) {
      return null;
    }
    return duration;
  }
}

IamErrorCode _errorCode(String value) => switch (value) {
  'INVALID_REQUEST' => IamErrorCode.invalidRequest,
  'VALIDATION_FAILED' => IamErrorCode.invalidRequest,
  'UNAUTHENTICATED' => IamErrorCode.unauthenticated,
  'FORBIDDEN' => IamErrorCode.forbidden,
  'ORGANIZATION_CONTEXT_REQUIRED' => IamErrorCode.organizationContextRequired,
  'SESSION_REVOKED' => IamErrorCode.sessionRevoked,
  'ACCOUNT_NOT_READY' => IamErrorCode.accountNotReady,
  'REAUTHENTICATION_REQUIRED' => IamErrorCode.reauthenticationRequired,
  'RESOURCE_NOT_FOUND' => IamErrorCode.resourceNotFound,
  'STATE_CONFLICT' => IamErrorCode.stateConflict,
  'IDEMPOTENCY_KEY_REUSED' => IamErrorCode.idempotencyKeyReused,
  'RATE_LIMITED' => IamErrorCode.rateLimited,
  'PROVIDER_UNAVAILABLE' => IamErrorCode.providerUnavailable,
  _ => IamErrorCode.internalError,
};

Map<String, dynamic> _map(
  Map<String, dynamic> json,
  String key, {
  bool required = true,
}) {
  final value = json[key];
  if (!required && value == null) return const <String, dynamic>{};
  if (value is! Map<String, dynamic>) throw const FormatException();
  return value;
}

String _string(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! String || value.trim().isEmpty) throw const FormatException();
  return value;
}

String _uuid(Map<String, dynamic> json, String key) {
  final value = _string(json, key);
  if (!RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$',
  ).hasMatch(value)) {
    throw const FormatException();
  }
  return value;
}

int _nonNegativeInt(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! int || value < 0) throw const FormatException();
  return value;
}

List<String> _stringList(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! List ||
      value.any((item) => item is! String || item.trim().isEmpty)) {
    throw const FormatException();
  }
  return List<String>.unmodifiable(value.cast<String>());
}

DateTime _date(Map<String, dynamic> json, String key) {
  final value = DateTime.tryParse(_string(json, key));
  if (value == null || !value.isUtc) throw const FormatException();
  return value;
}

bool _sameOrigin(Uri first, Uri second) =>
    first.scheme == second.scheme &&
    first.host == second.host &&
    first.port == second.port;

String _randomUuid() {
  final bytes = List<int>.generate(16, (_) => Random.secure().nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
      '${hex.substring(20)}';
}
