import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../../tool/android_oauth_manifest_verifier.dart';

void main() {
  test('production OAuth source uses one direct narrow Android filter', () {
    final android = File(
      'android/app/src/main/AndroidManifest.xml',
    ).readAsStringSync();
    final androidBuild = File(
      'android/app/build.gradle.kts',
    ).readAsStringSync();
    final ios = File('ios/Runner/Info.plist').readAsStringSync();

    expect(android, contains('android:scheme="kursplatform"'));
    expect(android, contains('android:host="oauth2redirect"'));
    expect(android, contains('android:path=""'));
    expect(android, contains('net.openid.appauth.RedirectUriReceiverActivity'));
    expect(android, contains('tools:node="replace"'));
    expect(androidBuild, isNot(contains('appAuthRedirectScheme')));
    expect(android, contains('android:taskAffinity=""'));
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
    'OAuth redirect matcher accepts only exact scheme host and empty path',
    () {
      const data = OAuthRedirectIntentData(
        scheme: 'kursplatform',
        host: 'oauth2redirect',
        path: '',
      );

      expect(
        data.matches(
          Uri.parse('kursplatform://oauth2redirect?code=test&state=test'),
        ),
        isTrue,
      );
      expect(data.matches(Uri.parse('kursplatform://other-host')), isFalse);
      expect(data.matches(Uri.parse('kursplatform:///')), isFalse);
      expect(
        data.matches(Uri.parse('kursplatform://oauth2redirect/other-path')),
        isFalse,
      );
    },
  );

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
      expect(composition, contains('ProductionOrganizationsRepository('));
      expect(
        composition,
        isNot(contains('UnavailableAuthenticationRepository')),
      );
      expect(composition, isNot(contains('OrganizationsMockRepository')));
      expect(composition, isNot(contains('UnavailableOrganizations')));
      final workspace = File(
        'lib/features/bootstrap/presentation/session_bootstrap_gate.dart',
      ).readAsStringSync();
      for (final forbidden in ['accessToken', 'refreshToken', 'Bearer ']) {
        expect(workspace, isNot(contains(forbidden)));
      }
    },
  );

  test('ORG transport rejects redirects and bounds response bodies', () {
    final transport = File(
      'lib/core/network/safe_http_transport.dart',
    ).readAsStringSync();
    expect(transport, contains('outgoing.followRedirects = false'));
    expect(transport, contains('if (incoming.isRedirect)'));
    expect(transport, contains('safeHttpMaxResponseBytes'));
  });
}
