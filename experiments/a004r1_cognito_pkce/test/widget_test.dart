import 'package:a004r1_cognito_pkce/main.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

class _PassingAuthorizationClient implements AuthorizationClient {
  @override
  Future<PkceEvidence> signIn(ExperimentConfig config) async {
    return PkceEvidence(
      authorizationCodeReceived: true,
      codeVerifierGenerated: true,
      nonceGenerated: true,
      accessTokenReceived: true,
      idTokenReceived: true,
      refreshTokenReceived: true,
      accessTokenExpiresAt: DateTime.utc(2026, 7, 15, 12),
    );
  }
}

void main() {
  const validConfig = ExperimentConfig(
    domain: 'kurs-platform-a004r1-test.auth.eu-central-1.amazoncognito.com',
    clientId: 'public-client-id',
  );

  test('configuration builds only allow-listed HTTPS endpoints', () {
    expect(validConfig.isValid, isTrue);
    expect(
      validConfig.serviceConfiguration.authorizationEndpoint,
      'https://${validConfig.domain}/oauth2/authorize',
    );
    expect(
      validConfig.serviceConfiguration.tokenEndpoint,
      'https://${validConfig.domain}/oauth2/token',
    );
    expect(const ExperimentConfig(domain: '', clientId: '').isValid, isFalse);
    expect(
      const ExperimentConfig(
        domain: 'example.invalid',
        clientId: 'public-client-id',
      ).isValid,
      isFalse,
    );
  });

  testWidgets('missing configuration disables real login', (tester) async {
    await tester.pumpWidget(
      const A004R1App(
        config: ExperimentConfig(domain: '', clientId: ''),
      ),
    );

    expect(find.textContaining('Yapılandırma eksik'), findsOneWidget);
    expect(tester.widget(find.byKey(const Key('start-pkce'))), isNotNull);
    expect(
      tester
          .widget<FilledButton>(find.byKey(const Key('start-pkce')))
          .onPressed,
      isNull,
    );
  });

  testWidgets('successful exchange renders booleans, never token values', (
    tester,
  ) async {
    await tester.pumpWidget(
      A004R1App(
        config: validConfig,
        authorizationClient: _PassingAuthorizationClient(),
      ),
    );

    await tester.tap(find.byKey(const Key('start-pkce')));
    await tester.pumpAndSettle();

    expect(
      find.text('Authorization Code + PKCE deneyi geçti.'),
      findsOneWidget,
    );
    expect(find.text('PKCE code_verifier üretildi'), findsOneWidget);
    expect(find.text('Access token alındı'), findsOneWidget);
    expect(find.textContaining('eyJ'), findsNothing);
  });
}
