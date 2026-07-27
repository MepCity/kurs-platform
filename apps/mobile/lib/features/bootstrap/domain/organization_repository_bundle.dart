import '../../../core/network/authenticated_api_session.dart';
import '../../organizations/domain/organization_brand_repository.dart';
import '../../organizations/domain/organizations_repository.dart';

class OrganizationRepositoryBundle {
  const OrganizationRepositoryBundle({
    required this.organizations,
    required this.brand,
  });

  final OrganizationsRepository organizations;
  final OrganizationBrandRepository brand;
}

typedef OrganizationRepositoryBuilder =
    OrganizationRepositoryBundle Function(
      AuthenticatedApiSession session,
      bool globalListScope,
    );
