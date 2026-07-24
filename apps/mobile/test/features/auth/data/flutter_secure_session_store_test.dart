import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/data/flutter_secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';

class _MemorySecureStorage implements SecureKeyValueStorage {
  final values = <String, String>{};
  bool failDelete = false;
  bool failWriteAfterPersisting = false;
  Completer<void>? payloadWriteStarted;
  Completer<void>? payloadWriteGate;
  int payloadWritesToBlock = 0;

  @override
  Future<void> delete(String key) async {
    if (failDelete) throw StateError('delete failed');
    values.remove(key);
  }

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async {
    values[key] = value;
    if (key.startsWith('iam.platform-session.v2.payload.') &&
        payloadWritesToBlock > 0) {
      payloadWritesToBlock--;
      if (payloadWriteStarted case final started? when !started.isCompleted) {
        started.complete();
      }
      await payloadWriteGate?.future;
    }
    if (failWriteAfterPersisting) throw StateError('write failed after value');
  }
}

class _MemoryMarkerStorage implements ApplicationMarkerStorage {
  final values = <String, String>{};
  bool failDelete = false;

  @override
  Future<void> delete(String key) async {
    if (failDelete) throw StateError('marker delete failed');
    values.remove(key);
  }

  @override
  Future<String?> read(String key) async => values[key];

  @override
  Future<void> write(String key, String value) async => values[key] = value;
}

class _SecureStorageWrapper implements SecureKeyValueStorage {
  _SecureStorageWrapper(this.delegate);
  final _MemorySecureStorage delegate;
  @override
  Future<void> delete(String key) => delegate.delete(key);
  @override
  Future<String?> read(String key) => delegate.read(key);
  @override
  Future<void> write(String key, String value) => delegate.write(key, value);
}

class _MarkerStorageWrapper implements ApplicationMarkerStorage {
  _MarkerStorageWrapper(this.delegate);
  final _MemoryMarkerStorage delegate;
  @override
  Future<void> delete(String key) => delegate.delete(key);
  @override
  Future<String?> read(String key) => delegate.read(key);
  @override
  Future<void> write(String key, String value) => delegate.write(key, value);
}

SecureSession _organizationSession({
  String membershipId = 'membership-1',
  String organizationId = 'organization-1',
  DateTime? authenticatedAt,
  DateTime? expiresAt,
  DateTime? refreshExpiresAt,
}) => SecureSession(
  userId: 'user-1',
  deviceId: 'device-1',
  scope: SecureSessionScope.organization,
  accessToken: 'opaque-access-token',
  refreshToken: 'opaque-refresh-token',
  authenticatedAt: authenticatedAt ?? DateTime.utc(2026, 7, 24, 9),
  expiresAt: expiresAt ?? DateTime.utc(2026, 7, 24, 10),
  refreshExpiresAt: refreshExpiresAt ?? DateTime.utc(2026, 8, 24, 10),
  organizationMembershipId: membershipId,
  organizationId: organizationId,
  sessionGeneration: 4,
);

Future<void> _commit(
  FlutterSecureSessionStore store,
  SecureSession session,
) async {
  final lease = await store.beginActivation();
  expect(await store.commit(lease, session), isTrue);
}

void main() {
  group('FlutterSecureSessionStore', () {
    test('reads only a completed current-installation session', () async {
      final secure = _MemorySecureStorage();
      final markers = _MemoryMarkerStorage();
      final store = FlutterSecureSessionStore(
        storage: secure,
        markerStorage: markers,
      );

      await _commit(store, _organizationSession());
      final restored = await store.read();

      expect(restored?.userId, 'user-1');
      expect(restored?.organizationId, 'organization-1');
      expect(restored?.sessionGeneration, 4);
      expect(restored?.refreshToken, 'opaque-refresh-token');
    });

    test(
      'write failure after payload and failed cleanup cannot reopen old user',
      () async {
        final secure = _MemorySecureStorage();
        final markers = _MemoryMarkerStorage();
        final store = FlutterSecureSessionStore(
          storage: secure,
          markerStorage: markers,
        );
        await _commit(store, _organizationSession());

        final lease = await store.beginActivation();
        secure
          ..failWriteAfterPersisting = true
          ..failDelete = true;
        await expectLater(
          store.commit(
            lease,
            _organizationSession(
              membershipId: 'membership-2',
              organizationId: 'organization-2',
            ),
          ),
          throwsA(isA<SecureSessionStoreFailure>()),
        );

        expect(await store.read(), isNull);
        expect(
          markers.values.containsKey('iam.platform-session.v2.commit'),
          isFalse,
        );
      },
    );

    test(
      'uninstall reinstall marker mismatch rejects surviving secure payload',
      () async {
        final secure = _MemorySecureStorage();
        final installedMarkers = _MemoryMarkerStorage();
        final oldStore = FlutterSecureSessionStore(
          storage: secure,
          markerStorage: installedMarkers,
        );
        await _commit(oldStore, _organizationSession());

        // Keychain/Keystore may survive, but app-sandbox marker storage does not.
        final reinstalledStore = FlutterSecureSessionStore(
          storage: secure,
          markerStorage: _MemoryMarkerStorage(),
        );

        expect(await reinstalledStore.read(), isNull);
      },
    );

    test('bekleyen eski yazma daha yeni aktivasyonu ezemez', () async {
      final secure = _MemorySecureStorage();
      final markers = _MemoryMarkerStorage();
      final store = FlutterSecureSessionStore(
        storage: secure,
        markerStorage: markers,
      );
      secure
        ..payloadWriteStarted = Completer<void>()
        ..payloadWriteGate = Completer<void>()
        ..payloadWritesToBlock = 1;

      final firstLease = await store.beginActivation();
      final firstWrite = store.commit(
        firstLease,
        _organizationSession(membershipId: 'membership-a'),
      );
      await secure.payloadWriteStarted!.future;
      final secondLeaseFuture = store.beginActivation();
      secure.payloadWriteGate!.complete();

      expect(await firstWrite, isFalse);
      final secondLease = await secondLeaseFuture;
      expect(
        await store.commit(
          secondLease,
          _organizationSession(membershipId: 'membership-b'),
        ),
        isTrue,
      );
      expect((await store.read())?.organizationMembershipId, 'membership-b');
    });

    test('iki başarılı aktivasyon eski v2 payloadı bırakmaz', () async {
      final secure = _MemorySecureStorage();
      final markers = _MemoryMarkerStorage();
      final store = FlutterSecureSessionStore(
        storage: secure,
        markerStorage: markers,
      );
      await _commit(store, _organizationSession(membershipId: 'membership-a'));
      await _commit(store, _organizationSession(membershipId: 'membership-b'));

      final payloadKeys = secure.values.keys
          .where((key) => key.startsWith('iam.platform-session.v2.payload.'))
          .toList();
      expect(payloadKeys, hasLength(1));
      expect((await store.read())?.organizationMembershipId, 'membership-b');
    });

    test('clear v1 ve aktif v2 değerlerini temizler', () async {
      final secure = _MemorySecureStorage();
      final markers = _MemoryMarkerStorage();
      final store = FlutterSecureSessionStore(
        storage: secure,
        markerStorage: markers,
      );
      secure.values['iam.platform-session.v1'] = 'legacy-token-payload';
      await _commit(store, _organizationSession());

      await store.clear();

      expect(secure.values['iam.platform-session.v1'], isNull);
      expect(
        secure.values.keys.where(
          (key) => key.startsWith('iam.platform-session.v2.payload.'),
        ),
        isEmpty,
      );
      expect(await store.read(), isNull);
    });

    test('marker veya payload temizleme hatası eski oturumu açmaz', () async {
      final secure = _MemorySecureStorage();
      final markers = _MemoryMarkerStorage();
      final store = FlutterSecureSessionStore(
        storage: secure,
        markerStorage: markers,
      );
      await _commit(store, _organizationSession());
      markers.failDelete = true;
      secure.failDelete = true;

      await expectLater(
        store.clear(),
        throwsA(isA<SecureSessionStoreFailure>()),
      );
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
    });

    test(
      'ayrı wrapper gerçek store yarışı yalnız B payloadını bırakır',
      () async {
        final secure = _MemorySecureStorage()
          ..payloadWriteStarted = Completer<void>()
          ..payloadWriteGate = Completer<void>()
          ..payloadWritesToBlock = 1;
        final markers = _MemoryMarkerStorage();
        final oldStore = FlutterSecureSessionStore(
          storage: _SecureStorageWrapper(secure),
          markerStorage: _MarkerStorageWrapper(markers),
        );
        final newStore = FlutterSecureSessionStore(
          storage: _SecureStorageWrapper(secure),
          markerStorage: _MarkerStorageWrapper(markers),
        );

        final oldLease = await oldStore.beginActivation();
        final oldCommit = oldStore.commit(
          oldLease,
          _organizationSession(membershipId: 'membership-a'),
        );
        await secure.payloadWriteStarted!.future;

        final newLease = await newStore.beginActivation();
        expect(
          await newStore.commit(
            newLease,
            _organizationSession(membershipId: 'membership-b'),
          ),
          isTrue,
        );
        secure.payloadWriteGate!.complete();

        expect(await oldCommit, isFalse);
        expect(
          (await newStore.read())?.organizationMembershipId,
          'membership-b',
        );
        expect(
          secure.values.keys.where(
            (key) => key.startsWith('iam.platform-session.v2.payload.'),
          ),
          hasLength(1),
        );
      },
    );

    test(
      'corrupted payload shapes and unknown schema versions fail closed',
      () async {
        final secure = _MemorySecureStorage();
        final markers = _MemoryMarkerStorage();
        final store = FlutterSecureSessionStore(
          storage: secure,
          markerStorage: markers,
        );
        await _commit(store, _organizationSession());
        final commit =
            jsonDecode(markers.values['iam.platform-session.v2.commit']!)
                as Map<String, dynamic>;
        final payloadKey = 'iam.platform-session.v2.payload.${commit['slot']}';
        secure.values[payloadKey] = jsonEncode(<String, Object?>{
          'version': 99,
          'installationId': commit['installationId'],
          'slot': commit['slot'],
          'session': <String, Object?>{},
        });

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
        expect(
          markers.values.containsKey('iam.platform-session.v2.commit'),
          isFalse,
        );
      },
    );

    test(
      'kalıcı şemada tip, kapsam ve zaman ihlalleri fail closed olur',
      () async {
        final corruptions = <void Function(Map<String, dynamic>)>[
          (session) => session.remove('userId'),
          (session) => session['deviceId'] = 7,
          (session) => session['organizationMembershipId'] = ' ',
          (session) => session['scope'] = 'GLOBAL_PLATFORM_ADMIN',
          (session) => session['authenticatedAt'] = '2026-07-24T11:00:00.000Z',
        ];

        for (final corrupt in corruptions) {
          final secure = _MemorySecureStorage();
          final markers = _MemoryMarkerStorage();
          final store = FlutterSecureSessionStore(
            storage: secure,
            markerStorage: markers,
          );
          await _commit(store, _organizationSession());
          final commit =
              jsonDecode(markers.values['iam.platform-session.v2.commit']!)
                  as Map<String, dynamic>;
          final payloadKey =
              'iam.platform-session.v2.payload.${commit['slot']}';
          final payload =
              jsonDecode(secure.values[payloadKey]!) as Map<String, dynamic>;
          final session = Map<String, dynamic>.from(
            payload['session'] as Map<String, dynamic>,
          );
          corrupt(session);
          payload['session'] = session;
          secure.values[payloadKey] = jsonEncode(payload);

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
          expect(
            markers.values.containsKey('iam.platform-session.v2.commit'),
            isFalse,
          );
        }
      },
    );

    test(
      'missing, invalid, and time-reversed fields are rejected by the model',
      () {
        expect(
          () => _organizationSession(membershipId: ' '),
          throwsArgumentError,
        );
        expect(
          () => _organizationSession(organizationId: ' '),
          throwsArgumentError,
        );
        expect(
          () => _organizationSession(
            authenticatedAt: DateTime.utc(2026, 7, 24, 11),
          ),
          throwsArgumentError,
        );
        expect(
          () => _organizationSession(
            expiresAt: DateTime.utc(2026, 9, 24),
            refreshExpiresAt: DateTime.utc(2026, 8, 24),
          ),
          throwsArgumentError,
        );
      },
    );
  });
}
