import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/presentation/sign_in_screen.dart';

class _Repository implements AuthenticationRepository {
  AuthContextChoices? choices;
  AuthenticationFailure? failure;
  String? activatedMembershipId;

  @override
  Future<AuthenticatedSessionActivation> activateOrganization(
    String membershipId,
  ) async {
    activatedMembershipId = membershipId;
    return AuthenticatedSessionActivation(
      session: ActivatedSession(
        scope: ActivatedSessionScope.organization,
        displayName: 'Yasir',
        organizationMembership: choices!.memberships.single,
      ),
      secureSession: _organizationSession,
    );
  }

  @override
  Future<AuthenticatedSessionActivation>
  activatePlatformAdministrator() async => AuthenticatedSessionActivation(
    session: ActivatedSession(
      scope: ActivatedSessionScope.globalPlatformAdministrator,
      displayName: 'Yasir',
    ),
    secureSession: _globalSession,
  );

  @override
  Future<AuthContextChoices> beginSignIn() async {
    if (failure != null) throw failure!;
    return choices!;
  }
}

final _organizationSession = SecureSession(
  userId: 'user-1',
  deviceId: 'device-1',
  scope: SecureSessionScope.organization,
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2026, 7, 24, 10),
  refreshExpiresAt: DateTime.utc(2026, 8, 24, 10),
  authenticatedAt: DateTime.utc(2026, 7, 24, 9),
  organizationMembershipId: 'membership-1',
  organizationId: 'organization-1',
  sessionGeneration: 2,
);

final _globalSession = SecureSession(
  userId: 'user-1',
  deviceId: 'device-1',
  scope: SecureSessionScope.globalPlatformAdministrator,
  accessToken: 'access-token',
  refreshToken: 'refresh-token',
  expiresAt: DateTime.utc(2026, 7, 24, 10),
  refreshExpiresAt: DateTime.utc(2026, 8, 24, 10),
  authenticatedAt: DateTime.utc(2026, 7, 24, 9),
);

class _SessionStore implements SecureSessionStore {
  SecureSession? value;
  int _attempt = 0;

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      SecureSessionWriteLease(++_attempt);

  @override
  void abandonActivation(SecureSessionWriteLease lease) {}

  @override
  Future<void> clear() async => value = null;

  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async {
    value = session;
    return true;
  }

  @override
  Future<SecureSession?> read() async => value;
}

class _FailingSessionStore implements SecureSessionStore {
  int _attempt = 0;

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      SecureSessionWriteLease(++_attempt);

  @override
  void abandonActivation(SecureSessionWriteLease lease) {}

  @override
  Future<void> clear() async {}

  @override
  Future<SecureSession?> read() async => null;

  @override
  Future<bool> commit(SecureSessionWriteLease lease, SecureSession session) =>
      Future<bool>.error(
        const SecureSessionStoreFailure(
          SecureSessionStoreFailureReason.unavailable,
        ),
      );
}

Widget _wrap(Widget child) => MaterialApp(
  theme: const AppTheme(
    primary: Color(0xFF2E7D32),
    secondary: Color(0xFFE65100),
  ).themeData,
  home: child,
);

void main() {
  testWidgets('does not render a password field and starts browser sign-in', (
    tester,
  ) async {
    final repository = _Repository()
      ..choices = const AuthContextChoices(
        displayName: 'Yasir',
        memberships: <AuthOrganizationMembership>[],
        canActivatePlatformAdministrator: true,
      );
    await tester.pumpWidget(
      _wrap(
        SignInScreen(
          repository: repository,
          secureSessionStore: _SessionStore(),
        ),
      ),
    );

    expect(find.text('Giriş Yap'), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.text('Giriş Yap'));
    await tester.pumpAndSettle();

    expect(find.text('Bağlam seçin'), findsOneWidget);
    expect(find.text('Platform yöneticisi'), findsOneWidget);
  });

  testWidgets('activates the selected organization context', (tester) async {
    final repository = _Repository()
      ..choices = const AuthContextChoices(
        displayName: 'Yasir',
        memberships: <AuthOrganizationMembership>[
          AuthOrganizationMembership(
            id: 'membership-1',
            organizationId: 'organization-1',
            organizationName: 'Fındıklı Kur’an Kursu',
            roleCodes: <String>['ORG_ADMIN'],
          ),
        ],
        canActivatePlatformAdministrator: false,
      );
    ActivatedSession? result;
    final sessionStore = _SessionStore();
    await tester.pumpWidget(
      _wrap(
        SignInScreen(
          repository: repository,
          secureSessionStore: sessionStore,
          onSessionActivated: (value) => result = value,
        ),
      ),
    );

    await tester.tap(find.text('Giriş Yap'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Fındıklı Kur’an Kursu'));
    await tester.pumpAndSettle();

    expect(repository.activatedMembershipId, 'membership-1');
    expect(result?.scope, ActivatedSessionScope.organization);
    expect(sessionStore.value?.refreshToken, 'refresh-token');
  });

  testWidgets('shows a recoverable safe error when sign-in cannot start', (
    tester,
  ) async {
    final repository = _Repository()
      ..failure = const AuthenticationFailure(
        AuthenticationFailureCode.providerUnavailable,
        'Kimlik sağlayıcısına şu anda ulaşılamıyor.',
      );
    await tester.pumpWidget(
      _wrap(
        SignInScreen(
          repository: repository,
          secureSessionStore: _SessionStore(),
        ),
      ),
    );

    await tester.tap(find.text('Giriş Yap'));
    await tester.pumpAndSettle();

    expect(
      find.text('Kimlik sağlayıcısına şu anda ulaşılamıyor.'),
      findsOneWidget,
    );
    expect(find.text('Geri Dön'), findsOneWidget);
  });

  testWidgets('does not activate UI when secure session persistence fails', (
    tester,
  ) async {
    final repository = _Repository()
      ..choices = const AuthContextChoices(
        displayName: 'Yasir',
        memberships: <AuthOrganizationMembership>[
          AuthOrganizationMembership(
            id: 'membership-1',
            organizationId: 'organization-1',
            organizationName: 'Fındıklı Kur’an Kursu',
            roleCodes: <String>['ORG_ADMIN'],
          ),
        ],
        canActivatePlatformAdministrator: false,
      );
    ActivatedSession? activated;
    await tester.pumpWidget(
      _wrap(
        SignInScreen(
          repository: repository,
          secureSessionStore: _FailingSessionStore(),
          onSessionActivated: (value) => activated = value,
        ),
      ),
    );

    await tester.tap(find.text('Giriş Yap'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Fındıklı Kur’an Kursu'));
    await tester.pumpAndSettle();

    expect(activated, isNull);
    expect(
      find.text('Oturum açılırken beklenmeyen bir hata oluştu.'),
      findsOneWidget,
    );
  });
}
