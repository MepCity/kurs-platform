/// Fixed design-token spacing and sizing values from UI-001.
///
/// These values are not institution-configurable. They are exposed as a
/// plain constants class so the widget tests and components can reference the
/// same source of truth.
abstract class AppSpacing {
  // Base grid: 4 dp.
  static const double space1 = 4;
  static const double space2 = 8;
  static const double space3 = 12;
  static const double space4 = 16;
  static const double space5 = 20;
  static const double space6 = 24;
  static const double space8 = 32;
  static const double space10 = 40;
  static const double space12 = 48;

  // Border radius.
  static const double radiusSm = 4;
  static const double radiusMd = 8;
  static const double radiusLg = 12;
  static const double radiusXl = 16;
  static const double radiusPill = 999;

  // Component sizing.
  static const double buttonHeightSm = 32;
  static const double buttonHeightMd = 40;
  static const double buttonHeightLg = 48;
  static const double inputHeight = 48;
  static const double inputHeightMultiline = 80;
  static const double chipHeight = 32;
  static const double listItemMinHeight = 48;
  static const double listItemMinHeightDouble = 64;
  static const double appBarHeight = 56;
  static const double navBarHeight = 64;
  static const double dialogMaxWidth = 320;
  static const double touchTarget = 48;
  static const double listDividerHeight = 1;
  static const double safeAreaBottomMin = 48;
  static const double keyboardAvoidMinPadding = 16;

  // Icon sizes.
  static const double iconXs = 12;
  static const double iconSm = 16;
  static const double iconMd = 20;
  static const double iconLg = 24;
  static const double iconXl = 32;
  static const double icon2Xl = 48;

  // Typography sizes.
  static const double textXs = 10;
  static const double textSm = 12;
  static const double textMd = 14;
  static const double textLg = 16;
  static const double textXl = 18;
  static const double text2Xl = 20;
  static const double text3Xl = 24;
  static const double text4Xl = 28;
  static const double text5Xl = 32;
  static const double text6Xl = 40;

  // Durations.
  static const int durationInstant = 100;
  static const int durationFast = 150;
  static const int durationNormal = 300;
  static const int durationSlow = 500;
  static const int durationSnackbar = 4000;
}
