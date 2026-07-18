import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/presentation/widgets/widgets.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

Widget _wrap(Widget child) {
  return MaterialApp(
    theme: const AppTheme(
      primary: Color(0xFF2E7D32),
      secondary: Color(0xFFE65100),
    ).themeData,
    home: Scaffold(body: child),
  );
}

void main() {
  group('AppEmptyState', () {
    testWidgets('renders title, description and action', (
      WidgetTester tester,
    ) async {
      bool tapped = false;
      await tester.pumpWidget(
        _wrap(
          AppEmptyState(
            title: 'Henüz sınıf yok',
            description: 'Kurum yöneticinizle iletişime geçin.',
            actionLabel: 'Yenile',
            onAction: () => tapped = true,
          ),
        ),
      );
      expect(find.text('Henüz sınıf yok'), findsOneWidget);
      expect(find.text('Kurum yöneticinizle iletişime geçin.'), findsOneWidget);
      expect(find.text('Yenile'), findsOneWidget);
      await tester.tap(find.text('Yenile'));
      await tester.pump();
      expect(tapped, isTrue);
    });
  });

  group('AppLoadingState', () {
    testWidgets('renders progress indicator', (WidgetTester tester) async {
      await tester.pumpWidget(
        _wrap(const AppLoadingState(label: 'Yükleniyor')),
      );
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Yükleniyor'), findsOneWidget);
    });
  });

  group('AppErrorState', () {
    testWidgets('renders message and retry', (WidgetTester tester) async {
      bool retried = false;
      await tester.pumpWidget(
        _wrap(
          AppErrorState(
            message: 'Bağlantı hatası',
            onRetry: () => retried = true,
          ),
        ),
      );
      expect(find.text('Bağlantı hatası'), findsOneWidget);
      expect(find.text('Tekrar Dene'), findsOneWidget);
      await tester.tap(find.text('Tekrar Dene'));
      await tester.pump();
      expect(retried, isTrue);
    });

    testWidgets('error icon uses ColorScheme.error', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(_wrap(const AppErrorState(message: 'Hata')));
      final icon = tester.widget<Icon>(find.byIcon(Icons.error_outline));
      final scheme = Theme.of(
        tester.element(find.byType(AppErrorState)),
      ).colorScheme;
      expect(icon.color, scheme.error);
    });
  });

  group('AppUnauthorizedState', () {
    testWidgets('renders message', (WidgetTester tester) async {
      await tester.pumpWidget(_wrap(const AppUnauthorizedState()));
      expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
    });
  });

  group('AppConfirmDialog', () {
    testWidgets('returns true on confirm', (WidgetTester tester) async {
      late Future<bool> result;
      await tester.pumpWidget(
        _wrap(
          Builder(
            builder: (BuildContext context) => TextButton(
              onPressed: () {
                result = AppConfirmDialog.show(
                  context: context,
                  title: 'Sil',
                  message: 'Emin misiniz?',
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.text('Sil'), findsOneWidget);
      expect(find.text('Emin misiniz?'), findsOneWidget);
      await tester.tap(find.text('Onayla'));
      await tester.pumpAndSettle();
      expect(await result, isTrue);
    });

    testWidgets('dialog width does not exceed 320 dp', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          Builder(
            builder: (BuildContext context) => TextButton(
              onPressed: () {
                AppConfirmDialog.show(
                  context: context,
                  title: 'Sil',
                  message: 'Emin misiniz?',
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      final Finder constrainedBox = find.byWidgetPredicate(
        (Widget widget) =>
            widget is ConstrainedBox && widget.constraints.maxWidth == 320,
      );
      expect(constrainedBox, findsOneWidget);
      final RenderBox renderBox =
          tester.renderObject(constrainedBox) as RenderBox;
      expect(renderBox.size.width, lessThanOrEqualTo(320));
    });

    testWidgets('dangerous confirm uses danger button', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        _wrap(
          Builder(
            builder: (BuildContext context) => TextButton(
              onPressed: () {
                AppConfirmDialog.show(
                  context: context,
                  title: 'Sil',
                  message: 'Emin misiniz?',
                  isDangerous: true,
                );
              },
              child: const Text('Open'),
            ),
          ),
        ),
      );
      await tester.tap(find.text('Open'));
      await tester.pumpAndSettle();
      expect(find.byType(AppButton), findsNWidgets(2));
      final danger = tester.widget<AppButton>(
        find.widgetWithText(AppButton, 'Onayla'),
      );
      expect(danger.variant, AppButtonVariant.danger);
    });
  });
}
