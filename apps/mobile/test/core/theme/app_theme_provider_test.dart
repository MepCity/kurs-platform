import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme_provider.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

void main() {
  group('AppThemeProvider', () {
    test('defaults to Zeytin theme', () {
      final provider = AppThemeProvider();
      expect(provider.theme.primary, const Color(0xFF2E7D32));
      expect(provider.theme.secondary, const Color(0xFFE65100));
    });

    test('can be injected with a custom theme', () {
      final provider = AppThemeProvider(
        theme: const AppTheme(
          primary: Color(0xFF1565C0),
          secondary: Color(0xFF00796B),
        ),
      );
      expect(provider.theme.primary, const Color(0xFF1565C0));
      expect(provider.theme.secondary, const Color(0xFF00796B));
    });

    test('notifies listeners when colors change', () {
      final provider = AppThemeProvider();
      var notified = false;
      provider.addListener(() => notified = true);

      provider.updateInstitutionColors(
        primary: const Color(0xFF1565C0),
        secondary: const Color(0xFF00796B),
      );

      expect(notified, isTrue);
      expect(provider.theme.primary, const Color(0xFF1565C0));
    });

    test('does not notify when colors are unchanged', () {
      final provider = AppThemeProvider();
      var notified = false;
      provider.addListener(() => notified = true);

      provider.updateInstitutionColors(
        primary: const Color(0xFF2E7D32),
        secondary: const Color(0xFFE65100),
      );

      expect(notified, isFalse);
    });

    group('updateInstitutionColorsFromHex', () {
      test('accepts #RRGGBB format', () {
        final provider = AppThemeProvider();
        provider.updateInstitutionColorsFromHex(
          primaryHex: '#1565C0',
          secondaryHex: '#00796B',
        );
        expect(provider.theme.primary, const Color(0xFF1565C0));
        expect(provider.theme.secondary, const Color(0xFF00796B));
      });

      test('accepts RRGGBB format without hash', () {
        final provider = AppThemeProvider();
        provider.updateInstitutionColorsFromHex(
          primaryHex: '1565C0',
          secondaryHex: '00796B',
        );
        expect(provider.theme.primary, const Color(0xFF1565C0));
        expect(provider.theme.secondary, const Color(0xFF00796B));
      });

      test('rejects short RGB hex', () {
        final provider = AppThemeProvider();
        expect(
          () => provider.updateInstitutionColorsFromHex(
            primaryHex: '#FFF',
            secondaryHex: '#000',
          ),
          throwsFormatException,
        );
      });

      test('rejects alpha-prefixed ARGB hex', () {
        final provider = AppThemeProvider();
        expect(
          () => provider.updateInstitutionColorsFromHex(
            primaryHex: '#FF1565C0',
            secondaryHex: '#FF00796B',
          ),
          throwsFormatException,
        );
      });

      test('rejects invalid characters', () {
        final provider = AppThemeProvider();
        expect(
          () => provider.updateInstitutionColorsFromHex(
            primaryHex: 'GGGGGG',
            secondaryHex: '00796B',
          ),
          throwsFormatException,
        );
      });

      test('rejects empty hex', () {
        final provider = AppThemeProvider();
        expect(
          () => provider.updateInstitutionColorsFromHex(
            primaryHex: '',
            secondaryHex: '#00796B',
          ),
          throwsFormatException,
        );
      });
    });
  });

  group('AppThemeScope', () {
    testWidgets('exposes provider to descendants', (WidgetTester tester) async {
      final provider = AppThemeProvider();
      late AppThemeProvider found;

      await tester.pumpWidget(
        AppThemeScope(
          notifier: provider,
          child: Builder(
            builder: (BuildContext context) {
              found = AppThemeScope.of(context);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(found, same(provider));
    });
  });
}
