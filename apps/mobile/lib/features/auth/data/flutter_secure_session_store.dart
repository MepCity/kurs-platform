import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../domain/secure_session_store.dart';

abstract interface class SecureKeyValueStorage {
  Future<String?> read(String key);
  Future<void> write(String key, String value);
  Future<void> delete(String key);
}

/// Keychain/Android Keystore adapter. It deliberately has no read-all method:
/// the app owns exactly one active platform session per installation.
class FlutterSecureKeyValueStorage implements SecureKeyValueStorage {
  FlutterSecureKeyValueStorage({FlutterSecureStorage? storage})
    : _storage =
          storage ??
          const FlutterSecureStorage(
            aOptions: AndroidOptions(
              storageNamespace: 'kurs_platform_iam_session_v1',
              migrateWithBackup: false,
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

class FlutterSecureSessionStore implements SecureSessionStore {
  FlutterSecureSessionStore({SecureKeyValueStorage? storage})
    : _storage = storage ?? FlutterSecureKeyValueStorage();

  static const _key = 'iam.platform-session.v1';
  static const _schemaVersion = 1;

  final SecureKeyValueStorage _storage;

  @override
  Future<void> clear() async {
    try {
      await _storage.delete(_key);
    } catch (_) {
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
  }

  @override
  Future<SecureSession?> read() async {
    final raw = await _readRaw();
    if (raw == null) return null;
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! Map<String, dynamic>) throw const FormatException();
      return _decode(decoded);
    } catch (_) {
      await _clearCorruptedValue();
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.corrupted,
      );
    }
  }

  @override
  Future<void> write(SecureSession session) async {
    try {
      await _storage.write(_key, jsonEncode(_encode(session)));
    } catch (_) {
      await _clearAfterWriteFailure();
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
  }

  Future<String?> _readRaw() async {
    try {
      return await _storage.read(_key);
    } catch (_) {
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
  }

  Future<void> _clearCorruptedValue() async {
    try {
      await _storage.delete(_key);
    } catch (_) {
      // The original value is still rejected; never return it to the caller.
    }
  }

  Future<void> _clearAfterWriteFailure() async {
    try {
      await _storage.delete(_key);
    } catch (_) {
      // A failed cleanup cannot make the incomplete write usable.
    }
  }

  Map<String, Object?> _encode(SecureSession session) => <String, Object?>{
    'version': _schemaVersion,
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
    if (json['version'] != _schemaVersion) throw const FormatException();
    final scope = switch (json['scope']) {
      'ORGANIZATION' => SecureSessionScope.organization,
      'GLOBAL_PLATFORM_ADMIN' => SecureSessionScope.globalPlatformAdministrator,
      _ => throw const FormatException(),
    };
    final userId = _requiredString(json, 'userId');
    final deviceId = _requiredString(json, 'deviceId');
    final accessToken = _requiredString(json, 'accessToken');
    final refreshToken = _requiredString(json, 'refreshToken');
    final organizationMembershipId = _nullableString(
      json,
      'organizationMembershipId',
    );
    final organizationId = _nullableString(json, 'organizationId');
    final sessionGeneration = json['sessionGeneration'];
    if (sessionGeneration != null || scope == SecureSessionScope.organization) {
      if (sessionGeneration is! int || sessionGeneration < 0) {
        throw const FormatException();
      }
    }
    if (scope == SecureSessionScope.organization &&
        (organizationMembershipId == null || organizationId == null)) {
      throw const FormatException();
    }
    if (scope == SecureSessionScope.globalPlatformAdministrator &&
        (organizationMembershipId != null ||
            organizationId != null ||
            sessionGeneration != null)) {
      throw const FormatException();
    }
    return SecureSession(
      userId: userId,
      deviceId: deviceId,
      scope: scope,
      accessToken: accessToken,
      refreshToken: refreshToken,
      expiresAt: _requiredDateTime(json, 'expiresAt'),
      refreshExpiresAt: _requiredDateTime(json, 'refreshExpiresAt'),
      authenticatedAt: _requiredDateTime(json, 'authenticatedAt'),
      organizationMembershipId: organizationMembershipId,
      organizationId: organizationId,
      sessionGeneration: sessionGeneration as int?,
    );
  }

  String _requiredString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value is! String || value.trim().isEmpty) throw const FormatException();
    return value;
  }

  String? _nullableString(Map<String, dynamic> json, String key) {
    final value = json[key];
    if (value == null) return null;
    if (value is! String || value.trim().isEmpty) throw const FormatException();
    return value;
  }

  DateTime _requiredDateTime(Map<String, dynamic> json, String key) {
    final value = _requiredString(json, key);
    final parsed = DateTime.tryParse(value);
    if (parsed == null || !parsed.isUtc) throw const FormatException();
    return parsed;
  }
}
