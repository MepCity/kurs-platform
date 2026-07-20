import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/presentation/widgets/widgets.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/presentation/platform_organization_create_screen.dart';

/// Fails every `createOrganization()` call with [failureMessage] — used to
/// exercise a banner error long enough to risk overflow, which the mock
/// repository's short, fixed Turkish messages never do.
class _LongBannerFailureRepository implements OrganizationsRepository {
  _LongBannerFailureRepository(this.failureMessage);

  final String failureMessage;

  @override
  Future<Organization> createOrganization(
    OrganizationCreateRequest request,
  ) async {
    throw OrganizationsFailure(
      OrganizationsFailureCode.internalError,
      failureMessage,
    );
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) => throw UnimplementedError('Not exercised by this sweep.');
}

const String _longBannerMessage =
    'Sunucu şu anda bu isteği işleyemiyor; lütfen bir süre bekleyip kurum '
    'adı, kısa ad ve saat dilimi bilgilerini kontrol ederek yeniden deneyin.';

/// Accessibility/responsive acceptance sweep for PLAT-02, mirroring the
/// matrix `platform_organization_list_screen_a11y_test.dart` (PLAT-01, ORG-006)
/// applies: text scale, narrow width, landscape, long Turkish content, and
/// every screen state.
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

/// Sets the *real* test surface — [WidgetTester.view] physical size and
/// device pixel ratio — to [size] (a logical-pixel size, since
/// `devicePixelRatio` is fixed at 1). Unlike wrapping the widget tree in a
/// manually-constructed `MediaQuery(data: MediaQueryData(size: ...))`, this
/// changes the actual `BoxConstraints` the framework's root `RenderView`
/// hands down — the constraint every descendant's layout is measured
/// against. A manual `MediaQuery` override only changes what
/// `MediaQuery.of(context)` *reports*; it does not touch those constraints,
/// so a test built that way can pass with an overflow that would occur on
/// every real device at that size. Mirrors
/// `app_text_scaling_test.dart`'s `setRealScreenSize` (UI-001/UI-003).
void setRealScreenSize(WidgetTester tester, Size size) {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
}

Widget _wrap(Widget child, {TextScaler textScaler = TextScaler.noScaling}) {
  return MaterialApp(
    theme: const AppTheme(
      primary: Color(0xFF2E7D32),
      secondary: Color(0xFFE65100),
    ).themeData,
    home: Builder(
      builder: (BuildContext context) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: textScaler),
          child: child,
        );
      },
    ),
  );
}

const String _longTurkishName =
    'Bahçelievler Merkez Mahallesi Gençlik ve Kültür Kur\'an Kursu Derneği '
    'Şubesi Yönetim Kurulu Başkanlığı';

void main() {
  group('PlatformOrganizationCreateScreen accessibility/responsive sweep', () {
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
          'editing state renders without overflow at ${sizeEntry.key} / ${scaleEntry.key}, with long Turkish input',
          (WidgetTester tester) async {
            setRealScreenSize(tester, sizeEntry.value);
            final repo = OrganizationsMockRepository(latency: Duration.zero);

            await tester.pumpWidget(
              _wrap(
                PlatformOrganizationCreateScreen(repository: repo),
                textScaler: scaleEntry.value,
              ),
            );
            await tester.enterText(
              find.byKey(const Key('organization_create_name_field')),
              _longTurkishName,
            );
            await tester.pumpAndSettle();

            assertNoOverflow(
              tester,
              'editing state at ${sizeEntry.key} / ${scaleEntry.key}',
            );
          },
        );
      }
    }

    for (final MapEntry<String, Size> sizeEntry in sizes.entries) {
      testWidgets(
        'field-error state renders without overflow at ${sizeEntry.key} / 2.0x',
        (WidgetTester tester) async {
          setRealScreenSize(tester, sizeEntry.value);
          final repo = OrganizationsMockRepository(latency: Duration.zero);
          await tester.pumpWidget(
            _wrap(
              PlatformOrganizationCreateScreen(repository: repo),
              textScaler: TextScaler.linear(2.0),
            ),
          );

          await tester.tap(find.text('Kurumu Oluştur'));
          await tester.pumpAndSettle();

          assertNoOverflow(
            tester,
            'field-error state at ${sizeEntry.key} / 2.0x',
          );
        },
      );

      testWidgets('success state (long org name) renders without overflow at '
          '${sizeEntry.key} / 2.0x', (WidgetTester tester) async {
        setRealScreenSize(tester, sizeEntry.value);
        final repo = OrganizationsMockRepository(latency: Duration.zero);
        await tester.pumpWidget(
          _wrap(
            PlatformOrganizationCreateScreen(repository: repo),
            textScaler: TextScaler.linear(2.0),
          ),
        );

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          _longTurkishName,
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        expect(find.text('"$_longTurkishName" oluşturuldu.'), findsOneWidget);
        assertNoOverflow(
          tester,
          'success state (long org name) at ${sizeEntry.key} / 2.0x',
        );
      });

      testWidgets('unauthorized state renders without overflow at '
          '${sizeEntry.key} / 2.0x', (WidgetTester tester) async {
        setRealScreenSize(tester, sizeEntry.value);
        final repo = OrganizationsMockRepository(
          latency: Duration.zero,
          session:
              const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope(
                actorUserId: 'actor-a',
              ),
        );
        await tester.pumpWidget(
          _wrap(
            PlatformOrganizationCreateScreen(repository: repo),
            textScaler: TextScaler.linear(2.0),
          ),
        );

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Kurum',
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        assertNoOverflow(
          tester,
          'unauthorized state at ${sizeEntry.key} / 2.0x',
        );
      });

      testWidgets(
        'submitting state renders without overflow at ${sizeEntry.key} / 2.0x',
        (WidgetTester tester) async {
          setRealScreenSize(tester, sizeEntry.value);
          final repo = OrganizationsMockRepository(
            latency: const Duration(milliseconds: 50),
          );
          await tester.pumpWidget(
            _wrap(
              PlatformOrganizationCreateScreen(repository: repo),
              textScaler: TextScaler.linear(2.0),
            ),
          );

          await tester.enterText(
            find.byKey(const Key('organization_create_name_field')),
            _longTurkishName,
          );
          await tester.tap(find.text('Kurumu Oluştur'));
          await tester.pump();

          expect(find.text('Oluşturuluyor…'), findsOneWidget);
          assertNoOverflow(
            tester,
            'submitting state at ${sizeEntry.key} / 2.0x',
          );

          await tester.pumpAndSettle();
        },
      );

      testWidgets('a long banner error renders without overflow at '
          '${sizeEntry.key} / 2.0x', (WidgetTester tester) async {
        setRealScreenSize(tester, sizeEntry.value);
        final repo = _LongBannerFailureRepository(_longBannerMessage);
        await tester.pumpWidget(
          _wrap(
            PlatformOrganizationCreateScreen(repository: repo),
            textScaler: TextScaler.linear(2.0),
          ),
        );

        await tester.enterText(
          find.byKey(const Key('organization_create_name_field')),
          'Kurum',
        );
        await tester.tap(find.text('Kurumu Oluştur'));
        await tester.pump();
        await tester.pumpAndSettle();

        // The form area is a `ListView` (deliberately, so long content
        // scrolls instead of overflowing) — at a short viewport the
        // banner can be outside the initially-built extent, so it must be
        // scrolled into view before `find.text` will see it at all. This
        // is expected scrolling behavior, not the overflow this sweep
        // guards against.
        await tester.scrollUntilVisible(
          find.text(_longBannerMessage),
          200,
          scrollable: find.byType(Scrollable).first,
        );
        await tester.pumpAndSettle();

        expect(find.text(_longBannerMessage), findsOneWidget);
        assertNoOverflow(
          tester,
          'long banner error at ${sizeEntry.key} / 2.0x',
        );
      });
    }

    testWidgets('real 640x320 surface at 2x does not overflow on success', (
      WidgetTester tester,
    ) async {
      // Regression test for the RenderFlex overflow (~328px) the success
      // view produced on a real 640×320 landscape surface at 2.0x text
      // scale with a long organization name, before `_SuccessView` became
      // scrollable. A prior version of this sweep only overrode
      // `MediaQuery.size` without touching `tester.view.physicalSize`, so
      // it never actually exercised the framework's real root layout
      // constraints and missed this failure.
      setRealScreenSize(tester, const Size(640, 320));
      final repo = OrganizationsMockRepository(latency: Duration.zero);
      await tester.pumpWidget(
        _wrap(
          PlatformOrganizationCreateScreen(repository: repo),
          textScaler: TextScaler.linear(2.0),
        ),
      );

      await tester.enterText(
        find.byKey(const Key('organization_create_name_field')),
        _longTurkishName,
      );
      await tester.tap(find.text('Kurumu Oluştur'));
      await tester.pump();
      await tester.pumpAndSettle();

      expect(find.text('"$_longTurkishName" oluşturuldu.'), findsOneWidget);
      assertNoOverflow(tester, 'real 640x320 surface at 2x on success');

      // The success view's primary action must still be reachable and
      // meet the 48x48 minimum touch target after becoming scrollable.
      final Finder createAnotherButton = find.widgetWithText(
        AppButton,
        'Yeni Kurum Ekle',
      );
      await tester.ensureVisible(createAnotherButton);
      await tester.pumpAndSettle();
      expect(createAnotherButton, findsOneWidget);
      final Size buttonSize = tester.getSize(createAnotherButton);
      expect(buttonSize.height, greaterThanOrEqualTo(48));
    });

    testWidgets('every text field exposes a non-empty semantics label', (
      WidgetTester tester,
    ) async {
      setRealScreenSize(tester, const Size(360, 640));
      final SemanticsHandle handle = tester.ensureSemantics();
      final repo = OrganizationsMockRepository(latency: Duration.zero);

      await tester.pumpWidget(
        _wrap(PlatformOrganizationCreateScreen(repository: repo)),
      );
      await tester.pumpAndSettle();

      for (final Key key in const <Key>[
        Key('organization_create_name_field'),
        Key('organization_create_short_name_field'),
        Key('organization_create_timezone_field'),
      ]) {
        final SemanticsNode node = tester.getSemantics(find.byKey(key));
        final bool hasAccessibleText =
            node.label.isNotEmpty ||
            node.hint.isNotEmpty ||
            node.value.isNotEmpty;
        expect(
          hasAccessibleText,
          isTrue,
          reason:
              '$key must expose a label/hint/value to assistive technology '
              '(found label="${node.label}", hint="${node.hint}", '
              'value="${node.value}")',
        );
      }

      handle.dispose();
    });

    testWidgets(
      'interactive controls (text fields, submit button) meet the 48x48 '
      'minimum touch target',
      (WidgetTester tester) async {
        setRealScreenSize(tester, const Size(360, 640));
        final repo = OrganizationsMockRepository(latency: Duration.zero);

        await tester.pumpWidget(
          _wrap(PlatformOrganizationCreateScreen(repository: repo)),
        );
        await tester.pumpAndSettle();

        for (final Key key in const <Key>[
          Key('organization_create_name_field'),
          Key('organization_create_short_name_field'),
          Key('organization_create_timezone_field'),
        ]) {
          final Size fieldSize = tester.getSize(find.byKey(key));
          expect(
            fieldSize.height,
            greaterThanOrEqualTo(48),
            reason:
                '$key visual+hit height must reach the 48dp minimum '
                'touch target',
          );
        }

        final Size submitButtonSize = tester.getSize(
          find.widgetWithText(AppButton, 'Kurumu Oluştur'),
        );
        expect(submitButtonSize.height, greaterThanOrEqualTo(48));
      },
    );

    testWidgets('the submit button reports a disabled state while submitting', (
      WidgetTester tester,
    ) async {
      setRealScreenSize(tester, const Size(360, 640));
      final SemanticsHandle handle = tester.ensureSemantics();
      final repo = OrganizationsMockRepository(
        latency: const Duration(milliseconds: 50),
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

      final SemanticsNode node = tester.getSemantics(
        find.text('Oluşturuluyor…'),
      );
      expect(node.getSemanticsData().flagsCollection.isEnabled, isNot(true));

      await tester.pumpAndSettle();
      handle.dispose();
    });
  });
}
