import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Semantic snackbar aligned with UI-001 §3.3 and §10.7.
///
/// Always shows an icon alongside text so the state is never conveyed by color
/// alone.
class AppSnackBar {
  const AppSnackBar._();

  static SnackBar success(
    BuildContext context, {
    required String message,
    Duration duration = const Duration(
      milliseconds: AppSpacing.durationSnackbar,
    ),
  }) {
    return _build(
      context,
      message: message,
      icon: Icons.check_circle_outline,
      backgroundColor: AppSemanticColors.of(context).success,
      foregroundColor: AppSemanticColors.of(context).onSuccess,
      duration: duration,
    );
  }

  static SnackBar error(
    BuildContext context, {
    required String message,
    Duration duration = const Duration(
      milliseconds: AppSpacing.durationSnackbar,
    ),
  }) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return _build(
      context,
      message: message,
      icon: Icons.error_outline,
      backgroundColor: scheme.error,
      foregroundColor: scheme.onError,
      duration: duration,
    );
  }

  static SnackBar info(
    BuildContext context, {
    required String message,
    Duration duration = const Duration(
      milliseconds: AppSpacing.durationSnackbar,
    ),
  }) {
    return _build(
      context,
      message: message,
      icon: Icons.info_outline,
      backgroundColor: AppSemanticColors.of(context).info,
      foregroundColor: AppSemanticColors.of(context).onInfo,
      duration: duration,
    );
  }

  static SnackBar _build(
    BuildContext context, {
    required String message,
    required IconData icon,
    required Color backgroundColor,
    required Color foregroundColor,
    required Duration duration,
  }) {
    return SnackBar(
      content: Row(
        children: <Widget>[
          Icon(icon, color: foregroundColor, size: AppSpacing.iconLg),
          const SizedBox(width: AppSpacing.space3),
          Expanded(
            child: Text(
              message,
              style: TextStyle(
                fontSize: AppSpacing.textMd,
                fontWeight: FontWeight.w400,
                height: 1.5,
                color: foregroundColor,
              ),
            ),
          ),
        ],
      ),
      backgroundColor: backgroundColor,
      duration: duration,
      behavior: SnackBarBehavior.floating,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      ),
      margin: const EdgeInsets.all(AppSpacing.space4),
    );
  }
}
