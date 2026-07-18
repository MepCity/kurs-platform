import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/presentation/widgets/widgets.dart';
import 'package:kurs_platform_mobile/core/theme/app_spacing.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

/// Custom non-linear TextScaler for stress testing accessibility scaling.
class _NonLinearTextScaler extends TextScaler {
  const _NonLinearTextScaler();

  @override
  double scale(double fontSize) {
    if (fontSize < 14) {
      return fontSize * 1.8;
    } else if (fontSize < 20) {
      return fontSize * 1.4;
    } else {
      return fontSize * 1.1;
    }
  }

  @override
  double get textScaleFactor => 1.0;
}

const Map<String, TextScaler> _scalers = <String, TextScaler>{
  '1.0': TextScaler.linear(1.0),
  '1.5': TextScaler.linear(1.5),
  '2.0': TextScaler.linear(2.0),
  'nonlinear': _NonLinearTextScaler(),
};

ThemeData _theme() => const AppTheme(
  primary: Color(0xFF2E7D32),
  secondary: Color(0xFFE65100),
).themeData;

void setRealScreenSize(
  WidgetTester tester, {
  Size physicalSize = const Size(1080, 1920),
  double devicePixelRatio = 3.0,
}) {
  tester.view.physicalSize = physicalSize;
  tester.view.devicePixelRatio = devicePixelRatio;
  addTearDown(tester.view.reset);
}

/// Asserts that no exception remains pending after a pump/pumpAndSettle.
///
/// Unlike a single [WidgetTester.takeException] call, this drains *every*
/// queued exception (Flutter framework errors, overflow errors, layout
/// assertions, etc.) and fails the test if anything was recorded. Overflow is
/// only one of the failure modes this catches; any [FlutterError] or thrown
/// object fails the test.
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

/// Asserts that no [Text] widget within [within] silently truncates its
/// content. Ellipsis/clip overflow or a capped [Text.maxLines] would hide
/// text without raising a layout exception, so they are verified explicitly
/// instead of being accepted as "no overflow".
void assertTextsNotClipped(WidgetTester tester, Finder within, String reason) {
  final Iterable<Text> texts = tester.widgetList<Text>(
    find.descendant(of: within, matching: find.byType(Text)),
  );
  for (final Text text in texts) {
    final String plain = text.data ?? text.textSpan?.toPlainText() ?? '';
    expect(
      text.overflow,
      isNot(anyOf(TextOverflow.ellipsis, TextOverflow.clip)),
      reason:
          '$reason: Text "$plain" must not truncate with overflow '
          '${text.overflow}',
    );
    expect(
      text.maxLines,
      isNull,
      reason:
          '$reason: Text "$plain" must not cap maxLines to '
          '${text.maxLines}',
    );
  }
}

Widget _buildScaled(Widget child, TextScaler textScaler) {
  return MaterialApp(
    theme: _theme(),
    home: Builder(
      builder: (BuildContext context) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: textScaler),
          child: Scaffold(
            body: SingleChildScrollView(
              child: Padding(padding: const EdgeInsets.all(16), child: child),
            ),
          ),
        );
      },
    ),
  );
}

/// App host that exposes a [Scaffold] whose [ScaffoldMessenger] sits below a
/// [MediaQuery] override, so shown snack bars inherit [textScaler] (including
/// non-linear scalers that the platform dispatcher cannot express).
Widget _buildSnackBarHost(TextScaler textScaler, {required String hostLabel}) {
  return MaterialApp(
    theme: _theme(),
    home: Builder(
      builder: (BuildContext context) {
        return MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: textScaler),
          child: ScaffoldMessenger(
            child: Scaffold(body: Center(child: Text(hostLabel))),
          ),
        );
      },
    ),
  );
}

void main() {
  group('UI-001 §15.1 text scaling acceptance', () {
    for (final MapEntry<String, TextScaler> entry in _scalers.entries) {
      final String label = entry.key;
      final TextScaler scaler = entry.value;

      testWidgets('AppButton variants at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            Column(
              children: <Widget>[
                AppButton.filled(
                  label: 'Kaydet ve bir sonraki adıma geç',
                  onPressed: () {},
                ),
                AppButton.tonal(label: 'Yeni kayıt oluştur', onPressed: () {}),
                AppButton.outlined(
                  label: 'İptal et ve geri dön',
                  onPressed: () {},
                ),
                AppButton.text(
                  label: 'Geri dönüşüm seçenekleri',
                  onPressed: () {},
                ),
                AppButton.danger(label: 'Kalıcı olarak sil', onPressed: () {}),
              ],
            ),
            scaler,
          ),
        );
        expect(find.text('Kaydet ve bir sonraki adıma geç'), findsOneWidget);
        assertNoOverflow(tester, 'AppButton at scale $label');
        assertTextsNotClipped(tester, find.byType(Column), 'AppButton $label');
      });

      testWidgets('AppTextField states at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            const Column(
              children: <Widget>[
                AppTextField(
                  label: 'Öğrenci adı',
                  helper: 'Öğrencinin resmi adı ve soyadı',
                ),
                AppTextField(
                  label: 'Veli telefon numarası',
                  error: 'Telefon numarası zorunludur',
                ),
                AppTextField(
                  label: 'Ek notlar',
                  helper: 'Opsiyonel açıklama metni',
                  minLines: 3,
                  maxLines: 5,
                ),
              ],
            ),
            scaler,
          ),
        );
        expect(find.text('Öğrenci adı'), findsOneWidget);
        assertNoOverflow(tester, 'AppTextField at scale $label');
      });

      testWidgets('AppListItem at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            const Column(
              children: <Widget>[
                AppListItem(
                  title: 'Ahmet Yılmaz',
                  subtitle: 'Hafta sonu A grubu, öğleden sonra programı',
                ),
                AppListItem(
                  title: 'Mehmet Kaya',
                  subtitle: 'Yaz dönemi kur’an kursu B sınıfı',
                ),
              ],
            ),
            scaler,
          ),
        );
        expect(find.text('Ahmet Yılmaz'), findsOneWidget);
        assertNoOverflow(tester, 'AppListItem at scale $label');
        assertTextsNotClipped(
          tester,
          find.byType(Column),
          'AppListItem $label',
        );
      });

      testWidgets('state widgets at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            Column(
              children: <Widget>[
                const AppEmptyState(
                  title: 'Henüz kayıt bulunmuyor',
                  description:
                      'İlk kaydı ekledikten sonra listeyi burada görebilirsiniz.',
                  actionLabel: 'Yeni kayıt ekle',
                ),
                const AppLoadingState(label: 'Kurumlar ve sınıflar yükleniyor'),
                const AppErrorState(
                  message: 'Bağlantı hatası oluştu, lütfen tekrar deneyin',
                ),
                const AppUnauthorizedState(),
              ],
            ),
            scaler,
          ),
        );
        assertNoOverflow(tester, 'state widgets at scale $label');
      });

      testWidgets('chips and sync indicators at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            const Wrap(
              children: <Widget>[
                AppStatusChip(
                  label: 'Derse katıldı',
                  type: AppStatusType.success,
                ),
                AppStatusChip(
                  label: 'Geç giriş yaptı',
                  type: AppStatusType.warning,
                ),
                AppStatusChip(label: 'Katılmadı', type: AppStatusType.error),
                AppStatusChip(
                  label: 'İzinli sayıldı',
                  type: AppStatusType.info,
                ),
                AppStatusChip(label: 'Bekleniyor'),
                AppSyncIndicator(status: AppSyncStatus.pending),
                AppSyncIndicator(status: AppSyncStatus.syncing),
                AppSyncIndicator(status: AppSyncStatus.success),
                AppSyncIndicator(status: AppSyncStatus.failed),
              ],
            ),
            scaler,
          ),
        );
        assertNoOverflow(tester, 'chips/sync at scale $label');
        assertTextsNotClipped(tester, find.byType(Wrap), 'chips $label');
      });

      testWidgets('AppTopBar at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            const Column(
              children: <Widget>[
                AppTopBar(
                  title: 'Çok uzun kurum adı test ediliyor',
                  supportMode: true,
                  actions: [Icon(Icons.settings)],
                ),
              ],
            ),
            scaler,
          ),
        );
        assertNoOverflow(tester, 'AppTopBar at scale $label');
      });

      testWidgets('AppCard with long content at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        await tester.pumpWidget(
          _buildScaled(
            AppCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  const Text(
                    'Çok uzun kurum kurs adı başlığı metni taşmadan sarmalıdır',
                  ),
                  const Text(
                    'Bu açıklama metni öğrencinin kayıt durumunu ve sınıf '
                    'bilgisini uzun Türkçe kelimelerle açıklar.',
                  ),
                  AppButton.filled(
                    label: 'Arşivlenmiş öğrenciyi sınıfa geri yükle',
                    onPressed: () {},
                  ),
                ],
              ),
            ),
            scaler,
          ),
        );
        expect(
          find.text(
            'Çok uzun kurum kurs adı başlığı metni taşmadan sarmalıdır',
          ),
          findsOneWidget,
        );
        expect(
          find.text('Arşivlenmiş öğrenciyi sınıfa geri yükle'),
          findsOneWidget,
        );
        assertNoOverflow(tester, 'AppCard at scale $label');
        assertTextsNotClipped(tester, find.byType(AppCard), 'AppCard $label');
      });

      testWidgets('AppConfirmDialog at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        const String title =
            'Öğrenci sınıf değişikliği onayı uzun başlık metni';
        const String message =
            'Bu işlem öğrencinin mevcut sınıf kaydını archivleyecek ve yeni '
            'sınıfa taşıma işlemi geri alınamaz olabilir.';
        const String confirmLabel = 'Arşivlenmiş öğrenciyi sınıfa geri yükle';
        const String cancelLabel = 'İptal et ve önceki ekrana dön';

        await tester.pumpWidget(
          MaterialApp(
            theme: _theme(),
            home: Builder(
              builder: (BuildContext context) =>
                  const Scaffold(body: Center(child: Text('dialog-host'))),
            ),
          ),
        );
        final BuildContext context = tester.element(find.text('dialog-host'));

        unawaited(
          showDialog<void>(
            context: context,
            builder: (BuildContext dialogContext) {
              return MediaQuery(
                data: MediaQuery.of(dialogContext).copyWith(textScaler: scaler),
                child: Center(
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(
                      maxWidth: AppSpacing.dialogMaxWidth,
                    ),
                    child: const AppConfirmDialog(
                      title: title,
                      message: message,
                      confirmLabel: confirmLabel,
                      cancelLabel: cancelLabel,
                      isDangerous: true,
                    ),
                  ),
                ),
              );
            },
          ),
        );
        await tester.pumpAndSettle();

        expect(find.text(title), findsOneWidget);
        expect(find.text(message), findsOneWidget);
        expect(find.text(confirmLabel), findsOneWidget);
        expect(find.text(cancelLabel), findsOneWidget);
        assertNoOverflow(tester, 'AppConfirmDialog at scale $label');
        assertTextsNotClipped(
          tester,
          find.byType(AppConfirmDialog),
          'AppConfirmDialog $label',
        );

        final RenderBox dialogBox = tester.renderObject<RenderBox>(
          find.byType(AlertDialog).first,
        );
        expect(
          dialogBox.size.width,
          lessThanOrEqualTo(AppSpacing.dialogMaxWidth),
          reason: 'dialog must keep its max width on a narrow screen',
        );

        await tester.tap(find.text(cancelLabel));
        await tester.pumpAndSettle();
        assertNoOverflow(tester, 'AppConfirmDialog close at scale $label');
        expect(find.byType(AppConfirmDialog), findsNothing);
      });

      testWidgets('AppSnackBar at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(tester);
        const String message =
            'Senkronizasyon tamamlanamadı, bağlantınızı kontrol ederek tekrar '
            'deneyin';

        await tester.pumpWidget(
          _buildSnackBarHost(scaler, hostLabel: 'snackbar-host'),
        );
        final BuildContext context = tester.element(find.text('snackbar-host'));
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(AppSnackBar.error(context, message: message));
        await tester.pumpAndSettle();

        expect(find.text(message), findsOneWidget);
        assertNoOverflow(tester, 'AppSnackBar at scale $label');
        assertTextsNotClipped(
          tester,
          find.byType(SnackBar),
          'AppSnackBar $label',
        );
      });

      testWidgets('landscape narrow screen at TextScaler($label)', (
        WidgetTester tester,
      ) async {
        setRealScreenSize(
          tester,
          physicalSize: const Size(1920, 1080),
          devicePixelRatio: 3.0,
        );
        await tester.pumpWidget(
          _buildScaled(
            const Column(
              children: <Widget>[
                AppButton.filled(label: 'Kaydet'),
                AppTextField(label: 'Öğrenci adı'),
                AppListItem(title: 'Kısa başlık'),
                AppStatusChip(
                  label: 'Durum bilgisi',
                  type: AppStatusType.success,
                ),
              ],
            ),
            scaler,
          ),
        );
        assertNoOverflow(tester, 'narrow landscape at scale $label');
      });
    }

    testWidgets('AppConfirmDialog.show static flow keeps width and wraps', (
      WidgetTester tester,
    ) async {
      setRealScreenSize(tester);
      await tester.pumpWidget(
        MaterialApp(
          theme: _theme(),
          home: Builder(
            builder: (BuildContext context) =>
                const Scaffold(body: Center(child: Text('dialog-host'))),
          ),
        ),
      );
      final BuildContext context = tester.element(find.text('dialog-host'));

      unawaited(
        AppConfirmDialog.show(
          context: context,
          title: 'Öğrenci sınıf değişikliği onayı uzun başlık metni',
          message:
              'Bu işlem öğrencinin mevcut sınıf kaydını archivleyecek ve yeni '
              'sınıfa taşıma işlemi geri alınamaz olabilir.',
          confirmLabel: 'Arşivlenmiş öğrenciyi sınıfa geri yükle',
          cancelLabel: 'İptal et ve önceki ekrana dön',
          isDangerous: true,
        ),
      );
      await tester.pumpAndSettle();

      expect(
        find.text('Arşivlenmiş öğrenciyi sınıfa geri yükle'),
        findsOneWidget,
      );
      expect(find.text('İptal et ve önceki ekrana dön'), findsOneWidget);
      assertNoOverflow(tester, 'AppConfirmDialog.show static flow');
      assertTextsNotClipped(
        tester,
        find.byType(AppConfirmDialog),
        'AppConfirmDialog.show',
      );

      final RenderBox dialogBox = tester.renderObject<RenderBox>(
        find.byType(AlertDialog).first,
      );
      expect(
        dialogBox.size.width,
        lessThanOrEqualTo(AppSpacing.dialogMaxWidth),
      );

      await tester.tap(find.text('İptal et ve önceki ekrana dön'));
      await tester.pumpAndSettle();
      assertNoOverflow(tester, 'AppConfirmDialog.show close');
    });

    testWidgets('AppSnackBar rendered text grows under larger text scale', (
      WidgetTester tester,
    ) async {
      setRealScreenSize(tester);
      const String message =
          'Senkronizasyon tamamlanamadı, bağlantınızı kontrol ederek tekrar '
          'deneyin';

      Future<double> textHeight(TextScaler scaler) {
        return _measureSnackBarTextHeight(tester, scaler, message);
      }

      final double h1 = await textHeight(const TextScaler.linear(1.0));
      final double h2 = await textHeight(const TextScaler.linear(2.0));
      expect(h2, greaterThan(h1), reason: 'snackbar text must scale up');
    });
  });
}

Future<double> _measureSnackBarTextHeight(
  WidgetTester tester,
  TextScaler scaler,
  String message,
) async {
  await tester.pumpWidget(
    _buildSnackBarHost(scaler, hostLabel: 'snackbar-host'),
  );
  final BuildContext context = tester.element(find.text('snackbar-host'));
  ScaffoldMessenger.of(
    context,
  ).showSnackBar(AppSnackBar.error(context, message: message));
  await tester.pumpAndSettle();
  final RenderBox box = tester.renderObject<RenderBox>(find.text(message));
  return box.size.height;
}
