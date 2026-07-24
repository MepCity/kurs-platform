import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/application/organization_brand_controller.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';

void main() {
  final org = Organization(
    id: 'org-1',
    name: 'Kurs',
    defaultTimezone: 'Europe/Istanbul',
    status: OrganizationStatus.active,
    createdAt: DateTime.utc(2026),
    updatedAt: DateTime.utc(2026),
    rowVersion: 1,
  );
  test(
    'loads and saves brand plus module settings with new row versions',
    () async {
      final repo = OrganizationsMockRepository(
        seed: [org],
        latency: Duration.zero,
      );
      final controller = OrganizationBrandController(
        organizationId: org.id,
        repository: repo,
      );
      await controller.load();
      expect(controller.status, OrganizationBrandStatus.ready);
      final saved = await controller.save(
        primary: '#1565C0',
        secondary: '#00796B',
        colors: const [
          OrganizationBrandColor(colorHex: '#FFC107', sortOrder: 0),
        ],
        modules: controller.modules!.items,
      );
      expect(saved, isTrue);
      expect(controller.brand!.primaryColor, '#1565C0');
      expect(controller.brand!.rowVersion, 2);
      expect(controller.modules!.rowVersion, 3);
    },
  );
}
