import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../domain/secure_session_store.dart';

abstract interface class SecureKeyValueStorage {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// Non-secret, sandbox-scoped records. They deliberately live outside
/// Keychain/Keystore so an app reinstall cannot reuse a surviving secret.
abstract interface class ApplicationMarkerStorage {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

class FlutterSecureKeyValueStorage implements SecureKeyValueStorage {
  FlutterSecureKeyValueStorage({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              storageNamespace: 'kurs_platform_iam_session_v1',
              // This is an encrypted algorithm-migration backup, not cloud
              // backup. It makes an interrupted cipher upgrade recoverable.
              migrateWithBackup: true,
            ),
            iOptions: IOSOptions(
              accountName: 'kurs-platform.iam.session.v1',
              accessibility: KeychainAccessibility.first_unlock_this_device,
              synchronizable: false,
            ),
          );

  final FlutterSecureStorage _storage;

  @override
  Future<void> delete(String key) => _storage.delete(key: key);

  @override
  Future<String?> read(String key) => _storage.read(key: key);

  @override
  Future<void> write(String key, String value) =>
      _storage.write(key: key, value: value);
}

class FlutterApplicationMarkerStorage implements ApplicationMarkerStorage {
  FlutterApplicationMarkerStorage({Future<SharedPreferences>? preferences})
    : _preferences = preferences ?? SharedPreferences.getInstance();

  final Future<SharedPreferences> _preferences;

  @override
  Future<void> delete(String key) async {
    await (await _preferences).remove(key);
  }

  @override
  Future<String?> read(String key) async => (await _preferences).getString(key);

  @override
  Future<void> write(String key, String value) async {
    await (await _preferences).setString(key, value);
  }
}

/// A two-store commit protocol for the one active platform session.
///
/// Token-bearing payloads are written only to Keychain/Keystore. A non-secret
/// app-sandbox marker is written last. Reads require both records to agree on
/// the current installation ID, so a partial secure write, failed cleanup, or
/// Keychain record surviving uninstall/reinstall cannot authenticate a user.
class FlutterSecureSessionStore implements SecureSessionStore {
  FlutterSecureSessionStore({
    SecureKeyValueStorage? storage,
    ApplicationMarkerStorage? markerStorage,
  }) : _storage = storage ?? FlutterSecureKeyValueStorage(),
       _markerStorage = markerStorage ?? FlutterApplicationMarkerStorage();

  static const _schemaVersion = 2;
  static const _legacyPayloadKey = 'iam.platform-session.v1';
  static const _installationKey = 'iam.installation.v1';
  static const _commitKey = 'iam.platform-session.v2.commit';
  static const _payloadPrefix = 'iam.platform-session.v2.payload.';

  final SecureKeyValueStorage _storage;
  final ApplicationMarkerStorage _markerStorage;
  Future<void> _tail = Future<void>.value();
  int _nextAttempt = 0;
  int _latestAttempt = 0;

  @override
  Future<SecureSessionWriteLease> beginActivation() async {
    final lease = SecureSessionWriteLease(++_nextAttempt);
    _latestAttempt = lease.value;
    await _serialize<void>(() async {
      await _installationId();
      if (!_isLatest(lease)) return;
      // A new activation must never fall back to the prior account if its
      // subsequent secure write is incomplete or fails.
      await _markerStorage.delete(_commitKey);
      await _storage.delete(_legacyPayloadKey);
    });
    return lease;
  }

  @override
  void abandonActivation(SecureSessionWriteLease lease) {
    if (!_isLatest(lease)) return;
    _latestAttempt = ++_nextAttempt;
    unawaited(
      _serialize<void>(() async {
        await _markerStorage.delete(_commitKey);
      }).catchError((Object _) {}),
    );
  }

  @override
  Future<void> clear() => _serialize<void>(() async {
    final commit = await _readCommitOrNull();
    await _markerStorage.delete(_commitKey);
    if (commit != null) await _deletePayload(commit.slot);
  });

  @override
  Future<bool> commit(SecureSessionWriteLease lease, SecureSession session) =>
      _serialize<bool>(() async {
        if (!_isLatest(lease)) return false;
        final installationId = await _installationId();
        if (!_isLatest(lease)) return false;
        final slot = '${lease.value}-${_randomIdentifier()}';
        final payloadKey = '$_payloadPrefix$slot';
        final payload = jsonEncode(<String, Object?>{
          'version': _schemaVersion,
          'installationId': installationId,
          'slot': slot,
          'session': _encode(session),
        });
        try {
          await _storage.write(payloadKey, payload);
          if (!_isLatest(lease)) {
            await _deletePayload(slot);
            return false;
          }
          // This marker is the only read authority. It is deliberately written
          // after the secure value, so a secure write that throws after writing
          // still has no readable session when cleanup also fails.
          await _markerStorage.write(
            _commitKey,
            jsonEncode(<String, Object?>{
              'version': _schemaVersion,
              'installationId': installationId,
              'slot': slot,
            }),
          );
          if (!_isLatest(lease)) {
            await _markerStorage.delete(_commitKey);
            await _deletePayload(slot);
            return false;
          }
          return true;
        } catch (_) {
          await _invalidateSlot(slot);
          throw const SecureSessionStoreFailure(
            SecureSessionStoreFailureReason.unavailable,
          );
        }
      });

  @override
  Future<SecureSession?> read() => _serialize<SecureSession?>(() async {
    final installationId = await _installationId();
    final commit = await _readCommitOrNull();
    if (commit == null) return null;
    if (commit.installationId != installationId) {
      await _invalidateSlot(commit.slot);
      return null;
    }
    try {
      final raw = await _storage.read('$_payloadPrefix${commit.slot}');
      if (raw == null) throw const FormatException();
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> ||
          decoded['version'] != _schemaVersion ||
          decoded['installationId'] != installationId ||
          decoded['slot'] != commit.slot ||
          decoded['session'] is! Map<String, dynamic>) {
        throw const FormatException();
      }
      return _decode(decoded['session'] as Map<String, dynamic>);
    } catch (_) {
      await _invalidateSlot(commit.slot);
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.corrupted,
      );
    }
  });

  Future<_CommitMarker?> _readCommitOrNull() async {
    try {
      final raw = await _markerStorage.read(_commitKey);
      if (raw == null) return null;
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic> ||
          decoded['version'] != _schemaVersion ||
          !_isNonBlankString(decoded['installationId']) ||
          !_isNonBlankString(decoded['slot'])) {
        throw const FormatException();
      }
      return _CommitMarker(
        installationId: decoded['installationId'] as String,
        slot: decoded['slot'] as String,
      );
    } catch (_) {
      await _deleteMarkerOnly();
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.corrupted,
      );
    }
  }

  Future<String> _installationId() async {
    try {
      final existing = await _markerStorage.read(_installationKey);
      if (existing != null) {
        if (!_isInstallationIdentifier(existing)) throw const FormatException();
        return existing;
      }
      final generated = _randomIdentifier();
      await _markerStorage.write(_installationKey, generated);
      final verified = await _markerStorage.read(_installationKey);
      if (verified != generated) throw const FormatException();
      return generated;
    } catch (_) {
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
  }

  Future<void> _invalidateSlot(String slot) async {
    await _deleteMarkerOnly();
    await _deletePayload(slot);
  }

  Future<void> _deleteMarkerOnly() async {
    try {
      await _markerStorage.delete(_commitKey);
    } catch (_) {
      // No marker read failure can make an unverified value valid.
    }
  }

  Future<void> _deletePayload(String slot) async {
    try {
      await _storage.delete('$_payloadPrefix$slot');
    } catch (_) {
      // The missing marker remains the hard read barrier.
    }
  }

  Future<T> _serialize<T>(Future<T> Function() operation) {
    final result = _tail.then((_) => operation());
    _tail = result.then<void>((_) {}, onError: (_, _) {});
    return result;
  }

  bool _isLatest(SecureSessionWriteLease lease) =>
      lease.value == _latestAttempt;

  Map<String, Object?> _encode(SecureSession session) => <String, Object?>{
    'userId': session.userId,
    'deviceId': session.deviceId,
    'scope': switch (session.scope) {
      SecureSessionScope.organization => 'ORGANIZATION',
      SecureSessionScope.globalPlatformAdministrator => 'GLOBAL_PLATFORM_ADMIN',
    },
    'accessToken': session.accessToken,
    'refreshToken': session.refreshToken,
    'expiresAt': session.expiresAt.toUtc().toIso8601String(),
    'refreshExpiresAt': session.refreshExpiresAt.toUtc().toIso8601String(),
    'authenticatedAt': session.authenticatedAt.toUtc().toIso8601String(),
    'organizationMembershipId': session.organizationMembershipId,
    'organizationId': session.organizationId,
    'sessionGeneration': session.sessionGeneration,
  };

  SecureSession _decode(Map<String, dynamic> json) {
    final scope = switch (json['scope']) {
      'ORGANIZATION' => SecureSessionScope.organization,
      'GLOBAL_PLATFORM_ADMIN' => SecureSessionScope.globalPlatformAdministrator,
      _ => throw const FormatException(),
    };
    final sessionGeneration = json['sessionGeneration'];
    if (scope == SecureSessionScope.organization &&
        (sessionGeneration is! int || sessionGeneration < 0)) {
      throw const FormatException();
    }
    if (scope == SecureSessionScope.globalPlatformAdministrator &&
        sessionGeneration != null) {
      throw const FormatException();
    }
    try {
      return SecureSession(
        userId: _requiredString(json, 'userId'),
        deviceId: _requiredString(json, 'deviceId'),
        scope: scope,
        accessToken: _requiredString(json, 'accessToken'),
        refreshToken: _requiredString(json, 'refreshToken'),
        expiresAt: _requiredDateTime(json, 'expiresAt'),
        refreshExpiresAt: _requiredDateTime(json, 'refreshExpiresAt'),
        authenticatedAt: _requiredDateTime(json, 'authenticatedAt'),
        organizationMembershipId: _nullableString(
          json,
          'organizationMembershipId',
        ),
        organizationId: _nullableString(json, 'organizationId'),
        sessionGeneration: sessionGeneration as int?,
      );
    } on ArgumentError {
      throw const FormatException();
    }
  }

  String _requiredString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (!_isNonBlankString(value)) throw const FormatException();
    return value as String;
  }

  String? _nullableString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (!_isNonBlankString(value)) throw const FormatException();
    return value as String;
  }

  DateTime _requiredDateTime(Map<String, dynamic> json, String key) {
    final parsed = DateTime.tryParse(_requiredString(json, key));
    if (parsed == null || !parsed.isUtc) throw const FormatException();
    return parsed;
  }

  bool _isNonBlankString(Object? value) =>
      value is String && value.trim().isNotEmpty;

  bool _isInstallationIdentifier(String value) =>
      RegExp(r'^[a-f0-9]{32}$').hasMatch(value);

  String _randomIdentifier() {
    final random = Random.secure();
    return List<String>.generate(
      16,
      (_) => random.nextInt(256).toRadixString(16).padLeft(2, '0'),
    ).join();
  }
}

class _CommitMarker {
  const _CommitMarker({required this.installationId, required this.slot});
  final String installationId;
  final String slot;
}
