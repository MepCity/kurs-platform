import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Sync status indicator aligned with UI-001 §3.5 and §10.9.
///
/// Communicates pending, syncing, failed and (transient) success states.
/// The indicator never relies on color alone: a distinct icon shape is shown
/// for each state.
class AppSyncIndicator extends StatelessWidget {
  const AppSyncIndicator({required this.status, this.onTap, super.key});

  final AppSyncStatus status;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    final (
      IconData icon,
      Color color,
      String semanticsLabel,
    ) = switch (status) {
      AppSyncStatus.pending => (
        Icons.access_time,
        semantic.neutral500,
        'Bekleyen işlem var',
      ),
      AppSyncStatus.syncing => (Icons.sync, semantic.info, 'Eşitleniyor'),
      AppSyncStatus.success => (
        Icons.check_circle,
        semantic.success,
        'Eşitleme başarılı',
      ),
      AppSyncStatus.failed => (Icons.error, scheme.error, 'Eşitleme başarısız'),
    };

    final bool isInteractive = status == AppSyncStatus.failed && onTap != null;
    final Widget indicator = Semantics(
      label: semanticsLabel,
      button: isInteractive,
      child: SizedBox(
        width: AppSpacing.touchTarget,
        height: AppSpacing.touchTarget,
        child: Center(
          child: SizedBox(
            width: AppSpacing.iconMd,
            height: AppSpacing.iconMd,
            child: status == AppSyncStatus.syncing
                ? _SpinningIcon(icon: icon, color: color)
                : Icon(icon, size: AppSpacing.iconMd, color: color),
          ),
        ),
      ),
    );

    return GestureDetector(
      onTap: isInteractive ? onTap : null,
      behavior: HitTestBehavior.opaque,
      child: indicator,
    );
  }
}

enum AppSyncStatus { pending, syncing, success, failed }

class _SpinningIcon extends StatefulWidget {
  const _SpinningIcon({required this.icon, required this.color});

  final IconData icon;
  final Color color;

  @override
  State<_SpinningIcon> createState() => _SpinningIconState();
}

class _SpinningIconState extends State<_SpinningIcon>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(seconds: 1),
      vsync: this,
    )..repeat();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return RotationTransition(
      turns: _controller,
      child: Icon(widget.icon, size: AppSpacing.iconMd, color: widget.color),
    );
  }
}
