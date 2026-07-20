import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';

void main() {
  group('OrganizationsMockRepository.createOrganization', () {
    test('creates an ACTIVE organization with rowVersion 1', () async {
      final DateTime fixedNow = DateTime.utc(2026, 7, 20, 12);
      final repo = OrganizationsMockRepository(
        seed: const <Organization>[],
        latency: Duration.zero,
        now: () => fixedNow,
      );

      final Organization created = await repo.createOrganization(
        const OrganizationCreateRequest(
          name: 'Fındıklı Kur\'an Kursu',
          shortName: 'Fındıklı',
          clientMutationId: 'cm_1',
        ),
      );

      expect(created.name, 'Fındıklı Kur\'an Kursu');
      expect(created.shortName, 'Fındıklı');
      expect(created.defaultTimezone, organizationDefaultTimezoneFallback);
      expect(created.status, OrganizationStatus.active);
      expect(created.rowVersion, 1);
      expect(created.createdAt, fixedNow);
      expect(created.updatedAt, fixedNow);
      expect(created.id, isNotEmpty);
    });

    test(
      'a subsequent list call includes the newly created organization',
      () async {
        final repo = OrganizationsMockRepository(
          seed: const <Organization>[],
          latency: Duration.zero,
        );

        await repo.createOrganization(
          const OrganizationCreateRequest(
            name: 'Yeni Kurum',
            clientMutationId: 'cm_1',
          ),
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(limit: 50),
        );
        expect(result.items.map((o) => o.name), contains('Yeni Kurum'));
      },
    );

    test('rejects when unauthenticated', () async {
      final repo = OrganizationsMockRepository(
        latency: Duration.zero,
        session: const OrganizationsMockSession.unauthenticated(),
      );

      await expectLater(
        repo.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum',
            clientMutationId: 'cm_1',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (f) => f.code,
            'code',
            OrganizationsFailureCode.unauthenticated,
          ),
        ),
      );
    });

    test('rejects when authenticated without platform-admin scope', () async {
      final repo = OrganizationsMockRepository(
        latency: Duration.zero,
        session:
            const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope(
              actorUserId: 'user-1',
            ),
      );

      await expectLater(
        repo.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum',
            clientMutationId: 'cm_1',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (f) => f.code,
            'code',
            OrganizationsFailureCode.forbidden,
          ),
        ),
      );
    });

    test('rejects an invalid request with VALIDATION_FAILED', () async {
      final repo = OrganizationsMockRepository(latency: Duration.zero);

      await expectLater(
        repo.createOrganization(
          const OrganizationCreateRequest(
            name: '   ',
            clientMutationId: 'cm_1',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (f) => f.code,
            'code',
            OrganizationsFailureCode.validationFailed,
          ),
        ),
      );
    });

    test(
      'a VALIDATION_FAILED failure carries the offending field in fieldErrors',
      () async {
        final repo = OrganizationsMockRepository(latency: Duration.zero);

        await expectLater(
          repo.createOrganization(
            const OrganizationCreateRequest(
              name: '   ',
              clientMutationId: 'cm_1',
            ),
          ),
          throwsA(
            isA<OrganizationsFailure>()
                .having(
                  (f) => f.code,
                  'code',
                  OrganizationsFailureCode.validationFailed,
                )
                .having(
                  (f) => f.fieldErrors?.name,
                  'fieldErrors.name',
                  isNotNull,
                ),
          ),
        );
      },
    );

    test('rejects when the session carries an organization-context token with '
        'ORGANIZATION_CONTEXT_REQUIRED', () async {
      final repo = OrganizationsMockRepository(
        latency: Duration.zero,
        session:
            const OrganizationsMockSession.platformAdminWithOrganizationContext(
              actorUserId: 'admin-1',
            ),
      );

      await expectLater(
        repo.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum',
            clientMutationId: 'cm_1',
          ),
        ),
        throwsA(
          isA<OrganizationsFailure>().having(
            (f) => f.code,
            'code',
            OrganizationsFailureCode.organizationContextRequired,
          ),
        ),
      );
    });

    test(
      'replays the same result for a retry with the same key and content',
      () async {
        final repo = OrganizationsMockRepository(latency: Duration.zero);
        const request = OrganizationCreateRequest(
          name: 'Kurum',
          clientMutationId: 'cm_1',
        );

        final Organization first = await repo.createOrganization(request);
        final Organization second = await repo.createOrganization(request);

        expect(second.id, first.id);
        expect(second.createdAt, first.createdAt);
      },
    );

    test(
      'rejects a retry with the same key but different content with IDEMPOTENCY_KEY_REUSED',
      () async {
        final repo = OrganizationsMockRepository(latency: Duration.zero);

        await repo.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum A',
            clientMutationId: 'cm_1',
          ),
        );

        await expectLater(
          repo.createOrganization(
            const OrganizationCreateRequest(
              name: 'Kurum B',
              clientMutationId: 'cm_1',
            ),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.idempotencyKeyReused,
            ),
          ),
        );
      },
    );

    test(
      'the same clientMutationId under a different actor is an independent key',
      () async {
        final repoA = OrganizationsMockRepository(
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'admin-a',
          ),
        );
        final repoB = OrganizationsMockRepository(
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'admin-b',
          ),
        );

        final Organization fromA = await repoA.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum A',
            clientMutationId: 'cm_shared',
          ),
        );
        final Organization fromB = await repoB.createOrganization(
          const OrganizationCreateRequest(
            name: 'Kurum B',
            clientMutationId: 'cm_shared',
          ),
        );

        expect(fromA.id, isNot(fromB.id));
        expect(fromB.name, 'Kurum B');
      },
    );

    test(
      'a key stays replay-safe after 500+ unrelated creates (no eviction)',
      () async {
        final repo = OrganizationsMockRepository(
          seed: const <Organization>[],
          latency: Duration.zero,
        );
        const request = OrganizationCreateRequest(
          name: 'İlk Kurum',
          clientMutationId: 'cm_first',
        );
        final Organization first = await repo.createOrganization(request);

        for (int i = 0; i < 600; i++) {
          await repo.createOrganization(
            OrganizationCreateRequest(
              name: 'Dolgu Kurum $i',
              clientMutationId: 'cm_filler_$i',
            ),
          );
        }

        final Organization replay = await repo.createOrganization(request);

        expect(replay.id, first.id);
        final result = await repo.listOrganizations(
          const OrganizationListQuery(limit: 100, search: 'İlk Kurum'),
        );
        expect(result.items, hasLength(1));
      },
    );
  });
}
