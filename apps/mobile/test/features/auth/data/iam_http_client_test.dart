import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/features/auth/data/iam_http_client.dart';

const _userId = '8c728d4e-4e84-4b22-81f9-ec92eb03fa6b';
const _deviceId = 'c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe';
const _membershipId = '5276942f-ef2f-44ca-9ef6-2af1dd58a0fc';
const _organizationId = '4a9cc266-53f0-4dd6-a388-9b13fcaaf3db';

final _config = AppRuntimeConfig.fromValues(
  environment: 'development',
  publicApiBaseUrl: 'https://api.example.invalid',
  cognitoIssuerUri:
      'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
  cognitoClientId: 'public-client',
);

class _FakeTransport implements IamTransport {
  final requests = <IamRequest>[];
  final responses = <Object>[];

  @override
  Future<IamResponse> send(IamRequest request) async {
    requests.add(request);
    final result = responses.removeAt(0);
    if (result is IamResponse) return result;
    throw result;
  }
}

IamResponse _jsonResponse(Object body, {int status = 200}) => IamResponse(
  statusCode: status,
  headers: const <String, String>{},
  body: jsonEncode(body),
);

Map<String, Object?> get _membership => <String, Object?>{
  'id': _membershipId,
  'organizationId': _organizationId,
  'organizationName': 'Fındıklı Kur’an Kursu',
  'organizationStatus': 'ACTIVE',
  'membershipStatus': 'ACTIVE',
  'roleCodes': <String>['ORG_ADMIN'],
  'sessionGeneration': 3,
};

Map<String, Object?> _session({
  required String scope,
  bool tokens = true,
  bool identity = true,
}) => <String, Object?>{
  if (identity)
    'user': <String, Object?>{
      'id': _userId,
      'displayName': 'Yasir',
      'status': 'ACTIVE',
    },
  if (identity)
    'device': <String, Object?>{
      'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
      'deviceIdentifier': _deviceId,
      'platform': 'ANDROID',
      'deviceName': 'Kurs Platform Android',
      'trustedAt': '2026-07-27T09:00:00Z',
    },
  if (scope == 'ORGANIZATION') 'organizationMembership': _membership,
  if (scope == 'GLOBAL_PLATFORM_ADMIN' && identity)
    'platformAdministrator': <String, Object?>{'status': 'ACTIVE'},
  'session': <String, Object?>{
    'scope': scope,
    if (tokens) 'accessToken': 'platform-access',
    if (tokens) 'refreshToken': 'platform-refresh',
    if (tokens) 'tokenType': 'Bearer',
    'expiresAt': '2026-07-27T10:00:00Z',
    if (tokens) 'refreshExpiresAt': '2026-08-27T10:00:00Z',
    'authenticatedAt': '2026-07-27T09:00:00Z',
  },
};

void main() {
  test(
    'seven IAM endpoints use exact methods, paths, auth and bodies',
    () async {
      final transport = _FakeTransport()
        ..responses.addAll(<Object>[
          _jsonResponse(<String, Object?>{
            'contextSelectionToken': 'context-token',
            'contextSelectionTokenExpiresAt': '2026-07-27T09:05:00Z',
            'availableScopes': <String>[
              'ORGANIZATION_SELECTION',
              'GLOBAL_PLATFORM_ADMIN',
            ],
            'user': <String, Object?>{
              'id': _userId,
              'displayName': 'Yasir',
              'status': 'ACTIVE',
            },
            'platformAdministrator': <String, Object?>{'status': 'ACTIVE'},
            'device': <String, Object?>{
              'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
              'deviceIdentifier': _deviceId,
              'platform': 'ANDROID',
              'trustedAt': '2026-07-27T09:00:00Z',
            },
          }),
          _jsonResponse(<String, Object?>{
            'items': <Object>[_membership],
            'page': <String, Object?>{'nextCursor': null, 'hasNextPage': false},
          }),
          _jsonResponse(_session(scope: 'GLOBAL_PLATFORM_ADMIN')),
          _jsonResponse(_session(scope: 'ORGANIZATION')),
          _jsonResponse(_session(scope: 'ORGANIZATION', tokens: false)),
          _jsonResponse(_session(scope: 'ORGANIZATION', identity: false)),
          const IamResponse(
            statusCode: 204,
            headers: <String, String>{},
            body: '',
          ),
        ]);
      final client = IamHttpClient(config: _config, transport: transport);

      await client.exchangeProviderToken(
        'provider-access',
        const DeviceRegistration(identifier: _deviceId, platform: 'ANDROID'),
      );
      await client.contextSelections('context-token');
      await client.activatePlatformAdministrator('context-token');
      await client.activateOrganization('context-token', _membershipId);
      await client.sessionMe('platform-access');
      await client.refresh('platform-refresh');
      await client.logout('platform-access', 'platform-refresh');

      expect(transport.requests.map((request) => request.method), <String>[
        'POST',
        'GET',
        'POST',
        'POST',
        'GET',
        'POST',
        'POST',
      ]);
      expect(transport.requests.map((request) => request.uri.path), <String>[
        '/api/v1/iam/auth/provider-token-exchange',
        '/api/v1/iam/auth/context-selections',
        '/api/v1/iam/auth/platform-admin/activate',
        '/api/v1/iam/auth/context-selections/$_membershipId/activate',
        '/api/v1/iam/sessions/me',
        '/api/v1/iam/sessions/refresh',
        '/api/v1/iam/sessions/logout',
      ]);
      expect(
        transport.requests.first.headers['Authorization'],
        'Bearer provider-access',
      );
      expect(
        transport.requests[1].headers['Authorization'],
        'Bearer context-token',
      );
      expect(
        jsonDecode(transport.requests[5].body!)['refreshToken'],
        'platform-refresh',
      );
      for (final index in <int>[0, 2, 3, 5, 6]) {
        expect(
          transport.requests[index].headers['Idempotency-Key'],
          isNotEmpty,
        );
      }
    },
  );

  test('network retry preserves request and idempotency identifiers', () async {
    final transport = _FakeTransport()
      ..responses.addAll(<Object>[
        const IamTransportException(),
        _jsonResponse(_session(scope: 'ORGANIZATION', identity: false)),
      ]);
    final client = IamHttpClient(config: _config, transport: transport);

    await client.refresh('refresh-once');

    expect(transport.requests, hasLength(2));
    expect(
      transport.requests[0].headers['X-Request-Id'],
      transport.requests[1].headers['X-Request-Id'],
    );
    expect(
      transport.requests[0].headers['Idempotency-Key'],
      transport.requests[1].headers['Idempotency-Key'],
    );
  });

  test(
    'Retry-After is bounded, valid waits and malformed does not wait',
    () async {
      final waits = <Duration>[];
      final transport = _FakeTransport()
        ..responses.addAll(<Object>[
          IamResponse(
            statusCode: 429,
            headers: const <String, String>{'retry-after': '2'},
            body: jsonEncode(<String, Object?>{
              'error': <String, Object?>{
                'code': 'RATE_LIMITED',
                'message': 'safe',
                'requestId': 'request',
              },
            }),
          ),
          _jsonResponse(_session(scope: 'ORGANIZATION', identity: false)),
          IamResponse(
            statusCode: 429,
            headers: const <String, String>{'retry-after': 'not-a-date'},
            body: jsonEncode(<String, Object?>{
              'error': <String, Object?>{'code': 'RATE_LIMITED'},
            }),
          ),
          _jsonResponse(_session(scope: 'ORGANIZATION', identity: false)),
        ]);
      final client = IamHttpClient(
        config: _config,
        transport: transport,
        delay: (duration) async => waits.add(duration),
      );

      await client.refresh('one');
      await client.refresh('two');

      expect(waits, <Duration>[const Duration(seconds: 2)]);
    },
  );

  test(
    'approved error codes are typed and unknown or malformed fail closed',
    () async {
      final cases = <(String, int, IamErrorCode)>[
        ('INVALID_REQUEST', 400, IamErrorCode.invalidRequest),
        ('VALIDATION_FAILED', 422, IamErrorCode.invalidRequest),
        ('UNAUTHENTICATED', 401, IamErrorCode.unauthenticated),
        ('SESSION_REVOKED', 401, IamErrorCode.sessionRevoked),
        ('FORBIDDEN', 403, IamErrorCode.forbidden),
        (
          'ORGANIZATION_CONTEXT_REQUIRED',
          403,
          IamErrorCode.organizationContextRequired,
        ),
        ('ACCOUNT_NOT_READY', 403, IamErrorCode.accountNotReady),
        (
          'REAUTHENTICATION_REQUIRED',
          403,
          IamErrorCode.reauthenticationRequired,
        ),
        ('RESOURCE_NOT_FOUND', 404, IamErrorCode.resourceNotFound),
        ('STATE_CONFLICT', 409, IamErrorCode.stateConflict),
        ('IDEMPOTENCY_KEY_REUSED', 409, IamErrorCode.idempotencyKeyReused),
        ('RATE_LIMITED', 429, IamErrorCode.rateLimited),
        ('PROVIDER_UNAVAILABLE', 503, IamErrorCode.providerUnavailable),
        ('INTERNAL_ERROR', 500, IamErrorCode.internalError),
        ('unknown', 500, IamErrorCode.internalError),
      ];
      for (final (wireCode, status, expectedCode) in cases) {
        final response = IamResponse(
          statusCode: status,
          headers: const <String, String>{},
          body: jsonEncode(<String, Object?>{
            'error': <String, Object?>{'code': wireCode},
          }),
        );
        final transport = _FakeTransport()..responses.add(response);
        if (status == 429 || status == 503) transport.responses.add(response);
        final client = IamHttpClient(config: _config, transport: transport);
        await expectLater(
          client.refresh('refresh'),
          throwsA(
            isA<IamApiException>().having(
              (error) => error.code,
              'code',
              expectedCode,
            ),
          ),
        );
      }

      final malformed = _FakeTransport()
        ..responses.add(_jsonResponse(<String>[]));
      await expectLater(
        IamHttpClient(config: _config, transport: malformed).refresh('refresh'),
        throwsA(
          isA<IamApiException>().having(
            (error) => error.code,
            'code',
            IamErrorCode.internalError,
          ),
        ),
      );
    },
  );

  test('inconsistent statuses and unsafe auth headers fail closed', () async {
    final inconsistent = _FakeTransport()
      ..responses.add(
        IamResponse(
          statusCode: 500,
          headers: const <String, String>{},
          body: jsonEncode(<String, Object?>{
            'error': <String, Object?>{'code': 'FORBIDDEN'},
          }),
        ),
      );
    await expectLater(
      IamHttpClient(config: _config, transport: inconsistent).refresh('token'),
      throwsA(
        isA<IamApiException>().having(
          (error) => error.code,
          'code',
          IamErrorCode.internalError,
        ),
      ),
    );

    final transport = _FakeTransport();
    final client = IamHttpClient(config: _config, transport: transport);
    await expectLater(
      client.contextSelections(' token'),
      throwsA(isA<FormatException>()),
    );
    await expectLater(
      client.contextSelections('token\nforged'),
      throwsA(isA<FormatException>()),
    );
    await expectLater(
      client.refresh('token', idempotencyKey: 'not-a-uuid'),
      throwsA(isA<FormatException>()),
    );
    expect(transport.requests, isEmpty);
  });
}
