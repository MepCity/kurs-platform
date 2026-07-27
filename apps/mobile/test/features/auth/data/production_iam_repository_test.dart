import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/config/app_runtime_config.dart';
import 'package:kurs_platform_mobile/features/auth/data/appauth_provider_client.dart';
import 'package:kurs_platform_mobile/features/auth/data/device_identity.dart';
import 'package:kurs_platform_mobile/features/auth/data/iam_http_client.dart';
import 'package:kurs_platform_mobile/features/auth/data/production_iam_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/session_repository.dart';

const _user = '8c728d4e-4e84-4b22-81f9-ec92eb03fa6b';
const _device = 'c8df0ed0-0ff5-412d-ae90-6f56d1d3edfe';
const _membership = '5276942f-ef2f-44ca-9ef6-2af1dd58a0fc';
const _organization = '4a9cc266-53f0-4dd6-a388-9b13fcaaf3db';

final _productionConfig = AppRuntimeConfig.fromValues(
  environment: 'development',
  publicApiBaseUrl: 'https://api.example.invalid',
  cognitoIssuerUri:
      'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
  cognitoClientId: 'public',
);

class _Provider implements ProviderAuthorizationClient {
  int calls = 0;
  Object result = 'provider-only-access';
  @override
  Future<String> authorize() async {
    calls++;
    if (result is String) return result as String;
    throw result;
  }
}

class _Device implements DeviceIdentity {
  @override
  Future<DeviceRegistration> get() async => const DeviceRegistration(
    identifier: _device,
    platform: 'ANDROID',
    name: 'Kurs Platform Android',
  );
}

class _Transport implements IamTransport {
  _Transport({this.wrongDevice = false});
  final bool wrongDevice;
  final requests = <IamRequest>[];

  @override
  Future<IamResponse> send(IamRequest request) async {
    requests.add(request);
    final body = switch (request.uri.path) {
      '/api/v1/iam/auth/provider-token-exchange' => <String, Object?>{
        'contextSelectionToken': 'memory-context-token',
        'contextSelectionTokenExpiresAt': '2026-07-27T09:05:00Z',
        'availableScopes': <String>[
          'ORGANIZATION_SELECTION',
          'GLOBAL_PLATFORM_ADMIN',
        ],
        'user': <String, Object?>{
          'id': _user,
          'displayName': 'Yasir',
          'status': 'ACTIVE',
        },
        'platformAdministrator': <String, Object?>{'status': 'ACTIVE'},
        'device': <String, Object?>{
          'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
          'deviceIdentifier': wrongDevice
              ? 'a8df0ed0-0ff5-412d-ae90-6f56d1d3edfe'
              : _device,
          'platform': 'ANDROID',
          'trustedAt': '2026-07-27T09:00:00Z',
        },
      },
      '/api/v1/iam/auth/context-selections' => <String, Object?>{
        'items': <Object>[
          <String, Object?>{
            'id': _membership,
            'organizationId': _organization,
            'organizationName': 'Fındıklı Kur’an Kursu',
            'organizationStatus': 'ACTIVE',
            'membershipStatus': 'ACTIVE',
            'roleCodes': <String>['ORG_ADMIN'],
            'sessionGeneration': 3,
          },
        ],
        'page': <String, Object?>{'nextCursor': null, 'hasNextPage': false},
      },
      '/api/v1/iam/auth/context-selections/$_membership/activate' =>
        _activation(global: false),
      '/api/v1/iam/auth/platform-admin/activate' => _activation(global: true),
      _ => throw StateError('Unexpected path ${request.uri.path}'),
    };
    return IamResponse(
      statusCode: 200,
      headers: const <String, String>{},
      body: jsonEncode(body),
    );
  }
}

class _ScriptTransport implements IamTransport {
  final requests = <IamRequest>[];
  final results = <Object>[];

  @override
  Future<IamResponse> send(IamRequest request) async {
    requests.add(request);
    final result = results.removeAt(0);
    if (result is IamResponse) return result;
    if (result is Future<IamResponse>) return result;
    throw result;
  }
}

class _DelayedActivationTransport implements IamTransport {
  final requests = <IamRequest>[];
  final firstActivationStarted = Completer<void>();
  final releaseFirstActivation = Completer<void>();
  int exchanges = 0;
  int activations = 0;

  @override
  Future<IamResponse> send(IamRequest request) async {
    requests.add(request);
    late final Object body;
    if (request.uri.path == '/api/v1/iam/auth/provider-token-exchange') {
      exchanges++;
      body = <String, Object?>{
        'contextSelectionToken': 'memory-context-$exchanges',
        'contextSelectionTokenExpiresAt': '2026-07-27T09:05:00Z',
        'availableScopes': <String>['ORGANIZATION_SELECTION'],
        'user': <String, Object?>{
          'id': _user,
          'displayName': 'Yasir',
          'status': 'ACTIVE',
        },
        'device': <String, Object?>{
          'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
          'deviceIdentifier': _device,
          'platform': 'ANDROID',
          'trustedAt': '2026-07-27T09:00:00Z',
        },
      };
    } else if (request.uri.path == '/api/v1/iam/auth/context-selections') {
      body = <String, Object?>{
        'items': <Object>[
          <String, Object?>{
            'id': _membership,
            'organizationId': _organization,
            'organizationName': 'Fındıklı Kur’an Kursu',
            'organizationStatus': 'ACTIVE',
            'membershipStatus': 'ACTIVE',
            'roleCodes': <String>['ORG_ADMIN'],
            'sessionGeneration': 3,
          },
        ],
        'page': <String, Object?>{'nextCursor': null, 'hasNextPage': false},
      };
    } else if (request.uri.path ==
        '/api/v1/iam/auth/context-selections/$_membership/activate') {
      activations++;
      if (activations == 1) {
        firstActivationStarted.complete();
        await releaseFirstActivation.future;
      }
      body = _activation(global: false);
    } else {
      throw StateError('Unexpected path ${request.uri.path}');
    }
    return IamResponse(
      statusCode: 200,
      headers: const <String, String>{},
      body: jsonEncode(body),
    );
  }
}

Map<String, Object?> _activation({required bool global}) => <String, Object?>{
  'user': <String, Object?>{
    'id': _user,
    'displayName': 'Yasir',
    'status': 'ACTIVE',
  },
  'device': <String, Object?>{
    'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
    'deviceIdentifier': _device,
    'platform': 'ANDROID',
    'trustedAt': '2026-07-27T09:00:00Z',
  },
  if (global)
    'platformAdministrator': <String, Object?>{'status': 'ACTIVE'}
  else
    'organizationMembership': <String, Object?>{
      'id': _membership,
      'organizationId': _organization,
      'organizationName': 'Fındıklı Kur’an Kursu',
      'organizationStatus': 'ACTIVE',
      'membershipStatus': 'ACTIVE',
      'roleCodes': <String>['ORG_ADMIN'],
      'sessionGeneration': 3,
    },
  'session': <String, Object?>{
    'scope': global ? 'GLOBAL_PLATFORM_ADMIN' : 'ORGANIZATION',
    'accessToken': 'platform-access',
    'refreshToken': 'platform-refresh',
    'tokenType': 'Bearer',
    'expiresAt': '2026-07-27T10:00:00Z',
    'refreshExpiresAt': '2026-08-27T10:00:00Z',
    'authenticatedAt': '2026-07-27T09:00:00Z',
  },
};

IamResponse _refreshResponse({
  int generation = 3,
  String authenticatedAt = '2026-07-27T07:00:00Z',
}) => IamResponse(
  statusCode: 200,
  headers: const <String, String>{},
  body: jsonEncode(<String, Object?>{
    'organizationMembership': <String, Object?>{
      'id': _membership,
      'organizationId': _organization,
      'organizationName': 'Fındıklı Kur’an Kursu',
      'organizationStatus': 'ACTIVE',
      'membershipStatus': 'ACTIVE',
      'roleCodes': <String>['ORG_ADMIN'],
      'sessionGeneration': generation,
    },
    'session': <String, Object?>{
      'scope': 'ORGANIZATION',
      'accessToken': 'new-access',
      'refreshToken': 'new-refresh',
      'tokenType': 'Bearer',
      'expiresAt': '2026-07-27T10:00:00Z',
      'refreshExpiresAt': '2026-08-27T10:00:00Z',
      'authenticatedAt': authenticatedAt,
    },
  }),
);

IamResponse _sessionMeResponse({
  String userId = _user,
  String deviceId = _device,
  String scope = 'ORGANIZATION',
  String membershipId = _membership,
  String organizationId = _organization,
  List<String> roleCodes = const <String>['TEACHER'],
  String expiresAt = '2026-07-27T10:00:00Z',
}) => IamResponse(
  statusCode: 200,
  headers: const <String, String>{},
  body: jsonEncode(<String, Object?>{
    'user': <String, Object?>{
      'id': userId,
      'displayName': 'Yasir',
      'status': 'ACTIVE',
    },
    'device': <String, Object?>{
      'id': '6ef04c06-351a-4ac9-b4a2-29652b213ad2',
      'deviceIdentifier': deviceId,
      'platform': 'ANDROID',
      'trustedAt': '2026-07-27T09:00:00Z',
    },
    if (scope == 'ORGANIZATION')
      'organizationMembership': <String, Object?>{
        'id': membershipId,
        'organizationId': organizationId,
        'organizationName': 'Kanonik Kurs',
        'organizationStatus': 'ACTIVE',
        'membershipStatus': 'ACTIVE',
        'roleCodes': roleCodes,
        'sessionGeneration': 3,
      }
    else
      'platformAdministrator': <String, Object?>{'status': 'ACTIVE'},
    'session': <String, Object?>{
      'scope': scope,
      'expiresAt': expiresAt,
      'authenticatedAt': '2026-07-27T07:00:00Z',
    },
  }),
);

IamResponse _idempotencyKeyReusedResponse() => IamResponse(
  statusCode: 409,
  headers: const <String, String>{},
  body: jsonEncode(<String, Object?>{
    'error': <String, Object?>{'code': 'IDEMPOTENCY_KEY_REUSED'},
  }),
);

SecureSession _expiredCandidate() => SecureSession(
  userId: _user,
  deviceId: _device,
  scope: SecureSessionScope.organization,
  accessToken: 'old-access',
  refreshToken: 'old-refresh',
  expiresAt: DateTime.utc(2026, 7, 27, 8),
  refreshExpiresAt: DateTime.utc(2026, 8, 27, 10),
  authenticatedAt: DateTime.utc(2026, 7, 27, 7),
  organizationMembershipId: _membership,
  organizationId: _organization,
  sessionGeneration: 3,
);

ProductionIamRepository _repository(_Provider provider, _Transport transport) {
  final config = AppRuntimeConfig.fromValues(
    environment: 'development',
    publicApiBaseUrl: 'https://api.example.invalid',
    cognitoIssuerUri:
        'https://cognito-idp.eu-central-1.amazonaws.com/eu-central-1_EXAMPLE',
    cognitoClientId: 'public',
  );
  return ProductionIamRepository(
    provider: provider,
    client: IamHttpClient(config: config, transport: transport),
    deviceIdentity: _Device(),
    now: () => DateTime.utc(2026, 7, 27, 9),
  );
}

void main() {
  test(
    'provider access is used only by exchange and context stays in memory',
    () async {
      final provider = _Provider();
      final transport = _Transport();
      final repository = _repository(provider, transport);

      final choices = await repository.beginSignIn();
      final activation = await repository.activateOrganization(_membership);

      expect(provider.calls, 1);
      expect(choices.memberships.single.id, _membership);
      expect(
        transport.requests.first.headers['Authorization'],
        'Bearer provider-only-access',
      );
      expect(
        transport.requests.skip(1).map((r) => r.headers['Authorization']),
        everyElement('Bearer memory-context-token'),
      );
      expect(activation.secureSession.accessToken, 'platform-access');
      expect(activation.secureSession.refreshToken, 'platform-refresh');
      expect(activation.secureSession.toString(), isNot(contains('provider')));
      expect(activation.secureSession.toString(), isNot(contains('context')));
    },
  );

  test('global activation has no tenant context', () async {
    final repository = _repository(_Provider(), _Transport());
    await repository.beginSignIn();

    final activation = await repository.activatePlatformAdministrator();

    expect(
      activation.session.scope,
      ActivatedSessionScope.globalPlatformAdministrator,
    );
    expect(activation.session.organizationMembership, isNull);
    expect(activation.secureSession.organizationId, isNull);
    expect(activation.secureSession.organizationMembershipId, isNull);
  });

  test('device mismatch and provider cancellation fail closed', () async {
    final mismatch = _repository(_Provider(), _Transport(wrongDevice: true));
    await expectLater(
      mismatch.beginSignIn(),
      throwsA(
        isA<AuthenticationFailure>().having(
          (failure) => failure.code,
          'code',
          AuthenticationFailureCode.internalError,
        ),
      ),
    );

    final provider = _Provider()
      ..result = const AuthenticationFailure(
        AuthenticationFailureCode.cancelled,
        'cancelled',
      );
    await expectLater(
      _repository(provider, _Transport()).beginSignIn(),
      throwsA(
        isA<AuthenticationFailure>().having(
          (failure) => failure.code,
          'code',
          AuthenticationFailureCode.cancelled,
        ),
      ),
    );
  });

  test(
    'refresh and logout preserve command key after lost responses',
    () async {
      final config = AppRuntimeConfig.fromValues(
        environment: 'development',
        publicApiBaseUrl: 'https://api.example.invalid',
        cognitoIssuerUri:
            'https://cognito-idp.eu-central-1.amazonaws.com/example',
        cognitoClientId: 'public',
      );
      final transport = _ScriptTransport()
        ..results.addAll(<Object>[
          const IamTransportException(),
          const IamTransportException(),
          IamResponse(
            statusCode: 200,
            headers: const <String, String>{},
            body: jsonEncode(<String, Object?>{
              'organizationMembership': <String, Object?>{
                'id': _membership,
                'organizationId': _organization,
                'organizationName': 'Fındıklı Kur’an Kursu',
                'organizationStatus': 'ACTIVE',
                'membershipStatus': 'ACTIVE',
                'roleCodes': <String>['ORG_ADMIN'],
                'sessionGeneration': 3,
              },
              'session': <String, Object?>{
                'scope': 'ORGANIZATION',
                'accessToken': 'new-access',
                'refreshToken': 'new-refresh',
                'tokenType': 'Bearer',
                'expiresAt': '2026-07-27T10:00:00Z',
                'refreshExpiresAt': '2026-08-27T10:00:00Z',
                'authenticatedAt': '2026-07-27T07:00:00Z',
              },
            }),
          ),
          const IamTransportException(),
          const IamTransportException(),
          const IamResponse(
            statusCode: 204,
            headers: <String, String>{},
            body: '',
          ),
        ]);
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: config, transport: transport),
        deviceIdentity: _Device(),
      );
      final candidate = _expiredCandidate();

      await expectLater(
        repository.refresh(candidate),
        throwsA(
          isA<SessionFailure>().having(
            (failure) => failure.kind,
            'kind',
            SessionFailureKind.transient,
          ),
        ),
      );
      final refreshed = await repository.refresh(candidate);
      expect(refreshed.refreshToken, 'new-refresh');
      expect(
        transport.requests
            .take(3)
            .map((request) => request.headers['Idempotency-Key']),
        everyElement(transport.requests.first.headers['Idempotency-Key']),
      );

      await expectLater(
        repository.logout(refreshed),
        throwsA(isA<SessionFailure>()),
      );
      await repository.logout(refreshed);
      expect(
        transport.requests
            .skip(3)
            .map((request) => request.headers['Idempotency-Key']),
        everyElement(transport.requests[3].headers['Idempotency-Key']),
      );
    },
  );

  test(
    'refresh IDEMPOTENCY_KEY_REUSED drops the rejected key and requires reauthentication',
    () async {
      final transport = _ScriptTransport()
        ..results.addAll(<Object>[
          _idempotencyKeyReusedResponse(),
          _refreshResponse(),
        ]);
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: _productionConfig, transport: transport),
        deviceIdentity: _Device(),
      );
      final candidate = _expiredCandidate();

      await expectLater(
        repository.refresh(candidate),
        throwsA(
          isA<SessionFailure>().having(
            (failure) => failure.kind,
            'kind',
            SessionFailureKind.terminal,
          ),
        ),
      );
      await repository.refresh(candidate);

      expect(transport.requests, hasLength(2));
      expect(
        transport.requests[1].headers['Idempotency-Key'],
        isNot(transport.requests[0].headers['Idempotency-Key']),
      );
    },
  );

  test(
    'logout IDEMPOTENCY_KEY_REUSED drops the rejected key before retry',
    () async {
      final transport = _ScriptTransport()
        ..results.addAll(<Object>[
          _idempotencyKeyReusedResponse(),
          const IamResponse(
            statusCode: 204,
            headers: <String, String>{},
            body: '',
          ),
        ]);
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: _productionConfig, transport: transport),
        deviceIdentity: _Device(),
      );
      final candidate = _expiredCandidate();

      await expectLater(
        repository.logout(candidate),
        throwsA(
          isA<SessionFailure>().having(
            (failure) => failure.kind,
            'kind',
            SessionFailureKind.terminal,
          ),
        ),
      );
      await repository.logout(candidate);

      expect(transport.requests, hasLength(2));
      expect(
        transport.requests[1].headers['Idempotency-Key'],
        isNot(transport.requests[0].headers['Idempotency-Key']),
      );
    },
  );

  test(
    'a stale activation response cannot erase a newer sign-in context',
    () async {
      final transport = _DelayedActivationTransport();
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: _productionConfig, transport: transport),
        deviceIdentity: _Device(),
        now: () => DateTime.utc(2026, 7, 27, 9),
      );
      await repository.beginSignIn();
      final staleActivation = repository.activateOrganization(_membership);
      await transport.firstActivationStarted.future;

      await repository.beginSignIn();
      transport.releaseFirstActivation.complete();
      await expectLater(
        staleActivation,
        throwsA(
          isA<AuthenticationFailure>().having(
            (failure) => failure.code,
            'code',
            AuthenticationFailureCode.cancelled,
          ),
        ),
      );

      final current = await repository.activateOrganization(_membership);
      expect(current.secureSession.organizationId, _organization);
      expect(
        transport.requests.last.headers['Authorization'],
        'Bearer memory-context-2',
      );
    },
  );

  test(
    'parallel refresh converges and context mismatches fail closed',
    () async {
      final response = Completer<IamResponse>();
      final transport = _ScriptTransport()..results.add(response.future);
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: _productionConfig, transport: transport),
        deviceIdentity: _Device(),
      );
      final candidate = _expiredCandidate();

      final first = repository.refresh(candidate);
      final second = repository.refresh(candidate);
      response.complete(_refreshResponse());
      final refreshed = await Future.wait(<Future<SecureSession>>[
        first,
        second,
      ]);
      expect(transport.requests, hasLength(1));
      expect(refreshed[0].refreshToken, 'new-refresh');
      expect(refreshed[1].refreshToken, 'new-refresh');

      for (final invalid in <IamResponse>[
        _refreshResponse(generation: 2),
        _refreshResponse(generation: 4),
        _refreshResponse(authenticatedAt: '2026-07-27T07:01:00Z'),
      ]) {
        final invalidRepository = ProductionIamRepository(
          provider: _Provider(),
          client: IamHttpClient(
            config: _productionConfig,
            transport: _ScriptTransport()..results.add(invalid),
          ),
          deviceIdentity: _Device(),
        );
        await expectLater(
          invalidRepository.refresh(candidate),
          throwsA(
            isA<SessionFailure>().having(
              (failure) => failure.kind,
              'kind',
              SessionFailureKind.malformed,
            ),
          ),
        );
      }
    },
  );

  test(
    'refreshed token is canonicalized by sessions/me role snapshot',
    () async {
      final transport = _ScriptTransport()
        ..results.addAll(<Object>[
          _refreshResponse(),
          _sessionMeResponse(roleCodes: const <String>['TEACHER']),
        ]);
      final repository = ProductionIamRepository(
        provider: _Provider(),
        client: IamHttpClient(config: _productionConfig, transport: transport),
        deviceIdentity: _Device(),
      );

      final refreshed = await repository.refresh(_expiredCandidate());
      final canonical = await repository.validate(refreshed);

      expect(transport.requests.map((request) => request.uri.path), <String>[
        '/api/v1/iam/sessions/refresh',
        '/api/v1/iam/sessions/me',
      ]);
      expect(
        transport.requests.last.headers['Authorization'],
        'Bearer new-access',
      );
      expect(canonical.organizationMembership?.roleCodes, <String>['TEACHER']);
      expect(
        canonical.organizationMembership?.organizationName,
        'Kanonik Kurs',
      );
    },
  );

  test(
    'sessions/me identity, scope and membership drift fail closed',
    () async {
      for (final response in <IamResponse>[
        _sessionMeResponse(userId: '9c728d4e-4e84-4b22-81f9-ec92eb03fa6b'),
        _sessionMeResponse(scope: 'GLOBAL_PLATFORM_ADMIN'),
        _sessionMeResponse(
          membershipId: '6276942f-ef2f-44ca-9ef6-2af1dd58a0fc',
        ),
      ]) {
        final repository = ProductionIamRepository(
          provider: _Provider(),
          client: IamHttpClient(
            config: _productionConfig,
            transport: _ScriptTransport()..results.add(response),
          ),
          deviceIdentity: _Device(),
        );

        await expectLater(
          repository.validate(
            SecureSession(
              userId: _user,
              deviceId: _device,
              scope: SecureSessionScope.organization,
              accessToken: 'new-access',
              refreshToken: 'new-refresh',
              expiresAt: DateTime.utc(2026, 7, 27, 10),
              refreshExpiresAt: DateTime.utc(2026, 8, 27, 10),
              authenticatedAt: DateTime.utc(2026, 7, 27, 7),
              organizationMembershipId: _membership,
              organizationId: _organization,
              sessionGeneration: 3,
            ),
          ),
          throwsA(
            isA<SessionFailure>().having(
              (failure) => failure.kind,
              'kind',
              SessionFailureKind.malformed,
            ),
          ),
        );
      }
    },
  );

  test('sessions/me SESSION_REVOKED is terminal', () async {
    final repository = ProductionIamRepository(
      provider: _Provider(),
      client: IamHttpClient(
        config: _productionConfig,
        transport: _ScriptTransport()
          ..results.add(
            IamResponse(
              statusCode: 401,
              headers: const <String, String>{},
              body: jsonEncode(<String, Object?>{
                'error': <String, Object?>{'code': 'SESSION_REVOKED'},
              }),
            ),
          ),
      ),
      deviceIdentity: _Device(),
    );

    await expectLater(
      repository.validate(_expiredCandidate()),
      throwsA(
        isA<SessionFailure>().having(
          (failure) => failure.kind,
          'kind',
          SessionFailureKind.terminal,
        ),
      ),
    );
  });

  test('SESSION_REVOKED is a terminal successful logout result', () async {
    final transport = _ScriptTransport()
      ..results.add(
        IamResponse(
          statusCode: 401,
          headers: const <String, String>{},
          body: jsonEncode(<String, Object?>{
            'error': <String, Object?>{'code': 'SESSION_REVOKED'},
          }),
        ),
      );
    final repository = ProductionIamRepository(
      provider: _Provider(),
      client: IamHttpClient(config: _productionConfig, transport: transport),
      deviceIdentity: _Device(),
    );

    await repository.logout(_expiredCandidate());

    expect(transport.requests, hasLength(1));
  });
}
