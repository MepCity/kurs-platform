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
    throw result;
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
                'authenticatedAt': '2026-07-27T09:00:00Z',
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
      final candidate = SecureSession(
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
}
