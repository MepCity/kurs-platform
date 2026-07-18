import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Button variants from UI-001 §10.1.
enum AppButtonVariant { filled, tonal, outlined, text, danger }

/// Institution-aware primary action button.
///
/// - Small visual height is 32 dp, medium 40 dp, large 48 dp as a minimum.
/// - Hit-test and semantics area is always at least 48×48 dp.
/// - The 48 dp hit area is a transparent outer layer; the inner Material
///   surface grows with the rendered text height so long labels wrap instead
///   of being clipped.
/// - Fixed width values smaller than 48 dp are clamped to 48 dp for the hit area.
/// - Uses [InkWell] for touch, hover, splash and pressed feedback, and a
///   [Focus] widget for focus, keyboard Enter/Space activation and disabled
///   focus handling.
class AppButton extends StatelessWidget {
  const AppButton({
    required this.label,
    this.onPressed,
    this.variant = AppButtonVariant.filled,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  });

  const AppButton.filled({
    required this.label,
    this.onPressed,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  }) : variant = AppButtonVariant.filled;

  const AppButton.tonal({
    required this.label,
    this.onPressed,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  }) : variant = AppButtonVariant.tonal;

  const AppButton.outlined({
    required this.label,
    this.onPressed,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  }) : variant = AppButtonVariant.outlined;

  const AppButton.text({
    required this.label,
    this.onPressed,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  }) : variant = AppButtonVariant.text;

  const AppButton.danger({
    required this.label,
    this.onPressed,
    this.size = AppButtonSize.medium,
    this.icon,
    this.width,
    this.focusNode,
    super.key,
  }) : variant = AppButtonVariant.danger;

  final String label;
  final VoidCallback? onPressed;
  final AppButtonVariant variant;
  final AppButtonSize size;
  final IconData? icon;
  final double? width;
  final FocusNode? focusNode;

  bool get _enabled => onPressed != null;

  double get _targetVisualHeight => switch (size) {
    AppButtonSize.small => AppSpacing.buttonHeightSm,
    AppButtonSize.medium => AppSpacing.buttonHeightMd,
    AppButtonSize.large => AppSpacing.buttonHeightLg,
  };

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final AppSemanticColors semantic = AppSemanticColors.of(context);
    final ColorScheme scheme = theme.colorScheme;

    final double minHitWidth = math.max(width ?? 0, AppSpacing.touchTarget);

    final (
      Color background,
      Color foreground,
      BorderSide? side,
    ) = switch (variant) {
      AppButtonVariant.filled => (
        _enabled ? scheme.primary : semantic.neutral200,
        _enabled ? scheme.onPrimary : semantic.neutral400,
        null,
      ),
      AppButtonVariant.tonal => (
        _enabled ? scheme.primaryContainer : semantic.neutral200,
        _enabled ? scheme.onPrimaryContainer : semantic.neutral400,
        null,
      ),
      AppButtonVariant.outlined => (
        Colors.transparent,
        _enabled ? scheme.primary : semantic.neutral400,
        BorderSide(
          color: _enabled ? scheme.primary : semantic.neutral300,
          width: 1.5,
        ),
      ),
      AppButtonVariant.text => (
        Colors.transparent,
        _enabled ? scheme.primary : semantic.neutral400,
        null,
      ),
      AppButtonVariant.danger => (
        _enabled ? scheme.error : semantic.neutral200,
        _enabled ? scheme.onError : semantic.neutral400,
        null,
      ),
    };

    final ShapeBorder pillShape = RoundedRectangleBorder(
      borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
      side: side ?? BorderSide.none,
    );

    return Semantics(
      button: true,
      enabled: _enabled,
      label: label,
      child: LayoutBuilder(
        builder: (BuildContext context, BoxConstraints constraints) {
          final double availableWidth = constraints.maxWidth.isFinite
              ? constraints.maxWidth
              : MediaQuery.sizeOf(context).width;
          final double effectiveMaxWidth = width != null
              ? minHitWidth
              : availableWidth;

          final double horizontalPadding = AppSpacing.space4 * 2;
          final double iconOccupiedWidth = icon != null
              ? AppSpacing.iconMd + AppSpacing.space2
              : 0.0;
          final double maxTextWidth = math.max(
            effectiveMaxWidth - horizontalPadding - iconOccupiedWidth,
            0.0,
          );

          final Widget content = Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.center,
            children: <Widget>[
              if (icon != null) ...<Widget>[
                Icon(icon, size: AppSpacing.iconMd, color: foreground),
                const SizedBox(width: AppSpacing.space2),
              ],
              ConstrainedBox(
                constraints: BoxConstraints(maxWidth: maxTextWidth),
                child: Text(
                  label,
                  softWrap: true,
                  style: theme.textTheme.labelLarge?.copyWith(
                    fontWeight: FontWeight.w500,
                    height: 1.25,
                    color: foreground,
                  ),
                ),
              ),
            ],
          );

          final Widget visualButton = Material(
            key: const ValueKey<String>('app_button_visual_material'),
            type: MaterialType.button,
            color: background,
            shape: pillShape,
            clipBehavior: Clip.antiAlias,
            child: InkWell(
              onTap: _enabled ? onPressed : null,
              excludeFromSemantics: true,
              borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minWidth: AppSpacing.touchTarget,
                  minHeight: _targetVisualHeight,
                  maxWidth: effectiveMaxWidth,
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: AppSpacing.space4,
                  ),
                  child: content,
                ),
              ),
            ),
          );

          return Align(
            alignment: Alignment.center,
            widthFactor: 1.0,
            heightFactor: 1.0,
            child: Focus(
              focusNode: focusNode,
              canRequestFocus: _enabled,
              onKeyEvent: (FocusNode node, KeyEvent event) {
                if (!_enabled) {
                  return KeyEventResult.ignored;
                }
                if (event is KeyDownEvent &&
                    (event.logicalKey == LogicalKeyboardKey.enter ||
                        event.logicalKey == LogicalKeyboardKey.space)) {
                  onPressed!();
                  return KeyEventResult.handled;
                }
                return KeyEventResult.ignored;
              },
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minWidth: minHitWidth,
                  minHeight: AppSpacing.touchTarget,
                  maxWidth: effectiveMaxWidth,
                ),
                child: Stack(
                  alignment: Alignment.center,
                  children: <Widget>[
                    Positioned.fill(
                      child: GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onTap: _enabled ? onPressed : null,
                        excludeFromSemantics: true,
                        child: const SizedBox.expand(),
                      ),
                    ),
                    Align(
                      alignment: Alignment.center,
                      widthFactor: 1.0,
                      heightFactor: 1.0,
                      child: visualButton,
                    ),
                  ],
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

enum AppButtonSize { small, medium, large }
