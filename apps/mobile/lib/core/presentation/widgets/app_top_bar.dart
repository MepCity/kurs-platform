import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';
import 'app_status_chip.dart';
import 'app_sync_indicator.dart';

/// Application top bar aligned with UI-002 §3.3 and UI-001 §10.5.
///
/// Supports institution branding, context selector, sync indicator and profile
/// menu trigger. The support-mode indicator is a separate, always-visible chip
/// so it never disappears inside an ellipsized title.
class AppTopBar extends StatelessWidget implements PreferredSizeWidget {
  const AppTopBar({
    super.key,
    this.title,
    this.titleWidget,
    this.leading,
    this.showBackButton = false,
    this.onBack,
    this.actions,
    this.supportMode = false,
  });

  final String? title;
  final Widget? titleWidget;
  final Widget? leading;
  final bool showBackButton;
  final VoidCallback? onBack;
  final List<Widget>? actions;
  final bool supportMode;

  @override
  Size get preferredSize => const Size.fromHeight(AppSpacing.appBarHeight);

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return AppBar(
      leading: _buildLeading(context),
      title: titleWidget ?? _buildTitle(context),
      actions: actions,
      toolbarHeight: AppSpacing.appBarHeight,
      backgroundColor: semantic.neutral0,
      foregroundColor: semantic.neutral800,
      elevation: 0,
      scrolledUnderElevation: AppSpacing.space1 / 4,
    );
  }

  Widget? _buildTitle(BuildContext context) {
    if (title == null) {
      return null;
    }
    final Text titleText = Text(
      title!,
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
    );
    if (!supportMode) {
      return titleText;
    }
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        Flexible(child: titleText),
        const SizedBox(width: AppSpacing.space2),
        const AppStatusChip(label: 'DESTEK', type: AppStatusType.info),
      ],
    );
  }

  Widget? _buildLeading(BuildContext context) {
    if (leading != null) {
      return leading;
    }
    if (showBackButton) {
      final AppSemanticColors semantic = AppSemanticColors.of(context);
      return IconButton(
        icon: Icon(Icons.arrow_back, color: semantic.neutral800),
        onPressed: onBack ?? () => Navigator.of(context).maybePop(),
        tooltip: 'Geri',
      );
    }
    return null;
  }
}

/// Factory helper that builds the standard top bar used by the shell.
class AppTopBarFactory {
  const AppTopBarFactory._();

  static AppTopBar platform({
    required String title,
    AppSyncStatus? syncStatus,
    VoidCallback? onProfile,
    VoidCallback? onSyncTap,
    bool supportMode = false,
    bool showBackButton = false,
    VoidCallback? onBack,
  }) {
    return AppTopBar(
      title: title,
      supportMode: supportMode,
      showBackButton: showBackButton,
      onBack: onBack,
      actions: <Widget>[
        if (syncStatus != null) ...<Widget>[
          AppSyncIndicator(status: syncStatus, onTap: onSyncTap),
          const SizedBox(width: AppSpacing.space2),
        ],
        IconButton(
          icon: const Icon(Icons.person_outline),
          onPressed: onProfile,
          tooltip: 'Profil',
        ),
        const SizedBox(width: AppSpacing.space2),
      ],
    );
  }

  static AppTopBar institution({
    required String institutionName,
    AppSyncStatus? syncStatus,
    VoidCallback? onProfile,
    VoidCallback? onSyncTap,
    bool supportMode = false,
  }) {
    return AppTopBar(
      title: institutionName,
      supportMode: supportMode,
      actions: <Widget>[
        if (syncStatus != null) ...<Widget>[
          AppSyncIndicator(status: syncStatus, onTap: onSyncTap),
          const SizedBox(width: AppSpacing.space2),
        ],
        IconButton(
          icon: const Icon(Icons.person_outline),
          onPressed: onProfile,
          tooltip: 'Profil',
        ),
        const SizedBox(width: AppSpacing.space2),
      ],
    );
  }
}
