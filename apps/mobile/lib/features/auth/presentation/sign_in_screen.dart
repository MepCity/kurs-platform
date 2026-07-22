import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../application/sign_in_controller.dart';
import '../domain/authentication_repository.dart';

/// AUTH-01 and CTX-01. Password entry is deliberately delegated to Cognito's
/// system-browser page; this app never renders or receives a password.
class SignInScreen extends StatefulWidget {
  const SignInScreen({
    required this.repository,
    super.key,
    this.onSessionActivated,
  });

  final AuthenticationRepository repository;
  final ValueChanged<ActivatedSession>? onSessionActivated;

  @override
  State<SignInScreen> createState() => _SignInScreenState();
}

class _SignInScreenState extends State<SignInScreen> {
  late SignInController _controller;

  @override
  void initState() {
    super.initState();
    _controller = SignInController(repository: widget.repository)
      ..addListener(_changed);
  }

  @override
  void didUpdateWidget(covariant SignInScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.repository != widget.repository) {
      _controller.removeListener(_changed);
      _controller.dispose();
      _controller = SignInController(repository: widget.repository)
        ..addListener(_changed);
    }
  }

  void _changed() => mounted ? setState(() {}) : null;

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _controller.dispose();
    super.dispose();
  }

  Future<void> _activateOrganization(String id) async {
    final session = await _controller.activateOrganization(id);
    if (session != null && mounted) widget.onSessionActivated?.call(session);
  }

  Future<void> _activatePlatformAdministrator() async {
    final session = await _controller.activatePlatformAdministrator();
    if (session != null && mounted) widget.onSessionActivated?.call(session);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      child: switch (_controller.status) {
        SignInStatus.choosingContext ||
        SignInStatus.activating => _ContextChoices(
          choices: _controller.choices!,
          busy: _controller.isBusy,
          onOrganization: _activateOrganization,
          onPlatformAdministrator: _activatePlatformAdministrator,
        ),
        SignInStatus.error => _ErrorView(
          message: _controller.message!,
          onRetry: _controller.retry,
        ),
        SignInStatus.ready || SignInStatus.authenticating => _SignInStart(
          busy: _controller.isBusy,
          onBegin: _controller.begin,
        ),
      },
    ),
  );
}

class _SignInStart extends StatelessWidget {
  const _SignInStart({required this.busy, required this.onBegin});
  final bool busy;
  final VoidCallback onBegin;

  @override
  Widget build(BuildContext context) => Center(
    child: SingleChildScrollView(
      padding: const EdgeInsets.all(AppSpacing.space6),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 440),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: <Widget>[
            Icon(
              Icons.menu_book_outlined,
              size: 56,
              color: Theme.of(context).colorScheme.primary,
            ),
            const SizedBox(height: AppSpacing.space6),
            Text(
              'Kurs Platform',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineMedium,
            ),
            const SizedBox(height: AppSpacing.space3),
            Text(
              'Güvenli giriş için tarayıcıda devam edeceksiniz.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyLarge,
            ),
            const SizedBox(height: AppSpacing.space8),
            AppButton.filled(
              label: busy ? 'Giriş başlatılıyor…' : 'Giriş Yap',
              icon: Icons.login,
              size: AppButtonSize.large,
              onPressed: busy ? null : onBegin,
            ),
            const SizedBox(height: AppSpacing.space4),
            Text(
              'Parolanız uygulama tarafından görülmez veya saklanmaz.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    ),
  );
}

class _ContextChoices extends StatelessWidget {
  const _ContextChoices({
    required this.choices,
    required this.busy,
    required this.onOrganization,
    required this.onPlatformAdministrator,
  });
  final AuthContextChoices choices;
  final bool busy;
  final ValueChanged<String> onOrganization;
  final VoidCallback onPlatformAdministrator;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(AppSpacing.space4),
    children: <Widget>[
      Text('Bağlam seçin', style: Theme.of(context).textTheme.headlineSmall),
      const SizedBox(height: AppSpacing.space2),
      Text('${choices.displayName}, çalışmak istediğiniz bağlamı seçin.'),
      const SizedBox(height: AppSpacing.space6),
      if (choices.canActivatePlatformAdministrator)
        _ChoiceTile(
          icon: Icons.admin_panel_settings_outlined,
          title: 'Platform yöneticisi',
          subtitle: 'Kurumdan bağımsız platform bağlamı',
          enabled: !busy,
          onTap: onPlatformAdministrator,
        ),
      ...choices.memberships.map(
        (membership) => _ChoiceTile(
          icon: Icons.business_outlined,
          title: membership.organizationName,
          subtitle: membership.roleCodes.join(', '),
          enabled: !busy,
          onTap: () => onOrganization(membership.id),
        ),
      ),
      if (busy)
        const Padding(
          padding: EdgeInsets.only(top: AppSpacing.space4),
          child: Center(child: CircularProgressIndicator()),
        ),
    ],
  );
}

class _ChoiceTile extends StatelessWidget {
  const _ChoiceTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.enabled,
    required this.onTap,
  });
  final IconData icon;
  final String title;
  final String subtitle;
  final bool enabled;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: Icon(icon),
      title: Text(title),
      subtitle: Text(subtitle),
      trailing: const Icon(Icons.chevron_right),
      enabled: enabled,
      onTap: onTap,
    ),
  );
}

class _ErrorView extends StatelessWidget {
  const _ErrorView({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) =>
      AppErrorState(message: message, onRetry: onRetry, retryLabel: 'Geri Dön');
}
