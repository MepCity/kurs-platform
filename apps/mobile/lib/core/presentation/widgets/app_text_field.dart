import 'package:flutter/material.dart';
import '../../theme/app_semantic_colors.dart';
import '../../theme/app_spacing.dart';

/// Institution-aware text field aligned with UI-001 §10.2.
///
/// Provides consistent label, helper, error and disabled states. The field
/// height is at least 48 dp for single-line and 80 dp for multiline input,
/// and expands when text scaling requires it.
class AppTextField extends StatelessWidget {
  const AppTextField({
    super.key,
    this.controller,
    this.label,
    this.hint,
    this.helper,
    this.error,
    this.keyboardType,
    this.textInputAction,
    this.obscureText = false,
    this.enabled = true,
    this.readOnly = false,
    this.minLines,
    this.maxLines = 1,
    this.maxLength,
    this.onChanged,
    this.onSubmitted,
    this.prefixIcon,
    this.suffixIcon,
    this.autofocus = false,
  });

  final TextEditingController? controller;
  final String? label;
  final String? hint;
  final String? helper;
  final String? error;
  final TextInputType? keyboardType;
  final TextInputAction? textInputAction;
  final bool obscureText;
  final bool enabled;
  final bool readOnly;
  final int? minLines;
  final int? maxLines;
  final int? maxLength;
  final ValueChanged<String>? onChanged;
  final ValueChanged<String>? onSubmitted;
  final Widget? prefixIcon;
  final Widget? suffixIcon;
  final bool autofocus;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);
    final bool hasError = error != null && error!.isNotEmpty;
    final bool isMultiline =
        (maxLines != null && maxLines! > 1) ||
        (minLines != null && minLines! > 1);

    return ConstrainedBox(
      constraints: BoxConstraints(
        minHeight: isMultiline
            ? AppSpacing.inputHeightMultiline
            : AppSpacing.inputHeight,
      ),
      child: TextField(
        controller: controller,
        keyboardType: keyboardType,
        textInputAction: textInputAction,
        obscureText: obscureText,
        enabled: enabled,
        readOnly: readOnly,
        minLines: minLines,
        maxLines: maxLines,
        maxLength: maxLength,
        onChanged: onChanged,
        onSubmitted: onSubmitted,
        autofocus: autofocus,
        style: TextStyle(
          fontSize: AppSpacing.textMd,
          fontWeight: FontWeight.w400,
          height: 1.5,
          color: enabled ? semantic.neutral800 : semantic.neutral400,
        ),
        decoration: InputDecoration(
          labelText: label,
          hintText: hint,
          helperText: hasError ? null : helper,
          errorText: hasError ? error : null,
          prefixIcon: prefixIcon,
          suffixIcon: suffixIcon,
        ),
      ),
    );
  }
}
