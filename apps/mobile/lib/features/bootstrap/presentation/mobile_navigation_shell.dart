import 'package:flutter/material.dart';

import '../../../../core/presentation/widgets/app_state_widgets.dart';
import '../../../../core/presentation/widgets/app_sync_indicator.dart';
import '../../../../core/presentation/widgets/app_top_bar.dart';
import 'mobile_shell_navigation_state.dart';
import 'mobile_shell_route_policy.dart';

export 'mobile_shell_route_policy.dart' show MobileShellRoutePolicyCatalog;

enum MobileShellRole {
  platformAdministrator,
  organizationAdministrator,
  teacher,
}

enum MobileShellPermission {
  manageClasses,
  restoreArchived,
  manageTerm,
  manageBrand,
  manageModules,
  manageAttendanceStatuses,
  createOrCloseTeacher,
  assignTeacherClasses,
  viewTeacherPermissions,
  revokeOtherDeviceSessions,
  exportReports,
  viewAuditLog,
  undo,
}

enum MobileShellRouteId {
  platformOrganizations,
  platformAudit,
  platformReport,
  home,
  classSelector,
  attendance,
  students,
  program,
  management,
  termCalendar,
  archivedClasses,
  archivedStudents,
  brandSettings,
  enabledModules,
  attendanceStatuses,
  staff,
  reportExport,
  auditLog,
  undo,
  profile,
  deviceSessions,
  more,
  syncStatus,
}

enum MobileShellAction {
  changeContext,
  changeRole,
  logout,
  exitSupportMode,
  selectClass,
  openProfile,
}

/// A typed shell event. Class selection keeps the server-issued class ID rather
/// than reducing the action to a label or an untyped callback argument.
class MobileShellActionRequest {
  const MobileShellActionRequest({required this.action, this.classId});
  final MobileShellAction action;
  final String? classId;
}

/// Parent widget'in shell'i açması için tipli navigasyon isteği.
///
/// `sequence` "exactly once" garanti için monoton artan sıra numarasıdır;
/// ayrıntılar için [MobileShellRequestSequence] sözleşmesine bak.
/// `requestId` yalnızca izlenebilirlik/günlük kimliği olarak korunur.
class MobileShellRouteRequest {
  const MobileShellRouteRequest({
    required this.sequence,
    required this.requestId,
    required this.route,
  });

  /// Monoton artan sıra. Shell yaşamı boyunca asla sıfırlanmaz; aynı veya
  /// daha küçük sequence tekrar işlenmez.
  final int sequence;

  /// İzlenebilirlik kimliği (günlük/telemetri).
  final String requestId;

  /// Açılması istenen rota.
  final MobileShellRouteId route;

  @override
  String toString() =>
      'MobileShellRouteRequest(sequence: $sequence, requestId: $requestId, route: ${route.name})';
}

class MobileShellClassRef {
  const MobileShellClassRef({required this.id, required this.name});
  final String id;
  final String name;

  @override
  bool operator ==(Object other) =>
      other is MobileShellClassRef && other.id == id && other.name == name;

  @override
  int get hashCode => Object.hash(id, name);
}

enum MobileShellClassStateKind {
  noAssignedClass,
  singleClass,
  selectionRequired,
  classSelected,
}

/// Deterministic teacher class context. Duplicate or blank IDs are not valid
/// selectable records and therefore cannot alter tab visibility or selection.
class MobileShellClassState {
  const MobileShellClassState._({
    required this.kind,
    required this.classes,
    this.selectedClass,
  });

  factory MobileShellClassState.resolve(
    Iterable<MobileShellClassRef> assignedClasses,
    String? selectedClassId,
  ) {
    final byId = <String, MobileShellClassRef>{};
    for (final item in assignedClasses) {
      if (item.id.trim().isNotEmpty) byId.putIfAbsent(item.id, () => item);
    }
    final classes = List<MobileShellClassRef>.unmodifiable(byId.values);
    if (classes.isEmpty) {
      return const MobileShellClassState._(
        kind: MobileShellClassStateKind.noAssignedClass,
        classes: [],
      );
    }
    if (classes.length == 1) {
      return MobileShellClassState._(
        kind: MobileShellClassStateKind.singleClass,
        classes: classes,
        selectedClass: classes.single,
      );
    }
    final selected = selectedClassId == null ? null : byId[selectedClassId];
    return MobileShellClassState._(
      kind: selected == null
          ? MobileShellClassStateKind.selectionRequired
          : MobileShellClassStateKind.classSelected,
      classes: classes,
      selectedClass: selected,
    );
  }

  final MobileShellClassStateKind kind;
  final List<MobileShellClassRef> classes;
  final MobileShellClassRef? selectedClass;

  String? get effectiveSelectedClassId => selectedClass?.id;
  bool get hasMultipleClasses => classes.length > 1;
  bool get needsSelection =>
      kind == MobileShellClassStateKind.selectionRequired;
}

/// A server-verified context snapshot. It is never a token or authority source.
/// Invalid combinations fail closed in release builds as well as debug builds.
class MobileShellContext {
  const MobileShellContext({
    required this.sessionVerified,
    required this.sessionContextId,
    required this.role,
    this.organizationId,
    this.organizationName,
    this.supportTargetOrganizationId,
    this.supportMode = false,
    this.displayName = 'Kullanıcı',
    this.availableContextCount = 1,
    this.availableRoleCount = 1,
    this.assignedClasses = const <MobileShellClassRef>[],
    this.selectedClassId,
    this.permissions = const <MobileShellPermission>{},
    this.syncStatus,
  });

  final bool sessionVerified;
  final String sessionContextId;
  final MobileShellRole role;
  final String? organizationId;
  final String? organizationName;
  final String? supportTargetOrganizationId;
  final bool supportMode;
  final String displayName;
  final int availableContextCount;
  final int availableRoleCount;
  final List<MobileShellClassRef> assignedClasses;
  final String? selectedClassId;
  final Set<MobileShellPermission> permissions;
  final AppSyncStatus? syncStatus;

  bool get isValid {
    if (!sessionVerified || sessionContextId.isEmpty) return false;
    if (supportMode) {
      return role == MobileShellRole.platformAdministrator &&
          supportTargetOrganizationId != null &&
          supportTargetOrganizationId!.isNotEmpty &&
          organizationId == supportTargetOrganizationId &&
          organizationName != null;
    }
    if (role == MobileShellRole.platformAdministrator) {
      return organizationId == null;
    }
    return organizationId != null &&
        organizationId!.isNotEmpty &&
        organizationName != null;
  }

  MobileShellClassState get classState =>
      MobileShellClassState.resolve(assignedClasses, selectedClassId);
  String? get effectiveSelectedClassId => classState.effectiveSelectedClassId;
  MobileShellClassRef? get selectedClass => classState.selectedClass;

  bool has(MobileShellPermission permission) =>
      permissions.contains(permission);
  MobileShellNavigationIdentity get navigationIdentity => (
    session: sessionContextId,
    organization: organizationId,
    support: supportTargetOrganizationId,
    role: role,
    supportMode: supportMode,
    classId: effectiveSelectedClassId,
  );
}

/// UI-only navigation guard. API authorization stays mandatory on the server.
class MobileNavigationShell extends StatefulWidget {
  const MobileNavigationShell({
    super.key,
    required this.context,
    this.onAction,
    this.onActionRequest,
    this.requests = const <MobileShellRouteRequest>[],
  });
  final MobileShellContext context;
  final ValueChanged<MobileShellAction>? onAction;
  final ValueChanged<MobileShellActionRequest>? onActionRequest;

  /// Parent'ın bu build için ürettiği tüm bekleyen navigasyon niyetleri.
  ///
  /// Bu bir tek "son istek" değil, tipli bir kuyruktur: parent tek bir frame
  /// içinde birden fazla monoton `sequence` üretebilir (ör. hızlı art arda
  /// iki kullanıcı eylemi) ve bunların hiçbiri kaybolmamalıdır. Shell, her
  /// build'de bu listeyi `sequence` sırasına göre işler; `sequence >
  /// lastConsumedSequence` olan her giriş sırasıyla tam bir kez tüketilir.
  /// Parent, önceki girdileri listede tutmaya devam edebilir (idempotent —
  /// zaten tüketilmiş sequence'lar high-watermark tarafından yok sayılır)
  /// veya yalnızca yenilerini ekleyebilir.
  final List<MobileShellRouteRequest> requests;
  @override
  State<MobileNavigationShell> createState() => _MobileNavigationShellState();
}

class _StackObserver extends NavigatorObserver {
  _StackObserver({
    required this.destination,
    required this.tracker,
    required this.generation,
    required this.onStackChanged,
    required this.onUntrustedMarker,
  });
  final MobileShellRouteId destination;
  final MobileShellStackTracker tracker;
  final int generation;
  final VoidCallback onStackChanged;

  /// Untrusted (marker'sız/bilinmeyen) bir rota tespit edildiğinde çağrılır.
  /// Navigator observer callback'i içinde SENKRON stack mutasyonu yapmak
  /// güvenli değildir (Navigator henüz bildirim döngüsünü tamamlamamış
  /// olabilir); bu yüzden gerçek temizlik burada değil, çağıranın
  /// planladığı güvenli bir post-frame reconciliation'da yapılır.
  final VoidCallback onUntrustedMarker;

  /// Yalnız açık [MobileShellRouteMarker] taşıyan rotaları güvenilir tanır.
  /// Root rotalar [_MobileNavigationShellState.build] içindeki
  /// `onGenerateRoute` tarafından her zaman `isRoot: true` marker ile
  /// üretilir; bu yüzden `settings.name` dolu olması tek başına rotayı root
  /// kabul etmek için yeterli değildir. Markersız/bilinmeyen bir rota için
  /// `trusted: false` taşıyan bir sentinel marker döner: ne root kabul
  /// edilir ne de politika kontrolüne girer — [MobileShellStackTracker]
  /// bunu listedeki konumunda tutar ve daima yetkisiz sayar (fail-closed),
  /// böylece altındaki gerçek girdilerin izlenebilirliği bozulmadan kalır.
  MobileShellRouteMarker _markerFor(Route<dynamic>? route) {
    final args = route?.settings.arguments;
    if (args is MobileShellRouteMarker) return args;
    return MobileShellRouteMarker(destination, isRoot: false, trusted: false);
  }

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (tracker.isStale(generation)) return;
    final marker = _markerFor(route);
    if (marker.isRoot) {
      tracker.ensureRoot(destination);
    } else {
      tracker.onPush(destination, marker, generation: generation);
      if (!marker.trusted) onUntrustedMarker();
    }
    onStackChanged();
  }

  @override
  void didPop(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (tracker.isStale(generation)) return;
    tracker.onPop(destination, generation: generation);
    onStackChanged();
  }

  @override
  void didRemove(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (tracker.isStale(generation)) return;
    tracker.onRemove(destination, generation: generation);
    onStackChanged();
  }

  @override
  void didReplace({Route<dynamic>? newRoute, Route<dynamic>? oldRoute}) {
    if (tracker.isStale(generation)) return;
    final marker = _markerFor(newRoute);
    tracker.onReplace(destination, marker, generation: generation);
    if (!marker.trusted) onUntrustedMarker();
    onStackChanged();
  }
}

class _MobileNavigationShellState extends State<MobileNavigationShell> {
  int _selectedIndex = 0;
  final Map<MobileShellRouteId, GlobalKey<NavigatorState>> _keys = {};
  final Map<MobileShellRouteId, _StackObserver> _observers = {};
  final MobileShellStackTracker _tracker = MobileShellStackTracker();

  /// Navigation identity kaydı (structural equality). Identity değişimi
  /// stack resetini belirler.
  MobileShellNavigationIdentity? _identity;

  /// Request high-watermark. Shell yaşamı boyunca monoton artar ve identity
  /// değişimlerinde sıfırlanmaz. Aynı veya daha küçük sequence tekrar
  /// işlenmez.
  int _lastConsumedSequence = MobileShellRequestSequence.initial;
  bool _canPop = false;

  /// Reset sırasında yeni push'ların eski navigator'a gitmesini engellemek
  /// için: reset sonrası ilk frame'e kadar push'ları erteler.
  bool _resetting = false;

  List<MobileShellDestination> get _destinations {
    final c = widget.context;
    if (c.role == MobileShellRole.platformAdministrator && !c.supportMode) {
      return const [
        MobileShellDestination(
          MobileShellRouteId.platformOrganizations,
          'Kurumlar',
          Icons.business_outlined,
          Icons.business,
        ),
        MobileShellDestination(
          MobileShellRouteId.platformAudit,
          'Denetim',
          Icons.history_outlined,
          Icons.history,
        ),
        MobileShellDestination(
          MobileShellRouteId.platformReport,
          'Rapor',
          Icons.assessment_outlined,
          Icons.assessment,
        ),
        MobileShellDestination(
          MobileShellRouteId.profile,
          'Profil',
          Icons.person_outline,
          Icons.person,
        ),
      ];
    }
    if (c.role == MobileShellRole.organizationAdministrator || c.supportMode) {
      return const [
        MobileShellDestination(
          MobileShellRouteId.home,
          'Ana Sayfa',
          Icons.home_outlined,
          Icons.home,
        ),
        MobileShellDestination(
          MobileShellRouteId.classSelector,
          'Sınıflar',
          Icons.school_outlined,
          Icons.school,
        ),
        MobileShellDestination(
          MobileShellRouteId.students,
          'Öğrenciler',
          Icons.people_outline,
          Icons.people,
        ),
        MobileShellDestination(
          MobileShellRouteId.management,
          'Yönetim',
          Icons.settings_outlined,
          Icons.settings,
        ),
        MobileShellDestination(
          MobileShellRouteId.more,
          'Daha Fazla',
          Icons.more_horiz,
          Icons.more_horiz,
        ),
      ];
    }
    final items = <MobileShellDestination>[];
    if (c.classState.needsSelection) {
      items.add(
        const MobileShellDestination(
          MobileShellRouteId.classSelector,
          'Sınıf Seç',
          Icons.school_outlined,
          Icons.school,
        ),
      );
    } else if (c.effectiveSelectedClassId != null) {
      items.addAll(const [
        MobileShellDestination(
          MobileShellRouteId.attendance,
          'Yoklama',
          Icons.checklist_outlined,
          Icons.checklist,
        ),
        MobileShellDestination(
          MobileShellRouteId.students,
          'Öğrenciler',
          Icons.people_outline,
          Icons.people,
        ),
        MobileShellDestination(
          MobileShellRouteId.program,
          'Program',
          Icons.menu_book_outlined,
          Icons.menu_book,
        ),
      ]);
    }
    items.add(
      const MobileShellDestination(
        MobileShellRouteId.more,
        'Daha Fazla',
        Icons.more_horiz,
        Icons.more_horiz,
      ),
    );
    return items;
  }

  @override
  void initState() {
    super.initState();
    _identity = widget.context.navigationIdentity;
    final scheduledIdentity = _identity;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // Eski bağlamda planlanan request yeni bağlamda çalışmasın.
      if (_identity != scheduledIdentity) return;
      _handleRequests();
    });
  }

  @override
  void didUpdateWidget(MobileNavigationShell old) {
    super.didUpdateWidget(old);
    final previous = old.context;
    final current = widget.context;
    final effect = MobileShellReconciler.evaluate(
      previous: previous,
      current: current,
    );
    switch (effect) {
      case MobileShellReconciliationResetStacks():
        _applyIdentityReset();
      case MobileShellReconciliationReconcileRoutes():
        _schedulePermissionReconciliation();
      case MobileShellReconciliationNone():
        break;
    }

    // Aktif sekme geçersizleşmişse (destination listesi küçüldü) güvenli
    // ilk sekmeye dön. Root rotaların kendileri politika kataloğunun bir
    // parçasıdır; destination listesi zaten yalnızca geçerli rotaları içerir.
    final destinations = _destinations;
    if (_selectedIndex >= destinations.length) {
      _selectedIndex = 0;
    }

    // Identity değişse de değişmese de bekleyen kuyruk bu build'in GÜNCEL
    // (current) context'ine göre planlanır: `_handleRequests` içindeki
    // sequence high-watermark zaten eski/tekrarlanan girişleri reddeder ve
    // her `_open` çağrısı daima `widget.context`'i taze okur. Identity
    // değiştiyse `_applyIdentityReset` `_identity`'yi zaten bu build'in
    // identity'sine güncelledi; aşağıdaki callback yalnızca identity BU
    // planlamadan SONRA tekrar değişirse (ör. hızlı ardışık güncellemeler)
    // devre dışı kalır — identity değiştiği İÇİN değil.
    final scheduledIdentity = current.navigationIdentity;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      // Eski bağlamda planlanan request yeni bağlamda çalışmasın.
      if (_identity != scheduledIdentity) return;
      _handleRequests();
    });
  }

  void _applyIdentityReset() {
    _keys.clear();
    _observers.clear();
    _tracker.resetAll();
    _selectedIndex = 0;
    _resetting = true;
    _identity = widget.context.navigationIdentity;
    // High-watermark korunur: identity değişiminde sıfırlanmaz.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _resetting = false;
    });
  }

  void _schedulePermissionReconciliation() {
    final scheduledIdentity = widget.context.navigationIdentity;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_identity != scheduledIdentity) return;
      _reconcilePermissions();
    });
  }

  /// Her destination stack'inde açık iç route'ları politika kataloğuyla
  /// yeniden doğrular. İlk yetkisiz route ve üzerindeki tüm route'lar atomik
  /// biçimde root'a kadar çıkarılır.
  void _reconcilePermissions() {
    final context = widget.context;
    final destinations = _destinations;
    var selectedReset = false;
    for (final d in destinations) {
      final index = _tracker.firstUnauthorizedIndex(d.route, context);
      if (index == null) continue;
      final nav = _key(d.route).currentState;
      if (nav == null) continue;
      nav.popUntil((route) => route.isFirst);
      _tracker.popToRoot(d.route, generation: _tracker.generation);
      if (d.route == destinations[_selectedIndex].route) {
        selectedReset = true;
      }
    }
    if (selectedReset) {
      _refreshBackSync();
    }
  }

  /// `widget.requests` kuyruğunu sequence sırasıyla, high-watermark
  /// kuralına göre değerlendirir.
  /// - Kuyruk `sequence`'a göre artan sırayla işlenir; parent'ın aynı frame
  ///   içinde ürettiği birden fazla niyet (ör. N ve N+1) sırasıyla ve tam
  ///   birer kez değerlendirilir — yalnızca en sonuncusu değil.
  /// - `sequence > lastConsumed` değilse o giriş atlanır (zaten tüketilmiş).
  /// - Kabul veya red (yetkisiz dahil) fark etmeksizin işlenen her giriş
  ///   high-watermark'ı kendi sequence'ına ilerletir.
  /// - Aynı sequence birden çok girişte tekrar ederse yalnızca ilki (sırayla
  ///   ilk karşılaşılan) işlenir; sonrakiler watermark tarafından reddedilir.
  void _handleRequests() {
    final ordered = [...widget.requests]
      ..sort((a, b) => a.sequence.compareTo(b.sequence));
    for (final request in ordered) {
      if (request.sequence <= _lastConsumedSequence) continue;
      _lastConsumedSequence = request.sequence;
      _open(request.route);
    }
  }

  GlobalKey<NavigatorState> _key(MobileShellRouteId id) =>
      _keys.putIfAbsent(id, GlobalKey<NavigatorState>.new);
  _StackObserver _observer(MobileShellRouteId id) {
    return _observers.putIfAbsent(
      id,
      () => _StackObserver(
        destination: id,
        tracker: _tracker,
        generation: _tracker.generation,
        onStackChanged: _refreshBack,
        onUntrustedMarker: () => _scheduleUntrustedReconciliation(id),
      ),
    );
  }

  void _refreshBack() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      _refreshBackSync();
    });
  }

  /// Untrusted (marker'sız/bilinmeyen) bir rota tespit edildiğinde ANINDA
  /// çalışır, ama stack'i hemen değil bir SONRAKİ frame'de temizler.
  /// Navigator observer bildirimi sırasında senkron `popUntil` çağırmak
  /// güvenli değildir; bu yüzden planlama anındaki immutable değerler
  /// (navigationIdentity, tracker generation) yakalanır ve gerçek
  /// temizlikten hemen önce tekrar doğrulanır:
  /// - widget hâlâ mounted mı,
  /// - identity planlandığından beri değişmedi mi,
  /// - tracker generation (reset) planlandığından beri değişmedi mi,
  /// - bu destination hâlâ görünür sekme kümesinde mi,
  /// - untrusted marker hâlâ stack'te mi (başka bir yol zaten temizlemiş
  ///   olabilir).
  /// Herhangi biri saptıysa temizlik sessizce atlanır — eski (stale)
  /// callback yeni identity/generation'ın Navigator'ını etkileyemez.
  void _scheduleUntrustedReconciliation(MobileShellRouteId destination) {
    final capturedIdentity = widget.context.navigationIdentity;
    final capturedGeneration = _tracker.generation;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (widget.context.navigationIdentity != capturedIdentity) return;
      if (_tracker.generation != capturedGeneration) return;
      if (!_destinations.any((d) => d.route == destination)) return;
      if (!_tracker.hasUntrusted(destination)) return;
      _key(destination).currentState?.popUntil((route) => route.isFirst);
      _tracker.popToRoot(destination, generation: capturedGeneration);
      _refreshBackSync();
    });
  }

  void _refreshBackSync() {
    final d = _destinations;
    if (d.isEmpty) return;
    final value = _key(d[_selectedIndex].route).currentState?.canPop() ?? false;
    if (value != _canPop) setState(() => _canPop = value);
  }

  void _select(int index) {
    if (index == _selectedIndex) {
      final route = _destinations[index].route;
      _key(route).currentState?.popUntil((r) => r.isFirst);
      _tracker.popToRoot(route, generation: _tracker.generation);
    } else {
      setState(() => _selectedIndex = index);
    }
    WidgetsBinding.instance.addPostFrameCallback((_) => _refreshBack());
  }

  void _open(MobileShellRouteId route) {
    if (_resetting) {
      // Reset devam ederken yeni request yanlış navigator'a push edilmesin.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted && !_resetting) _open(route);
      });
      return;
    }
    final c = widget.context;
    if (!MobileShellRoutePolicyCatalog.allows(c, route)) {
      _push(_destinations[_selectedIndex].route, route, unauthorized: true);
      return;
    }
    final target = MobileShellRoutePolicyCatalog.targetFor(c, route);
    if (target == null) {
      _push(_destinations[_selectedIndex].route, route, unauthorized: true);
      return;
    }
    final index = _destinations.indexWhere((item) => item.route == target);
    if (index < 0) {
      _push(_destinations[_selectedIndex].route, route, unauthorized: true);
      return;
    }
    setState(() => _selectedIndex = index);
    // Planlandığı andaki immutable değerleri yakala: navigationIdentity ve
    // tracker generation. Gerçek push, bir veya birden fazla frame sonra
    // gerçekleşeceği için bu değerler push anında yeniden doğrulanacak.
    _pushWhenReady(
      route,
      capturedIdentity: c.navigationIdentity,
      capturedGeneration: _tracker.generation,
    );
  }

  void _push(
    MobileShellRouteId destination,
    MobileShellRouteId route, {
    bool unauthorized = false,
  }) {
    final marker = MobileShellRouteMarker(route, isRoot: false);
    _key(destination).currentState?.push(
      MaterialPageRoute<void>(
        settings: RouteSettings(name: route.name, arguments: marker),
        builder: (_) => unauthorized
            ? const Scaffold(body: AppUnauthorizedState())
            : route == MobileShellRouteId.classSelector &&
                  widget.context.role == MobileShellRole.teacher
            ? _ClassSelector(
                state: widget.context.classState,
                onSelected: _selectClass,
              )
            : _Placeholder(route: route),
      ),
    );
  }

  /// Bir push'u navigator hazır olana kadar erteler. Planlama anından
  /// (bkz. [_open]) gerçek push anına kadar bir veya birden fazla frame
  /// geçebilir; bu sürede context, izinler veya identity değişmiş olabilir.
  /// Bu yüzden gerçek push'tan hemen önce:
  /// - `mounted` tekrar kontrol edilir,
  /// - [capturedIdentity] güncel `navigationIdentity` ile karşılaştırılır,
  /// - [capturedGeneration] güncel tracker generation ile karşılaştırılır,
  /// - route policy ve target destination güncel bağlama göre yeniden
  ///   hesaplanır.
  /// Herhangi biri planlandığı andaki değerden saptıysa (identity veya
  /// permission değişti, reset oldu) callback sessizce düşürülür — hiçbir
  /// şey push edilmez.
  void _pushWhenReady(
    MobileShellRouteId route, {
    required MobileShellNavigationIdentity capturedIdentity,
    required int capturedGeneration,
    int attempt = 0,
  }) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      if (_resetting) {
        // Reset devam ederken push edilmesin; reset bitene kadar bekle.
        _pushWhenReady(
          route,
          capturedIdentity: capturedIdentity,
          capturedGeneration: capturedGeneration,
          attempt: attempt,
        );
        return;
      }
      final c = widget.context;
      if (c.navigationIdentity != capturedIdentity) {
        return; // Identity değişti: eski callback sessizce düşürülür.
      }
      if (_tracker.generation != capturedGeneration) {
        return; // Reset oldu (yeni Navigator'lar): eski callback düşürülür.
      }
      if (!MobileShellRoutePolicyCatalog.allows(c, route)) {
        return; // Permission değişti: eski callback sessizce düşürülür.
      }
      final target = MobileShellRoutePolicyCatalog.targetFor(c, route);
      if (target == null) return;
      final index = _destinations.indexWhere((item) => item.route == target);
      if (index < 0) return;
      if (index != _selectedIndex) {
        setState(() => _selectedIndex = index);
      }
      if (_key(target).currentState == null && attempt == 0) {
        _pushWhenReady(
          route,
          capturedIdentity: capturedIdentity,
          capturedGeneration: capturedGeneration,
          attempt: 1,
        );
        return;
      }
      _push(target, route);
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.context;
    if (!c.isValid) {
      return const Scaffold(
        body: AppUnauthorizedState(
          message: 'Geçersiz veya doğrulanmamış bağlam.',
        ),
      );
    }

    final destinations = _destinations;
    return PopScope(
      canPop: !_canPop,
      onPopInvokedWithResult: (didPop, _) {
        if (!didPop) {
          _key(destinations[_selectedIndex].route).currentState?.maybePop();
        }
      },
      child: Scaffold(
        appBar: _bar(),
        body: IndexedStack(
          index: _selectedIndex,
          children: [
            for (final d in destinations)
              Navigator(
                key: _key(d.route),
                observers: [_observer(d.route)],
                onGenerateRoute: (_) => MaterialPageRoute<void>(
                  settings: RouteSettings(
                    name: d.route.name,
                    arguments: MobileShellRouteMarker(d.route, isRoot: true),
                  ),
                  builder: (_) => _root(d.route),
                ),
              ),
          ],
        ),
        bottomNavigationBar: destinations.length == 1
            ? _OneTab(
                label: destinations.single.label,
                icon: destinations.single.selectedIcon,
                onTap: () => _select(0),
              )
            : NavigationBar(
                height: 64,
                selectedIndex: _selectedIndex,
                onDestinationSelected: _select,
                destinations: [
                  for (final d in destinations)
                    NavigationDestination(
                      icon: Icon(d.icon, size: 20),
                      selectedIcon: Icon(d.selectedIcon, size: 24),
                      label: d.label,
                    ),
                ],
              ),
      ),
    );
  }

  Widget _root(MobileShellRouteId route) {
    final c = widget.context;
    if (route == MobileShellRouteId.classSelector &&
        c.role == MobileShellRole.teacher) {
      return _ClassSelector(state: c.classState, onSelected: _selectClass);
    }
    if (route == MobileShellRouteId.more) {
      return _MoreMenu(
        context: c,
        onRoute: _open,
        onAction: _onAction,
        classState: c.role == MobileShellRole.teacher ? c.classState : null,
      );
    }
    if (route == MobileShellRouteId.management) {
      return _ManagementMenu(context: c, onRoute: _open);
    }
    return _Placeholder(route: route);
  }

  void _emitAction(MobileShellAction action, {String? classId}) {
    widget.onActionRequest?.call(
      MobileShellActionRequest(action: action, classId: classId),
    );
    widget.onAction?.call(action);
  }

  void _selectClass(String classId) =>
      _emitAction(MobileShellAction.selectClass, classId: classId);

  void _showClassSelector() {
    if (!MobileShellRoutePolicyCatalog.allows(
      widget.context,
      MobileShellRouteId.classSelector,
    )) {
      return;
    }
    _push(
      _destinations[_selectedIndex].route,
      MobileShellRouteId.classSelector,
    );
  }

  void _onAction(MobileShellAction action) {
    if (action == MobileShellAction.openProfile) {
      _open(MobileShellRouteId.profile);
      return;
    }
    _emitAction(action);
  }

  PreferredSizeWidget _bar() {
    final c = widget.context;
    final d = _destinations[_selectedIndex];
    final title =
        c.role == MobileShellRole.platformAdministrator && !c.supportMode
        ? 'Kurs Platform · Platform Yöneticisi'
        : c.role == MobileShellRole.teacher
        ? '${c.organizationName} · Hoca · ${c.selectedClass?.name ?? 'Sınıf seçin'}'
        : '${c.organizationName} · Kurum Yöneticisi';
    return AppTopBar(
      title: title,
      supportMode: c.supportMode,
      showBackButton: _canPop || c.supportMode,
      onBack: () {
        if (_canPop) {
          _key(d.route).currentState?.maybePop();
        } else {
          _emitAction(MobileShellAction.exitSupportMode);
        }
      },
      actions: [
        if (c.role == MobileShellRole.teacher &&
            c.classState.kind == MobileShellClassStateKind.classSelected)
          IconButton(
            tooltip: 'Sınıf değiştir',
            constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
            icon: const Icon(Icons.swap_horiz),
            onPressed: _showClassSelector,
          ),
        if (c.syncStatus != null)
          AppSyncIndicator(
            status: c.syncStatus!,
            onTap: c.syncStatus == AppSyncStatus.failed
                ? () => _open(MobileShellRouteId.syncStatus)
                : null,
          ),
        PopupMenuButton<MobileShellAction>(
          tooltip: 'Profil',
          icon: const Icon(Icons.person_outline),
          onSelected: _onAction,
          itemBuilder: (_) => [
            PopupMenuItem<MobileShellAction>(
              enabled: false,
              child: Text(c.displayName),
            ),
            if (c.availableContextCount > 1)
              const PopupMenuItem(
                value: MobileShellAction.changeContext,
                child: Text('Kurum/Bağlam Değiştir'),
              ),
            if (c.availableRoleCount > 1)
              const PopupMenuItem(
                value: MobileShellAction.changeRole,
                child: Text('Rol Değiştir'),
              ),
            const PopupMenuItem(
              value: MobileShellAction.openProfile,
              child: Text('Profilim'),
            ),
            const PopupMenuItem(
              value: MobileShellAction.logout,
              child: Text('Çıkış Yap'),
            ),
          ],
        ),
      ],
    );
  }
}

class _OneTab extends StatelessWidget {
  const _OneTab({required this.label, required this.icon, required this.onTap});
  final String label;
  final IconData icon;
  final VoidCallback onTap;
  @override
  Widget build(BuildContext context) => Material(
    elevation: 4,
    child: SafeArea(
      top: false,
      child: SizedBox(
        height: 64,
        child: TextButton.icon(
          onPressed: onTap,
          icon: Icon(icon),
          label: Text(label),
        ),
      ),
    ),
  );
}

class _Placeholder extends StatelessWidget {
  const _Placeholder({required this.route});
  final MobileShellRouteId route;
  @override
  Widget build(BuildContext context) =>
      Scaffold(body: Center(child: Text(route.name)));
}

class _ClassSelector extends StatelessWidget {
  const _ClassSelector({required this.state, required this.onSelected});
  final MobileShellClassState state;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(24),
    children: [
      const Text('Sınıf seçin', style: TextStyle(fontSize: 22)),
      const SizedBox(height: 8),
      const Text('Devam etmek için çalışacağınız sınıfı seçin.'),
      const SizedBox(height: 16),
      for (final item in state.classes)
        Card(
          child: ListTile(
            selected: item.id == state.effectiveSelectedClassId,
            title: Text(item.name),
            trailing: item.id == state.effectiveSelectedClassId
                ? const Icon(Icons.check)
                : const Icon(Icons.chevron_right),
            onTap: () => onSelected(item.id),
          ),
        ),
    ],
  );
}

class _NoAssignedClassState extends StatelessWidget {
  const _NoAssignedClassState();
  @override
  Widget build(BuildContext context) => const Padding(
    padding: EdgeInsets.fromLTRB(24, 24, 24, 8),
    child: AppEmptyState(
      title: 'Henüz atanmış bir sınıfın yok',
      description:
          'Bir sınıfa atandığında günlük eğitim ekranların burada görünür.',
    ),
  );
}

class _MoreMenu extends StatelessWidget {
  const _MoreMenu({
    required this.context,
    required this.onRoute,
    required this.onAction,
    this.classState,
  });
  final MobileShellContext context;
  final ValueChanged<MobileShellRouteId> onRoute;
  final ValueChanged<MobileShellAction> onAction;
  final MobileShellClassState? classState;

  bool _allows(MobileShellRouteId route) =>
      MobileShellRoutePolicyCatalog.allows(context, route);

  @override
  Widget build(BuildContext buildContext) {
    final isOrganizationShell =
        context.role == MobileShellRole.organizationAdministrator ||
        context.supportMode;
    final managementEntries = <(String, MobileShellRouteId)>[
      ('Sınıf Yönetimi', MobileShellRouteId.management),
      ('Dönem ve Takvim', MobileShellRouteId.termCalendar),
      ('Arşivlenmiş Sınıflar', MobileShellRouteId.archivedClasses),
      ('Personel / Hoca Listesi', MobileShellRouteId.staff),
    ].where((entry) => _allows(entry.$2)).toList();
    final settingsEntries = <(String, MobileShellRouteId)>[
      ('Marka Ayarları', MobileShellRouteId.brandSettings),
      ('Etkin Modüller', MobileShellRouteId.enabledModules),
      ('Yoklama Durumları', MobileShellRouteId.attendanceStatuses),
    ].where((entry) => _allows(entry.$2)).toList();
    final reportingEntries = <(String, MobileShellRouteId)>[
      ('Excel Raporu İndir', MobileShellRouteId.reportExport),
      ('İşlem Geçmişi', MobileShellRouteId.auditLog),
      ('İşlemi Geri Al', MobileShellRouteId.undo),
    ].where((entry) => _allows(entry.$2)).toList();
    return ListView(
      children: [
        if (classState?.kind == MobileShellClassStateKind.noAssignedClass)
          const _NoAssignedClassState(),
        if (!isOrganizationShell && managementEntries.isNotEmpty)
          _MenuSection(
            title: 'Yönetim',
            entries: managementEntries,
            onRoute: onRoute,
          ),
        if (!isOrganizationShell && settingsEntries.isNotEmpty)
          _SettingsExpansion(entries: settingsEntries, onRoute: onRoute),
        if (reportingEntries.isNotEmpty)
          _MenuSection(
            title: 'Raporlar ve Denetim',
            entries: reportingEntries,
            onRoute: onRoute,
          ),
        _MenuSection(
          title: 'Profil ve Oturum',
          entries: [
            if (_allows(MobileShellRouteId.profile))
              ('Profilim', MobileShellRouteId.profile),
            if (_allows(MobileShellRouteId.deviceSessions))
              ('Cihaz/Oturum Bilgisi', MobileShellRouteId.deviceSessions),
          ],
          onRoute: onRoute,
          actions: const [('Çıkış Yap', MobileShellAction.logout)],
          onAction: onAction,
        ),
      ],
    );
  }
}

class _MenuSection extends StatelessWidget {
  const _MenuSection({
    required this.title,
    required this.entries,
    required this.onRoute,
    this.actions = const [],
    this.onAction,
  });
  final String title;
  final List<(String, MobileShellRouteId)> entries;
  final ValueChanged<MobileShellRouteId> onRoute;
  final List<(String, MobileShellAction)> actions;
  final ValueChanged<MobileShellAction>? onAction;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Padding(
        padding: const EdgeInsets.fromLTRB(24, 16, 24, 8),
        child: Text(title),
      ),
      for (final entry in entries)
        Material(
          child: ListTile(
            title: Text(entry.$1),
            trailing: const Icon(Icons.chevron_right),
            onTap: () => onRoute(entry.$2),
          ),
        ),
      for (final action in actions)
        Material(
          child: ListTile(
            title: Text(action.$1),
            trailing: const Icon(Icons.chevron_right),
            onTap: onAction == null ? null : () => onAction!(action.$2),
          ),
        ),
    ],
  );
}

class _SettingsExpansion extends StatelessWidget {
  const _SettingsExpansion({required this.entries, required this.onRoute});
  final List<(String, MobileShellRouteId)> entries;
  final ValueChanged<MobileShellRouteId> onRoute;

  @override
  Widget build(BuildContext context) => Material(
    color: Colors.transparent,
    child: ExpansionTile(
      title: const Text('Kurum Ayarları'),
      children: [
        for (final entry in entries)
          Material(
            color: Colors.transparent,
            child: ListTile(
              title: Text(entry.$1),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => onRoute(entry.$2),
            ),
          ),
      ],
    ),
  );
}

class _ManagementMenu extends StatelessWidget {
  const _ManagementMenu({required this.context, required this.onRoute});
  final MobileShellContext context;
  final ValueChanged<MobileShellRouteId> onRoute;

  @override
  Widget build(BuildContext context) {
    const routes = <(String, MobileShellRouteId)>[
      ('Sınıf Yönetimi', MobileShellRouteId.management),
      ('Dönem ve Takvim', MobileShellRouteId.termCalendar),
      ('Arşivlenmiş Sınıflar', MobileShellRouteId.archivedClasses),
      ('Arşivlenmiş Öğrenciler', MobileShellRouteId.archivedStudents),
      ('Marka Ayarları', MobileShellRouteId.brandSettings),
      ('Etkin Modüller', MobileShellRouteId.enabledModules),
      ('Yoklama Durumları', MobileShellRouteId.attendanceStatuses),
      ('Personel / Hoca Listesi', MobileShellRouteId.staff),
    ];
    final visible = routes.where(
      (entry) => MobileShellRoutePolicyCatalog.allows(this.context, entry.$2),
    );
    return ListView(
      children: <Widget>[
        for (final entry in visible)
          Material(
            child: ListTile(
              title: Text(entry.$1),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => onRoute(entry.$2),
            ),
          ),
      ],
    );
  }
}
