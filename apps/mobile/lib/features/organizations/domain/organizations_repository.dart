import 'organization.dart';
import 'organization_create_request.dart';
import 'organization_list_query.dart';
import 'organization_list_result.dart';

/// Port for the `GET /api/v1/organizations` and `POST /api/v1/organizations`
/// (`GLOBAL` scope) surfaces.
///
/// `ORG-006` and `ORG-007` depend only on this abstraction; the concrete
/// adapter (mock today, real HTTP client once `ORG-003`/`ORG-004` land) is
/// supplied by the composition root. Implementations must throw
/// `OrganizationsFailure` for every error case in
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §8.4 (list) and §7.4 (create).
abstract class OrganizationsRepository {
  Future<OrganizationListResult> listOrganizations(OrganizationListQuery query);

  /// `POST /api/v1/organizations` (§7). Returns the created organization
  /// (`status: ACTIVE`, `rowVersion: 1`) on success.
  ///
  /// Implementations must be idempotent on
  /// `[request.clientMutationId]`: a retry with the exact same field values
  /// returns the same organization instead of creating a duplicate; a retry
  /// with the same key but different field values throws
  /// `OrganizationsFailure(idempotencyKeyReused, ...)` (§5.2).
  Future<Organization> createOrganization(OrganizationCreateRequest request);
}
