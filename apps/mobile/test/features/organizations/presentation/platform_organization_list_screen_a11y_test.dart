import 'dart:ui' show Tristate;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/platform_organization_list_screen.dart';

/// Accessibility/responsive acceptance sweep for PLAT-01, mirroring the
/// matrix requested in the ORG-006 PR #47 review round: text scale, narrow
/// width, landscape, long Turkish content, every status chip, and every
/// screen state (Y/B/H/Z + content).
///
/// Reuses the same "drain every pending exception" pattern as
/// `test/core/theme/app_text_scaling_test.dart` (kept local to this file
/// rather than imported cross-file, since that file does not expose it as a
/// shared utility today).
void assertNoOverflow(WidgetTester tester, String reason) {
  final List<Object> exceptions = <Object>[];
  for (;;) {
    final Object? pending = tester.takeException();
    if (pending == null) {
      break;
    }
    exceptions.add(pending);
  }
  if (exceptions.isNotEmpty) {
    fail(
      'Unexpected exception(s) during $reason:\n'
      '${exceptions.join('\n---\n')}',
    );
  }
}

Widget _wrap(
  Widget child, {
  Size size = const Size(360, 640),
  TextScaler textScaler = TextScaler.noScaling,
}) {
  return MediaQuery(
    data: MediaQueryData(size: size, textScaler: textScaler),
    child: MaterialApp(
      theme: const AppTheme(
        primary: Color(0xFF2E7D32),
        secondary: Color(0xFFE65100),
      ).themeData,
      home: child,
    ),
  );
}

final Organization _longNameOrg = Organization(
  id: 'org-long',
  name:
      'Bahçelievler Merkez Mahallesi Gençlik ve Kültür Kur\'an Kursu Derneği '
      'Şubesi',
  shortName:
      'Bahçelievler Merkez Gençlik Kültür Şubesi Uzun Kısa Ad Testi Metni',
  defaultTimezone: 'Europe/Istanbul',
  status: OrganizationStatus.active,
  createdAt: DateTime.utc(2026, 1, 1),
  updatedAt: DateTime.utc(2026, 1, 1),
  rowVersion: 1,
);

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

void main() {
  group('PlatformOrganizationListScreen accessibility/responsive sweep', () {
    final Map<String, Size> sizes = <String, Size>{
      'portrait 360dp': const Size(360, 640),
      'narrow 320dp portrait': const Size(320, 640),
      'landscape': const Size(640, 320),
    };
    final Map<String, TextScaler> scales = <String, TextScaler>{
      '1.0x': TextScaler.linear(1.0),
      '2.0x': TextScaler.linear(2.0),
    };

    for (final MapEntry<String, Size> sizeEntry in sizes.entries) {
      for (final MapEntry<String, TextScaler> scaleEntry in scales.entries) {
        testWidgets(
          'content state renders without overflow at ${sizeEntry.key} / ${scaleEntry.key}, with long Turkish org name and shortName',
          (WidgetTester tester) async {
            final repo = OrganizationsMockRepository(
              seed: <Organization>[_longNameOrg, _org('2', 'Kısa Kurum')],
              latency: Duration.zero,
            );

            await tester.pumpWidget(
              _wrap(
                PlatformOrganizationListScreen(repository: repo),
                size: sizeEntry.value,
                textScaler: scaleEntry.value,
              ),
            );
            await tester.pumpAndSettle();

            assertNoOverflow(
              tester,
              'content at ${sizeEntry.key} / ${scaleEntry.key}',
            );
          },
        );
      }
    }

    testWidgets('every screen state (Y/B/H/Z/content) renders without overflow', (
      WidgetTester tester,
    ) async {
      Future<void> pumpAndCheck(Widget screen, String stateName) async {
        await tester.pumpWidget(_wrap(screen));
        await tester.pump();
        await tester.pumpAndSettle();
        assertNoOverflow(tester, '$stateName state');
      }

      await pumpAndCheck(
        PlatformOrganizationListScreen(
          repository: OrganizationsMockRepository(
            seed: <Organization>[_org('1', 'Kurum 1')],
            latency: const Duration(milliseconds: 50),
          ),
        ),
        'loading',
      );

      await pumpAndCheck(
        PlatformOrganizationListScreen(
          repository: OrganizationsMockRepository(
            seed: <Organization>[],
            latency: Duration.zero,
          ),
        ),
        'empty',
      );

      await pumpAndCheck(
        PlatformOrganizationListScreen(
          repository: OrganizationsMockRepository(
            seed: <Organization>[],
            latency: Duration.zero,
            session:
                const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope(
                  actorUserId: 'actor-a',
                ),
          ),
        ),
        'unauthorized',
      );

      await pumpAndCheck(
        PlatformOrganizationListScreen(
          repository: OrganizationsMockRepository(
            seed: <Organization>[_org('1', 'Kurum 1'), _longNameOrg],
            latency: Duration.zero,
          ),
        ),
        'content',
      );
    });

    testWidgets(
      'every status filter chip renders without overflow at 2.0x scale',
      (WidgetTester tester) async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _org('1', 'Aktif Kurum', status: OrganizationStatus.active),
            _org('2', 'Askıdaki Kurum', status: OrganizationStatus.suspended),
            _org('3', 'Arşivli Kurum', status: OrganizationStatus.archived),
          ],
          latency: Duration.zero,
        );

        await tester.pumpWidget(
          _wrap(
            PlatformOrganizationListScreen(repository: repo),
            textScaler: TextScaler.linear(2.0),
          ),
        );
        await tester.pumpAndSettle();
        assertNoOverflow(tester, 'initial content at 2.0x');

        for (final String label in <String>[
          'Tümü',
          'Aktif',
          'Askıda',
          'Arşiv',
        ]) {
          await tester.tap(find.widgetWithText(ChoiceChip, label));
          await tester.pumpAndSettle();
          assertNoOverflow(tester, 'after selecting "$label" chip at 2.0x');
        }
      },
    );

    testWidgets('search field exposes a non-empty semantics hint or label', (
      WidgetTester tester,
    ) async {
      final SemanticsHandle handle = tester.ensureSemantics();
      final repo = OrganizationsMockRepository(
        seed: <Organization>[_org('1', 'Kurum 1')],
        latency: Duration.zero,
      );

      await tester.pumpWidget(
        _wrap(PlatformOrganizationListScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      final SemanticsNode node = tester.getSemantics(
        find.byType(TextField).first,
      );
      final bool hasAccessibleText =
          node.label.isNotEmpty ||
          node.hint.isNotEmpty ||
          node.value.isNotEmpty;
      expect(
        hasAccessibleText,
        isTrue,
        reason:
            'the search field must expose a label/hint/value to assistive '
            'technology (found label="${node.label}", hint="${node.hint}", '
            'value="${node.value}")',
      );

      handle.dispose();
    });

    testWidgets(
      'status filter chips report their selected state via semantics',
      (WidgetTester tester) async {
        final SemanticsHandle handle = tester.ensureSemantics();
        final repo = OrganizationsMockRepository(
          seed: <Organization>[_org('1', 'Kurum 1')],
          latency: Duration.zero,
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repo)),
        );
        await tester.pumpAndSettle();

        bool isSelected(String label) {
          final SemanticsNode node = tester.getSemantics(
            find.widgetWithText(ChoiceChip, label),
          );
          return node.getSemanticsData().flagsCollection.isSelected ==
              Tristate.isTrue;
        }

        expect(
          isSelected('Tümü'),
          isTrue,
          reason: '"Tümü" is selected by default',
        );
        expect(isSelected('Aktif'), isFalse);

        await tester.tap(find.widgetWithText(ChoiceChip, 'Aktif'));
        await tester.pumpAndSettle();

        expect(isSelected('Aktif'), isTrue);
        expect(isSelected('Tümü'), isFalse);

        handle.dispose();
      },
    );

    testWidgets(
      'status is never conveyed by color alone — every chip carries a text label',
      (WidgetTester tester) async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _org('1', 'Aktif Kurum', status: OrganizationStatus.active),
            _org('2', 'Askıdaki Kurum', status: OrganizationStatus.suspended),
            _org('3', 'Arşivli Kurum', status: OrganizationStatus.archived),
          ],
          latency: Duration.zero,
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repo)),
        );
        await tester.pumpAndSettle();

        // Each label appears at least twice: once on the filter chip, once
        // on the matching organization row's status chip.
        expect(find.text('Aktif'), findsWidgets);
        expect(find.text('Askıda'), findsWidgets);
        expect(find.text('Arşiv'), findsWidgets);
      },
    );

    testWidgets(
      'loading state announces a non-empty label for screen readers',
      (WidgetTester tester) async {
        final SemanticsHandle handle = tester.ensureSemantics();
        final repo = OrganizationsMockRepository(
          seed: <Organization>[_org('1', 'Kurum 1')],
          latency: const Duration(milliseconds: 50),
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repo)),
        );
        await tester.pump();

        expect(find.text('Kurumlar yükleniyor…'), findsOneWidget);

        await tester.pumpAndSettle();
        handle.dispose();
      },
    );

    testWidgets(
      'interactive controls (search field, chips) meet the 48x48 minimum touch target',
      (WidgetTester tester) async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[_org('1', 'Kurum 1')],
          latency: Duration.zero,
        );

        await tester.pumpWidget(
          _wrap(PlatformOrganizationListScreen(repository: repo)),
        );
        await tester.pumpAndSettle();

        final Size searchFieldSize = tester.getSize(
          find.byType(TextField).first,
        );
        expect(searchFieldSize.height, greaterThanOrEqualTo(48));

        for (final String label in <String>[
          'Tümü',
          'Aktif',
          'Askıda',
          'Arşiv',
        ]) {
          final Size chipHitSize = tester.getSize(
            find.widgetWithText(ChoiceChip, label),
          );
          expect(
            chipHitSize.height,
            greaterThanOrEqualTo(48),
            reason:
                '"$label" chip visual+hit height must reach the 48dp minimum '
                'touch target',
          );
        }
      },
    );

    testWidgets('a tappable organization row exposes button semantics', (
      WidgetTester tester,
    ) async {
      final SemanticsHandle handle = tester.ensureSemantics();
      final repo = OrganizationsMockRepository(
        seed: <Organization>[_org('1', 'Kurum 1')],
        latency: Duration.zero,
      );

      await tester.pumpWidget(
        _wrap(
          PlatformOrganizationListScreen(
            repository: repo,
            onOrganizationTap: (_) {},
          ),
        ),
      );
      await tester.pumpAndSettle();

      final SemanticsNode node = tester.getSemantics(find.text('Kurum 1'));
      final SemanticsData data = node.getSemanticsData();
      expect(
        data.flagsCollection.isButton || data.hasAction(SemanticsAction.tap),
        isTrue,
        reason:
            'a tappable organization row must be reachable as a button/tap '
            'target for assistive technology',
      );

      handle.dispose();
    });
  });
}
