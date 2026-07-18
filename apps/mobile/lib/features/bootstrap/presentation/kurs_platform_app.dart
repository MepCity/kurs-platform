import 'package:flutter/material.dart';
import '../../../../core/theme/app_theme_provider.dart';

/// Provider-aware application root.
///
/// Accepts an optional [AppThemeProvider] through its constructor. When a
/// provider is supplied, ownership and disposal remain with the caller. When
/// no provider is supplied, [KursPlatformApp] creates and owns its own default
/// provider.
///
/// The provider is wrapped in an [AppThemeScope] so downstream widgets can reach
/// it with [AppThemeScope.of]. The [MaterialApp] rebuilds when the provider
/// notifies.
///
/// System insets are deliberately left to the owning screen/shell so that
/// [Scaffold], [AppBar] and bottom action areas can apply the correct padding
/// without double insets. A reusable safe-area helper is provided by
/// [AppBottomActionArea].
class KursPlatformApp extends StatefulWidget {
  const KursPlatformApp({super.key, this.provider, this.home});

  /// External provider. When null, the app creates and owns its own provider.
  final AppThemeProvider? provider;

  /// Initial screen. Defaults to a placeholder; the real navigation shell is
  /// introduced by UI-004.
  final Widget? home;

  @override
  State<KursPlatformApp> createState() => _KursPlatformAppState();
}

class _KursPlatformAppState extends State<KursPlatformApp> {
  late AppThemeProvider _provider;
  bool _ownsProvider = false;

  @override
  void initState() {
    super.initState();
    _provider = widget.provider ?? _createDefaultProvider();
    _ownsProvider = widget.provider == null;
  }

  @override
  void didUpdateWidget(KursPlatformApp oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.provider != null) {
      if (widget.provider != _provider && _ownsProvider) {
        _provider.dispose();
      }
      _provider = widget.provider!;
      _ownsProvider = false;
    } else if (!_ownsProvider) {
      _provider = _createDefaultProvider();
      _ownsProvider = true;
    }
  }

  @override
  void dispose() {
    if (_ownsProvider) {
      _provider.dispose();
    }
    super.dispose();
  }

  AppThemeProvider _createDefaultProvider() => AppThemeProvider();

  @override
  Widget build(BuildContext context) {
    return AppThemeScope(
      notifier: _provider,
      child: ListenableBuilder(
        listenable: _provider,
        builder: (BuildContext context, Widget? child) {
          return MaterialApp(
            title: 'Kurs Platform',
            theme: _provider.themeData,
            home: widget.home,
          );
        },
      ),
    );
  }
}
