import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Semantic status chips aligned with UI-001 §3.3 and §3.4.
///
/// Combines color with an icon and label so the state is never communicated
/// by color alone (WCAG 1.4.1).
///
/// Error colors are taken from the [ColorScheme] because they are part of the
/// Material palette. Success, warning and info colors come from
/// [AppSemanticColors] because [ColorScheme] does not define them.
///
/// Long labels are allowed to wrap to as many lines as needed so that content
/// is never silently truncated.
enum AppStatusType { success, warning, error, info, neutral }

class AppStatusChip extends StatelessWidget {
  const AppStatusChip({
    required this.label,
    this.type = AppStatusType.neutral,
    this.icon,
    super.key,
  });

  final String label;
  final AppStatusType type;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    final (Color background, Color foreground) = switch (type) {
      AppStatusType.success => (
        semantic.successContainer,
        semantic.onSuccessContainer,
      ),
      AppStatusType.warning => (
        semantic.warningContainer,
        semantic.onWarningContainer,
      ),
      AppStatusType.error => (scheme.errorContainer, scheme.onErrorContainer),
      AppStatusType.info => (semantic.infoContainer, semantic.onInfoContainer),
      AppStatusType.neutral => (semantic.neutral100, semantic.neutral700),
    };

    return Semantics(
      label: label,
      child: Container(
        padding: const EdgeInsets.symmetric(
          horizontal: AppSpacing.space3,
          vertical: AppSpacing.space2,
        ),
        decoration: ShapeDecoration(
          color: background,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(AppSpacing.radiusPill),
          ),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            if (icon != null) ...<Widget>[
              Icon(icon, size: AppSpacing.iconSm, color: foreground),
              const SizedBox(width: AppSpacing.space1),
            ],
            Flexible(
              child: Text(
                label,
                softWrap: true,
                style: TextStyle(
                  fontSize: AppSpacing.textXs,
                  fontWeight: FontWeight.w500,
                  height: 1.25,
                  color: foreground,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
