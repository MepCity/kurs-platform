import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/data/flutter_secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';

class _MemoryStorage implements SecureKeyValueStorage {
  final values = <String, String>{};
  bool failRead = false;
  bool failWrite = false;
  bool failDelete = false;

  @override
  Future<void> delete(String key) async {
    if (failDelete) throw StateError('delete failed');
    values.remove(key);
  }

  @override
  Future<String?> read(String key) async {
    if (failRead) throw StateError('read failed');
    return values[key];
  }

  @override
  Future<void> write(String key, String value) async {
    if (failWrite) throw StateError('write failed');
    values[key] = value;
  }
}

final _organizationSession = SecureSession(
  userId: 'user-1',
  deviceId: 'device-1',
  scope: SecureSessionScope.organization,
  accessToken: 'opaque-access-token',
  refreshToken: 'opaque-refresh-token',
  expiresAt: DateTime.utc(2026, 7, 24, 10),
  refreshExpiresAt: DateTime.utc(2026, 8, 24, 10),
  authenticatedAt: DateTime.utc(2026, 7, 24, 9),
  organizationMembershipId: 'membership-1',
  organizationId: 'organization-1',
  sessionGeneration: 4,
);

void main() {
  group('FlutterSecureSessionStore', () {
    test(
      'round trips one organization-scoped opaque platform session',
      () async {
        final storage = _MemoryStorage();
        final store = FlutterSecureSessionStore(storage: storage);

        await store.write(_organizationSession);
        final restored = await store.read();

        expect(restored?.userId, 'user-1');
        expect(restored?.deviceId, 'device-1');
        expect(restored?.scope, SecureSessionScope.organization);
        expect(restored?.organizationId, 'organization-1');
        expect(restored?.sessionGeneration, 4);
        expect(restored?.accessToken, 'opaque-access-token');
        expect(restored?.refreshToken, 'opaque-refresh-token');
      },
    );

    test(
      'replaces the previous user session instead of mixing contexts',
      () async {
        final storage = _MemoryStorage();
        final store = FlutterSecureSessionStore(storage: storage);
        await store.write(_organizationSession);
        await store.write(
          SecureSession(
            userId: 'user-2',
            deviceId: 'device-2',
            scope: SecureSessionScope.globalPlatformAdministrator,
            accessToken: 'other-access',
            refreshToken: 'other-refresh',
            expiresAt: DateTime.utc(2026, 7, 24, 11),
            refreshExpiresAt: DateTime.utc(2026, 8, 24, 11),
            authenticatedAt: DateTime.utc(2026, 7, 24, 10),
          ),
        );

        final restored = await store.read();

        expect(restored?.userId, 'user-2');
        expect(restored?.organizationId, isNull);
        expect(restored?.sessionGeneration, isNull);
        expect(restored?.refreshToken, 'other-refresh');
      },
    );

    test(
      'clears the session on explicit logout or revocation handling',
      () async {
        final storage = _MemoryStorage();
        final store = FlutterSecureSessionStore(storage: storage);
        await store.write(_organizationSession);

        await store.clear();

        expect(await store.read(), isNull);
      },
    );

    test(
      'rejects and removes malformed persisted values fail closed',
      () async {
        final storage = _MemoryStorage()
          ..values['iam.platform-session.v1'] = '{not-json';
        final store = FlutterSecureSessionStore(storage: storage);

        await expectLater(
          store.read(),
          throwsA(
            isA<SecureSessionStoreFailure>().having(
              (failure) => failure.reason,
              'reason',
              SecureSessionStoreFailureReason.corrupted,
            ),
          ),
        );

        expect(storage.values, isEmpty);
      },
    );

    test(
      'does not leave a prior token usable when secure write fails',
      () async {
        final storage = _MemoryStorage()..failWrite = true;
        final store = FlutterSecureSessionStore(storage: storage);

        await expectLater(
          store.write(_organizationSession),
          throwsA(
            isA<SecureSessionStoreFailure>().having(
              (failure) => failure.reason,
              'reason',
              SecureSessionStoreFailureReason.unavailable,
            ),
          ),
        );

        expect(storage.values, isEmpty);
      },
    );

    test('never exposes an unavailable secure-storage cause', () async {
      final storage = _MemoryStorage()..failRead = true;
      final store = FlutterSecureSessionStore(storage: storage);

      await expectLater(
        store.read(),
        throwsA(isA<SecureSessionStoreFailure>()),
      );
    });
  });
}
