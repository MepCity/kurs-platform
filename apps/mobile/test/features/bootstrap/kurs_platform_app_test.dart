import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme_provider.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';

void main() {
  Widget buildApp({
    AppThemeProvider? provider,
    Widget? home,
    Size? size,
    EdgeInsets? padding,
  }) {
    return MediaQuery(
      data: MediaQueryData(
        size: size ?? const Size(360, 640),
        padding: padding ?? EdgeInsets.zero,
      ),
      child: KursPlatformApp(provider: provider, home: home),
    );
  }

  group('KursPlatformApp', () {
    testWidgets('uses injected provider and does not dispose it', (
      WidgetTester tester,
    ) async {
      final provider = AppThemeProvider();
      addTearDown(provider.dispose);

      await tester.pumpWidget(
        buildApp(
          provider: provider,
          home: const Scaffold(body: Center(child: Text('Home'))),
        ),
      );

      expect(find.text('Home'), findsOneWidget);
    });

    testWidgets('MaterialApp rebuilds when provider colors change', (
      WidgetTester tester,
    ) async {
      final provider = AppThemeProvider();
      addTearDown(provider.dispose);

      await tester.pumpWidget(
        buildApp(
          provider: provider,
          home: Builder(
            builder: (BuildContext context) {
              return Container(
                color: Theme.of(context).colorScheme.primary,
                child: const Text('Home'),
              );
            },
          ),
        ),
      );

      final firstColor = tester.widget<Container>(find.byType(Container)).color;
      expect(firstColor, const Color(0xFF2E7D32));

      provider.updateInstitutionColors(
        primary: const Color(0xFF1565C0),
        secondary: const Color(0xFF00796B),
      );
      await tester.pumpAndSettle();

      final secondColor = tester
          .widget<Container>(find.byType(Container))
          .color;
      expect(secondColor, const Color(0xFF1565C0));
    });

    testWidgets('does not wrap content with a global SafeArea', (
      WidgetTester tester,
    ) async {
      await tester.pumpWidget(
        buildApp(
          padding: const EdgeInsets.only(left: 12, right: 12, bottom: 24),
          home: const Scaffold(body: Center(child: Text('Home'))),
        ),
      );

      expect(find.byType(SafeArea), findsNothing);
    });

    testWidgets('renders in portrait and landscape', (
      WidgetTester tester,
    ) async {
      final provider = AppThemeProvider();
      addTearDown(provider.dispose);

      for (final orientation in <Orientation>[
        Orientation.portrait,
        Orientation.landscape,
      ]) {
        final size = orientation == Orientation.portrait
            ? const Size(360, 640)
            : const Size(640, 360);

        await tester.pumpWidget(
          buildApp(
            provider: provider,
            size: size,
            home: const Scaffold(body: Center(child: Text('Orientation'))),
          ),
        );

        expect(find.text('Orientation'), findsOneWidget);
      }
    });

    testWidgets('AppThemeScope is available to descendants', (
      WidgetTester tester,
    ) async {
      final provider = AppThemeProvider();
      addTearDown(provider.dispose);
      late AppThemeProvider found;

      await tester.pumpWidget(
        buildApp(
          provider: provider,
          home: Builder(
            builder: (BuildContext context) {
              found = AppThemeScope.of(context);
              return const Text('Home');
            },
          ),
        ),
      );

      expect(found, same(provider));
    });
  });
}
