import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/theme/app_semantic_colors.dart';
import 'package:kurs_platform_mobile/core/theme/app_theme.dart';

void main() {
  group('AppTheme', () {
    test('produces deterministic ColorScheme for predefined themes', () {
      for (final MapEntry<String, (Color, Color)> entry
          in AppTheme.predefinedThemes.entries) {
        final AppTheme theme = AppTheme(
          primary: entry.value.$1,
          secondary: entry.value.$2,
        );
        final ColorScheme scheme = theme.colorScheme;
        expect(scheme.primary, entry.value.$1);
        expect(scheme.secondary, entry.value.$2);
        expect(scheme.brightness, Brightness.light);
      }
    });

    test('overrides primary and secondary in seed-generated scheme', () {
      const Color primary = Color(0xFF1565C0);
      const Color secondary = Color(0xFF00796B);
      final AppTheme theme = const AppTheme(
        primary: primary,
        secondary: secondary,
      );
      final ColorScheme scheme = theme.colorScheme;

      expect(scheme.primary, primary);
      expect(scheme.secondary, secondary);
    });

    test('onPrimary and onSecondary have sufficient contrast', () {
      for (final MapEntry<String, (Color, Color)> entry
          in AppTheme.predefinedThemes.entries) {
        final AppTheme theme = AppTheme(
          primary: entry.value.$1,
          secondary: entry.value.$2,
        );
        final ColorScheme scheme = theme.colorScheme;

        expect(
          contrastRatio(scheme.primary, scheme.onPrimary),
          greaterThanOrEqualTo(4.5),
          reason: 'onPrimary contrast for ${entry.key}',
        );
        expect(
          contrastRatio(scheme.secondary, scheme.onSecondary),
          greaterThanOrEqualTo(4.5),
          reason: 'onSecondary contrast for ${entry.key}',
        );
      }
    });

    test(
      'primary and secondary have sufficient graphical contrast against white',
      () {
        const Color white = Color(0xFFFFFFFF);
        for (final MapEntry<String, (Color, Color)> entry
            in AppTheme.predefinedThemes.entries) {
          expect(
            contrastRatio(entry.value.$1, white),
            greaterThanOrEqualTo(3.0),
            reason: 'primary graphical contrast for ${entry.key}',
          );
          expect(
            contrastRatio(entry.value.$2, white),
            greaterThanOrEqualTo(3.0),
            reason: 'secondary graphical contrast for ${entry.key}',
          );
        }
      },
    );

    test('semantic colors are attached to ThemeData', () {
      const AppTheme theme = AppTheme(
        primary: Color(0xFF2E7D32),
        secondary: Color(0xFFE65100),
      );
      final ThemeData data = theme.themeData;
      expect(data.extensions.values, isNotEmpty);
    });
  });

  group('UI-001 §15.2 container/surface contrast checks', () {
    final List<(Color? Function(ColorScheme), Color? Function(ColorScheme))>
    pairs = <(Color? Function(ColorScheme), Color? Function(ColorScheme))>[
      (
        (ColorScheme s) => s.primaryContainer,
        (ColorScheme s) => s.onPrimaryContainer,
      ),
      (
        (ColorScheme s) => s.secondaryContainer,
        (ColorScheme s) => s.onSecondaryContainer,
      ),
      ((ColorScheme s) => s.surface, (ColorScheme s) => s.onSurface),
      (
        (ColorScheme s) => s.surfaceContainerLowest,
        (ColorScheme s) => s.onSurface,
      ),
      (
        (ColorScheme s) => s.surfaceContainerLow,
        (ColorScheme s) => s.onSurface,
      ),
      ((ColorScheme s) => s.surfaceContainer, (ColorScheme s) => s.onSurface),
      (
        (ColorScheme s) => s.surfaceContainerHigh,
        (ColorScheme s) => s.onSurface,
      ),
      (
        (ColorScheme s) => s.surfaceContainerHighest,
        (ColorScheme s) => s.onSurface,
      ),
    ];

    for (final MapEntry<String, (Color, Color)> entry
        in AppTheme.predefinedThemes.entries) {
      test('all container/surface pairs pass WCAG AA for ${entry.key}', () {
        final AppTheme theme = AppTheme(
          primary: entry.value.$1,
          secondary: entry.value.$2,
        );
        final ColorScheme scheme = theme.colorScheme;
        for (final (
              Color? Function(ColorScheme) background,
              Color? Function(ColorScheme) foreground,
            )
            pair
            in pairs) {
          final Color? bg = pair.$1(scheme);
          final Color? fg = pair.$2(scheme);
          if (bg == null || fg == null) {
            continue;
          }
          expect(
            contrastRatio(bg, fg),
            greaterThanOrEqualTo(4.5),
            reason: 'contrast for ${entry.key}',
          );
        }
      });
    }
  });

  group('Component semantic color contrast checks', () {
    const AppTheme theme = AppTheme(
      primary: Color(0xFF2E7D32),
      secondary: Color(0xFFE65100),
    );
    final ColorScheme scheme = theme.colorScheme;
    final AppSemanticColors semantic = AppSemanticColors.light;

    test('AppStatusChip color pairs meet WCAG AA', () {
      final List<(Color, Color)> chipPairs = <(Color, Color)>[
        (semantic.successContainer, semantic.onSuccessContainer),
        (semantic.warningContainer, semantic.onWarningContainer),
        (scheme.errorContainer, scheme.onErrorContainer),
        (semantic.infoContainer, semantic.onInfoContainer),
        (semantic.neutral100, semantic.neutral700),
      ];
      for (final (Color bg, Color fg) in chipPairs) {
        expect(
          contrastRatio(bg, fg),
          greaterThanOrEqualTo(4.5),
          reason: 'AppStatusChip contrast ${bg.toString()} / ${fg.toString()}',
        );
      }
    });

    test('AppSnackBar color pairs meet WCAG AA', () {
      expect(
        contrastRatio(semantic.success, semantic.onSuccess),
        greaterThanOrEqualTo(4.5),
      );
      expect(
        contrastRatio(scheme.error, scheme.onError),
        greaterThanOrEqualTo(4.5),
      );
      expect(
        contrastRatio(semantic.info, semantic.onInfo),
        greaterThanOrEqualTo(4.5),
      );
    });

    const Color white = Color(0xFFFFFFFF);

    test('input border colors meet graphical contrast against white', () {
      final InputDecorationThemeData inputTheme =
          theme.themeData.inputDecorationTheme;
      final List<Color> borders = <Color>[
        inputTheme.border!.borderSide.color,
        inputTheme.focusedBorder!.borderSide.color,
        inputTheme.errorBorder!.borderSide.color,
      ];
      for (final Color border in borders) {
        expect(
          contrastRatio(border, white),
          greaterThanOrEqualTo(3.0),
          reason: 'input border ${border.toString()}',
        );
      }
    });

    test('disabled input border is exempt from WCAG contrast', () {
      final InputDecorationThemeData inputTheme =
          theme.themeData.inputDecorationTheme;
      const Color white = Color(0xFFFFFFFF);
      expect(
        contrastRatio(inputTheme.disabledBorder!.borderSide.color, white),
        lessThan(3.0),
      );
    });

    test('button variant color pairs meet required thresholds', () {
      expect(
        contrastRatio(scheme.primary, scheme.onPrimary),
        greaterThanOrEqualTo(4.5),
        reason: 'filled button',
      );
      expect(
        contrastRatio(scheme.error, scheme.onError),
        greaterThanOrEqualTo(4.5),
        reason: 'danger button',
      );
      expect(
        contrastRatio(scheme.primaryContainer, scheme.onPrimaryContainer),
        greaterThanOrEqualTo(4.5),
        reason: 'tonal button',
      );
      expect(
        contrastRatio(white, scheme.primary),
        greaterThanOrEqualTo(3.0),
        reason: 'outlined/text button foreground against white',
      );
    });
  });
}
