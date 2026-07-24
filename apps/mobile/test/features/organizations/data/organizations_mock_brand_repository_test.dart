import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';

Organization organization(String id) => Organization(
  id: id,
  name: 'Kurs $id',
  defaultTimezone: 'Europe/Istanbul',
  status: OrganizationStatus.active,
  createdAt: DateTime.utc(2026),
  updatedAt: DateTime.utc(2026),
  rowVersion: 1,
);

OrganizationBrand brand(int version, String primary) => OrganizationBrand(
  primaryColor: primary,
  secondaryColor: '#00796B',
  rowVersion: version,
);

void main() {
  group('OrganizationsMockRepository brand idempotency', () {
    test(
      'same actor, organization, operation, key and fingerprint replays first snapshot',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
        );
        final first = await repo.updateBrand(
          'org-1',
          brand(1, '#1565C0'),
          'same-key',
        );
        final replay = await repo.updateBrand(
          'org-1',
          brand(1, '#1565C0'),
          'same-key',
        );

        expect(replay, first);
        expect((await repo.getBrand('org-1')).rowVersion, 2);
      },
    );

    test(
      'same scoped key with different fingerprint is rejected without mutation',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
        );
        await repo.updateBrand('org-1', brand(1, '#1565C0'), 'same-key');

        await expectLater(
          repo.updateBrand('org-1', brand(1, '#C62828'), 'same-key'),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.idempotencyKeyReused,
            ),
          ),
        );
        expect((await repo.getBrand('org-1')).primaryColor, '#1565C0');
        expect((await repo.getBrand('org-1')).rowVersion, 2);
      },
    );

    test(
      'same key in another organization never replays the first organization result',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1'), organization('org-2')],
          latency: Duration.zero,
        );
        final first = await repo.updateBrand(
          'org-1',
          brand(1, '#1565C0'),
          'same-key',
        );
        final second = await repo.updateBrand(
          'org-2',
          brand(1, '#C62828'),
          'same-key',
        );

        expect(first.primaryColor, '#1565C0');
        expect(second.primaryColor, '#C62828');
        expect((await repo.getBrand('org-2')).primaryColor, '#C62828');
      },
    );

    test(
      'palette and module operations replay once and reject key reuse',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
        );
        final palette = OrganizationBrandColors(
          rowVersion: 1,
          items: const [
            OrganizationBrandColor(colorHex: '#ABCDEF', sortOrder: 0),
          ],
        );
        final firstPalette = await repo.replaceBrandColors(
          'org-1',
          palette,
          'palette-key',
        );
        expect(
          await repo.replaceBrandColors('org-1', palette, 'palette-key'),
          firstPalette,
        );
        await expectLater(
          repo.replaceBrandColors(
            'org-1',
            OrganizationBrandColors(rowVersion: 1, items: const []),
            'palette-key',
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.idempotencyKeyReused,
            ),
          ),
        );

        final modules = await repo.getModules('org-1');
        final changedModules = OrganizationModules(
          rowVersion: 2,
          items: modules.items
              .map(
                (item) => item.code == OrganizationModuleCode.att
                    ? item.copyWith(isEnabled: false)
                    : item,
              )
              .toList(),
        );
        final firstModules = await repo.updateModules(
          'org-1',
          changedModules,
          'modules-key',
        );
        expect(
          await repo.updateModules('org-1', changedModules, 'modules-key'),
          firstModules,
        );
        await expectLater(
          repo.updateModules(
            'org-1',
            OrganizationModules(rowVersion: 2, items: modules.items),
            'modules-key',
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.idempotencyKeyReused,
            ),
          ),
        );
        expect((await repo.getModules('org-1')).rowVersion, 3);
      },
    );
  });

  group('OrganizationsMockRepository common settings version', () {
    test('every write reconciles brand, palette and modules', () async {
      final repo = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
      );
      await repo.getBrand('org-1');
      await repo.getBrandColors('org-1');
      final modules = await repo.getModules('org-1');

      await repo.updateModules(
        'org-1',
        OrganizationModules(
          rowVersion: 1,
          items: modules.items
              .map(
                (item) => item.code == OrganizationModuleCode.att
                    ? item.copyWith(isEnabled: false)
                    : item,
              )
              .toList(),
        ),
        'modules-key',
      );

      expect((await repo.getBrand('org-1')).rowVersion, 2);
      expect((await repo.getBrandColors('org-1')).rowVersion, 2);
      expect((await repo.getModules('org-1')).rowVersion, 2);
    });

    test(
      'palette is normalized, sorted canonically and rejects duplicates',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
        );
        final saved = await repo.replaceBrandColors(
          'org-1',
          OrganizationBrandColors(
            rowVersion: 1,
            items: const [
              OrganizationBrandColor(colorHex: '#abcdef', sortOrder: 2),
              OrganizationBrandColor(colorHex: '#123456', sortOrder: 1),
            ],
          ),
          'palette-key',
        );
        expect(saved.items.map((item) => item.colorHex), [
          '#123456',
          '#ABCDEF',
        ]);

        await expectLater(
          repo.replaceBrandColors(
            'org-1',
            OrganizationBrandColors(
              rowVersion: 2,
              items: const [
                OrganizationBrandColor(colorHex: '#ABCDEF', sortOrder: 0),
                OrganizationBrandColor(colorHex: '#abcdef', sortOrder: 1),
              ],
            ),
            'duplicate-key',
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.validationFailed,
            ),
          ),
        );
      },
    );

    test('modules reject an incomplete or duplicated catalog', () async {
      final repo = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
      );
      final modules = await repo.getModules('org-1');
      await expectLater(
        repo.updateModules(
          'org-1',
          OrganizationModules(rowVersion: 1, items: modules.items.sublist(1)),
          'bad-catalog',
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.validationFailed,
          ),
        ),
      );
    });

    test('module reads and writes never create a ghost organization', () async {
      final repo = OrganizationsMockRepository(
        seed: [organization('org-1')],
        latency: Duration.zero,
      );
      await expectLater(
        repo.getModules('missing'),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.resourceNotFound,
          ),
        ),
      );
      await expectLater(
        repo.updateModules(
          'missing',
          OrganizationModules(rowVersion: 1, items: const []),
          'missing-key',
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (failure) => failure.code,
            'code',
            OrganizationsFailureCode.resourceNotFound,
          ),
        ),
      );
      expect((await repo.getBrand('org-1')).rowVersion, 1);
    });

    test(
      'platform, ORG_ADMIN and delegated permission combinations fail closed',
      () async {
        final seed = [organization('org-1')];
        final platform = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'platform',
          ),
        );
        expect(
          await platform.updateBrand(
            'org-1',
            brand(1, '#1565C0'),
            'platform-brand',
          ),
          isA<OrganizationBrand>(),
        );

        final brandOnly = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'teacher-brand',
            organizationId: 'org-1',
            canManageBrand: true,
          ),
        );
        expect(
          await brandOnly.updateBrand(
            'org-1',
            brand(1, '#1565C0'),
            'brand-only',
          ),
          isA<OrganizationBrand>(),
        );
        await expectLater(
          brandOnly.updateModules(
            'org-1',
            await brandOnly.getModules('org-1'),
            'forbidden-module',
          ),
          throwsA(isA<OrganizationsFailure>()),
        );

        final moduleOnly = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'teacher-module',
            organizationId: 'org-1',
            canManageModules: true,
          ),
        );
        final modules = await moduleOnly.getModules('org-1');
        expect(
          await moduleOnly.updateModules('org-1', modules, 'module-only'),
          isA<OrganizationModules>(),
        );
        await expectLater(
          moduleOnly.updateBrand(
            'org-1',
            brand(2, '#1565C0'),
            'forbidden-brand',
          ),
          throwsA(isA<OrganizationsFailure>()),
        );

        final revoked = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'revoked',
            organizationId: 'org-1',
            canManageBrand: true,
            canManageModules: true,
            revoked: true,
          ),
        );
        await expectLater(
          revoked.getBrand('org-1'),
          throwsA(
            isA<OrganizationsFailure>().having(
              (failure) => failure.code,
              'code',
              OrganizationsFailureCode.forbidden,
            ),
          ),
        );
      },
    );
  });
}
