import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/session_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/session_bootstrap_gate.dart';
import 'package:kurs_platform_mobile/features/bootstrap/domain/organization_repository_bundle.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/organization_brand_settings_screen.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/core/network/authenticated_api_session.dart';

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
  _Sessions({this.failure, this.activated, this.replacement});
  final SessionFailure? failure;
  final ActivatedSession? activated;
  final SecureSession? replacement;
  final activatedSessions = <ActivatedSession>[];
  int refreshes = 0;
  @override
  Future<ActivatedSession> validate(SecureSession candidate) async {
    if (failure != null) throw failure!;
    if (activatedSessions.isNotEmpty) return activatedSessions.removeAt(0);
    return activated ?? (throw UnimplementedError());
  }

  @override
  Future<SecureSession> refresh(SecureSession candidate) async {
    refreshes++;
    return replacement ?? (throw UnimplementedError());
  }

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

final _organizationCandidate = SecureSession(
  userId: 'user',
  deviceId: 'device',
  scope: SecureSessionScope.organization,
  accessToken: 'access',
  refreshToken: 'refresh',
  expiresAt: DateTime.utc(2027, 7, 27, 10),
  refreshExpiresAt: DateTime.utc(2027, 8, 27, 10),
  authenticatedAt: DateTime.utc(2026, 7, 27, 8),
  organizationMembershipId: 'membership',
  organizationId: 'organization',
  sessionGeneration: 3,
);

OrganizationRepositoryBundle _organizationRepositories(
  AuthenticatedApiSession _,
  bool globalListScope,
) {
  final repository = OrganizationsMockRepository(latency: Duration.zero);
  return OrganizationRepositoryBundle(
    organizations: repository,
    brand: repository,
  );
}

Widget _app(
  _Store store,
  _Sessions sessions, {
  OrganizationRepositoryBuilder organizationRepositoryBuilder =
      _organizationRepositories,
}) => MaterialApp(
  theme: const AppTheme(
    primary: Color(0xFF2E7D32),
    secondary: Color(0xFFE65100),
  ).themeData,
  home: SessionBootstrapGate(
    authenticationRepository: _Auth(),
    sessionRepository: sessions,
    sessionStore: store,
    organizationRepositoryBuilder: organizationRepositoryBuilder,
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
    'verified global session opens real list, create and support brand routes',
    (tester) async {
      final store = _Store()..value = _candidate;
      final sessions = _Sessions(
        activated: const ActivatedSession(
          scope: ActivatedSessionScope.globalPlatformAdministrator,
          displayName: 'Platform Yöneticisi',
        ),
      );

      await tester.pumpWidget(_app(store, sessions));
      await tester.pumpAndSettle();
      expect(find.text('Kurumlar'), findsWidgets);
      expect(find.text('Kurum Oluştur'), findsOneWidget);

      await tester.tap(find.text('Kurum Oluştur'));
      await tester.pumpAndSettle();
      expect(find.text('Kurum Oluştur'), findsWidgets);
      expect(find.text('Kurum Adı'), findsOneWidget);
      await tester.pumpWidget(const SizedBox.shrink());
      await tester.pumpWidget(_app(store, sessions));
      await tester.pumpAndSettle();

      await tester.tap(find.text("Ahi Evran Kur'an Kursu"));
      await tester.pumpAndSettle();
      expect(find.byType(OrganizationBrandSettingsScreen), findsOneWidget);
      expect(find.text('Etkin modüller'), findsNothing);
    },
  );

  testWidgets('ORG_ADMIN and TEACHER require one explicit non-union role', (
    tester,
  ) async {
    final store = _Store()..value = _organizationCandidate;
    final sessions = _Sessions(
      activated: const ActivatedSession(
        scope: ActivatedSessionScope.organization,
        displayName: 'Çok Rollü Kullanıcı',
        organizationMembership: AuthOrganizationMembership(
          id: 'membership',
          organizationId: 'organization',
          organizationName: 'Kurs',
          roleCodes: <String>['ORG_ADMIN', 'TEACHER'],
        ),
      ),
    );

    await tester.pumpWidget(_app(store, sessions));
    await tester.pumpAndSettle();
    expect(find.text('Rol Seçimi'), findsOneWidget);
    expect(find.text('Kurum Yöneticisi'), findsOneWidget);
    expect(find.text('Hoca'), findsOneWidget);

    await tester.tap(find.text('Hoca'));
    await tester.pumpAndSettle();
    expect(find.textContaining('Hoca'), findsWidgets);
    expect(find.text('Marka Ayarları'), findsNothing);
    expect(find.text('Etkin Modüller'), findsNothing);
  });

  testWidgets(
    'refresh role loss replaces ORG_ADMIN shell and blocks stale ORG retry',
    (tester) async {
      final replacement = SecureSession(
        userId: _organizationCandidate.userId,
        deviceId: _organizationCandidate.deviceId,
        scope: _organizationCandidate.scope,
        accessToken: 'access-refreshed',
        refreshToken: 'refresh-refreshed',
        expiresAt: DateTime.utc(2027, 7, 27, 11),
        refreshExpiresAt: DateTime.utc(2027, 8, 27, 11),
        authenticatedAt: _organizationCandidate.authenticatedAt,
        organizationMembershipId:
            _organizationCandidate.organizationMembershipId,
        organizationId: _organizationCandidate.organizationId,
        sessionGeneration: _organizationCandidate.sessionGeneration,
      );
      final sessions = _Sessions(replacement: replacement)
        ..activatedSessions.addAll(const <ActivatedSession>[
          ActivatedSession(
            scope: ActivatedSessionScope.organization,
            displayName: 'Çok Rollü Kullanıcı',
            organizationMembership: AuthOrganizationMembership(
              id: 'membership',
              organizationId: 'organization',
              organizationName: 'Eski Kurs',
              roleCodes: <String>['ORG_ADMIN', 'TEACHER'],
            ),
          ),
          ActivatedSession(
            scope: ActivatedSessionScope.organization,
            displayName: 'Çok Rollü Kullanıcı',
            organizationMembership: AuthOrganizationMembership(
              id: 'membership',
              organizationId: 'organization',
              organizationName: 'Yeni Kurs',
              roleCodes: <String>['TEACHER'],
            ),
          ),
        ]);
      final store = _Store()..value = _organizationCandidate;
      late AuthenticatedApiSession apiSession;
      OrganizationRepositoryBundle repositories(
        AuthenticatedApiSession session,
        bool globalListScope,
      ) {
        apiSession = session;
        return _organizationRepositories(session, globalListScope);
      }

      await tester.pumpWidget(
        _app(store, sessions, organizationRepositoryBuilder: repositories),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Kurum Yöneticisi'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Yönetim'));
      await tester.pumpAndSettle();
      expect(find.text('Marka Ayarları'), findsOneWidget);
      var staleOrgRetryCalls = 0;

      await expectLater(
        apiSession.refreshAndRun((_) async {
          staleOrgRetryCalls++;
          return 'stale-success';
        }),
        throwsA(isA<AuthenticatedApiSessionUnavailable>()),
      );
      await tester.pumpAndSettle();

      expect(sessions.refreshes, 1);
      expect(staleOrgRetryCalls, 0);
      expect(find.textContaining('Hoca'), findsWidgets);
      expect(find.text('Marka Ayarları'), findsNothing);
      expect(find.text('Etkin Modüller'), findsNothing);
      expect(find.text('Rol Seçimi'), findsNothing);
    },
  );

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
