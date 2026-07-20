import 'organization_list_query.dart';
import 'organization_list_result.dart';

/// Port for the `GET /api/v1/organizations` (`GLOBAL` scope) surface.
///
/// `ORG-006` depends only on this abstraction; the concrete adapter (mock
/// today, real HTTP client once `ORG-003`/`ORG-004` land) is supplied by the
/// composition root. Implementations must throw
/// `OrganizationsFailure` for every error case in
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §8.4.
abstract class OrganizationsRepository {
  Future<OrganizationListResult> listOrganizations(OrganizationListQuery query);
}
