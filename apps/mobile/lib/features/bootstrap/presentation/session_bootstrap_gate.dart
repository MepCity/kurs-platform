import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../auth/domain/authentication_repository.dart';
import '../../auth/domain/secure_session_store.dart';
import '../../auth/domain/session_repository.dart';
import '../../auth/presentation/sign_in_screen.dart';
import '../../organizations/domain/organization.dart';
import '../../organizations/domain/organization_status.dart';
import '../../organizations/presentation/organization_brand_settings_screen.dart';
import '../../organizations/presentation/platform_organization_create_screen.dart';
import '../../organizations/presentation/platform_organization_list_screen.dart';
import '../application/session_bootstrap_controller.dart';
import '../domain/organization_repository_bundle.dart';
import 'mobile_navigation_shell.dart';

class SessionBootstrapGate extends StatefulWidget {
  const SessionBootstrapGate({
    required this.authenticationRepository,
    required this.sessionRepository,
    required this.sessionStore,
    required this.organizationRepositoryBuilder,
    super.key,
  });
  final AuthenticationRepository authenticationRepository;
  final SessionRepository sessionRepository;
  final SecureSessionStore sessionStore;
  final OrganizationRepositoryBuilder organizationRepositoryBuilder;

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
      key: ValueKey(_controller.identityKey),
      session: _controller.session!,
      onLogout: _controller.logout,
      apiSession: _controller,
      repositoryBuilder: widget.organizationRepositoryBuilder,
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

class _AuthenticatedIntegrationPoint extends StatefulWidget {
  const _AuthenticatedIntegrationPoint({
    super.key,
    required this.session,
    required this.onLogout,
    required this.apiSession,
    required this.repositoryBuilder,
  });
  final ActivatedSession session;
  final VoidCallback onLogout;
  final SessionBootstrapController apiSession;
  final OrganizationRepositoryBuilder repositoryBuilder;

  @override
  State<_AuthenticatedIntegrationPoint> createState() =>
      _AuthenticatedIntegrationPointState();
}

class _AuthenticatedIntegrationPointState
    extends State<_AuthenticatedIntegrationPoint> {
  late final OrganizationRepositoryBundle _repositories;
  Organization? _supportTarget;
  MobileShellRole? _selectedOrganizationRole;
  int _requestSequence = 0;
  List<MobileShellRouteRequest> _requests = const [];

  @override
  void initState() {
    super.initState();
    _repositories = widget.repositoryBuilder(
      widget.apiSession,
      widget.session.scope == ActivatedSessionScope.globalPlatformAdministrator,
    );
    final roles = _organizationRoles;
    if (roles.length == 1) _selectedOrganizationRole = roles.single;
  }

  @override
  void dispose() {
    final repository = _repositories.organizations;
    if (repository is ChangeNotifier) {
      (repository as ChangeNotifier).dispose();
    }
    final brandRepository = _repositories.brand;
    if (!identical(repository, brandRepository) &&
        brandRepository is ChangeNotifier) {
      (brandRepository as ChangeNotifier).dispose();
    }
    super.dispose();
  }

  void _open(MobileShellRouteId route) {
    setState(() {
      _requestSequence++;
      _requests = <MobileShellRouteRequest>[
        MobileShellRouteRequest(
          sequence: _requestSequence,
          requestId: 'workspace-$_requestSequence',
          route: route,
        ),
      ];
    });
  }

  void _enterSupport(Organization organization) {
    setState(() {
      _supportTarget = organization;
      _requestSequence++;
      _requests = <MobileShellRouteRequest>[
        MobileShellRouteRequest(
          sequence: _requestSequence,
          requestId: 'support-$_requestSequence',
          route: MobileShellRouteId.brandSettings,
        ),
      ];
    });
  }

  void _handleAction(MobileShellActionRequest request) {
    switch (request.action) {
      case MobileShellAction.logout:
        widget.onLogout();
      case MobileShellAction.exitSupportMode:
        setState(() {
          _supportTarget = null;
          _requests = const [];
        });
      case MobileShellAction.changeContext ||
          MobileShellAction.selectClass ||
          MobileShellAction.openProfile:
        break;
      case MobileShellAction.changeRole:
        if (_organizationRoles.length > 1) {
          setState(() => _selectedOrganizationRole = null);
        }
    }
  }

  MobileShellContext get _context {
    final session = widget.session;
    if (session.scope == ActivatedSessionScope.globalPlatformAdministrator) {
      final target = _supportTarget;
      return MobileShellContext(
        sessionVerified: true,
        sessionContextId: widget.apiSession.identityKey,
        role: MobileShellRole.platformAdministrator,
        organizationId: target?.id,
        organizationName: target?.name,
        supportTargetOrganizationId: target?.id,
        supportMode: target != null,
        displayName: session.displayName,
      );
    }
    final membership = session.organizationMembership!;
    final selectedRole = _selectedOrganizationRole;
    if (selectedRole == null) {
      throw StateError('Doğrulanmış rol seçilmeden shell açılamaz.');
    }
    return MobileShellContext(
      sessionVerified: true,
      sessionContextId: widget.apiSession.identityKey,
      role: selectedRole,
      organizationId: membership.organizationId,
      organizationName: membership.organizationName,
      displayName: session.displayName,
      availableRoleCount:
          membership.roleCodes.contains('ORG_ADMIN') &&
              membership.roleCodes.contains('TEACHER')
          ? 2
          : 1,
      // IAM-001 does not expose fine-grained teacher permissions. Empty is
      // intentionally fail-closed; the route catalog remains ready for the
      // future verified permission source.
      permissions: const <MobileShellPermission>{},
    );
  }

  List<MobileShellRole> get _organizationRoles {
    final membership = widget.session.organizationMembership;
    if (membership == null) return const <MobileShellRole>[];
    return <MobileShellRole>[
      if (membership.roleCodes.contains('ORG_ADMIN'))
        MobileShellRole.organizationAdministrator,
      if (membership.roleCodes.contains('TEACHER')) MobileShellRole.teacher,
    ];
  }

  Widget _screen(BuildContext context, MobileShellRouteId route) {
    final shellContext = _context;
    return switch (route) {
      MobileShellRouteId.platformOrganizations =>
        PlatformOrganizationListScreen(
          repository: _repositories.organizations,
          onOrganizationTap: _enterSupport,
          onCreateRequested: () =>
              _open(MobileShellRouteId.platformOrganizationCreate),
        ),
      MobileShellRouteId.platformOrganizationCreate =>
        PlatformOrganizationCreateScreen(
          repository: _repositories.organizations,
          onCreated: (_) => Navigator.of(context).pop(),
        ),
      MobileShellRouteId.brandSettings => OrganizationBrandSettingsScreen(
        organizationId: shellContext.organizationId!,
        repository: _repositories.brand,
        canManageBrand: true,
        canManageModules: false,
        capabilities: OrganizationBrandSettingsCapabilities(
          canViewBrand: true,
          canUpdateBrand: _supportTarget?.status != OrganizationStatus.archived,
          canViewModules: false,
          canUpdateModules: false,
        ),
      ),
      MobileShellRouteId.enabledModules => OrganizationBrandSettingsScreen(
        organizationId: shellContext.organizationId!,
        repository: _repositories.brand,
        canManageBrand: false,
        canManageModules: true,
        capabilities: OrganizationBrandSettingsCapabilities(
          canViewBrand: false,
          canUpdateBrand: false,
          canViewModules: true,
          canUpdateModules:
              _supportTarget?.status != OrganizationStatus.archived,
        ),
      ),
      _ => Scaffold(body: Center(child: Text(route.name))),
    };
  }

  @override
  Widget build(BuildContext context) {
    if (widget.session.scope == ActivatedSessionScope.organization &&
        _selectedOrganizationRole == null) {
      return _RoleSelection(
        roles: _organizationRoles,
        onSelected: (role) => setState(() => _selectedOrganizationRole = role),
      );
    }
    return MobileNavigationShell(
      context: _context,
      requests: _requests,
      onActionRequest: _handleAction,
      screenBuilder: _screen,
    );
  }
}

class _RoleSelection extends StatelessWidget {
  const _RoleSelection({required this.roles, required this.onSelected});

  final List<MobileShellRole> roles;
  final ValueChanged<MobileShellRole> onSelected;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Rol Seçimi')),
    body: SafeArea(
      child: ListView(
        padding: const EdgeInsets.all(24),
        children: <Widget>[
          const Text(
            'Bu kurumda kullanacağınız rolü seçin. Roller birleştirilmez.',
          ),
          const SizedBox(height: 24),
          if (roles.contains(MobileShellRole.organizationAdministrator))
            AppButton.filled(
              label: 'Kurum Yöneticisi',
              onPressed: () =>
                  onSelected(MobileShellRole.organizationAdministrator),
            ),
          if (roles.contains(MobileShellRole.teacher)) ...[
            const SizedBox(height: 12),
            AppButton.outlined(
              label: 'Hoca',
              onPressed: () => onSelected(MobileShellRole.teacher),
            ),
          ],
          if (roles.isEmpty)
            const AppUnauthorizedState(
              message: 'Bu kurum için etkin bir rol bulunamadı.',
            ),
        ],
      ),
    ),
  );
}
