import 'package:flutter/material.dart';

/// Semantic colors that extend [ThemeData] for states not covered by
/// Material 3's [ColorScheme].
///
/// Implements UI-001 §3.3: success, warning and info semantic colors are
/// carried as a [ThemeExtension] because Flutter's [ColorScheme] does not
/// provide these fields.
@immutable
class AppSemanticColors extends ThemeExtension<AppSemanticColors> {
  const AppSemanticColors({
    required this.success,
    required this.onSuccess,
    required this.successContainer,
    required this.onSuccessContainer,
    required this.warning,
    required this.onWarning,
    required this.warningContainer,
    required this.onWarningContainer,
    required this.info,
    required this.onInfo,
    required this.infoContainer,
    required this.onInfoContainer,
    required this.neutral0,
    required this.neutral50,
    required this.neutral100,
    required this.neutral200,
    required this.neutral300,
    required this.neutral400,
    required this.neutral500,
    required this.neutral600,
    required this.neutral700,
    required this.neutral800,
    required this.neutral900,
    required this.neutral950,
  });

  const AppSemanticColors._light()
    : success = const Color(0xFF2E7D32),
      onSuccess = const Color(0xFFFFFFFF),
      successContainer = const Color(0xFFC8E6C9),
      onSuccessContainer = const Color(0xFF1B5E20),
      warning = const Color(0xFFBF360C),
      onWarning = const Color(0xFFFFFFFF),
      warningContainer = const Color(0xFFFFF3E0),
      onWarningContainer = const Color(0xFFBF360C),
      info = const Color(0xFF1565C0),
      onInfo = const Color(0xFFFFFFFF),
      infoContainer = const Color(0xFFBBDEFB),
      onInfoContainer = const Color(0xFF0D47A1),
      neutral0 = const Color(0xFFFFFFFF),
      neutral50 = const Color(0xFFF8F9FA),
      neutral100 = const Color(0xFFE9ECEF),
      neutral200 = const Color(0xFFDEE2E6),
      neutral300 = const Color(0xFFCED4DA),
      neutral400 = const Color(0xFFADB5BD),
      neutral500 = const Color(0xFF6C757D),
      neutral600 = const Color(0xFF495057),
      neutral700 = const Color(0xFF343A40),
      neutral800 = const Color(0xFF212529),
      neutral900 = const Color(0xFF121416),
      neutral950 = const Color(0xFF0A0B0D);

  /// Default light-mode semantic colors from UI-001.
  static const AppSemanticColors light = AppSemanticColors._light();

  final Color success;
  final Color onSuccess;
  final Color successContainer;
  final Color onSuccessContainer;

  final Color warning;
  final Color onWarning;
  final Color warningContainer;
  final Color onWarningContainer;

  final Color info;
  final Color onInfo;
  final Color infoContainer;
  final Color onInfoContainer;

  // Neutral palette is fixed and does not change with institution colors.
  final Color neutral0;
  final Color neutral50;
  final Color neutral100;
  final Color neutral200;
  final Color neutral300;
  final Color neutral400;
  final Color neutral500;
  final Color neutral600;
  final Color neutral700;
  final Color neutral800;
  final Color neutral900;
  final Color neutral950;

  /// Returns the light semantic colors from the current [BuildContext].
  static AppSemanticColors of(BuildContext context) {
    final AppSemanticColors? colors = Theme.of(
      context,
    ).extension<AppSemanticColors>();
    assert(colors != null, 'AppSemanticColors is not registered in ThemeData');
    return colors ?? AppSemanticColors.light;
  }

  @override
  AppSemanticColors copyWith({
    Color? success,
    Color? onSuccess,
    Color? successContainer,
    Color? onSuccessContainer,
    Color? warning,
    Color? onWarning,
    Color? warningContainer,
    Color? onWarningContainer,
    Color? info,
    Color? onInfo,
    Color? infoContainer,
    Color? onInfoContainer,
    Color? neutral0,
    Color? neutral50,
    Color? neutral100,
    Color? neutral200,
    Color? neutral300,
    Color? neutral400,
    Color? neutral500,
    Color? neutral600,
    Color? neutral700,
    Color? neutral800,
    Color? neutral900,
    Color? neutral950,
  }) {
    return AppSemanticColors(
      success: success ?? this.success,
      onSuccess: onSuccess ?? this.onSuccess,
      successContainer: successContainer ?? this.successContainer,
      onSuccessContainer: onSuccessContainer ?? this.onSuccessContainer,
      warning: warning ?? this.warning,
      onWarning: onWarning ?? this.onWarning,
      warningContainer: warningContainer ?? this.warningContainer,
      onWarningContainer: onWarningContainer ?? this.onWarningContainer,
      info: info ?? this.info,
      onInfo: onInfo ?? this.onInfo,
      infoContainer: infoContainer ?? this.infoContainer,
      onInfoContainer: onInfoContainer ?? this.onInfoContainer,
      neutral0: neutral0 ?? this.neutral0,
      neutral50: neutral50 ?? this.neutral50,
      neutral100: neutral100 ?? this.neutral100,
      neutral200: neutral200 ?? this.neutral200,
      neutral300: neutral300 ?? this.neutral300,
      neutral400: neutral400 ?? this.neutral400,
      neutral500: neutral500 ?? this.neutral500,
      neutral600: neutral600 ?? this.neutral600,
      neutral700: neutral700 ?? this.neutral700,
      neutral800: neutral800 ?? this.neutral800,
      neutral900: neutral900 ?? this.neutral900,
      neutral950: neutral950 ?? this.neutral950,
    );
  }

  @override
  AppSemanticColors lerp(ThemeExtension<AppSemanticColors>? other, double t) {
    if (other is! AppSemanticColors) {
      return this;
    }
    return AppSemanticColors(
      success: Color.lerp(success, other.success, t)!,
      onSuccess: Color.lerp(onSuccess, other.onSuccess, t)!,
      successContainer: Color.lerp(
        successContainer,
        other.successContainer,
        t,
      )!,
      onSuccessContainer: Color.lerp(
        onSuccessContainer,
        other.onSuccessContainer,
        t,
      )!,
      warning: Color.lerp(warning, other.warning, t)!,
      onWarning: Color.lerp(onWarning, other.onWarning, t)!,
      warningContainer: Color.lerp(
        warningContainer,
        other.warningContainer,
        t,
      )!,
      onWarningContainer: Color.lerp(
        onWarningContainer,
        other.onWarningContainer,
        t,
      )!,
      info: Color.lerp(info, other.info, t)!,
      onInfo: Color.lerp(onInfo, other.onInfo, t)!,
      infoContainer: Color.lerp(infoContainer, other.infoContainer, t)!,
      onInfoContainer: Color.lerp(onInfoContainer, other.onInfoContainer, t)!,
      neutral0: Color.lerp(neutral0, other.neutral0, t)!,
      neutral50: Color.lerp(neutral50, other.neutral50, t)!,
      neutral100: Color.lerp(neutral100, other.neutral100, t)!,
      neutral200: Color.lerp(neutral200, other.neutral200, t)!,
      neutral300: Color.lerp(neutral300, other.neutral300, t)!,
      neutral400: Color.lerp(neutral400, other.neutral400, t)!,
      neutral500: Color.lerp(neutral500, other.neutral500, t)!,
      neutral600: Color.lerp(neutral600, other.neutral600, t)!,
      neutral700: Color.lerp(neutral700, other.neutral700, t)!,
      neutral800: Color.lerp(neutral800, other.neutral800, t)!,
      neutral900: Color.lerp(neutral900, other.neutral900, t)!,
      neutral950: Color.lerp(neutral950, other.neutral950, t)!,
    );
  }
}
