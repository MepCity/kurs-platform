import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/mobile_navigation_shell.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/mobile_shell_navigation_state.dart';

const _class = MobileShellClassRef(id: 'class-1', name: 'Sınıf');

MobileShellContext _teacher({
  String session = 'session',
  String org = 'org-1',
  String? classId,
  List<MobileShellClassRef> classes = const [_class],
  Set<MobileShellPermission> permissions = const {},
}) => MobileShellContext(
  sessionVerified: true,
  sessionContextId: session,
  role: MobileShellRole.teacher,
  organizationId: org,
  organizationName: 'Kurs',
  assignedClasses: classes,
  selectedClassId: classId,
  permissions: permissions,
);

MobileShellContext _admin({
  String session = 'admin-session',
  String org = 'org-1',
  Set<MobileShellPermission> permissions = const {},
}) => MobileShellContext(
  sessionVerified: true,
  sessionContextId: session,
  role: MobileShellRole.organizationAdministrator,
  organizationId: org,
  organizationName: 'Kurs',
  permissions: permissions,
);

void main() {
  group('MobileShellReconciler', () {
    test('identity change (organization A→B) triggers reset', () {
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(org: 'org-a'),
        current: _teacher(org: 'org-b'),
      );
      expect(effect, isA<MobileShellReconciliationResetStacks>());
    });

    test('identity change (role) triggers reset', () {
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(),
        current: _admin(),
      );
      expect(effect, isA<MobileShellReconciliationResetStacks>());
    });

    test('identity change (session context id) triggers reset', () {
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(session: 's1'),
        current: _teacher(session: 's2'),
      );
      expect(effect, isA<MobileShellReconciliationResetStacks>());
    });

    test('identity change (selected class A→B) triggers reset', () {
      final classes = const [
        MobileShellClassRef(id: 'class-1', name: 'A'),
        MobileShellClassRef(id: 'class-2', name: 'B'),
      ];
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(classId: 'class-1', classes: classes),
        current: _teacher(classId: 'class-2', classes: classes),
      );
      expect(effect, isA<MobileShellReconciliationResetStacks>());
    });

    test('selectionRequired transition (classId null) triggers reset', () {
      final classes = const [
        MobileShellClassRef(id: 'class-1', name: 'A'),
        MobileShellClassRef(id: 'class-2', name: 'B'),
      ];
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(classId: 'class-1', classes: classes),
        current: _teacher(classId: null, classes: classes),
      );
      expect(effect, isA<MobileShellReconciliationResetStacks>());
    });

    test('permission-only change triggers route reconciliation, not reset', () {
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(
          permissions: const {MobileShellPermission.restoreArchived},
        ),
        current: _teacher(
          permissions: const {MobileShellPermission.manageBrand},
        ),
      );
      expect(effect, isA<MobileShellReconciliationReconcileRoutes>());
    });

    test('same permission set new instance triggers none', () {
      final perms = {MobileShellPermission.restoreArchived};
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(permissions: perms),
        current: _teacher(permissions: {MobileShellPermission.restoreArchived}),
      );
      expect(effect, isA<MobileShellReconciliationNone>());
    });

    test('class rename (same id) triggers none', () {
      final effect = MobileShellReconciler.evaluate(
        previous: _teacher(
          classId: 'class-1',
          classes: const [MobileShellClassRef(id: 'class-1', name: 'Eski ad')],
        ),
        current: _teacher(
          classId: 'class-1',
          classes: const [MobileShellClassRef(id: 'class-1', name: 'Yeni ad')],
        ),
      );
      expect(effect, isA<MobileShellReconciliationNone>());
    });

    test(
      'adding a second class while keeping valid selection triggers none',
      () {
        final effect = MobileShellReconciler.evaluate(
          previous: _teacher(
            classId: 'a',
            classes: const [MobileShellClassRef(id: 'a', name: 'A')],
          ),
          current: _teacher(
            classId: 'a',
            classes: const [
              MobileShellClassRef(id: 'a', name: 'A'),
              MobileShellClassRef(id: 'b', name: 'B'),
            ],
          ),
        );
        expect(effect, isA<MobileShellReconciliationNone>());
      },
    );

    test('organization name change triggers none', () {
      final effect = MobileShellReconciler.evaluate(
        previous: MobileShellContext(
          sessionVerified: true,
          sessionContextId: 's',
          role: MobileShellRole.organizationAdministrator,
          organizationId: 'o',
          organizationName: 'Eski',
        ),
        current: MobileShellContext(
          sessionVerified: true,
          sessionContextId: 's',
          role: MobileShellRole.organizationAdministrator,
          organizationId: 'o',
          organizationName: 'Yeni',
        ),
      );
      expect(effect, isA<MobileShellReconciliationNone>());
    });

    test('permissionsChanged uses content comparison not identity', () {
      final perms = {MobileShellPermission.restoreArchived};
      expect(
        MobileShellReconciler.permissionsChanged(
          _teacher(permissions: perms),
          _teacher(permissions: {MobileShellPermission.restoreArchived}),
        ),
        isFalse,
      );
      expect(
        MobileShellReconciler.permissionsChanged(
          _teacher(permissions: perms),
          _teacher(permissions: const {MobileShellPermission.manageBrand}),
        ),
        isTrue,
      );
    });
  });

  group('MobileShellStackTracker', () {
    test('initial stack for unknown destination is root only', () {
      final tracker = MobileShellStackTracker();
      final stack = tracker.stackFor(MobileShellRouteId.more);
      expect(stack, hasLength(1));
      expect(stack.single.isRoot, isTrue);
      expect(stack.single.routeId, MobileShellRouteId.more);
    });

    test('push tracks inner route above root', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      final stack = tracker.stackFor(MobileShellRouteId.more);
      expect(stack, hasLength(2));
      expect(stack.first.isRoot, isTrue);
      expect(stack.last.routeId, MobileShellRouteId.auditLog);
    });

    test('pop removes top inner route but keeps root', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      tracker.onPop(MobileShellRouteId.more, generation: gen);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
      expect(tracker.topMarker(MobileShellRouteId.more)?.isRoot, isTrue);
    });

    test('remove mirrors pop for the top inner route', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      tracker.onRemove(MobileShellRouteId.more, generation: gen);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
    });

    test('replace swaps top marker and keeps stack length', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      tracker.onReplace(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: gen,
      );
      final stack = tracker.stackFor(MobileShellRouteId.more);
      expect(stack, hasLength(2));
      expect(stack.last.routeId, MobileShellRouteId.undo);
    });

    test('replace on root-only stack inserts inner route above root', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.ensureRoot(MobileShellRouteId.more);
      tracker.onReplace(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: gen,
      );
      final stack = tracker.stackFor(MobileShellRouteId.more);
      expect(stack, hasLength(2));
      expect(stack.last.routeId, MobileShellRouteId.undo);
    });

    test('popToRoot clears inner routes but keeps root', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: gen,
      );
      tracker.popToRoot(MobileShellRouteId.more, generation: gen);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
      expect(tracker.topMarker(MobileShellRouteId.more)?.isRoot, isTrue);
    });

    test('resetAll advances generation and stale generation ignored', () {
      final tracker = MobileShellStackTracker();
      final oldGen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: oldGen,
      );
      tracker.resetAll();
      final newGen = tracker.generation;
      expect(newGen, greaterThan(oldGen));
      // Stale push is ignored.
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: oldGen,
      );
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
      // Fresh push on new generation works.
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: newGen,
      );
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(2));
    });

    test('stale pop after reset does not corrupt tracker', () {
      final tracker = MobileShellStackTracker();
      final oldGen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: oldGen,
      );
      tracker.resetAll();
      final newGen = tracker.generation;
      // Stale callback from old navigator.
      tracker.onPop(MobileShellRouteId.more, generation: oldGen);
      tracker.onRemove(MobileShellRouteId.more, generation: oldGen);
      tracker.onReplace(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: oldGen,
      );
      // Tracker state unchanged from new generation perspective.
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
      expect(() {
        tracker.onPush(
          MobileShellRouteId.more,
          MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
          generation: newGen,
        );
      }, returnsNormally);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(2));
    });

    test('unknown/missing marker is tracked as an untrusted sentinel and '
        'popToRoot clears it deterministically', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      // Gerçek akışta _StackObserver._markerFor, marker taşımayan bir
      // rota için `trusted: false` sentinel üretir ve onu diğer push'lar
      // gibi normal biçimde tracker.onPush ile ekler (bkz.
      // mobile_navigation_shell.dart _StackObserver.didPush).
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      expect(tracker.hasUntrusted(MobileShellRouteId.more), isFalse);

      tracker.onPush(
        MobileShellRouteId.more,
        const MobileShellRouteMarker(
          MobileShellRouteId.more,
          isRoot: false,
          trusted: false,
        ),
        generation: gen,
      );
      expect(tracker.hasUntrusted(MobileShellRouteId.more), isTrue);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(3));

      // Planlı untrusted-route reconciliation'ın uyguladığı temizlik:
      // destination kök'e kadar sıfırlanır (altındaki güvenilir girdi
      // dahil, deterministik biçimde).
      tracker.popToRoot(MobileShellRouteId.more, generation: gen);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
      expect(tracker.topMarker(MobileShellRouteId.more)?.isRoot, isTrue);
      expect(tracker.hasUntrusted(MobileShellRouteId.more), isFalse);
    });

    test('firstUnauthorizedIndex finds first disallowed inner route', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.auditLog, isRoot: false),
        generation: gen,
      );
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.undo, isRoot: false),
        generation: gen,
      );
      final context = _teacher(permissions: const {});
      // teacher has no viewAuditLog permission.
      final index = tracker.firstUnauthorizedIndex(
        MobileShellRouteId.more,
        context,
      );
      expect(index, 1);
      // popToRoot removes both inner routes.
      tracker.popToRoot(MobileShellRouteId.more, generation: gen);
      expect(tracker.stackFor(MobileShellRouteId.more), hasLength(1));
    });

    test('untrusted marker is always unauthorized even if its routeId would '
        'normally be allowed', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        const MobileShellRouteMarker(
          MobileShellRouteId.profile,
          isRoot: false,
          trusted: false,
        ),
        generation: gen,
      );
      // profile herkese açık bir rotadır; normalde allows() true döner.
      final context = _teacher();
      expect(
        MobileShellRoutePolicyCatalog.allows(
          context,
          MobileShellRouteId.profile,
        ),
        isTrue,
      );
      // Ama untrusted olduğu için fail-closed: her zaman yetkisiz.
      expect(
        tracker.firstUnauthorizedIndex(MobileShellRouteId.more, context),
        1,
      );
    });

    test('firstUnauthorizedIndex is null when all routes allowed', () {
      final tracker = MobileShellStackTracker();
      final gen = tracker.generation;
      tracker.onPush(
        MobileShellRouteId.more,
        MobileShellRouteMarker(MobileShellRouteId.profile, isRoot: false),
        generation: gen,
      );
      final context = _teacher();
      expect(
        tracker.firstUnauthorizedIndex(MobileShellRouteId.more, context),
        isNull,
      );
    });
  });

  group('MobileShellRouteMarker', () {
    test('equality includes route id and isRoot', () {
      expect(
        const MobileShellRouteMarker(MobileShellRouteId.more, isRoot: false),
        const MobileShellRouteMarker(MobileShellRouteId.more, isRoot: false),
      );
      expect(
        const MobileShellRouteMarker(MobileShellRouteId.more, isRoot: false),
        isNot(
          const MobileShellRouteMarker(MobileShellRouteId.more, isRoot: true),
        ),
      );
      expect(
        const MobileShellRouteMarker(MobileShellRouteId.more, isRoot: false),
        isNot(
          const MobileShellRouteMarker(
            MobileShellRouteId.auditLog,
            isRoot: false,
          ),
        ),
      );
    });
  });
}
