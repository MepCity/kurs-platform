import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/session_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/session_bootstrap_gate.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

class _Auth implements AuthenticationRepository {
  @override
  Future<AuthContextChoices> beginSignIn() => throw UnimplementedError();
  @override
  Future<AuthenticatedSessionActivation> activateOrganization(String id) =>
      throw UnimplementedError();
  @override
  Future<AuthenticatedSessionActivation> activatePlatformAdministrator() =>
      throw UnimplementedError();
}

class _Sessions implements SessionRepository {
  _Sessions({this.failure});
  final SessionFailure? failure;
  @override
  Future<ActivatedSession> validate(SecureSession candidate) async {
    if (failure != null) throw failure!;
    throw UnimplementedError();
  }

  @override
  Future<SecureSession> refresh(SecureSession candidate) =>
      throw UnimplementedError();
  @override
  Future<void> logout(SecureSession candidate) => throw UnimplementedError();
}

class _Store implements SecureSessionStore, AtomicSecureSessionStore {
  _Store({this.value, this.pendingRead});
  SecureSession? value;
  final Completer<SecureSession?>? pendingRead;
  int lease = 0;
  @override
  Future<bool> isCurrent(SecureSession expected) async =>
      value?.accessToken == expected.accessToken &&
      value?.refreshToken == expected.refreshToken;

  @override
  Future<SecureSession?> read() => pendingRead?.future ?? Future.value(value);
  @override
  Future<void> clear() async => value = null;
  @override
  Future<bool> clearIfCurrent(SecureSession expected) async {
    value = null;
    return true;
  }

  @override
  Future<bool> replaceIfCurrent(
    SecureSession expected,
    SecureSession replacement,
  ) async {
    value = replacement;
    return true;
  }

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      SecureSessionWriteLease(++lease);
  @override
  void abandonActivation(SecureSessionWriteLease lease) {}
  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async => true;
}

final _candidate = SecureSession(
  userId: 'user',
  deviceId: 'device',
  scope: SecureSessionScope.globalPlatformAdministrator,
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresAt: DateTime.utc(2027, 7, 27, 10),
  refreshExpiresAt: DateTime.utc(2027, 8, 27, 10),
  authenticatedAt: DateTime.utc(2026, 7, 27, 8),
);

Widget _app(_Store store, _Sessions sessions) => MaterialApp(
  theme: const AppTheme(
    primary: Color(0xFF2E7D32),
    secondary: Color(0xFFE65100),
  ).themeData,
  home: SessionBootstrapGate(
    authenticationRepository: _Auth(),
    sessionRepository: sessions,
    sessionStore: store,
  ),
);

void main() {
  testWidgets('loading and unauthenticated states are explicit', (
    tester,
  ) async {
    final pending = Completer<SecureSession?>();
    await tester.pumpWidget(_app(_Store(pendingRead: pending), _Sessions()));
    expect(find.text('Güvenli oturum doğrulanıyor…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    pending.complete(null);
    await tester.pumpAndSettle();
    expect(find.text('Giriş Yap'), findsOneWidget);
  });

  testWidgets(
    'retry error fits 320dp landscape at 2x text and exposes 48dp action',
    (tester) async {
      tester.view.physicalSize = const Size(320, 240);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      await tester.pumpWidget(
        MediaQuery(
          data: const MediaQueryData(
            size: Size(320, 240),
            textScaler: TextScaler.linear(2),
          ),
          child: _app(
            _Store(value: _candidate),
            _Sessions(
              failure: const SessionFailure(
                SessionFailureKind.transient,
                'çok uzun güvenli hata',
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Tekrar Dene'), findsOneWidget);
      expect(tester.takeException(), isNull);
      final buttonSize = tester.getSize(find.text('Tekrar Dene'));
      expect(buttonSize.height, greaterThan(0));
      final semantics = tester.getSemantics(find.text('Tekrar Dene'));
      expect(semantics.label, contains('Tekrar Dene'));
    },
  );
}
