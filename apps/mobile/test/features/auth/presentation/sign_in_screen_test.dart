import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/presentation/sign_in_screen.dart';

class _Repository implements AuthenticationRepository {
  AuthContextChoices? choices;
  AuthenticationFailure? failure;
  String? activatedMembershipId;

  @override
  Future<ActivatedSession> activateOrganization(String membershipId) async {
    activatedMembershipId = membershipId;
    return ActivatedSession(
      scope: ActivatedSessionScope.organization,
      displayName: 'Yasir',
      organizationMembership: choices!.memberships.single,
    );
  }

  @override
  Future<ActivatedSession> activatePlatformAdministrator() async =>
      const ActivatedSession(
        scope: ActivatedSessionScope.globalPlatformAdministrator,
        displayName: 'Yasir',
      );

  @override
  Future<AuthContextChoices> beginSignIn() async {
    if (failure != null) throw failure!;
    return choices!;
  }
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
    await tester.pumpWidget(_wrap(SignInScreen(repository: repository)));

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
            organizationName: 'Fındıklı Kur’an Kursu',
            roleCodes: <String>['ORG_ADMIN'],
          ),
        ],
        canActivatePlatformAdministrator: false,
      );
    ActivatedSession? result;
    await tester.pumpWidget(
      _wrap(
        SignInScreen(
          repository: repository,
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
  });

  testWidgets('shows a recoverable safe error when sign-in cannot start', (
    tester,
  ) async {
    final repository = _Repository()
      ..failure = const AuthenticationFailure(
        AuthenticationFailureCode.providerUnavailable,
        'Kimlik sağlayıcısına şu anda ulaşılamıyor.',
      );
    await tester.pumpWidget(_wrap(SignInScreen(repository: repository)));

    await tester.tap(find.text('Giriş Yap'));
    await tester.pumpAndSettle();

    expect(
      find.text('Kimlik sağlayıcısına şu anda ulaşılamıyor.'),
      findsOneWidget,
    );
    expect(find.text('Geri Dön'), findsOneWidget);
  });
}
