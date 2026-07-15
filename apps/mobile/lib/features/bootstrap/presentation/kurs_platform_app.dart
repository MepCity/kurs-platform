import 'package:flutter/material.dart';

/// Provider-independent application root.
///
/// Product navigation and authenticated screens are intentionally introduced by
/// their owning downstream tasks.
class KursPlatformApp extends StatelessWidget {
  const KursPlatformApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(home: SizedBox.shrink());
  }
}
