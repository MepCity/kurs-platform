import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';
import 'app_button.dart';

/// Dangerous-operation confirmation dialog.
///
/// Implements UI-002 §9 and the "O" (onay) state from EKRAN_ENVANTERI.
/// The dialog explains the action, asks for explicit confirmation and provides
/// safe cancellation.
class AppConfirmDialog extends StatelessWidget {
  const AppConfirmDialog({
    required this.title,
    required this.message,
    super.key,
    this.confirmLabel = 'Onayla',
    this.cancelLabel = 'İptal',
    this.isDangerous = false,
  });

  final String title;
  final String message;
  final String confirmLabel;
  final String cancelLabel;
  final bool isDangerous;

  /// Shows the dialog and returns true if the user confirmed.
  static Future<bool> show({
    required BuildContext context,
    required String title,
    required String message,
    String confirmLabel = 'Onayla',
    String cancelLabel = 'İptal',
    bool isDangerous = false,
  }) async {
    final bool? result = await showDialog<bool>(
      context: context,
      builder: (BuildContext context) => Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(
            maxWidth: AppSpacing.dialogMaxWidth,
          ),
          child: AppConfirmDialog(
            title: title,
            message: message,
            confirmLabel: confirmLabel,
            cancelLabel: cancelLabel,
            isDangerous: isDangerous,
          ),
        ),
      ),
    );
    return result ?? false;
  }

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);

    return AlertDialog(
      backgroundColor: semantic.neutral0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(AppSpacing.radiusLg),
      ),
      scrollable: true,
      title: Text(
        title,
        style: TextStyle(
          fontSize: AppSpacing.textXl,
          fontWeight: FontWeight.w600,
          height: 1.25,
          color: semantic.neutral800,
        ),
      ),
      content: Text(
        message,
        style: TextStyle(
          fontSize: AppSpacing.textLg,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: semantic.neutral600,
        ),
      ),
      actions: <Widget>[
        AppButton.text(
          label: cancelLabel,
          onPressed: () => Navigator.of(context).pop(false),
        ),
        if (isDangerous)
          AppButton.danger(
            label: confirmLabel,
            onPressed: () => Navigator.of(context).pop(true),
          )
        else
          AppButton.filled(
            label: confirmLabel,
            onPressed: () => Navigator.of(context).pop(true),
          ),
      ],
    );
  }
}
