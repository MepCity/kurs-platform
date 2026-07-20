import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/bootstrap/presentation/mobile_navigation_shell.dart';

const _class = MobileShellClassRef(id: 'class-1', name: 'Sınıf');

MobileShellContext _context({
  required MobileShellRole role,
  bool support = false,
  Set<MobileShellPermission> permissions = const {},
  bool withClass = true,
}) => MobileShellContext(
  sessionVerified: true,
  sessionContextId: 'session-$role-$support',
  role: role,
  organizationId: role == MobileShellRole.platformAdministrator && !support
      ? null
      : 'org-1',
  organizationName: role == MobileShellRole.platformAdministrator && !support
      ? null
      : 'Kurs',
  supportMode: support,
  supportTargetOrganizationId: support ? 'org-1' : null,
  assignedClasses: withClass ? const <MobileShellClassRef>[_class] : const [],
  permissions: permissions,
);

void main() {
  group('MobileShellRoutePolicyCatalog', () {
    test('covers every MobileShellRouteId exhaustively', () {
      expect(MobileShellRoutePolicyCatalog.isExhaustive, isTrue);
      for (final route in MobileShellRouteId.values) {
        expect(
          MobileShellRoutePolicyCatalog.allows(
            _context(role: MobileShellRole.teacher),
            route,
          ),
          isA<bool>(),
        );
      }
    });

    test(
      'table driven organization routes use admin default and teacher permissions',
      () {
        const policies = <(MobileShellRouteId, MobileShellPermission, bool)>[
          (
            MobileShellRouteId.brandSettings,
            MobileShellPermission.manageBrand,
            false,
          ),
          (
            MobileShellRouteId.enabledModules,
            MobileShellPermission.manageModules,
            false,
          ),
          (
            MobileShellRouteId.attendanceStatuses,
            MobileShellPermission.manageAttendanceStatuses,
            false,
          ),
          (
            MobileShellRouteId.archivedClasses,
            MobileShellPermission.restoreArchived,
            false,
          ),
          (
            MobileShellRouteId.archivedStudents,
            MobileShellPermission.restoreArchived,
            true,
          ),
          (
            MobileShellRouteId.reportExport,
            MobileShellPermission.exportReports,
            false,
          ),
          (
            MobileShellRouteId.auditLog,
            MobileShellPermission.viewAuditLog,
            false,
          ),
          (MobileShellRouteId.undo, MobileShellPermission.undo, false),
        ];
        for (final policy in policies) {
          expect(
            MobileShellRoutePolicyCatalog.allows(
              _context(
                role: MobileShellRole.organizationAdministrator,
                withClass: policy.$3,
              ),
              policy.$1,
            ),
            isTrue,
            reason: '${policy.$1} admin',
          );
          expect(
            MobileShellRoutePolicyCatalog.allows(
              _context(role: MobileShellRole.teacher, withClass: !policy.$3),
              policy.$1,
            ),
            isFalse,
            reason: '${policy.$1} teacher deny',
          );
          expect(
            MobileShellRoutePolicyCatalog.allows(
              _context(role: MobileShellRole.teacher, permissions: {policy.$2}),
              policy.$1,
            ),
            isTrue,
            reason: '${policy.$1} teacher permission',
          );
        }
      },
    );

    test(
      'management and staff policies preserve independent teacher grants',
      () {
        expect(
          MobileShellRoutePolicyCatalog.allows(
            _context(
              role: MobileShellRole.teacher,
              permissions: {MobileShellPermission.manageClasses},
            ),
            MobileShellRouteId.management,
          ),
          isTrue,
        );
        expect(
          MobileShellRoutePolicyCatalog.allows(
            _context(role: MobileShellRole.teacher),
            MobileShellRouteId.staff,
          ),
          isFalse,
        );
        for (final permission in const [
          MobileShellPermission.createOrCloseTeacher,
          MobileShellPermission.assignTeacherClasses,
          MobileShellPermission.viewTeacherPermissions,
          MobileShellPermission.revokeOtherDeviceSessions,
        ]) {
          expect(
            MobileShellRoutePolicyCatalog.allows(
              _context(
                role: MobileShellRole.teacher,
                permissions: {permission},
              ),
              MobileShellRouteId.staff,
            ),
            isTrue,
          );
        }
      },
    );

    test('isolates global, organization and support route families', () {
      final global = _context(role: MobileShellRole.platformAdministrator);
      final organization = _context(
        role: MobileShellRole.organizationAdministrator,
      );
      final support = _context(
        role: MobileShellRole.platformAdministrator,
        support: true,
      );
      expect(
        MobileShellRoutePolicyCatalog.allows(
          organization,
          MobileShellRouteId.platformOrganizations,
        ),
        isFalse,
      );
      expect(
        MobileShellRoutePolicyCatalog.allows(
          support,
          MobileShellRouteId.platformAudit,
        ),
        isFalse,
      );
      expect(
        MobileShellRoutePolicyCatalog.allows(
          global,
          MobileShellRouteId.brandSettings,
        ),
        isFalse,
      );
    });

    test('resolves profile stack by context', () {
      final global = _context(role: MobileShellRole.platformAdministrator);
      final organization = _context(
        role: MobileShellRole.organizationAdministrator,
      );
      for (final route in const [
        MobileShellRouteId.profile,
        MobileShellRouteId.deviceSessions,
        MobileShellRouteId.syncStatus,
      ]) {
        expect(
          MobileShellRoutePolicyCatalog.targetFor(global, route),
          MobileShellRouteId.profile,
        );
        expect(
          MobileShellRoutePolicyCatalog.targetFor(organization, route),
          MobileShellRouteId.more,
        );
      }
    });
  });
}
