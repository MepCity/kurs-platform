import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/core/presentation/widgets/app_sync_indicator.dart';
import 'package:kurs_platform_mobile/features/auth/data/unavailable_authentication_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/kurs_platform_app.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/mobile_navigation_shell.dart';

MobileShellContext teacher({
  List<MobileShellClassRef> classes = const [],
  String? selected,
  Set<MobileShellPermission> permissions = const {},
}) => MobileShellContext(
  sessionVerified: true,
  sessionContextId: 'session',
  role: MobileShellRole.teacher,
  organizationId: 'org',
  organizationName: 'Bereket',
  assignedClasses: classes,
  selectedClassId: selected,
  permissions: permissions,
);

MobileShellContext organizationAdministrator({
  Set<MobileShellPermission> permissions = const {},
}) => MobileShellContext(
  sessionVerified: true,
  sessionContextId: 'organization-admin',
  role: MobileShellRole.organizationAdministrator,
  organizationId: 'org',
  organizationName: 'Bereket',
  permissions: permissions,
);

MobileShellContext platformAdministrator({bool support = false}) =>
    MobileShellContext(
      sessionVerified: true,
      sessionContextId: 'platform-$support',
      role: MobileShellRole.platformAdministrator,
      organizationId: support ? 'org' : null,
      organizationName: support ? 'Bereket' : null,
      supportMode: support,
      supportTargetOrganizationId: support ? 'org' : null,
    );
Widget app(
  MobileShellContext context, {
  MobileShellRouteId? route,
  int sequence = 1,
  ValueChanged<MobileShellAction>? onAction,
  ValueChanged<MobileShellActionRequest>? onActionRequest,
}) => KursPlatformApp(
  authenticationRepository: const UnavailableAuthenticationRepository(),
  home: MobileNavigationShell(
    context: context,
    requests: route == null
        ? const []
        : [
            MobileShellRouteRequest(
              sequence: sequence,
              requestId: '$sequence:${route.name}:${context.permissions}',
              route: route,
            ),
          ],
    onAction: onAction,
    onActionRequest: onActionRequest,
  ),
);

void main() {
  testWidgets('fails closed for invalid support combinations', (tester) async {
    for (final context in [
      teacher().copyWithSupportForTest(),
      MobileShellContext(
        sessionVerified: true,
        sessionContextId: 's',
        role: MobileShellRole.organizationAdministrator,
        organizationId: 'org',
        organizationName: 'Kurs',
        supportMode: true,
        supportTargetOrganizationId: 'org',
      ),
      MobileShellContext(
        sessionVerified: true,
        sessionContextId: 's',
        role: MobileShellRole.platformAdministrator,
        organizationId: 'org',
        organizationName: 'Kurs',
        supportMode: true,
      ),
    ]) {
      await tester.pumpWidget(app(context));
      expect(find.text('Geçersiz veya doğrulanmamış bağlam.'), findsOneWidget);
    }
  });

  testWidgets('valid support context opens administrator shell and badge', (
    tester,
  ) async {
    final c = MobileShellContext(
      sessionVerified: true,
      sessionContextId: 's',
      role: MobileShellRole.platformAdministrator,
      organizationId: 'org',
      organizationName: 'Kurs',
      supportMode: true,
      supportTargetOrganizationId: 'org',
    );
    await tester.pumpWidget(app(c));
    expect(find.text('DESTEK'), findsOneWidget);
    expect(find.text('Yönetim'), findsOneWidget);
  });

  testWidgets(
    'teacher class selection uses stable IDs and auto-selects one class',
    (tester) async {
      await tester.pumpWidget(
        app(
          teacher(
            classes: const [MobileShellClassRef(id: 'a', name: 'A')],
          ),
        ),
      );
      expect(find.text('Yoklama'), findsOneWidget);
      await tester.pumpWidget(
        app(
          teacher(
            classes: const [
              MobileShellClassRef(id: 'a', name: 'A'),
              MobileShellClassRef(id: 'b', name: 'A'),
            ],
          ),
        ),
      );
      expect(find.text('Yoklama'), findsNothing);
      await tester.pumpWidget(
        app(
          teacher(
            classes: const [
              MobileShellClassRef(id: 'a', name: 'Yeni ad'),
              MobileShellClassRef(id: 'b', name: 'A'),
            ],
            selected: 'a',
          ),
        ),
      );
      expect(find.text('Yoklama'), findsOneWidget);
    },
  );

  testWidgets('destination changes from one to four and back do not throw', (
    tester,
  ) async {
    await tester.pumpWidget(app(teacher()));
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [MobileShellClassRef(id: 'a', name: 'A')],
        ),
      ),
    );
    await tester.pumpWidget(app(teacher()));
    expect(tester.takeException(), isNull);
  });

  testWidgets('more menu pushes route and app bar pops it', (tester) async {
    await tester.pumpWidget(
      app(teacher(permissions: const {MobileShellPermission.restoreArchived})),
    );
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Arşivlenmiş Sınıflar'));
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);
    await tester.tap(find.byTooltip('Geri'));
    await tester.pumpAndSettle();
    expect(find.text('Arşivlenmiş Sınıflar'), findsOneWidget);
  });

  testWidgets('direct routes are guarded and restore remains independent', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(teacher(), route: MobileShellRouteId.archivedClasses, sequence: 1),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
    await tester.pumpWidget(
      app(
        teacher(permissions: const {MobileShellPermission.restoreArchived}),
        route: MobileShellRouteId.archivedClasses,
        sequence: 2,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('archivedClasses'), findsOneWidget);
    await tester.pumpWidget(
      app(
        teacher(permissions: const {MobileShellPermission.manageClasses}),
        route: MobileShellRouteId.archivedClasses,
        sequence: 3,
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
  });

  testWidgets(
    'staff permissions are independent and settings entries stay separate',
    (tester) async {
      await tester.pumpWidget(
        app(
          teacher(
            permissions: const {
              MobileShellPermission.assignTeacherClasses,
              MobileShellPermission.manageBrand,
            },
          ),
        ),
      );
      await tester.tap(find.text('Daha Fazla'));
      await tester.pumpAndSettle();
      expect(find.text('Personel / Hoca Listesi'), findsOneWidget);
      await tester.tap(find.text('Kurum Ayarları'));
      await tester.pumpAndSettle();
      expect(find.text('Marka Ayarları'), findsOneWidget);
      expect(find.text('Etkin Modüller'), findsNothing);
    },
  );

  testWidgets('profile menu and failed sync action are typed surfaces', (
    tester,
  ) async {
    MobileShellAction? action;
    final c = MobileShellContext(
      sessionVerified: true,
      sessionContextId: 's',
      role: MobileShellRole.organizationAdministrator,
      organizationId: 'o',
      organizationName: 'K',
      availableContextCount: 2,
      availableRoleCount: 2,
      syncStatus: AppSyncStatus.failed,
    );
    await tester.pumpWidget(app(c, onAction: (value) => action = value));
    await tester.tap(find.byTooltip('Profil'));
    await tester.pumpAndSettle();
    expect(find.text('Kurum/Bağlam Değiştir'), findsOneWidget);
    await tester.tap(find.text('Rol Değiştir'));
    expect(action, MobileShellAction.changeRole);
    await tester.pumpWidget(app(c, route: MobileShellRouteId.syncStatus));
    await tester.pumpAndSettle();
    expect(find.text('syncStatus'), findsOneWidget);
  });

  testWidgets('organization management root and more menu are separated', (
    tester,
  ) async {
    await tester.pumpWidget(app(organizationAdministrator()));
    await tester.tap(find.text('Yönetim'));
    await tester.pumpAndSettle();
    for (final label in const [
      'Sınıf Yönetimi',
      'Dönem ve Takvim',
      'Arşivlenmiş Sınıflar',
      'Marka Ayarları',
      'Etkin Modüller',
      'Yoklama Durumları',
      'Personel / Hoca Listesi',
    ]) {
      expect(find.text(label), findsOneWidget);
    }
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Sınıf Yönetimi'), findsNothing);
    expect(find.text('Marka Ayarları'), findsNothing);
    expect(find.text('Profilim'), findsOneWidget);
  });

  testWidgets('teacher settings expand independently and open allowed route', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(teacher(permissions: const {MobileShellPermission.manageBrand})),
    );
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Kurum Ayarları'), findsOneWidget);
    expect(find.text('Marka Ayarları'), findsNothing);
    await tester.tap(find.text('Kurum Ayarları'));
    await tester.pumpAndSettle();
    expect(find.text('Marka Ayarları'), findsOneWidget);
    expect(find.text('Etkin Modüller'), findsNothing);
    await tester.tap(find.text('Marka Ayarları'));
    await tester.pumpAndSettle();
    expect(find.text('brandSettings'), findsOneWidget);
  });

  testWidgets('each staff permission independently exposes and opens staff', (
    tester,
  ) async {
    for (final permission in const [
      MobileShellPermission.createOrCloseTeacher,
      MobileShellPermission.assignTeacherClasses,
      MobileShellPermission.viewTeacherPermissions,
      MobileShellPermission.revokeOtherDeviceSessions,
    ]) {
      await tester.pumpWidget(app(teacher(permissions: {permission})));
      await tester.tap(find.text('Daha Fazla'));
      await tester.pumpAndSettle();
      expect(find.text('Personel / Hoca Listesi'), findsOneWidget);
      await tester.tap(find.text('Personel / Hoca Listesi'));
      await tester.pumpAndSettle();
      expect(find.text('staff'), findsOneWidget);
    }
  });

  testWidgets('profile, session and logout rows produce typed outcomes', (
    tester,
  ) async {
    MobileShellAction? action;
    await tester.pumpWidget(
      app(organizationAdministrator(), onAction: (value) => action = value),
    );
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Profilim'), findsOneWidget);
    expect(find.text('Cihaz/Oturum Bilgisi'), findsOneWidget);
    expect(find.text('Çıkış Yap'), findsOneWidget);
    await tester.tap(find.text('Profilim'));
    await tester.pumpAndSettle();
    expect(find.text('profile'), findsOneWidget);
    await tester.tap(find.byTooltip('Geri'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Cihaz/Oturum Bilgisi'));
    await tester.pumpAndSettle();
    expect(find.text('deviceSessions'), findsOneWidget);
    await tester.tap(find.byTooltip('Geri'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Çıkış Yap'));
    expect(action, MobileShellAction.logout);
  });

  testWidgets(
    'global profile actions use profile stack while support uses org shell',
    (tester) async {
      await tester.pumpWidget(
        app(platformAdministrator(), route: MobileShellRouteId.syncStatus),
      );
      await tester.pumpAndSettle();
      expect(find.text('syncStatus'), findsOneWidget);
      expect(find.text('Daha Fazla'), findsNothing);
      await tester.pumpWidget(app(platformAdministrator(support: true)));
      await tester.tap(find.text('Daha Fazla'));
      await tester.pumpAndSettle();
      expect(find.text('Kurumlar'), findsNothing);
      expect(find.text('Profilim'), findsOneWidget);
    },
  );

  testWidgets('0 sınıfta boş durum görünür ve operasyonel sekmeler görünmez', (
    tester,
  ) async {
    await tester.pumpWidget(app(teacher()));
    for (final label in const ['Yoklama', 'Öğrenciler', 'Program']) {
      expect(find.text(label), findsNothing);
    }
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Henüz atanmış bir sınıfın yok'), findsOneWidget);
  });

  testWidgets('0 sınıfta Daha Fazla profil ve oturum erişimi korunur', (
    tester,
  ) async {
    await tester.pumpWidget(app(teacher()));
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Profilim'), findsOneWidget);
    expect(find.text('Cihaz/Oturum Bilgisi'), findsOneWidget);
    expect(find.text('Çıkış Yap'), findsOneWidget);
  });

  testWidgets('0 sınıfta kurum kapsamlı yönetim izni çalışır', (tester) async {
    await tester.pumpWidget(
      app(teacher(permissions: const {MobileShellPermission.manageClasses})),
    );
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Sınıf Yönetimi'));
    await tester.pumpAndSettle();
    expect(find.text('management'), findsOneWidget);
  });

  testWidgets('1 sınıf otomatik etkinleşir ve CLS-01 görünmez', (tester) async {
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [MobileShellClassRef(id: 'a', name: 'A')],
        ),
      ),
    );
    expect(find.text('Yoklama'), findsOneWidget);
    expect(find.textContaining('A'), findsOneWidget);
    expect(find.text('Sınıf Seç'), findsNothing);
    expect(find.byTooltip('Sınıf değiştir'), findsNothing);
  });

  testWidgets('2+ sınıf ve seçim yokken CLS-01 zorunlu görünür', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [
            MobileShellClassRef(id: 'a', name: 'A Sınıfı'),
            MobileShellClassRef(id: 'b', name: 'B Sınıfı'),
          ],
        ),
      ),
    );
    expect(find.text('Sınıf seçin'), findsOneWidget);
    expect(find.text('Yoklama'), findsNothing);
    expect(find.text('Daha Fazla'), findsOneWidget);
  });

  testWidgets('seçim yokken doğrudan operasyonel rotalar reddedilir', (
    tester,
  ) async {
    for (final route in const [
      MobileShellRouteId.attendance,
      MobileShellRouteId.students,
      MobileShellRouteId.program,
    ]) {
      await tester.pumpWidget(
        app(
          teacher(
            classes: const [
              MobileShellClassRef(id: 'a', name: 'A'),
              MobileShellClassRef(id: 'b', name: 'B'),
            ],
          ),
          route: route,
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Bu işlem için yetkiniz yok.'), findsOneWidget);
    }
  });

  testWidgets('sınıf seçimi classId taşıyan tipli action üretir', (
    tester,
  ) async {
    MobileShellActionRequest? action;
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [
            MobileShellClassRef(id: 'class-a', name: 'A'),
            MobileShellClassRef(id: 'class-b', name: 'B'),
          ],
        ),
        onActionRequest: (value) => action = value,
      ),
    );
    await tester.tap(find.text('B'));
    expect(action?.action, MobileShellAction.selectClass);
    expect(action?.classId, 'class-b');
  });

  testWidgets('geçerli seçimden sonra sekmeler ve sınıf değiştirme görünür', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(
        teacher(
          selected: 'b',
          classes: const [
            MobileShellClassRef(id: 'a', name: 'Uzun A'),
            MobileShellClassRef(id: 'b', name: 'Uzun B'),
          ],
        ),
      ),
    );
    expect(find.text('Yoklama'), findsOneWidget);
    expect(find.textContaining('Uzun B'), findsOneWidget);
    final control = find.byTooltip('Sınıf değiştir');
    expect(control, findsOneWidget);
    expect(tester.getSize(control).width, greaterThanOrEqualTo(48));
    expect(tester.getSize(control).height, greaterThanOrEqualTo(48));
    await tester.tap(control);
    await tester.pumpAndSettle();
    expect(find.text('Sınıf seçin'), findsOneWidget);
  });

  testWidgets('sınıf listesi değişimleri deterministik yüzeye geçer', (
    tester,
  ) async {
    const initial = [
      MobileShellClassRef(id: 'a', name: 'A'),
      MobileShellClassRef(id: 'b', name: 'B'),
    ];
    await tester.pumpWidget(app(teacher(classes: initial, selected: 'a')));
    await tester.pumpWidget(app(teacher()));
    expect(find.text('Yoklama'), findsNothing);
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Henüz atanmış bir sınıfın yok'), findsOneWidget);
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [MobileShellClassRef(id: 'b', name: 'Yeni B')],
        ),
      ),
    );
    expect(find.text('Yoklama'), findsOneWidget);
    expect(find.textContaining('Yeni B'), findsOneWidget);
    await tester.pumpWidget(
      app(
        teacher(
          classes: const [
            MobileShellClassRef(id: 'b', name: 'Yeni B'),
            MobileShellClassRef(id: 'c', name: 'C'),
          ],
        ),
      ),
    );
    expect(find.text('Sınıf seçin'), findsOneWidget);
  });

  test('yinelenen sınıf IDleri sınıf sayısını ve seçimi bozmaz', () {
    final state = MobileShellClassState.resolve(const [
      MobileShellClassRef(id: 'a', name: 'İlk ad'),
      MobileShellClassRef(id: 'a', name: 'Tekrar'),
      MobileShellClassRef(id: 'b', name: 'B'),
    ], 'a');
    expect(state.kind, MobileShellClassStateKind.classSelected);
    expect(state.classes, hasLength(2));
    expect(state.selectedClass?.name, 'İlk ad');
  });

  test('seçili sınıf kaldırılıp 2+ sınıf kalırsa seçim zorunlu olur', () {
    final state = MobileShellClassState.resolve(const [
      MobileShellClassRef(id: 'b', name: 'B'),
      MobileShellClassRef(id: 'c', name: 'C'),
    ], 'a');
    expect(state.kind, MobileShellClassStateKind.selectionRequired);
    expect(state.effectiveSelectedClassId, isNull);
  });

  test('ikinci sınıf eklenmesi ve yeniden adlandırma geçerli seçimi korur', () {
    final added = MobileShellClassState.resolve(const [
      MobileShellClassRef(id: 'a', name: 'A'),
      MobileShellClassRef(id: 'b', name: 'B'),
    ], 'a');
    final renamed = MobileShellClassState.resolve(const [
      MobileShellClassRef(id: 'a', name: 'Yeniden Adlandırılmış A'),
      MobileShellClassRef(id: 'b', name: 'B'),
    ], 'a');
    expect(added.effectiveSelectedClassId, 'a');
    expect(renamed.effectiveSelectedClassId, 'a');
    expect(renamed.selectedClass?.name, 'Yeniden Adlandırılmış A');
  });

  testWidgets('CLS-01 ve boş durum 320 dp landscape 200 yüzde taşmaz', (
    tester,
  ) async {
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(
          size: Size(568, 320),
          textScaler: TextScaler.linear(2),
        ),
        child: app(
          teacher(
            classes: const [
              MobileShellClassRef(
                id: 'a',
                name: 'Çok Uzun Türkçe Sınıf Adı ve Açıklamalı Grup',
              ),
              MobileShellClassRef(id: 'b', name: 'İkinci Uzun Sınıf Adı'),
            ],
          ),
        ),
      ),
    );
    expect(find.text('Sınıf seçin'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(
      MediaQuery(
        data: const MediaQueryData(
          size: Size(568, 320),
          textScaler: TextScaler.linear(2),
        ),
        child: app(teacher()),
      ),
    );
    await tester.tap(find.text('Daha Fazla'));
    await tester.pumpAndSettle();
    expect(find.text('Henüz atanmış bir sınıfın yok'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}

extension on MobileShellContext {
  MobileShellContext copyWithSupportForTest() => MobileShellContext(
    sessionVerified: true,
    sessionContextId: sessionContextId,
    role: role,
    organizationId: organizationId,
    organizationName: organizationName,
    supportMode: true,
    supportTargetOrganizationId: organizationId,
  );
}
