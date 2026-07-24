import 'organization_brand.dart';

/// ORG-008'in ORGANIZATION scope marka/modül uçları için dar port.
abstract class OrganizationBrandRepository {
  Future<OrganizationBrand> getBrand(String organizationId);
  Future<OrganizationBrand> updateBrand(
    String organizationId,
    OrganizationBrand brand,
    String clientMutationId,
  );
  Future<OrganizationModules> getModules(String organizationId);
  Future<OrganizationModules> updateModules(
    String organizationId,
    OrganizationModules modules,
    String clientMutationId,
  );
}
