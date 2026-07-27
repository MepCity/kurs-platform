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

Organization organizationWithStatus(String id, OrganizationStatus status) =>
    Organization(
      id: id,
      name: 'Kurs $id',
      defaultTimezone: 'Europe/Istanbul',
      status: status,
      createdAt: DateTime.utc(2026),
      updatedAt: DateTime.utc(2026),
      rowVersion: 1,
    );

OrganizationBrand brand(int version, String primary) => OrganizationBrand(
  primaryColor: primary,
  secondaryColor: '#00796B',
  rowVersion: version,
);

const _org1Support = OrganizationsMockSession.platformSupport(
  actorUserId: 'support-org-1',
  organizationId: 'org-1',
);

void main() {
  group('OrganizationsMockRepository brand idempotency', () {
    test(
      'same actor, organization, operation, key and fingerprint replays first snapshot',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
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
          session: _org1Support,
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
      'another key in the same support context creates a separate mutation',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1'), organization('org-2')],
          latency: Duration.zero,
          session: _org1Support,
        );
        final first = await repo.updateBrand(
          'org-1',
          brand(1, '#1565C0'),
          'same-key',
        );
        final second = await repo.updateBrand(
          'org-1',
          brand(2, '#C62828'),
          'another-key',
        );

        expect(first.primaryColor, '#1565C0');
        expect(second.primaryColor, '#C62828');
        expect((await repo.getBrand('org-1')).primaryColor, '#C62828');
      },
    );

    test(
      'palette and module operations replay once and reject key reuse',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: _org1Support,
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
        session: _org1Support,
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
          session: _org1Support,
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
        session: _org1Support,
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
        session: _org1Support,
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
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'platform',
            organizationId: 'org-1',
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

  group('OrganizationsMockRepository organization-scoped support access', () {
    Future<void> expectFailure(
      Future<Object?> call,
      OrganizationsFailureCode code,
    ) => expectLater(
      call,
      throwsA(
        isA<OrganizationsFailure>().having(
          (failure) => failure.code,
          'code',
          code,
        ),
      ),
    );

    List<OrganizationModule> catalog() => OrganizationModuleCode.values.indexed
        .map(
          (entry) => OrganizationModule(
            code: entry.$2,
            isEnabled: true,
            sortOrder: entry.$1,
          ),
        )
        .toList();

    Future<void> exerciseAllSix(OrganizationsMockRepository repo) async {
      final currentBrand = await repo.getBrand('org-1');
      await repo.updateBrand(
        'org-1',
        brand(currentBrand.rowVersion, '#1565C0'),
        'brand-write',
      );
      final currentColors = await repo.getBrandColors('org-1');
      await repo.replaceBrandColors(
        'org-1',
        OrganizationBrandColors(
          rowVersion: currentColors.rowVersion,
          items: const [
            OrganizationBrandColor(colorHex: '#123456', sortOrder: 0),
          ],
        ),
        'colors-write',
      );
      final currentModules = await repo.getModules('org-1');
      await repo.updateModules(
        'org-1',
        OrganizationModules(
          rowVersion: currentModules.rowVersion,
          items: currentModules.items
              .map(
                (item) => item.code == OrganizationModuleCode.att
                    ? item.copyWith(isEnabled: false)
                    : item,
              )
              .toList(),
        ),
        'modules-write',
      );
    }

    Future<void> expectAllSixForbidden(OrganizationsMockRepository repo) async {
      await expectFailure(
        repo.getBrand('org-1'),
        OrganizationsFailureCode.forbidden,
      );
      await expectFailure(
        repo.updateBrand('org-1', brand(1, '#1565C0'), 'brand'),
        OrganizationsFailureCode.forbidden,
      );
      await expectFailure(
        repo.getBrandColors('org-1'),
        OrganizationsFailureCode.forbidden,
      );
      await expectFailure(
        repo.replaceBrandColors(
          'org-1',
          OrganizationBrandColors(rowVersion: 1, items: const []),
          'colors',
        ),
        OrganizationsFailureCode.forbidden,
      );
      await expectFailure(
        repo.getModules('org-1'),
        OrganizationsFailureCode.forbidden,
      );
      await expectFailure(
        repo.updateModules(
          'org-1',
          OrganizationModules(rowVersion: 1, items: catalog()),
          'modules',
        ),
        OrganizationsFailureCode.forbidden,
      );
    }

    test(
      'ACTIVE org actor reads and ORG_ADMIN writes all six endpoints',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'org-admin',
            organizationId: 'org-1',
            isOrganizationAdmin: true,
          ),
        );
        await exerciseAllSix(repo);
      },
    );

    test(
      'SUSPENDED org actor receives FORBIDDEN on all six endpoints',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organizationWithStatus('org-1', OrganizationStatus.suspended)],
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'member',
            organizationId: 'org-1',
            isOrganizationAdmin: true,
          ),
        );
        await expectAllSixForbidden(repo);
      },
    );

    test(
      'SUSPENDED platform-support reads and writes all six endpoints',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organizationWithStatus('org-1', OrganizationStatus.suspended)],
          latency: Duration.zero,
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'support',
            organizationId: 'org-1',
          ),
        );
        await exerciseAllSix(repo);
      },
    );

    test(
      'ARCHIVED org actor receives FORBIDDEN on all six endpoints',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organizationWithStatus('org-1', OrganizationStatus.archived)],
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'member',
            organizationId: 'org-1',
            isOrganizationAdmin: true,
          ),
        );
        await expectAllSixForbidden(repo);
      },
    );

    test(
      'ARCHIVED platform-support allows three GETs and rejects three writes',
      () async {
        final repo = OrganizationsMockRepository(
          seed: [organizationWithStatus('org-1', OrganizationStatus.archived)],
          latency: Duration.zero,
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'support',
            organizationId: 'org-1',
          ),
        );
        final beforeBrand = await repo.getBrand('org-1');
        final beforeColors = await repo.getBrandColors('org-1');
        final beforeModules = await repo.getModules('org-1');
        await expectFailure(
          repo.updateBrand('org-1', brand(1, '#1565C0'), 'brand'),
          OrganizationsFailureCode.stateConflict,
        );
        await expectFailure(
          repo.replaceBrandColors('org-1', beforeColors, 'colors'),
          OrganizationsFailureCode.stateConflict,
        );
        await expectFailure(
          repo.updateModules('org-1', beforeModules, 'modules'),
          OrganizationsFailureCode.stateConflict,
        );
        expect(await repo.getBrand('org-1'), beforeBrand);
        expect(await repo.getBrandColors('org-1'), beforeColors);
        expect(await repo.getModules('org-1'), beforeModules);
      },
    );

    test(
      'global credential, wrong support target and revoked support fail closed',
      () async {
        final seed = [organization('org-1'), organization('org-2')];
        final global = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'global',
          ),
        );
        final wrongTarget = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'support',
            organizationId: 'org-2',
          ),
        );
        final revoked = OrganizationsMockRepository(
          seed: seed,
          latency: Duration.zero,
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'revoked',
            organizationId: 'org-1',
            revoked: true,
          ),
        );
        await expectFailure(
          global.getBrand('org-1'),
          OrganizationsFailureCode.forbidden,
        );
        await expectFailure(
          wrongTarget.getBrand('org-1'),
          OrganizationsFailureCode.forbidden,
        );
        await expectFailure(
          revoked.getBrand('org-1'),
          OrganizationsFailureCode.forbidden,
        );
      },
    );

    test(
      'missing organization is hidden from org actor and visible to support only as not found',
      () async {
        final support = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: const OrganizationsMockSession.platformSupport(
            actorUserId: 'support',
            organizationId: 'missing',
          ),
        );
        final member = OrganizationsMockRepository(
          seed: [organization('org-1')],
          latency: Duration.zero,
          session: const OrganizationsMockSession.organizationMember(
            actorUserId: 'member',
            organizationId: 'missing',
          ),
        );
        for (final call in [
          support.getBrand('missing'),
          support.getBrandColors('missing'),
          support.getModules('missing'),
        ]) {
          await expectFailure(call, OrganizationsFailureCode.resourceNotFound);
        }
        for (final call in [
          member.getBrand('missing'),
          member.getBrandColors('missing'),
          member.getModules('missing'),
        ]) {
          await expectFailure(call, OrganizationsFailureCode.forbidden);
        }
        await expectFailure(
          support.getBrand('org-1'),
          OrganizationsFailureCode.forbidden,
        );
      },
    );
  });
}
