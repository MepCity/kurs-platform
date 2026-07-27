import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Android and iOS contain only the production OAuth redirect', () {
    final android = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final ios = File('ios/Runner/Info.plist').readAsStringSync();

    expect(android, contains('android:scheme="kursplatform"'));
    expect(android, contains('android:host="oauth2redirect"'));
    expect(android, contains('net.openid.appauth.RedirectUriReceiverActivity'));
    expect(android, isNot(contains('android:taskAffinity=""')));
    expect(ios, contains('<string>kursplatform</string>'));
    for (final forbidden in <String>[
      'kursplatforma004r1',
      'development://',
      'localhost',
    ]) {
      expect(android, isNot(contains(forbidden)));
      expect(ios, isNot(contains(forbidden)));
    }
  });

  test(
    'production transport forbids redirects and composition is available',
    () {
      final transport = File(
        'lib/features/auth/data/iam_http_client.dart',
      ).readAsStringSync();
      final composition = File('lib/main.dart').readAsStringSync();

      expect(transport, contains('outgoing.followRedirects = false'));
      expect(transport, contains('if (incoming.isRedirect)'));
      expect(composition, contains('ProductionIamRepository('));
      expect(
        composition,
        isNot(contains('UnavailableAuthenticationRepository')),
      );
    },
  );
}
