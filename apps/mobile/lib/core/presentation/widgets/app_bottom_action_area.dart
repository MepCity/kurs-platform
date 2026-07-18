import 'dart:math' as math;

import 'package:flutter/material.dart';
import '../../theme/app_spacing.dart';

/// A reusable bottom action area that respects system safe areas and keyboard
/// insets without adding a blanket [SafeArea] around the whole app.
///
/// Use this for FABs, bottom-aligned primary actions and sticky form buttons.
/// It does not contain navigation controls; that responsibility belongs to
/// UI-004.
///
/// Bottom padding is the larger of:
/// - the persistent system bottom inset ([FlutterView.viewPadding.bottom]),
/// - the minimum safe-area bottom token ([AppSpacing.safeAreaBottomMin]),
/// - the keyboard inset ([FlutterView.viewInsets.bottom]) plus a minimum
///   clearance ([AppSpacing.keyboardAvoidMinPadding]).
///
/// Raw view metrics are used instead of the ambient [MediaQuery] because
/// [Scaffold] removes the bottom view inset from its body.
class AppBottomActionArea extends StatelessWidget {
  const AppBottomActionArea({required this.child, super.key});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    final MediaQueryData mediaQuery = MediaQueryData.fromView(View.of(context));

    final double bottom = math.max(
      math.max(mediaQuery.viewPadding.bottom, AppSpacing.safeAreaBottomMin),
      mediaQuery.viewInsets.bottom + AppSpacing.keyboardAvoidMinPadding,
    );

    return Padding(
      padding: EdgeInsets.fromLTRB(
        AppSpacing.space4,
        AppSpacing.space4,
        AppSpacing.space4,
        bottom,
      ),
      child: child,
    );
  }
}
