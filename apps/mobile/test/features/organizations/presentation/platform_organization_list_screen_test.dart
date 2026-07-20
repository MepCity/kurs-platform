import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/platform_organization_list_screen.dart';

Widget _wrap(Widget child, {Size size = const Size(360, 640)}) {
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

Organization _org(
  String id,
  String name, {
  OrganizationStatus status = OrganizationStatus.active,
}) {
  final DateTime now = DateTime.utc(2026, 1, 1);
  return Organization(
    id: id,
    name: name,
    defaultTimezone: 'Europe/Istanbul',
    status: status,
    createdAt: now,
    updatedAt: now,
    rowVersion: 1,
  );
}

/// Fails the first call, then succeeds — used to drive the H (error) state
/// and verify its retry action.
class _FailOnceRepository implements OrganizationsRepository {
  int callCount = 0;

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) async {
    callCount++;
    if (callCount == 1) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.internalError,
        'Sunucu geçici olarak yanıt vermiyor.',
      );
    }
    return const OrganizationListResult(
      items: <Organization>[],
      nextCursor: null,
      hasNextPage: false,
    );
  }
}

/// Never resolves [listOrganizations] until [resolve] is called — used to
/// deterministically control which repository's response "wins" a race
/// against a widget rebuild, without depending on real timer durations.
class _ControlledRepository implements OrganizationsRepository {
  final Completer<OrganizationListResult> _completer =
      Completer<OrganizationListResult>();
  int callCount = 0;

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) {
    callCount++;
    return _completer.future;
  }

  void resolve(OrganizationListResult result) {
    if (!_completer.isCompleted) {
      _completer.complete(result);
    }
  }
}

void main() {
  group('PlatformOrganizationListScreen', () {
    testWidgets('shows a loading indicator, then the organization list', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[_org('1', 'Fındıklı Kur\'an Kursu')],
        latency: const Duration(milliseconds: 50),
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );

      expect(find.text('Kurumlar yükleniyor…'), findsOneWidget);

      await tester.pumpAndSettle();

      expect(find.text('Kurumlar yükleniyor…'), findsNothing);
      expect(find.text('Fındıklı Kur\'an Kursu'), findsOneWidget);
    });

    testWidgets('shows the empty state when there are no organizations', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[],
        latency: Duration.zero,
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Henüz kurum yok'), findsOneWidget);
    });

    testWidgets('shows the unauthorized state for a non-admin session', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[_org('1', 'Fındıklı Kur\'an Kursu')],
        latency: Duration.zero,
        session:
            const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope(
              actorUserId: 'actor-a',
            ),
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Bu ekrana yalnızca platform yöneticileri erişebilir.'),
        findsOneWidget,
      );
    });

    testWidgets('shows the error state and retries successfully', (
      WidgetTester tester,
    ) async {
      final repo = _FailOnceRepository();

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Sunucu geçici olarak yanıt vermiyor.'), findsOneWidget);

      await tester.tap(find.text('Tekrar Dene'));
      await tester.pumpAndSettle();

      expect(find.text('Henüz kurum yok'), findsOneWidget);
      expect(repo.callCount, 2);
    });

    testWidgets('search field filters the visible list', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[
          _org('1', 'Fındıklı Kur\'an Kursu'),
          _org('2', 'Bahçelievler Kur\'an Kursu'),
        ],
        latency: Duration.zero,
      );

      await tester.pumpWidget(
        _wrap(
          PlatformOrganizationListScreen(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 10),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Fındıklı Kur\'an Kursu'), findsOneWidget);
      expect(find.text('Bahçelievler Kur\'an Kursu'), findsOneWidget);

      await tester.enterText(find.byType(TextField), 'bahçelievler');
      await tester.pump(const Duration(milliseconds: 20));
      await tester.pumpAndSettle();

      expect(find.text('Fındıklı Kur\'an Kursu'), findsNothing);
      expect(find.text('Bahçelievler Kur\'an Kursu'), findsOneWidget);
    });

    testWidgets('status filter chip narrows the visible list', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[
          _org('1', 'Aktif Kurs', status: OrganizationStatus.active),
          _org('2', 'Askıdaki Kurs', status: OrganizationStatus.suspended),
        ],
        latency: Duration.zero,
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      expect(find.text('Aktif Kurs'), findsOneWidget);
      expect(find.text('Askıdaki Kurs'), findsOneWidget);

      await tester.tap(find.widgetWithText(ChoiceChip, 'Askıda'));
      await tester.pumpAndSettle();

      expect(find.text('Aktif Kurs'), findsNothing);
      expect(find.text('Askıdaki Kurs'), findsOneWidget);
    });

    testWidgets('scrolling near the bottom loads the next page', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: List<Organization>.generate(
          25,
          (int i) => _org(
            'id-${i.toString().padLeft(2, '0')}',
            'Kurum ${i.toString().padLeft(2, '0')}',
          ),
        ),
        latency: const Duration(milliseconds: 10),
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      // Default page size is 20; item 24 (the 25th) is only present after a
      // second page loads.
      expect(find.text('Kurum 24'), findsNothing);

      final Finder listFinder = find.byKey(const Key('organization_list_view'));
      // First drag reaches the pre-append bottom and triggers loadMore();
      // once page 2 arrives the scrollable's max extent grows, so a second
      // drag is needed to actually bring the newly appended last item into
      // the lazily-built viewport.
      await tester.drag(listFinder, const Offset(0, -20000));
      await tester.pumpAndSettle();
      await tester.drag(listFinder, const Offset(0, -20000));
      await tester.pumpAndSettle();

      expect(find.text('Kurum 24'), findsOneWidget);
    });

    testWidgets('tapping a row invokes onOrganizationTap', (
      WidgetTester tester,
    ) async {
      final repo = OrganizationsMockRepository(
        seed: <Organization>[_org('1', 'Fındıklı Kur\'an Kursu')],
        latency: Duration.zero,
      );
      Organization? tapped;

      await tester.pumpWidget(
        _wrap(
          PlatformOrganizationListScreen(
            repository: repo,
            onOrganizationTap: (Organization org) => tapped = org,
          ),
        ),
      );
      await tester.pumpAndSettle();

      await tester.tap(find.text('Fındıklı Kur\'an Kursu'));
      await tester.pump();

      expect(tapped?.id, '1');
    });

    group('repository lifecycle', () {
      testWidgets('swapping the repository shows the new one\'s data', (
        WidgetTester tester,
      ) async {
        final repoA = OrganizationsMockRepository(
          seed: <Organization>[_org('a', 'Kurum A')],
          latency: Duration.zero,
        );
        final repoB = OrganizationsMockRepository(
          seed: <Organization>[_org('b', 'Kurum B')],
          latency: Duration.zero,
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repoA)),
        );
        await tester.pumpAndSettle();
        expect(find.text('Kurum A'), findsOneWidget);

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repoB)),
        );
        await tester.pumpAndSettle();

        expect(find.text('Kurum A'), findsNothing);
        expect(find.text('Kurum B'), findsOneWidget);
      });

      testWidgets(
        'a late response from the old repository does not clobber the new one\'s state',
        (WidgetTester tester) async {
          final repoA = _ControlledRepository();
          final repoB = OrganizationsMockRepository(
            seed: <Organization>[_org('b', 'Kurum B')],
            latency: Duration.zero,
          );

          await tester.pumpWidget(
            _wrap(PlatformOrganizationListScreen(repository: repoA)),
          );
          await tester.pump();
          expect(repoA.callCount, 1);

          // Swap before repoA ever resolves.
          await tester.pumpWidget(
            _wrap(PlatformOrganizationListScreen(repository: repoB)),
          );
          await tester.pumpAndSettle();

          expect(find.text('Kurum B'), findsOneWidget);

          // repoA's stale response finally arrives.
          repoA.resolve(
            OrganizationListResult(
              items: <Organization>[_org('a', 'Kurum A')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          await tester.pumpAndSettle();

          expect(
            find.text('Kurum A'),
            findsNothing,
            reason:
                "the disposed old controller's state update must be "
                'discarded',
          );
          expect(find.text('Kurum B'), findsOneWidget);
        },
      );

      testWidgets(
        'changing searchDebounce alone (same repository) rebuilds the controller with the new debounce',
        (WidgetTester tester) async {
          final repo = OrganizationsMockRepository(
            seed: <Organization>[
              _org('1', 'Fındıklı Kur\'an Kursu'),
              _org('2', 'Bahçelievler Kur\'an Kursu'),
            ],
            latency: Duration.zero,
          );

          await tester.pumpWidget(
            _wrap(
              PlatformOrganizationListScreen(
                repository: repo,
                searchDebounce: const Duration(milliseconds: 350),
              ),
            ),
          );
          await tester.pumpAndSettle();

          // Same repository instance, only the debounce changes — exercises
          // the `didUpdateWidget` branch in isolation from a repository swap.
          await tester.pumpWidget(
            _wrap(
              PlatformOrganizationListScreen(
                repository: repo,
                searchDebounce: const Duration(milliseconds: 10),
              ),
            ),
          );
          await tester.pumpAndSettle();

          await tester.enterText(find.byType(TextField), 'bahçelievler');
          await tester.pump(const Duration(milliseconds: 20));
          await tester.pumpAndSettle();

          expect(find.text('Fındıklı Kur\'an Kursu'), findsNothing);
          expect(find.text('Bahçelievler Kur\'an Kursu'), findsOneWidget);
        },
      );
    });
  });
}
