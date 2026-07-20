import 'package:flutter/material.dart';

import 'mobile_navigation_shell.dart';

/// Tipli hedef tanımı. Navigation shell yalnızca [MobileShellRouteId]'lere
/// bakarak karar verir; başlık veya widget tipine bakmaz.
class MobileShellDestination {
  const MobileShellDestination(
    this.route,
    this.label,
    this.icon,
    this.selectedIcon,
  );
  final MobileShellRouteId route;
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}

/// Tek bir rota için UI guard kuralı. API yetkilendirmesi sunucuda bağımsız
/// kalır; bu kural yalnızca menü/görünürlük kararı verir.
class MobileShellRouteRule {
  const MobileShellRouteRule({
    required this.destination,
    this.organizationRequired = false,
    this.classRequired = false,
    this.teacherClassRequired = false,
    this.teacherMultipleClassesRequired = false,
    this.roles,
    this.permission,
    this.anyPermissions = const <MobileShellPermission>{},
    this.allowSupport = false,
    this.globalOnly = false,
  });
  final MobileShellRouteId destination;
  final bool organizationRequired;
  final bool classRequired;
  final bool teacherClassRequired;
  final bool teacherMultipleClassesRequired;
  final Set<MobileShellRole>? roles;
  final MobileShellPermission? permission;
  final Set<MobileShellPermission> anyPermissions;
  final bool allowSupport;
  final bool globalOnly;
  bool allows(MobileShellContext context) =>
      context.isValid &&
      (!globalOnly ||
          (context.role == MobileShellRole.platformAdministrator &&
              !context.supportMode)) &&
      (!organizationRequired || context.organizationId != null) &&
      (!classRequired || context.effectiveSelectedClassId != null) &&
      (!teacherClassRequired ||
          context.role != MobileShellRole.teacher ||
          context.effectiveSelectedClassId != null) &&
      (!teacherMultipleClassesRequired ||
          context.role != MobileShellRole.teacher ||
          context.classState.hasMultipleClasses) &&
      (roles == null ||
          roles!.contains(context.role) ||
          (context.supportMode && allowSupport)) &&
      ((context.role == MobileShellRole.organizationAdministrator ||
              (context.supportMode && allowSupport)) ||
          permission == null ||
          context.has(permission!)) &&
      (anyPermissions.isEmpty ||
          context.role == MobileShellRole.organizationAdministrator ||
          (context.supportMode && allowSupport) ||
          anyPermissions.any(context.has));
}

const _rules = <MobileShellRouteId, MobileShellRouteRule>{
  MobileShellRouteId.platformOrganizations: MobileShellRouteRule(
    destination: MobileShellRouteId.platformOrganizations,
    roles: {MobileShellRole.platformAdministrator},
    globalOnly: true,
  ),
  MobileShellRouteId.platformAudit: MobileShellRouteRule(
    destination: MobileShellRouteId.platformAudit,
    roles: {MobileShellRole.platformAdministrator},
    globalOnly: true,
  ),
  MobileShellRouteId.platformReport: MobileShellRouteRule(
    destination: MobileShellRouteId.platformReport,
    roles: {MobileShellRole.platformAdministrator},
    globalOnly: true,
  ),
  MobileShellRouteId.home: MobileShellRouteRule(
    destination: MobileShellRouteId.home,
    organizationRequired: true,
    roles: {MobileShellRole.organizationAdministrator},
  ),
  MobileShellRouteId.classSelector: MobileShellRouteRule(
    destination: MobileShellRouteId.classSelector,
    organizationRequired: true,
    teacherMultipleClassesRequired: true,
  ),
  MobileShellRouteId.attendance: MobileShellRouteRule(
    destination: MobileShellRouteId.attendance,
    organizationRequired: true,
    classRequired: true,
    roles: {MobileShellRole.teacher},
  ),
  MobileShellRouteId.students: MobileShellRouteRule(
    destination: MobileShellRouteId.students,
    organizationRequired: true,
    teacherClassRequired: true,
  ),
  MobileShellRouteId.program: MobileShellRouteRule(
    destination: MobileShellRouteId.program,
    organizationRequired: true,
    classRequired: true,
    roles: {MobileShellRole.teacher},
  ),
  MobileShellRouteId.management: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    roles: {MobileShellRole.organizationAdministrator, MobileShellRole.teacher},
    permission: MobileShellPermission.manageClasses,
    allowSupport: true,
  ),
  MobileShellRouteId.termCalendar: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    permission: MobileShellPermission.manageTerm,
    allowSupport: true,
  ),
  MobileShellRouteId.archivedClasses: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    permission: MobileShellPermission.restoreArchived,
    allowSupport: true,
  ),
  MobileShellRouteId.archivedStudents: MobileShellRouteRule(
    destination: MobileShellRouteId.students,
    organizationRequired: true,
    classRequired: true,
    permission: MobileShellPermission.restoreArchived,
    allowSupport: true,
  ),
  MobileShellRouteId.brandSettings: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    permission: MobileShellPermission.manageBrand,
    allowSupport: true,
  ),
  MobileShellRouteId.enabledModules: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    permission: MobileShellPermission.manageModules,
    allowSupport: true,
  ),
  MobileShellRouteId.attendanceStatuses: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    permission: MobileShellPermission.manageAttendanceStatuses,
    allowSupport: true,
  ),
  MobileShellRouteId.staff: MobileShellRouteRule(
    destination: MobileShellRouteId.management,
    organizationRequired: true,
    anyPermissions: {
      MobileShellPermission.createOrCloseTeacher,
      MobileShellPermission.assignTeacherClasses,
      MobileShellPermission.viewTeacherPermissions,
      MobileShellPermission.revokeOtherDeviceSessions,
    },
    allowSupport: true,
  ),
  MobileShellRouteId.reportExport: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
    organizationRequired: true,
    permission: MobileShellPermission.exportReports,
    allowSupport: true,
  ),
  MobileShellRouteId.auditLog: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
    organizationRequired: true,
    permission: MobileShellPermission.viewAuditLog,
    allowSupport: true,
  ),
  MobileShellRouteId.undo: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
    organizationRequired: true,
    permission: MobileShellPermission.undo,
    allowSupport: true,
  ),
  MobileShellRouteId.profile: MobileShellRouteRule(
    destination: MobileShellRouteId.profile,
  ),
  MobileShellRouteId.deviceSessions: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
  ),
  MobileShellRouteId.more: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
  ),
  MobileShellRouteId.syncStatus: MobileShellRouteRule(
    destination: MobileShellRouteId.more,
  ),
};

/// Tipli, kapsamlı UI navigasyon politika kataloğu. Bu bir istemci taraflı
/// guard'dır; her arka plan API'si bağımsız sunucu yetkilendirmesi yapar.
class MobileShellRoutePolicyCatalog {
  const MobileShellRoutePolicyCatalog._();

  static bool allows(MobileShellContext context, MobileShellRouteId route) =>
      _rules[route]?.allows(context) ?? false;

  static MobileShellRouteId? targetFor(
    MobileShellContext context,
    MobileShellRouteId route,
  ) {
    final rule = _rules[route];
    if (rule == null || !rule.allows(context)) return null;
    final isGlobal =
        context.role == MobileShellRole.platformAdministrator &&
        !context.supportMode;
    if (route == MobileShellRouteId.profile ||
        route == MobileShellRouteId.deviceSessions ||
        route == MobileShellRouteId.syncStatus) {
      return isGlobal ? MobileShellRouteId.profile : MobileShellRouteId.more;
    }
    if (context.role == MobileShellRole.teacher &&
        {
          MobileShellRouteId.management,
          MobileShellRouteId.termCalendar,
          MobileShellRouteId.archivedClasses,
          MobileShellRouteId.brandSettings,
          MobileShellRouteId.enabledModules,
          MobileShellRouteId.attendanceStatuses,
          MobileShellRouteId.staff,
        }.contains(route)) {
      return MobileShellRouteId.more;
    }
    return rule.destination;
  }

  static bool get isExhaustive =>
      MobileShellRouteId.values.every(_rules.containsKey);
}
