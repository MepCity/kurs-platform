import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'app_semantic_colors.dart';

/// Deterministic on-color selection for a given sRGB hex.
///
/// Implements UI-001 §3.2.2 adım 3: choose the higher contrast candidate between
/// white and black. The equality branch returns white as required by the token
/// contract.
@visibleForTesting
Color selectOnColor(Color color) {
  final double luminance = relativeLuminance(color);
  final double whiteContrast = 1.05 / (luminance + 0.05);
  final double blackContrast = (luminance + 0.05) / 0.05;
  return whiteContrast >= blackContrast ? Colors.white : Colors.black;
}

/// WCAG 2.1 relative luminance for an sRGB color.
@visibleForTesting
double relativeLuminance(Color color) {
  double channel(double c) {
    final double v = c <= 0.03928
        ? c / 12.92
        : math.pow((c + 0.055) / 1.055, 2.4).toDouble();
    return v;
  }

  return 0.2126 * channel(color.r) +
      0.7152 * channel(color.g) +
      0.0722 * channel(color.b);
}

/// Contrast ratio between two colors per WCAG 2.1.
@visibleForTesting
double contrastRatio(Color a, Color b) {
  final double l1 = relativeLuminance(a) + 0.05;
  final double l2 = relativeLuminance(b) + 0.05;
  return l1 > l2 ? l1 / l2 : l2 / l1;
}

/// Token-based theme factory for the Kurs Platform mobile application.
///
/// This class does not depend on any network or storage. It only consumes the
/// institution's [primary] and [secondary] hex colors and produces a
/// deterministic [ColorScheme] and [ThemeData] according to UI-001.
class AppTheme {
  const AppTheme({required this.primary, required this.secondary});

  final Color primary;
  final Color secondary;

  /// Pre-defined themes from UI-001 §3.2.1.
  static const Map<String, (Color primary, Color secondary)> predefinedThemes =
      <String, (Color, Color)>{
        'Zeytin': (Color(0xFF2E7D32), Color(0xFFE65100)),
        'Safir': (Color(0xFF1565C0), Color(0xFF00796B)),
        'Nar': (Color(0xFFC62828), Color(0xFF455A64)),
        'Lavanta': (Color(0xFF6A1B9A), Color(0xFF006064)),
        'Toprak': (Color(0xFF6D4C41), Color(0xFF2E7D32)),
      };

  static const Color _error = Color(0xFFD32F2F);
  static const Color _onError = Color(0xFFFFFFFF);
  static const Color _errorContainer = Color(0xFFFFCDD2);
  static const Color _onErrorContainer = Color(0xFFB71C1C);

  /// Creates a [ColorScheme] from the institution colors using Material 3
  /// tonal palette generation.
  ColorScheme get colorScheme {
    final ColorScheme seedScheme = ColorScheme.fromSeed(
      seedColor: primary,
      dynamicSchemeVariant: DynamicSchemeVariant.content,
      brightness: Brightness.light,
    );

    final Color onPrimary = selectOnColor(primary);
    final Color onSecondary = selectOnColor(secondary);

    return seedScheme.copyWith(
      primary: primary,
      onPrimary: onPrimary,
      secondary: secondary,
      onSecondary: onSecondary,
      error: _error,
      onError: _onError,
      errorContainer: _errorContainer,
      onErrorContainer: _onErrorContainer,
    );
  }

  /// Full [ThemeData] built from the color scheme and fixed design tokens.
  ThemeData get themeData {
    final ColorScheme scheme = colorScheme;
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      brightness: Brightness.light,
      extensions: const <ThemeExtension<dynamic>>[AppSemanticColors.light],
      scaffoldBackgroundColor: const Color(0xFFFFFFFF),
      appBarTheme: AppBarTheme(
        backgroundColor: const Color(0xFFFFFFFF),
        foregroundColor: const Color(0xFF212529),
        elevation: 0,
        centerTitle: true,
        systemOverlayStyle: SystemUiOverlayStyle.dark,
        titleTextStyle: TextStyle(
          color: const Color(0xFF212529),
          fontSize: 18,
          fontWeight: FontWeight.w600,
          height: 1.25,
        ),
      ),
      cardTheme: CardThemeData(
        color: const Color(0xFFFFFFFF),
        elevation: 1,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        margin: EdgeInsets.zero,
      ),
      dividerTheme: const DividerThemeData(
        color: Color(0xFFE9ECEF),
        thickness: 1,
        space: 1,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFFFFFFFF),
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 12,
          vertical: 16,
        ),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(4),
          borderSide: const BorderSide(color: Color(0xFF7B8591), width: 1),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(4),
          borderSide: const BorderSide(color: Color(0xFF7B8591), width: 1),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(4),
          borderSide: BorderSide(color: scheme.primary, width: 2),
        ),
        errorBorder: const OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(4)),
          borderSide: BorderSide(color: _error, width: 2),
        ),
        focusedErrorBorder: const OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(4)),
          borderSide: BorderSide(color: _error, width: 2),
        ),
        disabledBorder: const OutlineInputBorder(
          borderRadius: BorderRadius.all(Radius.circular(4)),
          borderSide: BorderSide(color: Color(0xFFDEE2E6), width: 1),
        ),
        labelStyle: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w400,
          height: 1.25,
          color: Color(0xFF6C757D),
        ),
        floatingLabelStyle: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w400,
          height: 1.25,
          color: scheme.primary,
        ),
        errorStyle: const TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w400,
          height: 1.25,
          color: _error,
        ),
        hintStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: Color(0xFFADB5BD),
        ),
      ),
      elevatedButtonTheme: ElevatedButtonThemeData(
        style: ElevatedButton.styleFrom(
          backgroundColor: scheme.primary,
          foregroundColor: scheme.onPrimary,
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(999),
          ),
          textStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            height: 1.25,
          ),
        ),
      ),
      textButtonTheme: TextButtonThemeData(
        style: TextButton.styleFrom(
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(999),
          ),
          textStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            height: 1.25,
          ),
        ),
      ),
      outlinedButtonTheme: OutlinedButtonThemeData(
        style: OutlinedButton.styleFrom(
          foregroundColor: scheme.primary,
          side: BorderSide(color: scheme.primary, width: 1.5),
          minimumSize: const Size(48, 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(999),
          ),
          textStyle: const TextStyle(
            fontSize: 14,
            fontWeight: FontWeight.w500,
            height: 1.25,
          ),
        ),
      ),
      chipTheme: ChipThemeData(
        backgroundColor: const Color(0xFFE9ECEF),
        disabledColor: const Color(0xFFDEE2E6),
        selectedColor: scheme.primaryContainer,
        showCheckmark: true,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
        labelStyle: const TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w400,
          height: 1.25,
        ),
        iconTheme: IconThemeData(color: scheme.onPrimaryContainer, size: 16),
      ),
      bottomSheetTheme: BottomSheetThemeData(
        backgroundColor: const Color(0xFFFFFFFF),
        elevation: 8,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
      ),
      dialogTheme: DialogThemeData(
        backgroundColor: const Color(0xFFFFFFFF),
        elevation: 8,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
      snackBarTheme: SnackBarThemeData(
        backgroundColor: const Color(0xFF343A40),
        contentTextStyle: const TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: Color(0xFFFFFFFF),
        ),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        behavior: SnackBarBehavior.floating,
        elevation: 6,
      ),
      textTheme: const TextTheme(
        displayLarge: TextStyle(
          fontSize: 40,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        displayMedium: TextStyle(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        displaySmall: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        headlineMedium: TextStyle(
          fontSize: 28,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        headlineSmall: TextStyle(
          fontSize: 24,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        titleLarge: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.w700,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        titleMedium: TextStyle(
          fontSize: 18,
          fontWeight: FontWeight.w600,
          height: 1.25,
          color: Color(0xFF212529),
        ),
        titleSmall: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w600,
          height: 1.5,
          color: Color(0xFF212529),
        ),
        bodyLarge: TextStyle(
          fontSize: 16,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: Color(0xFF495057),
        ),
        bodyMedium: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: Color(0xFF495057),
        ),
        bodySmall: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: Color(0xFF6C757D),
        ),
        labelLarge: TextStyle(
          fontSize: 14,
          fontWeight: FontWeight.w500,
          height: 1.25,
          color: Color(0xFF495057),
        ),
        labelMedium: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w500,
          height: 1.25,
          color: Color(0xFF6C757D),
        ),
        labelSmall: TextStyle(
          fontSize: 10,
          fontWeight: FontWeight.w500,
          height: 1.25,
          color: Color(0xFF6C757D),
        ),
      ),
    );
  }
}
