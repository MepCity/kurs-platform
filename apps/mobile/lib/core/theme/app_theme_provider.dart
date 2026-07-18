import 'package:flutter/material.dart';
import 'app_semantic_colors.dart';
import 'app_theme.dart';

/// Holds the current institution-aware theme and exposes it to the widget tree.
///
/// This provider intentionally does not depend on storage, network, or any
/// other asynchronous source. The owning feature (e.g. session bootstrap)
/// creates and disposes it. Colors may be updated when the active institution
/// context is known.
class AppThemeProvider extends ChangeNotifier {
  AppThemeProvider({AppTheme? theme})
    : _theme =
          theme ??
          const AppTheme(
            primary: Color(0xFF2E7D32),
            secondary: Color(0xFFE65100),
          );

  AppTheme _theme;

  AppTheme get theme => _theme;

  ThemeData get themeData {
    return _theme.themeData.copyWith(
      extensions: const <ThemeExtension<dynamic>>[AppSemanticColors.light],
    );
  }

  /// Updates the institution colors only when they actually changed.
  ///
  /// Calling this with the same colors does not notify listeners or trigger
  /// a rebuild.
  void updateInstitutionColors({
    required Color primary,
    required Color secondary,
  }) {
    if (_theme.primary == primary && _theme.secondary == secondary) {
      return;
    }
    _theme = AppTheme(primary: primary, secondary: secondary);
    notifyListeners();
  }

  /// Convenience helper to update from a 6-digit hex string.
  ///
  /// Accepts exactly six hexadecimal digits, optionally prefixed with `#`.
  /// Any other length (including short RGB, alpha-prefixed ARGB and empty
  /// values) is rejected with a [FormatException].
  void updateInstitutionColorsFromHex({
    required String primaryHex,
    required String secondaryHex,
  }) {
    updateInstitutionColors(
      primary: _parseHex(primaryHex),
      secondary: _parseHex(secondaryHex),
    );
  }

  static Color _parseHex(String hex) {
    final String trimmed = hex.trim();
    final String value = trimmed.replaceFirst('#', '');
    if (value.length != 6) {
      throw FormatException(
        'Hex color must be exactly 6 digits (#RRGGBB or RRGGBB): "$hex"',
      );
    }
    final int? colorValue = int.tryParse(value, radix: 16);
    if (colorValue == null) {
      throw FormatException('Invalid hex color: "$hex"');
    }
    return Color(colorValue | 0xFF000000);
  }
}

/// Exposes an [AppThemeProvider] to the widget tree via [InheritedNotifier].
///
/// This keeps ownership explicit: whoever creates the provider is responsible
/// for disposing it. [KursPlatformApp] can receive a provider through its
/// constructor or locate it through this scope when wrapped by an upstream
/// owner.
class AppThemeScope extends InheritedNotifier<AppThemeProvider> {
  const AppThemeScope({
    required AppThemeProvider super.notifier,
    required super.child,
    super.key,
  });

  static AppThemeProvider of(BuildContext context) {
    final AppThemeScope? scope = context
        .dependOnInheritedWidgetOfExactType<AppThemeScope>();
    assert(scope != null, 'AppThemeScope not found in widget tree');
    assert(scope!.notifier != null, 'AppThemeScope notifier is null');
    return scope!.notifier!;
  }

  /// Looks up the scope without establishing a dependency.
  ///
  /// Useful when the caller only needs the current value once and does not
  /// want to rebuild on theme changes.
  static AppThemeProvider? maybeOf(BuildContext context) {
    final AppThemeScope? scope =
        context.getElementForInheritedWidgetOfExactType<AppThemeScope>()?.widget
            as AppThemeScope?;
    return scope?.notifier;
  }
}
