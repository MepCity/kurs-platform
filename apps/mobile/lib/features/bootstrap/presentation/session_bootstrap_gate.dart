import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../auth/domain/authentication_repository.dart';
import '../../auth/domain/secure_session_store.dart';
import '../../auth/domain/session_repository.dart';
import '../../auth/presentation/sign_in_screen.dart';
import '../application/session_bootstrap_controller.dart';

class SessionBootstrapGate extends StatefulWidget {
  const SessionBootstrapGate({
    required this.authenticationRepository,
    required this.sessionRepository,
    required this.sessionStore,
    super.key,
  });
  final AuthenticationRepository authenticationRepository;
  final SessionRepository sessionRepository;
  final SecureSessionStore sessionStore;

  @override
  State<SessionBootstrapGate> createState() => _SessionBootstrapGateState();
}

class _SessionBootstrapGateState extends State<SessionBootstrapGate> {
  late SessionBootstrapController _controller;

  @override
  void initState() {
    super.initState();
    _controller = SessionBootstrapController(
      repository: widget.sessionRepository,
      sessionStore: widget.sessionStore,
    )..addListener(_changed);
    _controller.start();
  }

  void _changed() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => switch (_controller.status) {
    BootstrapStatus.loading => const Scaffold(
      body: SafeArea(
        child: _ScrollableBootstrapState(
          child: AppLoadingState(label: 'Güvenli oturum doğrulanıyor…'),
        ),
      ),
    ),
    BootstrapStatus.retryableError => Scaffold(
      body: SafeArea(
        child: _ScrollableBootstrapState(
          child: AppErrorState(
            message: _controller.message!,
            retryLabel: 'Tekrar Dene',
            onRetry: _controller.start,
          ),
        ),
      ),
    ),
    BootstrapStatus.unauthenticated => SignInScreen(
      repository: widget.authenticationRepository,
      secureSessionStore: widget.sessionStore,
      onSessionActivated: (_) => _controller.start(),
    ),
    BootstrapStatus.authenticated => _AuthenticatedIntegrationPoint(
      session: _controller.session!,
      onLogout: _controller.logout,
    ),
  };
}

class _ScrollableBootstrapState extends StatelessWidget {
  const _ScrollableBootstrapState({required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) => SingleChildScrollView(
      child: ConstrainedBox(
        constraints: BoxConstraints(minHeight: constraints.maxHeight),
        child: child,
      ),
    ),
  );
}

class _AuthenticatedIntegrationPoint extends StatelessWidget {
  const _AuthenticatedIntegrationPoint({
    required this.session,
    required this.onLogout,
  });
  final ActivatedSession session;
  final VoidCallback onLogout;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Kurs Platform'),
      actions: <Widget>[
        IconButton(
          constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
          tooltip: 'Güvenli çıkış yap',
          onPressed: onLogout,
          icon: const Icon(Icons.logout),
        ),
      ],
    ),
    body: SafeArea(
      child: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            '${session.displayName} için güvenli oturum doğrulandı. '
            'Ürün rotaları ORG-009D kapsamında bağlanacaktır.',
            textAlign: TextAlign.center,
          ),
        ),
      ),
    ),
  );
}
