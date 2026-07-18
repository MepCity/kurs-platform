import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';
import 'app_button.dart';

/// Empty state aligned with UI-001 §10.10.
///
/// Shows an icon, title, optional description and an optional action button.
/// This component handles the "B" (empty) state for every data-driven screen.
class AppEmptyState extends StatelessWidget {
  const AppEmptyState({
    required this.title,
    super.key,
    this.description,
    this.icon,
    this.actionLabel,
    this.onAction,
  });

  final String title;
  final String? description;
  final IconData? icon;
  final String? actionLabel;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return Padding(
      padding: const EdgeInsets.all(AppSpacing.space8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          if (icon != null)
            Icon(icon, size: AppSpacing.icon2Xl, color: semantic.neutral400),
          if (icon != null) const SizedBox(height: AppSpacing.space10),
          Text(
            title,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: AppSpacing.text4Xl,
              fontWeight: FontWeight.w700,
              height: 1.25,
              color: semantic.neutral800,
            ),
          ),
          if (description != null) ...<Widget>[
            const SizedBox(height: AppSpacing.space4),
            Text(
              description!,
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: AppSpacing.textLg,
                fontWeight: FontWeight.w400,
                height: 1.75,
                color: semantic.neutral500,
              ),
            ),
          ],
          if (actionLabel != null) ...<Widget>[
            const SizedBox(height: AppSpacing.space10),
            AppButton.filled(label: actionLabel!, onPressed: onAction),
          ],
        ],
      ),
    );
  }
}

/// Loading state aligned with UI-001 §1 (loading state) and EKRAN_ENVANTERI.
///
/// Shows a centered progress indicator with an optional label.
class AppLoadingState extends StatelessWidget {
  const AppLoadingState({super.key, this.label});

  final String? label;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: <Widget>[
          CircularProgressIndicator(
            color: Theme.of(context).colorScheme.primary,
          ),
          if (label != null) ...<Widget>[
            const SizedBox(height: AppSpacing.space4),
            Text(
              label!,
              style: TextStyle(
                fontSize: AppSpacing.textSm,
                fontWeight: FontWeight.w400,
                height: 1.5,
                color: semantic.neutral500,
              ),
            ),
          ],
        ],
      ),
    );
  }
}

/// Error state for data-driven screens.
///
/// Shows a message, an optional retry button and a safe icon. This component
/// handles the "H" (error) state for every data-driven screen.
class AppErrorState extends StatelessWidget {
  const AppErrorState({
    required this.message,
    super.key,
    this.onRetry,
    this.retryLabel = 'Tekrar Dene',
  });

  final String message;
  final VoidCallback? onRetry;
  final String retryLabel;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return Padding(
      padding: const EdgeInsets.all(AppSpacing.space8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.error_outline,
            size: AppSpacing.icon2Xl,
            color: scheme.error,
          ),
          const SizedBox(height: AppSpacing.space6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: AppSpacing.textLg,
              fontWeight: FontWeight.w400,
              height: 1.5,
              color: semantic.neutral800,
            ),
          ),
          if (onRetry != null) ...<Widget>[
            const SizedBox(height: AppSpacing.space6),
            AppButton.outlined(label: retryLabel, onPressed: onRetry),
          ],
        ],
      ),
    );
  }
}

/// Unauthorized state for protected screens.
///
/// Handles the "Z" (yetkisiz) state for every data-driven screen.
class AppUnauthorizedState extends StatelessWidget {
  const AppUnauthorizedState({
    super.key,
    this.message = 'Bu işlem için yetkiniz yok.',
  });

  final String message;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return Padding(
      padding: const EdgeInsets.all(AppSpacing.space8),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.center,
        children: <Widget>[
          Icon(
            Icons.lock_outline,
            size: AppSpacing.icon2Xl,
            color: semantic.neutral400,
          ),
          const SizedBox(height: AppSpacing.space6),
          Text(
            message,
            textAlign: TextAlign.center,
            style: TextStyle(
              fontSize: AppSpacing.textLg,
              fontWeight: FontWeight.w400,
              height: 1.5,
              color: semantic.neutral800,
            ),
          ),
        ],
      ),
    );
  }
}
