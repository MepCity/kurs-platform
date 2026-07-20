import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/platform_organization_create_screen.dart';

Widget _wrap(Widget child, {Size size = const Size(360, 800)}) {
  return MediaQuery(
    data: MediaQueryData(size: size),
    child: MaterialApp(
      theme: const AppTheme(
        primary: Color(0xFF2E7D32),
        secondary: Color(0xFFE65100),
      ).themeData,
      home: child,
    ),
  );
}

/// Fails every call with the queued failure until [queueSuccess] is set.
class _ScriptedRepository implements OrganizationsRepository {
  OrganizationsFailure? nextFailure;
  Organization? nextSuccess;
  int callCount = 0;
  final List<OrganizationCreateRequest> calls = <OrganizationCreateRequest>[];

  @override
  Future<Organization> createOrganization(
    OrganizationCreateRequest request,
  ) async {
    callCount++;
    calls.add(request);
    if (nextFailure != null) {
      final OrganizationsFailure failure = nextFailure!;
      nextFailure = null;
      throw failure;
    }
    return nextSuccess!;
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) => throw UnimplementedError('Not exercised by PLAT-02 tests.');
}

/// Never resolves until [resolve] is called — for asserting a disposed
/// controller's late response is discarded rather than clobbering whatever
/// replaced it.
class _ControlledRepository implements OrganizationsRepository {
  final Completer<Organization> _completer = Completer<Organization>();
  int callCount = 0;

  @override
  Future<Organization> createOrganization(OrganizationCreateRequest request) {
    callCount++;
    return _completer.future;
  }

  void resolve(Organization organization) {
    if (!_completer.isCompleted) {
      _completer.complete(organization);
    }
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) => throw UnimplementedError('Not exercised by PLAT-02 tests.');
}

Organization _org(String id, String name) {
  final DateTime now = DateTime.utc(2026, 1, 1);
  return Organization(
    id: id,
    name: name,
    defaultTimezone: 'Europe/Istanbul',
    status: OrganizationStatus.active,
    createdAt: now,
    updatedAt: now,
    rowVersion: 1,
  );
}

void main() {
  group('PlatformOrganizationCreateScreen', () {
    testWidgets(
      'shows a field error and does not call the repository for a blank name',
      (WidgetTester tester) async {
        final repo = _ScriptedRepository();
        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repo)),
        );

        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();

        expect(find.text('Kurum adı boş olamaz.'), findsOneWidget);
        expect(repo.callCount, 0);
      },
    );

    testWidgets('submits the entered name and shows the success view', (
      WidgetTester tester,
    ) async {
      final repo = _ScriptedRepository()
        ..nextSuccess = _org('org-1', 'Yeni Kurum');
      Organization? notified;
      await tester.pumpWidget(
        _wrap(
          PlatformOrganizationCreateScreen(
            repository: repo,
            onCreated: (org) => notified = org,
          ),
        ),
      );

      await tester.enterText(
        find.byKey(const Key('organization_create_name_field')),
        'Yeni Kurum',
      );
      await tester.tap(find.text('Kurumu Oluştur'));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(find.text('"Yeni Kurum" oluşturuldu.'), findsOneWidget);
      expect(notified?.id, 'org-1');
      expect(repo.calls.single.name, 'Yeni Kurum');
    });

    testWidgets(
      'shows a banner error on a recoverable failure and allows retry',
      (WidgetTester tester) async {
        final repo = _ScriptedRepository()
          ..nextFailure = const OrganizationsFailure(
            OrganizationsFailureCode.internalError,
            'Sunucu geçici olarak yanıt vermiyor.',
          );
        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repo)),
        );

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Kurum',
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(
          find.text('Sunucu geçici olarak yanıt vermiyor.'),
          findsOneWidget,
        );

        repo.nextSuccess = _org('org-1', 'Kurum');
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(find.text('"Kurum" oluşturuldu.'), findsOneWidget);
        expect(repo.calls, hasLength(2));
        expect(repo.calls[1].clientMutationId, repo.calls[0].clientMutationId);
      },
    );

    testWidgets('shows the unauthorized state on a forbidden failure', (
      WidgetTester tester,
    ) async {
      final repo = _ScriptedRepository()
        ..nextFailure = const OrganizationsFailure(
          OrganizationsFailureCode.forbidden,
          'Bu işlem için platform yöneticisi yetkisi gerekir.',
        );
      await tester.pumpWidget(
        _wrap(PlatformOrganizationCreateScreen(repository: repo)),
      );

      await tester.enterText(
        find.byKey(const Key('organization_create_name_field')),
        'Kurum',
      );
      await tester.tap(find.text('Kurumu Oluştur'));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(
        find.text('Bu ekrana yalnızca platform yöneticileri erişebilir.'),
        findsOneWidget,
      );
    });

    testWidgets(
      'shows the unauthorized state on an ORGANIZATION_CONTEXT_REQUIRED failure',
      (WidgetTester tester) async {
        final repo = _ScriptedRepository()
          ..nextFailure = const OrganizationsFailure(
            OrganizationsFailureCode.organizationContextRequired,
            'Kurum oluşturma yalnızca platform genel bağlamında yapılabilir.',
          );
        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repo)),
        );

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Kurum',
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(
          find.text('Bu ekrana yalnızca platform yöneticileri erişebilir.'),
          findsOneWidget,
        );
      },
    );

    testWidgets('"Yeni Kurum Ekle" resets the form for another creation', (
      WidgetTester tester,
    ) async {
      final repo = _ScriptedRepository()
        ..nextSuccess = _org('org-1', 'Birinci Kurum');
      await tester.pumpWidget(
        _wrap(PlatformOrganizationCreateScreen(repository: repo)),
      );

      await tester.enterText(
        find.byKey(const Key('organization_create_name_field')),
        'Birinci Kurum',
      );
      await tester.tap(find.text('Kurumu Oluştur'));
      await tester.pump();
      await tester.pumpAndSettle();

      await tester.tap(find.text('Yeni Kurum Ekle'));
      await tester.pump();

      expect(find.text('Kurumu Oluştur'), findsOneWidget);
      expect(find.text('Birinci Kurum'), findsNothing);
    });

    group('repository lifecycle', () {
      testWidgets('swapping the repository resets the form and sends the next '
          'submit to the new repository only', (WidgetTester tester) async {
        final repoA = _ScriptedRepository();
        final repoB = _ScriptedRepository()
          ..nextSuccess = _org('org-b', 'Kurum B');

        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repoA)),
        );
        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Yarım Yazılmış Kurum',
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repoB)),
        );
        await tester.pump();

        expect(find.text('Yarım Yazılmış Kurum'), findsNothing);

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Kurum B',
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(find.text('"Kurum B" oluşturuldu.'), findsOneWidget);
        expect(repoA.callCount, 0);
        expect(repoB.calls.single.name, 'Kurum B');
      });

      testWidgets(
        "a late response from the old repository does not clobber the "
        "new one's state",
        (WidgetTester tester) async {
          final repoA = _ControlledRepository();
          final repoB = _ScriptedRepository()
            ..nextSuccess = _org('org-b', 'Kurum B');

          await tester.pumpWidget(
            _wrap(PlatformOrganizationCreateScreen(repository: repoA)),
          );
          await tester.enterText(
            find.byKey(const Key('organization_create_name_field')),
            'Kurum A',
          );
          await tester.tap(find.text('Kurumu Oluştur'));
          await tester.pump();
          expect(repoA.callCount, 1);

          // Swap before repoA ever resolves.
          await tester.pumpWidget(
            _wrap(PlatformOrganizationCreateScreen(repository: repoB)),
          );
          await tester.pumpAndSettle();

          await tester.enterText(
            find.byKey(const Key('organization_create_name_field')),
            'Kurum B',
          );
          await tester.tap(find.text('Kurumu Oluştur'));
          await tester.pump();
          await tester.pumpAndSettle();

          expect(find.text('"Kurum B" oluşturuldu.'), findsOneWidget);

          // repoA's stale response finally arrives.
          repoA.resolve(_org('org-a', 'Kurum A'));
          await tester.pumpAndSettle();

          expect(
            find.text('"Kurum A" oluşturuldu.'),
            findsNothing,
            reason:
                "the disposed old controller's listener must not fire and "
                'must not clobber the new screen state',
          );
          expect(find.text('"Kurum B" oluşturuldu.'), findsOneWidget);
        },
      );

      testWidgets(
        'a swap from an authorized to an unauthorized repository does not '
        'keep the old authorization',
        (WidgetTester tester) async {
          final repoA = _ScriptedRepository()
            ..nextSuccess = _org('org-a', 'Kurum A');
          final repoB = _ScriptedRepository()
            ..nextFailure = const OrganizationsFailure(
              OrganizationsFailureCode.forbidden,
              'Bu işlem için platform yöneticisi yetkisi gerekir.',
            );

          await tester.pumpWidget(
            _wrap(PlatformOrganizationCreateScreen(repository: repoA)),
          );

          await tester.pumpWidget(
            _wrap(PlatformOrganizationCreateScreen(repository: repoB)),
          );
          await tester.pump();

          await tester.enterText(
            find.byKey(const Key('organization_create_name_field')),
            'Kurum B',
          );
          await tester.tap(find.text('Kurumu Oluştur'));
          await tester.pump();
          await tester.pumpAndSettle();

          expect(
            find.text('Bu ekrana yalnızca platform yöneticileri erişebilir.'),
            findsOneWidget,
          );
          expect(repoA.callCount, 0);
        },
      );
    });
  });
}
