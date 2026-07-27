import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/core/network/authenticated_api_session.dart';
import 'package:kurs_platform_mobile/core/network/safe_http_transport.dart';
import 'package:kurs_platform_mobile/features/organizations/data/production_organizations_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';

const _orgId = '123e4567-e89b-42d3-a456-426614174000';
const _org = <String, Object?>{
  'id': _orgId,
  'name': 'İstanbul Kur’an Kursu',
  'shortName': 'İstanbul',
  'defaultTimezone': 'Europe/Istanbul',
  'status': 'ACTIVE',
  'createdAt': '2026-07-27T08:00:00Z',
  'updatedAt': '2026-07-27T08:00:00Z',
  'rowVersion': 1,
};

final _config = AppRuntimeConfig.fromValues(
  environment: 'production',
  publicApiBaseUrl: 'https://api.example.test',
  cognitoIssuerUri: 'https://cognito-idp.eu-central-1.amazonaws.com/pool',
  cognitoClientId: 'public-client',
);

class _Session implements AuthenticatedApiSession {
  String token = 'access-old';
  String replacement = 'access-new';
  int refreshes = 0;
  int terminations = 0;
  bool available = true;

  @override
  String get identityKey => available ? 'user:global:1' : '';

  @override
  Future<T> run<T>(Future<T> Function(String bearerToken) operation) {
    if (!available) {
      throw const AuthenticatedApiSessionUnavailable(terminal: true);
    }
    return operation(token);
  }

  @override
  Future<T> refreshAndRun<T>(Future<T> Function(String bearerToken) operation) {
    refreshes++;
    token = replacement;
    return operation(token);
  }

  @override
  Future<void> terminate() async {
    terminations++;
    available = false;
  }
}

class _Transport implements SafeHttpTransport {
  _Transport(this.handler);
  final FutureOr<SafeHttpResponse> Function(SafeHttpRequest request) handler;
  final requests = <SafeHttpRequest>[];

  @override
  Future<SafeHttpResponse> send(SafeHttpRequest request) async {
    requests.add(request);
    return handler(request);
  }
}

SafeHttpResponse _response(
  int status,
  Object body, {
  Map<String, String> headers = const {},
}) => SafeHttpResponse(
  statusCode: status,
  headers: <String, String>{'content-type': 'application/json', ...headers},
  body: body is String ? body : jsonEncode(body),
);

Map<String, Object?> _error(String code, {List<Object?>? fieldErrors}) => {
  'error': {
    'code': code,
    'message': 'Sunucu ayrıntısı gösterilmemeli.',
    'requestId': 'request-1',
    'fieldErrors': ?fieldErrors,
  },
};

ProductionOrganizationsRepository _repository(
  _Session session,
  _Transport transport, {
  bool global = true,
}) => ProductionOrganizationsRepository(
  config: _config,
  session: session,
  transport: transport,
  globalListScope: global,
  now: () => DateTime.utc(2026, 7, 27, 8),
);

void main() {
  test(
    'global list preserves opaque cursor and emits safe request metadata',
    () async {
      final session = _Session();
      final transport = _Transport(
        (_) => _response(200, {
          'items': [_org],
          'page': {'nextCursor': 'opaque+/=cursor', 'hasNextPage': true},
        }),
      );
      final result = await _repository(session, transport).listOrganizations(
        const OrganizationListQuery(
          status: OrganizationStatus.active,
          search: ' İstanbul ',
          sort: OrganizationSortField.createdAt,
          order: OrganizationSortOrder.descending,
          limit: 20,
          cursor: 'opaque+/=input',
        ),
      );

      expect(result.items.single.id, _orgId);
      expect(result.nextCursor, 'opaque+/=cursor');
      final request = transport.requests.single;
      expect(request.method, 'GET');
      expect(request.uri.queryParameters, {
        'status': 'ACTIVE',
        'search': 'İstanbul',
        'sort': 'createdAt',
        'order': 'DESC',
        'limit': '20',
        'cursor': 'opaque+/=input',
      });
      expect(request.headers['Authorization'], 'Bearer access-old');
      expect(
        request.headers['X-Request-Id'],
        matches(RegExp(r'^[0-9a-f-]{36}$')),
      );
      expect(request.headers, isNot(contains('Idempotency-Key')));
    },
  );

  test('organization scope list sends no forbidden query parameters', () async {
    final transport = _Transport(
      (_) => _response(200, {
        'items': [_org],
        'page': {'nextCursor': null, 'hasNextPage': false},
      }),
    );
    await _repository(
      _Session(),
      transport,
      global: false,
    ).listOrganizations(const OrganizationListQuery());
    expect(transport.requests.single.uri.hasQuery, isFalse);
  });

  test('create validates Location, 201 and stable idempotency key', () async {
    final transport = _Transport(
      (_) => _response(
        201,
        _org,
        headers: {'location': '/api/v1/organizations/$_orgId'},
      ),
    );
    final repository = _repository(_Session(), transport);
    const request = OrganizationCreateRequest(
      name: ' İstanbul Kur’an Kursu ',
      shortName: ' İstanbul ',
      defaultTimezone: '',
      clientMutationId: '123e4567-e89b-42d3-a456-426614174001',
    );
    await repository.createOrganization(request);
    await repository.createOrganization(request);

    expect(transport.requests, hasLength(2));
    for (final sent in transport.requests) {
      expect(
        sent.headers['Idempotency-Key'],
        '123e4567-e89b-42d3-a456-426614174001',
      );
      expect(sent.headers, isNot(contains('If-Match-Row-Version')));
      expect(jsonDecode(sent.body!), {
        'name': 'İstanbul Kur’an Kursu',
        'shortName': 'İstanbul',
        'defaultTimezone': 'Europe/Istanbul',
      });
    }
  });

  test('create rejects wrong success status or Location fail closed', () async {
    for (final response in [
      _response(200, _org),
      _response(201, _org, headers: {'location': '/wrong'}),
    ]) {
      final repository = _repository(_Session(), _Transport((_) => response));
      await expectLater(
        repository.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurs',
            clientMutationId: '123e4567-e89b-42d3-a456-426614174001',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.internalError,
          ),
        ),
      );
    }
  });

  test('brand, palette and modules enforce ETag and write headers', () async {
    final responses = <SafeHttpResponse>[
      _response(
        200,
        {
          'primaryColor': '#2E7D32',
          'secondaryColor': '#E65100',
          'rowVersion': 7,
          'logo': null,
        },
        headers: {'etag': '"7"'},
      ),
      _response(
        200,
        {
          'rowVersion': 8,
          'items': [
            {'colorHex': '#4CAF50', 'sortOrder': 0},
          ],
        },
        headers: {'etag': '"8"'},
      ),
      _response(
        200,
        {
          'rowVersion': 9,
          'items': [
            for (final entry in const [
              ('ATT', true),
              ('PROGRAM', true),
              ('CONTENT', false),
              ('PROGRESS', true),
              ('EXPORT', true),
              ('AUDIT', true),
            ].indexed)
              {
                'moduleCode': entry.$2.$1,
                'isEnabled': entry.$2.$2,
                'sortOrder': entry.$1,
              },
          ],
        },
        headers: {'etag': '"9"'},
      ),
    ];
    final transport = _Transport((_) => responses.removeAt(0));
    final repository = _repository(_Session(), transport);

    await repository.updateBrand(
      _orgId,
      const OrganizationBrand(
        primaryColor: '#2E7D32',
        secondaryColor: '#E65100',
        rowVersion: 7,
      ),
      'brand-key',
    );
    await repository.replaceBrandColors(
      _orgId,
      OrganizationBrandColors(
        rowVersion: 8,
        items: const [
          OrganizationBrandColor(colorHex: '#4CAF50', sortOrder: 0),
        ],
      ),
      'palette-key',
    );
    await repository.updateModules(
      _orgId,
      OrganizationModules(
        rowVersion: 9,
        items: [
          for (final entry in OrganizationModuleCode.values.indexed)
            OrganizationModule(
              code: entry.$2,
              isEnabled: entry.$2 != OrganizationModuleCode.content,
              sortOrder: entry.$1,
            ),
        ],
      ),
      'modules-key',
    );

    expect(transport.requests.map((request) => request.method), [
      'PATCH',
      'PUT',
      'PATCH',
    ]);
    expect(
      transport.requests.map(
        (request) => request.headers['If-Match-Row-Version'],
      ),
      ['7', '8', '9'],
    );
    expect(
      transport.requests.map((request) => request.headers['Idempotency-Key']),
      ['brand-key', 'palette-key', 'modules-key'],
    );
  });

  test(
    'all six file-free brand endpoints use their exact methods and paths',
    () async {
      final brand = _response(
        200,
        {
          'primaryColor': '#2E7D32',
          'secondaryColor': '#E65100',
          'rowVersion': 7,
          'logo': null,
        },
        headers: {'etag': '"7"'},
      );
      final palette = _response(
        200,
        {'rowVersion': 7, 'items': const <Object?>[]},
        headers: {'etag': '"7"'},
      );
      final modules = _response(
        200,
        {
          'rowVersion': 7,
          'items': [
            for (final entry in const [
              ('ATT', true),
              ('PROGRAM', true),
              ('CONTENT', false),
              ('PROGRESS', true),
              ('EXPORT', true),
              ('AUDIT', true),
            ].indexed)
              {
                'moduleCode': entry.$2.$1,
                'isEnabled': entry.$2.$2,
                'sortOrder': entry.$1,
              },
          ],
        },
        headers: {'etag': '"7"'},
      );
      final responses = [brand, brand, palette, palette, modules, modules];
      final transport = _Transport((_) => responses.removeAt(0));
      final repository = _repository(_Session(), transport);

      await repository.getBrand(_orgId);
      await repository.updateBrand(
        _orgId,
        const OrganizationBrand(
          primaryColor: '#2E7D32',
          secondaryColor: '#E65100',
          rowVersion: 7,
        ),
        'brand',
      );
      await repository.getBrandColors(_orgId);
      await repository.replaceBrandColors(
        _orgId,
        OrganizationBrandColors(rowVersion: 7, items: const []),
        'palette',
      );
      await repository.getModules(_orgId);
      await repository.updateModules(
        _orgId,
        OrganizationModules(
          rowVersion: 7,
          items: [
            for (final entry in OrganizationModuleCode.values.indexed)
              OrganizationModule(
                code: entry.$2,
                isEnabled: entry.$2 != OrganizationModuleCode.content,
                sortOrder: entry.$1,
              ),
          ],
        ),
        'modules',
      );

      expect(
        transport.requests.map(
          (request) => '${request.method} ${request.uri.path}',
        ),
        [
          'GET /api/v1/organizations/$_orgId/brand',
          'PATCH /api/v1/organizations/$_orgId/brand',
          'GET /api/v1/organizations/$_orgId/brand-colors',
          'PUT /api/v1/organizations/$_orgId/brand-colors',
          'GET /api/v1/organizations/$_orgId/modules',
          'PATCH /api/v1/organizations/$_orgId/modules',
        ],
      );
    },
  );

  test('missing or inconsistent ETag is an INTERNAL_ERROR', () async {
    final repository = _repository(
      _Session(),
      _Transport(
        (_) => _response(
          200,
          {
            'primaryColor': '#2E7D32',
            'secondaryColor': '#E65100',
            'rowVersion': 7,
            'logo': null,
          },
          headers: {'etag': '"6"'},
        ),
      ),
    );
    await expectLater(
      repository.getBrand(_orgId),
      throwsA(
        isA<OrganizationsFailure>().having(
          (failure) => failure.code,
          'code',
          OrganizationsFailureCode.internalError,
        ),
      ),
    );
  });

  test('status and error-code mismatch fails closed', () async {
    final repository = _repository(
      _Session(),
      _Transport((_) => _response(403, _error('RATE_LIMITED'))),
    );
    await expectLater(
      repository.listOrganizations(const OrganizationListQuery()),
      throwsA(
        isA<OrganizationsFailure>().having(
          (failure) => failure.code,
          'code',
          OrganizationsFailureCode.internalError,
        ),
      ),
    );
  });

  test('contract status/error matrix remains typed and fail closed', () async {
    const cases = <(int, String, OrganizationsFailureCode)>[
      (401, 'SESSION_REVOKED', OrganizationsFailureCode.sessionRevoked),
      (403, 'FORBIDDEN', OrganizationsFailureCode.forbidden),
      (404, 'RESOURCE_NOT_FOUND', OrganizationsFailureCode.resourceNotFound),
      (409, 'VERSION_CONFLICT', OrganizationsFailureCode.versionConflict),
      (409, 'STATE_CONFLICT', OrganizationsFailureCode.stateConflict),
      (
        409,
        'IDEMPOTENCY_KEY_REUSED',
        OrganizationsFailureCode.idempotencyKeyReused,
      ),
      (422, 'VALIDATION_FAILED', OrganizationsFailureCode.validationFailed),
      (429, 'RATE_LIMITED', OrganizationsFailureCode.rateLimited),
      (500, 'INTERNAL_ERROR', OrganizationsFailureCode.internalError),
    ];
    for (final entry in cases) {
      final repository = _repository(
        _Session(),
        _Transport(
          (_) => _response(
            entry.$1,
            _error(entry.$2),
            headers: entry.$1 == 429 ? const {'retry-after': '17'} : const {},
          ),
        ),
      );
      await expectLater(
        repository.listOrganizations(const OrganizationListQuery()),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            entry.$2,
            entry.$3,
          ),
        ),
      );
    }
  });

  test('Retry-After is bounded and exposed only for 429', () async {
    final repository = _repository(
      _Session(),
      _Transport(
        (_) => _response(
          429,
          _error('RATE_LIMITED'),
          headers: {'retry-after': '17'},
        ),
      ),
    );
    await expectLater(
      repository.listOrganizations(const OrganizationListQuery()),
      throwsA(
        isA<OrganizationsFailure>()
            .having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.rateLimited,
            )
            .having(
              (failure) => failure.retryAfter,
              'retryAfter',
              const Duration(seconds: 17),
            ),
      ),
    );
  });

  test(
    'success and error responses require one valid JSON Content-Type',
    () async {
      for (final contentType in <String>[
        'application/json',
        'application/json; charset=UTF-8',
        'Application/JSON; charset="utf-8"',
      ]) {
        final repository = _repository(
          _Session(),
          _Transport(
            (_) => _response(
              200,
              {
                'items': [_org],
                'page': {'nextCursor': null, 'hasNextPage': false},
              },
              headers: {'content-type': contentType},
            ),
          ),
        );
        expect(
          (await repository.listOrganizations(
            const OrganizationListQuery(),
          )).items,
          hasLength(1),
        );
      }

      for (final contentType in <String?>[
        null,
        'text/plain',
        'text/html',
        'application/json, text/plain',
        'application/json; charset=utf-8; charset=iso-8859-1',
        'application/json; broken',
      ]) {
        final headers = <String, String>{};
        if (contentType != null) headers['content-type'] = contentType;
        final response = SafeHttpResponse(
          statusCode: 403,
          headers: headers,
          body: jsonEncode(_error('FORBIDDEN')),
        );
        final repository = _repository(_Session(), _Transport((_) => response));
        await expectLater(
          repository.listOrganizations(const OrganizationListQuery()),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.internalError,
            ),
          ),
        );
      }
    },
  );

  test('429 accepts only positive bounded Retry-After values', () async {
    for (final entry in <(String, Duration)>[
      ('1', const Duration(seconds: 1)),
      ('60', const Duration(seconds: 60)),
      ('Mon, 27 Jul 2026 08:00:30 GMT', const Duration(seconds: 30)),
    ]) {
      final repository = _repository(
        _Session(),
        _Transport(
          (_) => _response(
            429,
            _error('RATE_LIMITED'),
            headers: {'retry-after': entry.$1},
          ),
        ),
      );
      await expectLater(
        repository.listOrganizations(const OrganizationListQuery()),
        throwsA(
          isA<OrganizationsFailure>()
              .having(
                (failure) => failure.code,
                'code',
                OrganizationsFailureCode.rateLimited,
              )
              .having((failure) => failure.retryAfter, 'retryAfter', entry.$2),
        ),
      );
    }

    for (final value in <String?>[
      null,
      '0',
      '-1',
      '61',
      'not-a-date',
      'Mon, 27 Jul 2026 07:59:59 GMT',
    ]) {
      final headers = <String, String>{};
      if (value != null) headers['retry-after'] = value;
      final repository = _repository(
        _Session(),
        _Transport(
          (_) => _response(429, _error('RATE_LIMITED'), headers: headers),
        ),
      );
      await expectLater(
        repository.listOrganizations(const OrganizationListQuery()),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.internalError,
          ),
        ),
      );
    }
  });

  test(
    '401 UNAUTHENTICATED refreshes once and retries with new token',
    () async {
      final session = _Session();
      var calls = 0;
      final transport = _Transport((request) {
        calls++;
        if (calls == 1) return _response(401, _error('UNAUTHENTICATED'));
        expect(request.headers['Authorization'], 'Bearer access-new');
        return _response(200, {
          'items': [_org],
          'page': {'nextCursor': null, 'hasNextPage': false},
        });
      });

      final result = await _repository(
        session,
        transport,
      ).listOrganizations(const OrganizationListQuery());
      expect(result.items, hasLength(1));
      expect(session.refreshes, 1);
      expect(
        transport.requests[0].headers['X-Request-Id'],
        isNot(transport.requests[1].headers['X-Request-Id']),
      );
    },
  );

  test(
    'SESSION_REVOKED terminates secure workspace and leaks no token',
    () async {
      final session = _Session();
      final repository = _repository(
        session,
        _Transport((_) => _response(401, _error('SESSION_REVOKED'))),
      );
      late Object caught;
      try {
        await repository.listOrganizations(const OrganizationListQuery());
        fail('expected failure');
      } catch (error) {
        caught = error;
      }
      expect(session.terminations, 1);
      expect(caught, isA<OrganizationsFailure>());
      expect(caught.toString(), isNot(contains('access-old')));
    },
  );

  test(
    'malformed response and transport/redirect rejection stay retryable or closed',
    () async {
      final malformed = _repository(
        _Session(),
        _Transport((_) => _response(200, '{not-json')),
      );
      await expectLater(
        malformed.listOrganizations(const OrganizationListQuery()),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.internalError,
          ),
        ),
      );

      final rejected = _repository(
        _Session(),
        _Transport((_) => throw const SafeHttpTransportException()),
      );
      await expectLater(
        rejected.listOrganizations(const OrganizationListQuery()),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.transientNetwork,
          ),
        ),
      );
    },
  );

  test(
    '422 maps only known create fields and preserves safe messages',
    () async {
      final repository = _repository(
        _Session(),
        _Transport(
          (_) => _response(
            422,
            _error(
              'VALIDATION_FAILED',
              fieldErrors: [
                {
                  'field': 'name',
                  'code': 'REQUIRED',
                  'message': 'Kurum adı zorunludur.',
                },
                {
                  'field': 'unknown',
                  'code': 'INVALID',
                  'message': 'Gizli ayrıntı',
                },
              ],
            ),
          ),
        ),
      );
      await expectLater(
        repository.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurs',
            clientMutationId: '123e4567-e89b-42d3-a456-426614174001',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>()
              .having(
                (failure) => failure.fieldErrors?.name,
                'name error',
                'Kurum adı zorunludur.',
              )
              .having(
                (failure) => failure.message,
                'safe message',
                'Gönderilen bilgiler doğrulanamadı.',
              ),
        ),
      );
    },
  );
}
