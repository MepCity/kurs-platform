import 'package:flutter/foundation.dart';

import 'flutter_secure_session_store.dart';
import 'iam_http_client.dart';

abstract interface class DeviceIdentity {
  Future<DeviceRegistration> get();
}

class InstallationDeviceIdentity implements DeviceIdentity {
  InstallationDeviceIdentity(this._sessionStore);
  final FlutterSecureSessionStore _sessionStore;
  Future<DeviceRegistration>? _pending;

  @override
  Future<DeviceRegistration> get() =>
      _pending ??= _load().whenComplete(() => _pending = null);

  Future<DeviceRegistration> _load() async {
    final seed = await _sessionStore.installationIdentifier();
    if (!RegExp(r'^[a-f0-9]{32}$').hasMatch(seed)) {
      throw const FormatException();
    }
    final chars = seed.split('');
    chars[12] = '4';
    final variant = int.parse(chars[16], radix: 16);
    chars[16] = ((variant & 0x3) | 0x8).toRadixString(16);
    final hex = chars.join();
    final identifier =
        '${hex.substring(0, 8)}-${hex.substring(8, 12)}-'
        '${hex.substring(12, 16)}-${hex.substring(16, 20)}-'
        '${hex.substring(20)}';
    return switch (defaultTargetPlatform) {
      TargetPlatform.iOS => DeviceRegistration(
        identifier: identifier,
        platform: 'IOS',
        name: 'Kurs Platform iOS',
      ),
      TargetPlatform.android => DeviceRegistration(
        identifier: identifier,
        platform: 'ANDROID',
        name: 'Kurs Platform Android',
      ),
      _ => throw UnsupportedError('Desteklenmeyen mobil platform.'),
    };
  }
}
