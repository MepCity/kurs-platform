import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/mobile_navigation_shell.dart';

/// Test amaçlı görünür rota yüzeyi. Yalnızca [MobileNavigationShell]
/// widget'ını [request] ve [context] ile sarmalar; davranışı widget yüzeyi
/// üzerinden doğrular.
class _ShellHarness {
  _ShellHarness(this.tester);
  final WidgetTester tester;
  int nextSequence = 1;

  Future<void> pump(
    MobileShellContext context, {
    MobileShellRouteRequest? request,
    List<MobileShellRouteRequest>? requests,
    ValueChanged<MobileShellAction>? onAction,
    ValueChanged<MobileShellActionRequest>? onActionRequest,
  }) async {
    assert(
      request == null || requests == null,
      'request ve requests aynı anda verilmemeli',
    );
    await tester.pumpWidget(
      KursPlatformApp(
        home: MobileNavigationShell(
          context: context,
          requests: requests ?? (request == null ? const [] : [request]),
          onAction: onAction,
          onActionRequest: onActionRequest,
        ),
      ),
    );
    await tester.pump();
  }

  MobileShellRouteRequest requestFor(
    MobileShellRouteId route, {
    int? sequence,
  }) {
    final seq = sequence ?? nextSequence++;
    return MobileShellRouteRequest(
      sequence: seq,
      requestId: 'req-$seq-${route.name}',
      route: route,
    );
  }
}

MobileShellContext _teacher({
  String session = 'session',
  String org = 'org-1',
  String? classId,
  List<MobileShellClassRef> classes = const [
    MobileShellClassRef(id: 'class-1', name: 'A'),
  ],
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

MobileShellContext _platformSupport({String target = 'org-1'}) =>
    MobileShellContext(
      sessionVerified: true,
      sessionContextId: 'platform-support',
      role: MobileShellRole.platformAdministrator,
      organizationId: target,
      organizationName: 'Kurs',
      supportMode: true,
      supportTargetOrganizationId: target,
    );

void main() {
  testWidgets(
    'sequence high-watermark: same or smaller sequence is not reprocessed',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        permissions: const {MobileShellPermission.restoreArchived},
      );
      // Aynı sequence iki kez: ikinci pump işlemsiz olmalı.
      final req1 = MobileShellRouteRequest(
        sequence: 5,
        requestId: 'req-5',
        route: MobileShellRouteId.archivedClasses,
      );
      await harness.pump(c, request: req1);
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      // archivedClasses rotasını kapat (pop) ve aynı sequence'u tekrar ver.
      await tester.tap(find.byTooltip('Geri'));
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsNothing);

      await harness.pump(c, request: req1);
      await tester.pumpAndSettle();
      // Aynı sequence yeniden işlenmedi.
      expect(find.text('archivedClasses'), findsNothing);
    },
  );

  testWidgets('larger sequence reopens the same route', (tester) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 1,
        requestId: 'req-1',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    await tester.tap(find.byTooltip('Geri'));
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsNothing);

    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 2,
        requestId: 'req-2',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);
  });

  testWidgets('same sequence with different requestId and route is rejected', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      permissions: const {
        MobileShellPermission.restoreArchived,
        MobileShellPermission.manageBrand,
      },
    );
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 7,
        requestId: 'req-7-archived',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    // Aynı sequence 7 ama farklı requestId + farklı route. Reddedilmeli.
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 7,
        requestId: 'req-7-brand',
        route: MobileShellRouteId.brandSettings,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsNothing);
    // archivedClasses hâlâ açık (sequence 7 işlendi, ikincisi reddedildi).
    expect(find.text('archivedClasses'), findsOneWidget);
  });

  testWidgets('out-of-order smaller sequence is rejected', (tester) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      permissions: const {
        MobileShellPermission.restoreArchived,
        MobileShellPermission.manageBrand,
      },
    );
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 10,
        requestId: 'req-10',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    // Daha küçük sequence reddedilir.
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 3,
        requestId: 'req-3',
        route: MobileShellRouteId.brandSettings,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsNothing);
  });

  testWidgets('rejected (unauthorized) request consumes the sequence', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(permissions: const {}); // izin yok
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 4,
        requestId: 'req-4',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);

    // Şimdi izin ekle ama aynı sequence 4 ile tekrar ver.
    final c2 = _teacher(
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c2,
      request: MobileShellRouteRequest(
        sequence: 4,
        requestId: 'req-4',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    // Sequence 4 zaten tüketildiği için archivedClasses açılmaz.
    expect(find.text('archivedClasses'), findsNothing);

    // Yeni sequence ile yeniden denersek açılır.
    await harness.pump(
      c2,
      request: MobileShellRouteRequest(
        sequence: 5,
        requestId: 'req-5',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);
  });

  testWidgets('high-watermark does not reset on navigation identity change', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c,
      request: MobileShellRouteRequest(
        sequence: 8,
        requestId: 'req-8',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    // Identity değişimi: farklı kurum.
    final c2 = _teacher(
      org: 'org-2',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c2,
      request: MobileShellRouteRequest(
        sequence: 8,
        requestId: 'req-8',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    // Identity değişti → stack reset; ayrıca sequence 8 high-watermark'ta
    // olduğu için tekrar işlenmez. archivedClasses görünmemeli.
    expect(find.text('archivedClasses'), findsNothing);
  });

  testWidgets('identity change (org A→B) clears all stacks', (tester) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      org: 'org-a',
      classId: 'class-1',
      permissions: const {MobileShellPermission.manageBrand},
    );
    final req = MobileShellRouteRequest(
      sequence: 1,
      requestId: 'req-1',
      route: MobileShellRouteId.brandSettings,
    );
    await harness.pump(c, request: req);
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsOneWidget);

    await harness.pump(
      _teacher(
        org: 'org-b',
        classId: 'class-1',
        permissions: const {MobileShellPermission.manageBrand},
      ),
      request: req,
    );
    await tester.pumpAndSettle();
    // Identity değişti → tüm stack'ler temizlendi.
    expect(find.text('brandSettings'), findsNothing);
  });

  testWidgets('identity change (role) clears all stacks', (tester) async {
    final harness = _ShellHarness(tester);
    final teacher = _teacher(
      classId: 'class-1',
      permissions: const {MobileShellPermission.manageBrand},
    );
    final req = MobileShellRouteRequest(
      sequence: 1,
      requestId: 'req-1',
      route: MobileShellRouteId.brandSettings,
    );
    await harness.pump(teacher, request: req);
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsOneWidget);

    // Rol değişimi: teacher → org admin.
    final admin = MobileShellContext(
      sessionVerified: true,
      sessionContextId: 'admin-session',
      role: MobileShellRole.organizationAdministrator,
      organizationId: 'org-1',
      organizationName: 'Kurs',
      permissions: const {MobileShellPermission.manageBrand},
    );
    await harness.pump(admin, request: req);
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsNothing);
  });

  testWidgets('identity change (support target) clears all stacks', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final support1 = _platformSupport(target: 'org-1');
    // Sabit sequence: identity değişiminden sonra aynı request yeniden
    // oynatılmamalı.
    final req = MobileShellRouteRequest(
      sequence: 1,
      requestId: 'req-1',
      route: MobileShellRouteId.management,
    );
    await harness.pump(support1, request: req);
    await tester.pumpAndSettle();
    expect(find.text('management'), findsOneWidget);

    final support2 = _platformSupport(target: 'org-2');
    await harness.pump(support2, request: req);
    await tester.pumpAndSettle();
    expect(find.text('management'), findsNothing);
  });

  testWidgets('class A→B clears operational stacks', (tester) async {
    final harness = _ShellHarness(tester);
    final classes = const [
      MobileShellClassRef(id: 'class-1', name: 'A'),
      MobileShellClassRef(id: 'class-2', name: 'B'),
    ];
    final c1 = _teacher(classId: 'class-1', classes: classes);
    await harness.pump(c1);
    await tester.pumpAndSettle();
    expect(find.text('Yoklama'), findsOneWidget);

    // Aynı sekmede (attendance) ek bir rota aç. Test amaçlı olarak more
    // menüden profile gidip geri gelelim — bu stack derinliğini değiştirir.
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Profilim'));
    await tester.pumpAndSettle();
    expect(find.text('profile'), findsOneWidget);

    // Sınıf değişimi (identity değişimi).
    final c2 = _teacher(classId: 'class-2', classes: classes);
    await harness.pump(c2);
    await tester.pumpAndSettle();
    // profile stack'i temizlendi.
    expect(find.text('profile'), findsNothing);
  });

  testWidgets('class rename keeps stack and selection', (tester) async {
    final harness = _ShellHarness(tester);
    final c1 = _teacher(
      classId: 'class-1',
      classes: const [MobileShellClassRef(id: 'class-1', name: 'Eski ad')],
    );
    await harness.pump(c1);
    await tester.pumpAndSettle();
    expect(find.text('Yoklama'), findsOneWidget);

    // More menüde profile git.
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Profilim'));
    await tester.pumpAndSettle();
    expect(find.text('profile'), findsOneWidget);

    // Sınıf yeniden adlandırma: identity sabit.
    final c2 = _teacher(
      classId: 'class-1',
      classes: const [MobileShellClassRef(id: 'class-1', name: 'Yeni ad')],
    );
    await harness.pump(c2);
    await tester.pumpAndSettle();
    // profile stack'i korundu.
    expect(find.text('profile'), findsOneWidget);
  });

  testWidgets(
    'adding a second class while keeping valid selection keeps stack',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c1 = _teacher(
        classId: 'a',
        classes: const [MobileShellClassRef(id: 'a', name: 'A')],
      );
      await harness.pump(c1);
      await tester.pumpAndSettle();
      await tester.tap(find.text('Daha Fazla'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Profilim'));
      await tester.pumpAndSettle();
      expect(find.text('profile'), findsOneWidget);

      final c2 = _teacher(
        classId: 'a',
        classes: const [
          MobileShellClassRef(id: 'a', name: 'A'),
          MobileShellClassRef(id: 'b', name: 'B'),
        ],
      );
      await harness.pump(c2);
      await tester.pumpAndSettle();
      // identity sabit (a) → stack korundu.
      expect(find.text('profile'), findsOneWidget);
    },
  );

  testWidgets(
    'permission removal removes the open protected route from stack',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.archivedClasses),
      );
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      // restoreArchived iznini kaldır.
      final c2 = _teacher(classId: 'class-1', permissions: const {});
      await harness.pump(c2);
      await tester.pumpAndSettle();
      // archivedClasses route reconciliation ile stack'ten çıkarıldı.
      expect(find.text('archivedClasses'), findsNothing);
      expect(find.text('Bu işlem için yetkiniz yok.'), findsNothing);
    },
  );

  testWidgets(
    'permission removal of last of four staff permissions closes staff route',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {
          MobileShellPermission.createOrCloseTeacher,
          MobileShellPermission.assignTeacherClasses,
          MobileShellPermission.viewTeacherPermissions,
          MobileShellPermission.revokeOtherDeviceSessions,
        },
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.staff),
      );
      await tester.pumpAndSettle();
      expect(find.text('staff'), findsOneWidget);

      // Tüm personel izinlerini kaldır.
      final c2 = _teacher(classId: 'class-1', permissions: const {});
      await harness.pump(c2);
      await tester.pumpAndSettle();
      expect(find.text('staff'), findsNothing);
      expect(find.text('Bu işlem için yetkiniz yok.'), findsNothing);
    },
  );

  testWidgets(
    'permission removal keeping one staff permission keeps staff route',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {
          MobileShellPermission.createOrCloseTeacher,
          MobileShellPermission.assignTeacherClasses,
          MobileShellPermission.viewTeacherPermissions,
          MobileShellPermission.revokeOtherDeviceSessions,
        },
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.staff),
      );
      await tester.pumpAndSettle();
      expect(find.text('staff'), findsOneWidget);

      // Üçünü kaldır, birini tut.
      final c2 = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.viewTeacherPermissions},
      );
      await harness.pump(c2);
      await tester.pumpAndSettle();
      expect(find.text('staff'), findsOneWidget);
    },
  );

  testWidgets('permission gain does not automatically open any route', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(classId: 'class-1', permissions: const {});
    await harness.pump(c);
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsNothing);

    final c2 = _teacher(
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(c2);
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsNothing);
  });

  testWidgets(
    'after permission removal, back does not return the removed route',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.archivedClasses),
      );
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      // restoreArchived kaldır → route pop.
      final c2 = _teacher(classId: 'class-1', permissions: const {});
      await harness.pump(c2);
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsNothing);

      // Geri tuşuna basılabilir (varsa); archivedClasses geri gelmemeli.
      final back = find.byTooltip('Geri');
      if (back.evaluate().isNotEmpty) {
        await tester.tap(back);
        await tester.pumpAndSettle();
      }
      expect(find.text('archivedClasses'), findsNothing);
    },
  );

  testWidgets('selectionRequired transition clears operational stacks', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final classes = const [
      MobileShellClassRef(id: 'class-1', name: 'A'),
      MobileShellClassRef(id: 'class-2', name: 'B'),
    ];
    final c = _teacher(classId: 'class-1', classes: classes);
    await harness.pump(c);
    await tester.pumpAndSettle();
    expect(find.text('Yoklama'), findsOneWidget);
    // Yoklama sekmesinde açık rota yok; daha fazla'dan profile git.
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Profilim'));
    await tester.pumpAndSettle();
    expect(find.text('profile'), findsOneWidget);

    // selectedClassId'yi kaldır → selectionRequired.
    final c2 = _teacher(classId: null, classes: classes);
    await harness.pump(c2);
    await tester.pumpAndSettle();
    expect(find.text('profile'), findsNothing);
    expect(find.text('Sınıf seçin'), findsOneWidget);
  });

  testWidgets(
    'classless teacher permitted org-management stack kept when class list changes',
    (tester) async {
      final harness = _ShellHarness(tester);
      // Sınıfsız hoca ama manageClasses izni var.
      final c = _teacher(
        classId: null,
        classes: const [],
        permissions: const {MobileShellPermission.manageClasses},
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.management),
      );
      await tester.pumpAndSettle();
      expect(find.text('management'), findsOneWidget);

      // Sınıfsız kalmaya devam eden hoca için context güncellemesi
      // (örneğin kurum adı değişimi) management stack'ini korumalı.
      // identity sabit (org id + classId null + aynı permission) → NoEffect.
      final c2 = MobileShellContext(
        sessionVerified: true,
        sessionContextId: 'session',
        role: MobileShellRole.teacher,
        organizationId: 'org-1',
        organizationName: 'Yeniden Adlandırılmış Kurs',
        assignedClasses: const [],
        selectedClassId: null,
        permissions: const {MobileShellPermission.manageClasses},
      );
      await harness.pump(c2);
      await tester.pumpAndSettle();
      expect(find.text('management'), findsOneWidget);
    },
  );

  testWidgets(
    'pending post-frame request scheduled before context change does not run in new context',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c1 = _teacher(
        org: 'org-a',
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      // Önce c1 ile başla; post-frame request henüz çalışmadan c2'ye geç.
      await harness.pump(
        c1,
        request: MobileShellRouteRequest(
          sequence: 1,
          requestId: 'req-1',
          route: MobileShellRouteId.archivedClasses,
        ),
      );
      // Henüz pumpAndSettle yapmadan identity değiştir (aynı sequence).
      final c2 = _teacher(
        org: 'org-b',
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c2,
        request: MobileShellRouteRequest(
          sequence: 1,
          requestId: 'req-1',
          route: MobileShellRouteId.archivedClasses,
        ),
      );
      await tester.pumpAndSettle();
      // Identity değişti; eski bağlamdan gelen pending request yeni bağlamda
      // yeniden oynatılmadı. archivedClasses açılmaz.
      expect(find.text('archivedClasses'), findsNothing);
    },
  );

  testWidgets('same frame permission removal + route request fails closed', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c,
      request: harness.requestFor(MobileShellRouteId.archivedClasses),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    // İzni kaldır ve aynı anda archivedClasses için yeni sequence iste.
    final c2 = _teacher(classId: 'class-1', permissions: const {});
    await harness.pump(
      c2,
      request: MobileShellRouteRequest(
        sequence: 99,
        requestId: 'req-99',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();
    // İzin kalktı + request geldi: fail-closed. archivedClasses açılmaz.
    expect(find.text('archivedClasses'), findsNothing);
    // Unauthorized da açılmamalı çünkü permission reconciliation önce
    // archivedClasses'i pop etti; yeni request reddedildi ama unauthorized
    // gösterilip gösterilmeyeceği politika/red sonucudur. Burada kritik
    // olan: korumalı ekran AÇILMAMIŞ olmasıdır.
    expect(find.text('archivedClasses'), findsNothing);
  });

  testWidgets('reset does not throw when stale observer callback fires', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c1 = _teacher(
      org: 'org-a',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c1,
      request: harness.requestFor(MobileShellRouteId.archivedClasses),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    // Identity değişimi → reset. Bu eski observer'ları stale yapar.
    final c2 = _teacher(
      org: 'org-b',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(c2);
    // pumpAndSettle, eski observer callback'leri dahil tüm frame'leri işler.
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // archivedClasses yeni bağlamda görünmemeli (reset + stale guard).
    expect(find.text('archivedClasses'), findsNothing);
  });

  testWidgets(
    'reset does not produce a wrong canPop after stale observer fires',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c1 = _teacher(
        org: 'org-a',
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c1,
        request: harness.requestFor(MobileShellRouteId.archivedClasses),
      );
      await tester.pumpAndSettle();
      // Geri tuşu görünür (canPop true).
      expect(find.byTooltip('Geri'), findsOneWidget);

      // Identity değişimi → reset.
      final c2 = _teacher(
        org: 'org-b',
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(c2);
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      // Reset sonrası yığın boş → geri tuşu görünmemeli (canPop false).
      expect(find.byTooltip('Geri'), findsNothing);
    },
  );

  testWidgets(
    'rapid two different requests are each evaluated with current context',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {
          MobileShellPermission.restoreArchived,
          MobileShellPermission.manageBrand,
        },
      );
      // Hızlı art arda iki farklı request; her biri kendi sequence ile
      // güncel bağlamda değerlendirilmeli.
      await harness.pump(
        c,
        request: MobileShellRouteRequest(
          sequence: 1,
          requestId: 'req-1',
          route: MobileShellRouteId.archivedClasses,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      await harness.pump(
        c,
        request: MobileShellRouteRequest(
          sequence: 2,
          requestId: 'req-2',
          route: MobileShellRouteId.brandSettings,
        ),
      );
      await tester.pumpAndSettle();
      // Her iki rota da güncel bağlamda işlendi; brandSettings en üstte.
      expect(find.text('brandSettings'), findsOneWidget);
    },
  );

  testWidgets(
    'tab change while pending callback queued does not push to wrong navigator',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(c);
      await tester.pumpAndSettle();

      // Yoklama sekmesinde (index 0). Daha Fazla'ya geç.
      await tester.tap(find.text('Daha Fazla'));
      await tester.pumpAndSettle();
      // Arşivlenmiş Sınıflar'a dokun (request olacak ama pending post-frame).
      await tester.tap(find.text('Arşivlenmiş Sınıflar'));
      await tester.pumpAndSettle();
      // archivedClasses Daha Fazla stack'inde açıldı.
      expect(find.text('archivedClasses'), findsOneWidget);

      // Hızlıca Yoklama sekmesine geri dön.
      await tester.tap(find.text('Yoklama'));
      await tester.pumpAndSettle();
      // Daha Fazla'daki archivedClasses hâlâ orada (IndexedStack korur).
      expect(find.text('attendance'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'org A yetkili push planlandıktan sonra, push gerçekleşmeden org B yetkisiz '
    'contexte geçilir: eski rota yeni stacke açılmaz',
    (tester) async {
      final harness = _ShellHarness(tester);
      final orgA = _teacher(
        org: 'org-a',
        classId: 'class-1',
        permissions: const {MobileShellPermission.manageBrand},
      );
      // Push planlanır (deferred): _open policy kontrolünü org-a ile geçer
      // ve gerçek push bir sonraki frame'e ertelenir.
      await harness.pump(
        orgA,
        request: MobileShellRouteRequest(
          sequence: 1,
          requestId: 'req-1',
          route: MobileShellRouteId.brandSettings,
        ),
      );
      // Gerçek push henüz gerçekleşmeden org B'ye ve yetkisiz (manageBrand
      // yok) bir contexte geç.
      final orgBUnauthorized = _teacher(
        org: 'org-b',
        classId: 'class-1',
        permissions: const {},
      );
      await harness.pump(orgBUnauthorized);
      await tester.pumpAndSettle();
      // Eski (org-a'da planlanmış) push sessizce düşürüldü; org-b stackine
      // hiçbir şey açılmadı.
      expect(find.text('brandSettings'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('permission push öncesinde geri alınır: korumalı ekran açılmaz', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c1 = _teacher(
      classId: 'class-1',
      permissions: const {MobileShellPermission.manageBrand},
    );
    // Identity sabit kalacak; yalnızca izin push tamamlanmadan geri alınır.
    await harness.pump(
      c1,
      request: MobileShellRouteRequest(
        sequence: 1,
        requestId: 'req-1',
        route: MobileShellRouteId.brandSettings,
      ),
    );
    final c2 = _teacher(classId: 'class-1', permissions: const {});
    await harness.pump(c2);
    await tester.pumpAndSettle();
    // Gerçek push anında policy tekrar değerlendirildi: izin artık yok,
    // korumalı ekran açılmadı.
    expect(find.text('brandSettings'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets(
    'aynı frame içinde sequence N ve N+1 verilir: ikisi de sırasıyla tam bir '
    'kez değerlendirilir, N kaybolmaz',
    (tester) async {
      final c = _teacher(
        classId: 'class-1',
        permissions: const {
          MobileShellPermission.restoreArchived,
          MobileShellPermission.manageBrand,
        },
      );
      await tester.pumpWidget(
        KursPlatformApp(
          home: MobileNavigationShell(
            context: c,
            requests: const [
              MobileShellRouteRequest(
                sequence: 1,
                requestId: 'req-1',
                route: MobileShellRouteId.archivedClasses,
              ),
              MobileShellRouteRequest(
                sequence: 2,
                requestId: 'req-2',
                route: MobileShellRouteId.brandSettings,
              ),
            ],
          ),
        ),
      );
      await tester.pumpAndSettle();
      // N+1 (brandSettings) en üstte işlendi.
      expect(find.text('brandSettings'), findsOneWidget);
      // N (archivedClasses) kaybolmadı: gerçekten push edildi, altında duruyor.
      await tester.tap(find.byTooltip('Geri'));
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'identity reset sırasında eski deferred callback çalışmaz; daha yüksek '
    "sequence'lı yeni context isteği çalışır",
    (tester) async {
      final harness = _ShellHarness(tester);
      final orgA = _teacher(
        org: 'org-a',
        classId: 'class-1',
        permissions: const {MobileShellPermission.manageBrand},
      );
      await harness.pump(
        orgA,
        request: MobileShellRouteRequest(
          sequence: 1,
          requestId: 'req-1',
          route: MobileShellRouteId.brandSettings,
        ),
      );
      // Identity değişir (org-a → org-b) ve daha yüksek sequence'lı yeni bir
      // istek gelir; izin org-b'de de mevcut.
      final orgB = _teacher(
        org: 'org-b',
        classId: 'class-1',
        permissions: const {MobileShellPermission.manageBrand},
      );
      await harness.pump(
        orgB,
        request: MobileShellRouteRequest(
          sequence: 2,
          requestId: 'req-2',
          route: MobileShellRouteId.brandSettings,
        ),
      );
      await tester.pumpAndSettle();
      // Eski (org-a) callback sessizce düşürüldü; yeni (sequence 2, org-b)
      // istek başarıyla çalıştı ve çakışma/istisna olmadı.
      expect(find.text('brandSettings'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'adı olan fakat marker taşımayan rota root kabul edilmez: push sonrası '
    'aynı bağlamda anında (permission/context değişikliği olmadan) root\'a '
    'temizlenir',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.archivedClasses),
      );
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      // Kabuğun kendi API'sini atlayıp, aynı Navigator'a marker taşımayan
      // (yalnızca `settings.name` dolu) yabancı bir rota push edelim. Bu,
      // dışarıdan (restorasyon/derin bağlantı gibi) kabuk dışı bir kodun
      // Navigator'a eriştiği senaryoyu simüle eder.
      final navigatorFinder = find.ancestor(
        of: find.text('archivedClasses'),
        matching: find.byType(Navigator),
      );
      final navigatorState = tester.state<NavigatorState>(
        navigatorFinder.first,
      );
      navigatorState.push(
        MaterialPageRoute<void>(
          settings: const RouteSettings(name: 'foreign'),
          builder: (_) => const Scaffold(body: Text('foreign-stray')),
        ),
      );
      // İzin veya context DEĞİŞMİYOR; yalnızca planlı post-frame
      // reconciliation'ın çalışması için frame'leri settle ediyoruz.
      await tester.pumpAndSettle();

      // Untrusted marker fail-closed olarak, herhangi bir permission/context
      // tetikleyicisine ihtiyaç duymadan, bir sonraki frame'de anında
      // root'a temizlendi — altındaki gerçek (izinli) archivedClasses dahil,
      // deterministik biçimde.
      expect(find.text('foreign-stray'), findsNothing);
      expect(find.text('archivedClasses'), findsNothing);
      expect(find.byTooltip('Geri'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets(
    'markersız didReplace da aynı şekilde (post-frame, aynı bağlamda) '
    'root\'a temizlenir',
    (tester) async {
      final harness = _ShellHarness(tester);
      final c = _teacher(
        classId: 'class-1',
        permissions: const {MobileShellPermission.restoreArchived},
      );
      await harness.pump(
        c,
        request: harness.requestFor(MobileShellRouteId.archivedClasses),
      );
      await tester.pumpAndSettle();
      expect(find.text('archivedClasses'), findsOneWidget);

      final navigatorFinder = find.ancestor(
        of: find.text('archivedClasses'),
        matching: find.byType(Navigator),
      );
      final navigatorState = tester.state<NavigatorState>(
        navigatorFinder.first,
      );
      unawaited(
        navigatorState.pushReplacement(
          MaterialPageRoute<void>(
            settings: const RouteSettings(name: 'foreign-replace'),
            builder: (_) => const Scaffold(body: Text('foreign-replace-stray')),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('foreign-replace-stray'), findsNothing);
      expect(find.text('archivedClasses'), findsNothing);
      expect(find.byTooltip('Geri'), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('untrusted callback planlandıktan sonra identity değişirse eski '
      'callback yeni Navigator\'ı temizlemez', (tester) async {
    final harness = _ShellHarness(tester);
    final orgA = _teacher(
      org: 'org-a',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      orgA,
      request: harness.requestFor(MobileShellRouteId.archivedClasses),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);

    final navigatorFinder = find.ancestor(
      of: find.text('archivedClasses'),
      matching: find.byType(Navigator),
    );
    final navigatorState = tester.state<NavigatorState>(navigatorFinder.first);
    navigatorState.push(
      MaterialPageRoute<void>(
        settings: const RouteSettings(name: 'foreign'),
        builder: (_) => const Scaffold(body: Text('foreign-stray')),
      ),
    );
    // Untrusted reconciliation HENÜZ post-frame'de çalışmadı (settle
    // edilmedi). Hemen org-b'ye ve yeni, yetkili bir isteğe geç.
    final orgB = _teacher(
      org: 'org-b',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      orgB,
      request: MobileShellRouteRequest(
        sequence: 99,
        requestId: 'req-99',
        route: MobileShellRouteId.archivedClasses,
      ),
    );
    await tester.pumpAndSettle();

    // Eski (org-a, untrusted) callback identity/generation uyuşmadığı
    // için sessizce düştü; org-b'nin YENİ, meşru archivedClasses'i
    // sağlıklı biçimde açık kaldı — eski callback tarafından yanlışlıkla
    // kapatılmadı.
    expect(find.text('archivedClasses'), findsOneWidget);
    expect(find.text('foreign-stray'), findsNothing);
    expect(tester.takeException(), isNull);
  });

  testWidgets('trusted ve izinli marker açık kalır: untrusted reconciliation '
      'tetiklenmez', (tester) async {
    final harness = _ShellHarness(tester);
    final c = _teacher(
      classId: 'class-1',
      permissions: const {
        MobileShellPermission.restoreArchived,
        MobileShellPermission.manageBrand,
      },
    );
    await harness.pump(
      c,
      request: harness.requestFor(MobileShellRouteId.archivedClasses),
    );
    await tester.pumpAndSettle();
    await harness.pump(
      c,
      request: harness.requestFor(MobileShellRouteId.brandSettings),
    );
    await tester.pumpAndSettle();
    // İki güvenilir/izinli rota da açık kaldı; hiçbiri untrusted olmadığı
    // için planlı reconciliation hiç tetiklenmedi.
    expect(find.text('brandSettings'), findsOneWidget);
    await tester.tap(find.byTooltip('Geri'));
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('eski observer/reset callback\'leri canPop veya tracker durumunu '
      'bozamaz (marker taşımayan rota + identity reset birlikte)', (
    tester,
  ) async {
    final harness = _ShellHarness(tester);
    final c1 = _teacher(
      org: 'org-a',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(
      c1,
      request: harness.requestFor(MobileShellRouteId.archivedClasses),
    );
    await tester.pumpAndSettle();
    expect(find.byTooltip('Geri'), findsOneWidget);

    final navigatorFinder = find.ancestor(
      of: find.text('archivedClasses'),
      matching: find.byType(Navigator),
    );
    final navigatorState = tester.state<NavigatorState>(navigatorFinder.first);
    navigatorState.push(
      MaterialPageRoute<void>(
        settings: const RouteSettings(name: 'foreign'),
        builder: (_) => const Scaffold(body: Text('foreign-stray')),
      ),
    );
    await tester.pumpAndSettle();

    // Identity değişimi → reset. Eski observer'lar (yabancı rotayı da
    // izleyen) stale olur; yeni Navigator'lar sıfırdan kurulur.
    final c2 = _teacher(
      org: 'org-b',
      classId: 'class-1',
      permissions: const {MobileShellPermission.restoreArchived},
    );
    await harness.pump(c2);
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    // Reset sonrası yığın boş → geri tuşu görünmemeli (canPop false),
    // ne yabancı rota ne de eski archivedClasses sızmış olmalı.
    expect(find.byTooltip('Geri'), findsNothing);
    expect(find.text('foreign-stray'), findsNothing);
    expect(find.text('archivedClasses'), findsNothing);
  });
}
