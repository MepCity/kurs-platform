import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Material 3 card aligned with UI-001 §10.3.
///
/// Uses 8 dp radius, 1 dp elevation and 16 dp padding by default.
class AppCard extends StatelessWidget {
  const AppCard({
    required this.child,
    super.key,
    this.padding = const EdgeInsets.all(AppSpacing.space4),
    this.margin = const EdgeInsets.symmetric(vertical: AppSpacing.space2),
    this.onTap,
    this.elevation = 1,
  });

  final Widget child;
  final EdgeInsetsGeometry padding;
  final EdgeInsetsGeometry? margin;
  final VoidCallback? onTap;
  final double elevation;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);
    final Widget padded = Padding(padding: padding, child: child);
    return Card(
      margin: margin,
      elevation: elevation,
      color: semantic.neutral0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
      ),
      child: onTap != null
          ? InkWell(
              onTap: onTap,
              borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
              child: padded,
            )
          : padded,
    );
  }
}
