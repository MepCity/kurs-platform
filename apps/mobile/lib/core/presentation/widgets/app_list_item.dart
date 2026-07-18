import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Standard list item aligned with UI-001 §10.4.
///
/// Supports leading icon, title, subtitle, trailing widget and an optional
/// divider. The minimum height is 48 dp for single-line items and 64 dp for
/// two-line items.
class AppListItem extends StatelessWidget {
  const AppListItem({
    required this.title,
    super.key,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onTap,
    this.divider = true,
    this.dividerIndent = AppSpacing.space4,
  });

  final String title;
  final String? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool divider;
  final double dividerIndent;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);
    final double minHeight = subtitle != null
        ? AppSpacing.listItemMinHeightDouble
        : AppSpacing.listItemMinHeight;

    final Widget content = Padding(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.space4,
        vertical: AppSpacing.space2,
      ),
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: minHeight),
        child: Row(
          children: <Widget>[
            if (leading != null) ...<Widget>[
              leading!,
              const SizedBox(width: AppSpacing.space3),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Text(
                    title,
                    softWrap: true,
                    style: TextStyle(
                      fontSize: AppSpacing.textMd,
                      fontWeight: FontWeight.w500,
                      height: 1.5,
                      color: semantic.neutral800,
                    ),
                  ),
                  if (subtitle != null)
                    Text(
                      subtitle!,
                      softWrap: true,
                      style: TextStyle(
                        fontSize: AppSpacing.textSm,
                        fontWeight: FontWeight.w400,
                        height: 1.5,
                        color: semantic.neutral500,
                      ),
                    ),
                ],
              ),
            ),
            if (trailing != null) ...<Widget>[
              const SizedBox(width: AppSpacing.space3),
              trailing!,
            ],
          ],
        ),
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        onTap != null ? InkWell(onTap: onTap, child: content) : content,
        if (divider)
          Divider(
            indent: dividerIndent,
            height: AppSpacing.listDividerHeight,
            thickness: AppSpacing.listDividerHeight,
            color: semantic.neutral100,
          ),
      ],
    );
  }
}
