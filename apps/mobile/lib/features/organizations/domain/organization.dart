import 'organization_status.dart';

/// Core organization record.
///
/// Field set matches `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §6.1. This
/// type carries only the fields the ORG-001 contract exposes; branding
/// (`ORG-002`) and module toggles are out of scope here.
class Organization {
  const Organization({
    required this.id,
    required this.name,
    required this.defaultTimezone,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
    required this.rowVersion,
    this.shortName,
  });

  /// Server-generated UUID.
  final String id;

  /// 1-200 characters, never blank.
  final String name;

  /// Optional 1-50 character short name.
  final String? shortName;

  /// IANA timezone identifier, e.g. `Europe/Istanbul`.
  final String defaultTimezone;

  final OrganizationStatus status;
  final DateTime createdAt;
  final DateTime updatedAt;

  /// Monotonically increasing optimistic-concurrency counter.
  final int rowVersion;
}
