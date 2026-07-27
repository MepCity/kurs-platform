import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';

import '../../../core/config/app_runtime_config.dart';
import '../../../core/network/authenticated_api_session.dart';
import '../../../core/network/safe_http_transport.dart';
import '../domain/organization.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';
import '../domain/organization_create_request.dart';
import '../domain/organization_list_query.dart';
import '../domain/organization_list_result.dart';
import '../domain/organization_search_normalization.dart';
import '../domain/organization_status.dart';
import '../domain/organizations_failure.dart';
import '../domain/organizations_repository.dart';

/// Production adapter for the eight file-free ORG endpoints in ORG-009C.
///
/// The adapter receives only an opaque authorization capability. It never
/// persists or exposes a platform token and creates a fresh request ID for
/// every physical HTTP attempt.
class ProductionOrganizationsRepository extends ChangeNotifier
    implements OrganizationsRepository, OrganizationBrandRepository {
  ProductionOrganizationsRepository({
    required AppRuntimeConfig config,
    required AuthenticatedApiSession session,
    SafeHttpTransport? transport,
    DateTime Function()? now,
    bool globalListScope = true,
  }) : _baseUri = config.publicApiBaseUrl,
       // ignore: prefer_initializing_formals
       _session = session,
       _transport = transport ?? DartIoSafeHttpTransport(),
       _now = now ?? DateTime.now,
       // ignore: prefer_initializing_formals
       _globalListScope = globalListScope;

  final Uri _baseUri;
  final AuthenticatedApiSession _session;
  final SafeHttpTransport _transport;
  final DateTime Function() _now;
  final bool _globalListScope;

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) async {
    final normalized = query.normalized();
    if (normalized.limit < 1 ||
        normalized.limit > 100 ||
        (normalized.search?.length ?? 0) > organizationSearchMaxLength) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.validationFailed,
        'Sayfa boyutu geçersiz.',
      );
    }
    if (normalized.cursor != null &&
        !_validVisibleAscii(normalized.cursor!, 8192)) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.invalidCursor,
        'Sayfalama bilgisi geçersiz. Listeyi yenileyin.',
      );
    }
    final parameters = _globalListScope
        ? <String, String>{
            if (normalized.status != null)
              'status': normalized.status!.toWire(),
            if (normalized.search != null) 'search': normalized.search!,
            'sort': switch (normalized.sort) {
              OrganizationSortField.name => 'name',
              OrganizationSortField.createdAt => 'createdAt',
            },
            'order': normalized.order == OrganizationSortOrder.ascending
                ? 'ASC'
                : 'DESC',
            'limit': normalized.limit.toString(),
            if (normalized.cursor != null) 'cursor': normalized.cursor!,
          }
        : const <String, String>{};
    final response = await _request(
      'GET',
      '/api/v1/organizations',
      queryParameters: parameters,
    );
    return _protocol(() {
      _expectStatus(response, HttpStatus.ok);
      final json = _jsonObject(response.body);
      final rawItems = _list(json, 'items');
      if (rawItems.length > normalized.limit) throw const FormatException();
      final page = _object(json, 'page');
      final nextCursor = _nullableString(page, 'nextCursor', max: 8192);
      final hasNextPage = _bool(page, 'hasNextPage');
      if (hasNextPage != (nextCursor != null)) throw const FormatException();
      final items = List<Organization>.unmodifiable(
        rawItems.map(_organization),
      );
      if (items.map((item) => item.id).toSet().length != items.length) {
        throw const FormatException();
      }
      return OrganizationListResult(
        items: items,
        nextCursor: nextCursor,
        hasNextPage: hasNextPage,
      );
    });
  }

  @override
  Future<Organization> createOrganization(
    OrganizationCreateRequest request,
  ) async {
    final validation = request.validate();
    if (validation.hasErrors) {
      throw OrganizationsFailure(
        OrganizationsFailureCode.validationFailed,
        validation.firstMessage ?? 'Kurum alanları geçersiz.',
        fieldErrors: validation,
      );
    }
    final response = await _request(
      'POST',
      '/api/v1/organizations',
      idempotencyKey: request.clientMutationId,
      body: <String, Object?>{
        'name': request.normalizedName,
        if (request.normalizedShortName != null)
          'shortName': request.normalizedShortName,
        'defaultTimezone': request.normalizedDefaultTimezone,
      },
    );
    return _protocol(() {
      _expectStatus(response, HttpStatus.created);
      final result = _organization(_jsonObject(response.body));
      final expectedLocation = '/api/v1/organizations/${result.id}';
      if (response.headers['location'] != expectedLocation ||
          result.status != OrganizationStatus.active ||
          result.rowVersion != 1) {
        throw const FormatException();
      }
      notifyListeners();
      return result;
    });
  }

  @override
  Future<OrganizationBrand> getBrand(String organizationId) =>
      _brandRequest('GET', organizationId, '/brand', _brand);

  @override
  Future<OrganizationBrand> updateBrand(
    String organizationId,
    OrganizationBrand brand,
    String clientMutationId,
  ) => _brandRequest(
    'PATCH',
    organizationId,
    '/brand',
    _brand,
    rowVersion: brand.rowVersion,
    idempotencyKey: clientMutationId,
    body: <String, Object?>{
      'primaryColor': brand.primaryColor,
      'secondaryColor': brand.secondaryColor,
    },
  );

  @override
  Future<OrganizationBrandColors> getBrandColors(String organizationId) =>
      _brandRequest('GET', organizationId, '/brand-colors', _colors);

  @override
  Future<OrganizationBrandColors> replaceBrandColors(
    String organizationId,
    OrganizationBrandColors colors,
    String clientMutationId,
  ) => _brandRequest(
    'PUT',
    organizationId,
    '/brand-colors',
    _colors,
    rowVersion: colors.rowVersion,
    idempotencyKey: clientMutationId,
    body: <String, Object?>{
      'items': colors.items
          .map(
            (item) => <String, Object?>{
              'colorHex': item.colorHex,
              'sortOrder': item.sortOrder,
            },
          )
          .toList(growable: false),
    },
  );

  @override
  Future<OrganizationModules> getModules(String organizationId) =>
      _brandRequest('GET', organizationId, '/modules', _modules);

  @override
  Future<OrganizationModules> updateModules(
    String organizationId,
    OrganizationModules modules,
    String clientMutationId,
  ) => _brandRequest(
    'PATCH',
    organizationId,
    '/modules',
    _modules,
    rowVersion: modules.rowVersion,
    idempotencyKey: clientMutationId,
    body: <String, Object?>{
      'items': modules.items
          .map(
            (item) => <String, Object?>{
              'moduleCode': item.code.wireName,
              'isEnabled': item.isEnabled,
              'sortOrder': item.sortOrder,
            },
          )
          .toList(growable: false),
    },
  );

  Future<T> _brandRequest<T>(
    String method,
    String organizationId,
    String suffix,
    T Function(Map<String, dynamic>) parser, {
    int? rowVersion,
    String? idempotencyKey,
    Map<String, Object?>? body,
  }) async {
    if (!_isUuid(organizationId)) throw const FormatException();
    final response = await _request(
      method,
      '/api/v1/organizations/${Uri.encodeComponent(organizationId)}$suffix',
      rowVersion: rowVersion,
      idempotencyKey: idempotencyKey,
      body: body,
    );
    return _protocol(() {
      _expectStatus(response, HttpStatus.ok);
      final value = parser(_jsonObject(response.body));
      final actualVersion = switch (value) {
        OrganizationBrand value => value.rowVersion,
        OrganizationBrandColors value => value.rowVersion,
        OrganizationModules value => value.rowVersion,
        _ => throw const FormatException(),
      };
      if (response.headers['etag'] != '"$actualVersion"') {
        throw const FormatException();
      }
      return value;
    });
  }

  Future<SafeHttpResponse> _request(
    String method,
    String path, {
    Map<String, String> queryParameters = const <String, String>{},
    Map<String, Object?>? body,
    String? idempotencyKey,
    int? rowVersion,
  }) async {
    if (idempotencyKey != null && !_validVisibleAscii(idempotencyKey, 128)) {
      throw const FormatException();
    }
    if (rowVersion != null && rowVersion < 1) throw const FormatException();
    final uri = _baseUri
        .resolve(path)
        .replace(
          queryParameters: queryParameters.isEmpty ? null : queryParameters,
        );
    if (!_sameOrigin(_baseUri, uri) ||
        (uri.scheme != 'https' &&
            !(uri.scheme == 'http' &&
                (uri.host == 'localhost' ||
                    uri.host == '127.0.0.1' ||
                    uri.host == '::1')))) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.internalError,
        'Sunucu adresi güvenli değil.',
      );
    }
    final encodedBody = body == null ? null : jsonEncode(body);
    Future<SafeHttpResponse> send(String token) {
      if (!_validVisibleAscii(token, 4096)) throw const FormatException();
      return _transport.send(
        SafeHttpRequest(
          method: method,
          uri: uri,
          headers: Map<String, String>.unmodifiable(<String, String>{
            'Accept': 'application/json',
            'Authorization': 'Bearer $token',
            'X-Request-Id': _randomUuid(),
            if (encodedBody != null) 'Content-Type': 'application/json',
            'Idempotency-Key': ?idempotencyKey,
            if (rowVersion != null)
              'If-Match-Row-Version': rowVersion.toString(),
          }),
          body: encodedBody,
        ),
      );
    }

    try {
      var response = await _session.run(send);
      var error = _errorOf(response);
      if (error?.code == OrganizationsFailureCode.unauthenticated) {
        response = await _session.refreshAndRun(send);
        error = _errorOf(response);
      }
      if (error != null) {
        if (error.code == OrganizationsFailureCode.sessionRevoked ||
            error.code == OrganizationsFailureCode.unauthenticated) {
          await _session.terminate();
        }
        throw error;
      }
      return response;
    } on OrganizationsFailure {
      rethrow;
    } on AuthenticatedApiSessionUnavailable catch (failure) {
      throw OrganizationsFailure(
        failure.terminal
            ? OrganizationsFailureCode.sessionRevoked
            : OrganizationsFailureCode.transientNetwork,
        failure.terminal
            ? 'Oturumunuz sona erdi. Lütfen yeniden giriş yapın.'
            : 'Sunucuya ulaşılamıyor. Lütfen yeniden deneyin.',
      );
    } on SafeHttpTransportException {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.transientNetwork,
        'Sunucuya ulaşılamıyor. Lütfen yeniden deneyin.',
      );
    } on FormatException {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.internalError,
        'Sunucu yanıtı güvenli biçimde doğrulanamadı.',
      );
    }
  }

  OrganizationsFailure? _errorOf(SafeHttpResponse response) {
    if (response.statusCode >= 200 && response.statusCode < 300) return null;
    try {
      final envelope = _jsonObject(response.body);
      final error = _object(envelope, 'error');
      final code = _string(error, 'code', max: 64);
      _string(error, 'message', max: 500);
      final requestId = _string(error, 'requestId', max: 128);
      if (!RegExp(r'^[A-Za-z0-9._:-]+$').hasMatch(requestId)) {
        throw const FormatException();
      }
      final mapped = _mappedError(code, response.statusCode);
      if (mapped == null) throw const FormatException();
      final fields = <String, String>{};
      final rawFields = error['fieldErrors'];
      if (rawFields != null) {
        if (rawFields is! List || rawFields.length > 20) {
          throw const FormatException();
        }
        for (final raw in rawFields) {
          if (raw is! Map<String, dynamic>) throw const FormatException();
          final field = _string(raw, 'field', max: 64);
          _string(raw, 'code', max: 64);
          fields[field] = _string(raw, 'message', max: 300);
        }
      }
      final retryAfter = mapped == OrganizationsFailureCode.rateLimited
          ? _retryAfter(response.headers['retry-after'])
          : null;
      return OrganizationsFailure(
        mapped,
        mapped == OrganizationsFailureCode.rateLimited && retryAfter != null
            ? '${retryAfter.inSeconds} saniye sonra tekrar deneyin.'
            : _safeMessage(mapped),
        fieldErrors: fields.isEmpty
            ? null
            : OrganizationCreateFieldErrors.fromServerFieldErrors(fields),
        retryAfter: retryAfter,
      );
    } on OrganizationsFailure {
      rethrow;
    } on Object {
      return const OrganizationsFailure(
        OrganizationsFailureCode.internalError,
        'Sunucu yanıtı güvenli biçimde doğrulanamadı.',
      );
    }
  }

  void _expectStatus(SafeHttpResponse response, int expected) {
    if (response.statusCode != expected) throw const FormatException();
  }

  Organization _organization(Object? raw) {
    if (raw is! Map<String, dynamic>) throw const FormatException();
    final name = _string(raw, 'name', max: organizationNameMaxLength);
    final shortName = _nullableString(
      raw,
      'shortName',
      max: organizationShortNameMaxLength,
    );
    final timezone = _string(raw, 'defaultTimezone', max: 100);
    final createdAt = _utcDate(raw, 'createdAt');
    final updatedAt = _utcDate(raw, 'updatedAt');
    final rowVersion = _positiveInt(raw, 'rowVersion');
    if (name.trim().isEmpty ||
        (shortName != null && shortName.trim().isEmpty) ||
        updatedAt.isBefore(createdAt)) {
      throw const FormatException();
    }
    return Organization(
      id: _uuid(raw, 'id'),
      name: name,
      shortName: shortName,
      defaultTimezone: timezone,
      status: OrganizationStatus.fromWire(_string(raw, 'status', max: 16)),
      createdAt: createdAt,
      updatedAt: updatedAt,
      rowVersion: rowVersion,
    );
  }

  OrganizationBrand _brand(Map<String, dynamic> json) {
    final primary = normalizeBrandHex(_string(json, 'primaryColor', max: 7));
    final secondary = normalizeBrandHex(
      _string(json, 'secondaryColor', max: 7),
    );
    if (validateBrandHex('primary', primary) != null ||
        validateBrandHex('secondary', secondary) != null) {
      throw const FormatException();
    }
    final logo = json['logo'];
    if (logo != null && logo is! Map<String, dynamic>) {
      throw const FormatException();
    }
    return OrganizationBrand(
      primaryColor: primary,
      secondaryColor: secondary,
      rowVersion: _positiveInt(json, 'rowVersion'),
    );
  }

  OrganizationBrandColors _colors(Map<String, dynamic> json) {
    final items = _list(json, 'items');
    if (items.length > 20) throw const FormatException();
    final parsed = items
        .map((raw) {
          if (raw is! Map<String, dynamic>) throw const FormatException();
          final color = normalizeBrandHex(_string(raw, 'colorHex', max: 7));
          final order = _boundedInt(raw, 'sortOrder', 0, 999);
          if (!RegExp(r'^#[0-9A-F]{6}$').hasMatch(color)) {
            throw const FormatException();
          }
          return OrganizationBrandColor(colorHex: color, sortOrder: order);
        })
        .toList(growable: false);
    if (parsed.map((item) => item.colorHex).toSet().length != parsed.length ||
        !_isSortedColors(parsed)) {
      throw const FormatException();
    }
    return OrganizationBrandColors(
      rowVersion: _positiveInt(json, 'rowVersion'),
      items: parsed,
    );
  }

  OrganizationModules _modules(Map<String, dynamic> json) {
    final items = _list(json, 'items');
    if (items.length != OrganizationModuleCode.values.length) {
      throw const FormatException();
    }
    final parsed = items
        .map((raw) {
          if (raw is! Map<String, dynamic>) throw const FormatException();
          final code = switch (_string(raw, 'moduleCode', max: 16)) {
            'ATT' => OrganizationModuleCode.att,
            'PROGRAM' => OrganizationModuleCode.program,
            'CONTENT' => OrganizationModuleCode.content,
            'PROGRESS' => OrganizationModuleCode.progress,
            'EXPORT' => OrganizationModuleCode.export,
            'AUDIT' => OrganizationModuleCode.audit,
            _ => throw const FormatException(),
          };
          return OrganizationModule(
            code: code,
            isEnabled: _bool(raw, 'isEnabled'),
            sortOrder: _boundedInt(raw, 'sortOrder', 0, 999),
          );
        })
        .toList(growable: false);
    if (parsed.map((item) => item.code).toSet().length != parsed.length ||
        !_isSortedModules(parsed)) {
      throw const FormatException();
    }
    return OrganizationModules(
      rowVersion: _positiveInt(json, 'rowVersion'),
      items: parsed,
    );
  }

  Duration? _retryAfter(String? value) {
    if (value == null || value.length > 128) return null;
    final seconds = int.tryParse(value.trim());
    if (seconds != null && seconds >= 0 && seconds <= 60) {
      return Duration(seconds: seconds);
    }
    try {
      final duration = HttpDate.parse(value).difference(_now().toUtc());
      if (!duration.isNegative && duration <= const Duration(seconds: 60)) {
        return duration;
      }
    } on Object {
      return null;
    }
    return null;
  }
}

T _protocol<T>(T Function() decode) {
  try {
    return decode();
  } on OrganizationsFailure {
    rethrow;
  } on Object {
    throw const OrganizationsFailure(
      OrganizationsFailureCode.internalError,
      'Sunucu yanıtı güvenli biçimde doğrulanamadı.',
    );
  }
}

OrganizationsFailureCode? _mappedError(String code, int status) =>
    switch ((code, status)) {
      ('UNAUTHENTICATED', 401) => OrganizationsFailureCode.unauthenticated,
      ('SESSION_REVOKED', 401) => OrganizationsFailureCode.sessionRevoked,
      ('FORBIDDEN', 403) => OrganizationsFailureCode.forbidden,
      ('ORGANIZATION_CONTEXT_REQUIRED', 403) =>
        OrganizationsFailureCode.organizationContextRequired,
      ('ACCOUNT_NOT_READY', 403) => OrganizationsFailureCode.forbidden,
      ('RESOURCE_NOT_FOUND', 404) => OrganizationsFailureCode.resourceNotFound,
      ('INVALID_CURSOR', 400) => OrganizationsFailureCode.invalidCursor,
      ('VALIDATION_FAILED', 422) => OrganizationsFailureCode.validationFailed,
      ('VERSION_CONFLICT', 409) => OrganizationsFailureCode.versionConflict,
      ('STATE_CONFLICT', 409) => OrganizationsFailureCode.stateConflict,
      ('IDEMPOTENCY_KEY_REUSED', 409) =>
        OrganizationsFailureCode.idempotencyKeyReused,
      ('RATE_LIMITED', 429) => OrganizationsFailureCode.rateLimited,
      ('INTERNAL_ERROR', 500) => OrganizationsFailureCode.internalError,
      _ => null,
    };

String _safeMessage(OrganizationsFailureCode code) => switch (code) {
  OrganizationsFailureCode.unauthenticated ||
  OrganizationsFailureCode.sessionRevoked =>
    'Oturumunuz sona erdi. Lütfen yeniden giriş yapın.',
  OrganizationsFailureCode.forbidden ||
  OrganizationsFailureCode.organizationContextRequired ||
  OrganizationsFailureCode.resourceNotFound => 'Bu işlem için yetkiniz yok.',
  OrganizationsFailureCode.invalidCursor =>
    'Sayfalama bilgisi geçersiz. Listeyi yenileyin.',
  OrganizationsFailureCode.validationFailed =>
    'Gönderilen bilgiler doğrulanamadı.',
  OrganizationsFailureCode.idempotencyKeyReused =>
    'Bu işlem anahtarı farklı bilgilerle kullanılmış.',
  OrganizationsFailureCode.versionConflict =>
    'Ayarlar başka bir işlemle değişti. Yenileyip tekrar deneyin.',
  OrganizationsFailureCode.stateConflict =>
    'Kurumun mevcut durumu bu işleme izin vermiyor.',
  OrganizationsFailureCode.rateLimited =>
    'Çok fazla istek gönderildi. Bir süre sonra tekrar deneyin.',
  OrganizationsFailureCode.internalError =>
    'İşlem şu anda tamamlanamadı. Lütfen yeniden deneyin.',
  OrganizationsFailureCode.transientNetwork =>
    'Sunucuya ulaşılamıyor. Lütfen yeniden deneyin.',
};

Map<String, dynamic> _jsonObject(String body) {
  final decoded = jsonDecode(body);
  if (decoded is! Map<String, dynamic>) throw const FormatException();
  return decoded;
}

Map<String, dynamic> _object(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! Map<String, dynamic>) throw const FormatException();
  return value;
}

List<Object?> _list(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! List) throw const FormatException();
  return value;
}

String _string(Map<String, dynamic> json, String key, {required int max}) {
  final value = json[key];
  if (value is! String ||
      value.isEmpty ||
      value.length > max ||
      value.codeUnits.any((unit) => unit < 0x20 && unit != 0x09)) {
    throw const FormatException();
  }
  return value;
}

String? _nullableString(
  Map<String, dynamic> json,
  String key, {
  required int max,
}) {
  final value = json[key];
  if (value == null) return null;
  return _string(json, key, max: max);
}

String _uuid(Map<String, dynamic> json, String key) {
  final value = _string(json, key, max: 36);
  if (!_isUuid(value)) throw const FormatException();
  return value;
}

bool _bool(Map<String, dynamic> json, String key) {
  final value = json[key];
  if (value is! bool) throw const FormatException();
  return value;
}

int _positiveInt(Map<String, dynamic> json, String key) =>
    _boundedInt(json, key, 1, 0x7fffffff);

int _boundedInt(Map<String, dynamic> json, String key, int min, int max) {
  final value = json[key];
  if (value is! int || value < min || value > max) {
    throw const FormatException();
  }
  return value;
}

DateTime _utcDate(Map<String, dynamic> json, String key) {
  final value = DateTime.tryParse(_string(json, key, max: 64));
  if (value == null || !value.isUtc) throw const FormatException();
  return value;
}

bool _sameOrigin(Uri left, Uri right) =>
    left.scheme == right.scheme &&
    left.host == right.host &&
    left.port == right.port;

bool _validVisibleAscii(String value, int max) =>
    value.isNotEmpty &&
    value.length <= max &&
    value.codeUnits.every((unit) => unit >= 0x21 && unit <= 0x7e);

bool _isUuid(String value) => RegExp(
  r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$',
).hasMatch(value);

bool _isSortedColors(List<OrganizationBrandColor> items) {
  for (var index = 1; index < items.length; index++) {
    final previous = items[index - 1];
    final current = items[index];
    if (previous.sortOrder > current.sortOrder ||
        (previous.sortOrder == current.sortOrder &&
            previous.colorHex.compareTo(current.colorHex) > 0)) {
      return false;
    }
  }
  return true;
}

bool _isSortedModules(List<OrganizationModule> items) {
  for (var index = 1; index < items.length; index++) {
    if (items[index - 1].sortOrder > items[index].sortOrder) return false;
  }
  return true;
}

String _randomUuid() {
  final bytes = List<int>.generate(16, (_) => Random.secure().nextInt(256));
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  final hex = bytes.map((b) => b.toRadixString(16).padLeft(2, '0')).join();
  return '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
      '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
      '${hex.substring(20)}';
}
