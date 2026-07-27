import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/application/organization_brand_controller.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';

Organization organization(String id) => Organization(
  id: id,
  name: 'Kurs $id',
  defaultTimezone: 'Europe/Istanbul',
  status: OrganizationStatus.active,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
  rowVersion: 1,
);

List<OrganizationBrandColor> palette(String hex) => <OrganizationBrandColor>[
  OrganizationBrandColor(colorHex: hex, sortOrder: 0),
];

List<OrganizationModule> toggledModules(OrganizationModules value) => value
    .items
    .map(
      (item) => item.code == OrganizationModuleCode.att
          ? item.copyWith(isEnabled: !item.isEnabled)
          : item,
    )
    .toList();

const _org1Support = OrganizationsMockSession.platformSupport(
  actorUserId: 'support-org-1',
  organizationId: 'org-1',
);

void expectSharedVersion(OrganizationBrandController controller, int version) {
  expect(controller.brand?.rowVersion, version);
  expect(controller.colors?.rowVersion, version);
  expect(controller.modules?.rowVersion, version);
}

class _CountingRepository implements OrganizationBrandRepository {
  _CountingRepository(this.delegate);

  final OrganizationBrandRepository delegate;
  int getBrandCalls = 0;
  int getColorsCalls = 0;
  int getModulesCalls = 0;
  int updateBrandCalls = 0;
  Completer<void>? brandGate;

  @override
  Future<OrganizationBrand> getBrand(String organizationId) {
    getBrandCalls++;
    return delegate.getBrand(organizationId);
  }

  @override
  Future<OrganizationBrandColors> getBrandColors(String organizationId) {
    getColorsCalls++;
    return delegate.getBrandColors(organizationId);
  }

  @override
  Future<OrganizationModules> getModules(String organizationId) {
    getModulesCalls++;
    return delegate.getModules(organizationId);
  }

  @override
  Future<OrganizationBrand> updateBrand(
    String organizationId,
    OrganizationBrand brand,
    String clientMutationId,
  ) async {
    updateBrandCalls++;
    await brandGate?.future;
    return delegate.updateBrand(organizationId, brand, clientMutationId);
  }

  @override
  Future<OrganizationBrandColors> replaceBrandColors(
    String organizationId,
    OrganizationBrandColors colors,
    String clientMutationId,
  ) => delegate.replaceBrandColors(organizationId, colors, clientMutationId);

  @override
  Future<OrganizationModules> updateModules(
    String organizationId,
    OrganizationModules modules,
    String clientMutationId,
  ) => delegate.updateModules(organizationId, modules, clientMutationId);
}

void main() {
  group('OrganizationBrandController rowVersion reconciliation', () {
    test(
      'brand → palette preserves drafts and avoids false conflict',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
        );
        final controller = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        await controller.load();
        controller.setColorsDraft(palette('#ABCDEF'));

        expect(await controller.saveBrand('#1565C0', '#00796B'), isTrue);
        expectSharedVersion(controller, 2);
        expect(controller.colorsSection.dirty, isTrue);
        expect(
          controller.colorsSection.draft!.items.single.colorHex,
          '#ABCDEF',
        );

        expect(await controller.saveColors(palette('#ABCDEF')), isTrue);
        expectSharedVersion(controller, 3);
      },
    );

    test('palette → modules uses the reconciled common version', () async {
      final repo = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
        session: _org1Support,
      );
      final controller = OrganizationBrandController(
        organizationId: 'org-1',
        repository: repo,
      );
      await controller.load();

      expect(await controller.saveColors(palette('#ABCDEF')), isTrue);
      expectSharedVersion(controller, 2);
      expect(
        await controller.saveModules(toggledModules(controller.modules!)),
        isTrue,
      );
      expectSharedVersion(controller, 3);
    });

    test('modules → brand uses the reconciled common version', () async {
      final repo = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
        session: _org1Support,
      );
      final controller = OrganizationBrandController(
        organizationId: 'org-1',
        repository: repo,
      );
      await controller.load();

      expect(
        await controller.saveModules(toggledModules(controller.modules!)),
        isTrue,
      );
      expectSharedVersion(controller, 2);
      expect(await controller.saveBrand('#1565C0', '#00796B'), isTrue);
      expectSharedVersion(controller, 3);
    });

    test(
      'real conflict reloads snapshots and preserves dirty draft for retry',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
        );
        final first = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        final concurrent = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        await first.load();
        await concurrent.load();
        first.setBrandDraft('#1565C0', '#00796B');
        expect(
          await concurrent.saveModules(toggledModules(concurrent.modules!)),
          isTrue,
        );

        expect(await first.saveBrand('#1565C0', '#00796B'), isFalse);
        expect(first.status, OrganizationBrandStatus.ready);
        expect(first.brandSection.dirty, isTrue);
        expect(first.brandSection.draft!.primaryColor, '#1565C0');
        expect(first.brandSection.snapshot!.rowVersion, 2);
        expect(first.brandSection.error, contains('yeniden kaydedebilirsiniz'));

        expect(await first.saveBrand('#1565C0', '#00796B'), isTrue);
        expectSharedVersion(first, 3);
      },
    );

    test(
      'reload updates clean drafts but never overwrites dirty drafts',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
        );
        final controller = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        final concurrent = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        await controller.load();
        await concurrent.load();
        controller.setBrandDraft('#1565C0', '#00796B');
        expect(await concurrent.saveColors(palette('#ABCDEF')), isTrue);

        await controller.load();
        expect(controller.brandSection.draft!.primaryColor, '#1565C0');
        expect(controller.brandSection.dirty, isTrue);
        expect(controller.modulesSection.dirty, isFalse);
        expect(controller.modulesSection.draft!.rowVersion, 2);
      },
    );
  });

  group('OrganizationBrandController section state', () {
    test(
      'structural dirty state and messages follow draft lifecycle',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
        );
        final controller = OrganizationBrandController(
          organizationId: 'org-1',
          repository: repo,
        );
        await controller.load();
        expect(controller.brandSection.dirty, isFalse);

        controller.setBrandDraft('#1565C0', '#00796B');
        expect(controller.brandSection.dirty, isTrue);
        expect(await controller.saveBrand('#1565C0', '#00796B'), isTrue);
        expect(controller.brandSection.success, 'Kaydedildi.');
        expect(controller.brandSection.error, isNull);
        expect(controller.brandSection.activeAttempt, isNull);

        controller.setBrandDraft('#C62828', '#455A64');
        expect(controller.brandSection.success, isNull);
        expect(controller.brandSection.error, isNull);
      },
    );

    test(
      'validation failure stays in its section and never shows success',
      () async {
        final controller = OrganizationBrandController(
          organizationId: 'org-1',
          repository: OrganizationsMockRepository(
            seed: [organization('org-1')],
            latency: Duration.zero,
            session: _org1Support,
          ),
        );
        await controller.load();

        expect(await controller.saveBrand('#BAD', '#00796B'), isFalse);
        expect(controller.status, OrganizationBrandStatus.ready);
        expect(controller.brandSection.error, isNotNull);
        expect(controller.brandSection.success, isNull);
      },
    );

    test('visibility flags prevent calls for hidden resources', () async {
      final base = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
        session: _org1Support,
      );
      final brandOnlyRepo = _CountingRepository(base);
      final brandOnly = OrganizationBrandController(
        organizationId: 'org-1',
        repository: brandOnlyRepo,
        loadModuleSettings: false,
      );
      await brandOnly.load();
      expect(brandOnlyRepo.getBrandCalls, 1);
      expect(brandOnlyRepo.getColorsCalls, 1);
      expect(brandOnlyRepo.getModulesCalls, 0);

      final modulesOnlyRepo = _CountingRepository(base);
      final modulesOnly = OrganizationBrandController(
        organizationId: 'org-1',
        repository: modulesOnlyRepo,
        loadBrandSettings: false,
      );
      await modulesOnly.load();
      expect(modulesOnlyRepo.getBrandCalls, 0);
      expect(modulesOnlyRepo.getColorsCalls, 0);
      expect(modulesOnlyRepo.getModulesCalls, 1);
    });

    test(
      'double save while pending produces one request and visible attempt',
      () async {
        final counting = _CountingRepository(
          OrganizationsMockRepository(
            seed: [organization('org-1')],
            latency: Duration.zero,
            session: _org1Support,
          ),
        )..brandGate = Completer<void>();
        final controller = OrganizationBrandController(
          organizationId: 'org-1',
          repository: counting,
        );
        await controller.load();

        final first = controller.saveBrand('#1565C0', '#00796B');
        final second = controller.saveBrand('#1565C0', '#00796B');
        expect(controller.brandSection.saving, isTrue);
        expect(controller.brandSection.activeAttempt, isNotNull);
        expect(await second, isFalse);
        counting.brandGate!.complete();
        expect(await first, isTrue);
        expect(counting.updateBrandCalls, 1);
      },
    );
  });
}
