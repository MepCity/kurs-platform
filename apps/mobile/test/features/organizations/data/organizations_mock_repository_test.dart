import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_repository.dart';
import 'package:kurs_platform_mobile/features/organizations/data/organizations_mock_session.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';

Organization _at(
  String id,
  String name, {
  DateTime? createdAt,
  OrganizationStatus status = OrganizationStatus.active,
  String? shortName,
}) {
  final DateTime at = createdAt ?? DateTime.utc(2026, 1, 1);
  return Organization(
    id: id,
    name: name,
    shortName: shortName,
    defaultTimezone: 'Europe/Istanbul',
    status: status,
    createdAt: at,
    updatedAt: at,
    rowVersion: 1,
  );
}

List<Organization> _seed(int count) {
  return List<Organization>.generate(count, (int index) {
    final DateTime createdAt = DateTime.utc(
      2026,
      1,
      1,
    ).add(Duration(days: index));
    final OrganizationStatus status = switch (index % 3) {
      0 => OrganizationStatus.active,
      1 => OrganizationStatus.suspended,
      _ => OrganizationStatus.archived,
    };
    return _at(
      'id-${index.toString().padLeft(3, '0')}',
      'Kurum ${index.toString().padLeft(3, '0')}',
      createdAt: createdAt,
      status: status,
      shortName: index.isEven ? 'Kısa$index' : null,
    );
  });
}

void main() {
  group('OrganizationsMockRepository', () {
    test('returns items sorted by name ascending by default', () async {
      final repo = OrganizationsMockRepository(
        seed: _seed(5),
        latency: Duration.zero,
      );

      final result = await repo.listOrganizations(
        const OrganizationListQuery(limit: 10),
      );

      expect(result.items.map((o) => o.name).toList(), <String>[
        'Kurum 000',
        'Kurum 001',
        'Kurum 002',
        'Kurum 003',
        'Kurum 004',
      ]);
      expect(result.hasNextPage, isFalse);
      expect(result.nextCursor, isNull);
    });

    test('filters by status', () async {
      final repo = OrganizationsMockRepository(
        seed: _seed(9),
        latency: Duration.zero,
      );

      final result = await repo.listOrganizations(
        const OrganizationListQuery(
          status: OrganizationStatus.suspended,
          limit: 50,
        ),
      );

      expect(result.items, isNotEmpty);
      expect(
        result.items.every((o) => o.status == OrganizationStatus.suspended),
        isTrue,
      );
    });

    test(
      'filters by case-insensitive search across name and shortName',
      () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(9),
          latency: Duration.zero,
        );

        final byName = await repo.listOrganizations(
          const OrganizationListQuery(search: 'kurum 00', limit: 50),
        );
        expect(byName.items, isNotEmpty);

        final byShortName = await repo.listOrganizations(
          const OrganizationListQuery(search: 'kısa2', limit: 50),
        );
        expect(byShortName.items.map((o) => o.id), contains('id-002'));
      },
    );

    test(
      'normalizes search: leading/trailing whitespace does not affect matches',
      () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(9),
          latency: Duration.zero,
        );

        final padded = await repo.listOrganizations(
          const OrganizationListQuery(search: '  kurum 00  ', limit: 50),
        );
        final trimmed = await repo.listOrganizations(
          const OrganizationListQuery(search: 'kurum 00', limit: 50),
        );

        expect(padded.items.map((o) => o.id), trimmed.items.map((o) => o.id));
      },
    );

    group('sort tie-break (§4.1: id is never reversed by order)', () {
      test('name ascending, equal name -> id ascending', () async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _at('id-b', 'Aynı İsim'),
            _at('id-a', 'Aynı İsim'),
            _at('id-c', 'Aynı İsim'),
          ],
          latency: Duration.zero,
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(
            sort: OrganizationSortField.name,
            order: OrganizationSortOrder.ascending,
            limit: 10,
          ),
        );

        expect(result.items.map((o) => o.id).toList(), <String>[
          'id-a',
          'id-b',
          'id-c',
        ]);
      });

      test('name descending, equal name -> id still ascending', () async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _at('id-b', 'Aynı İsim'),
            _at('id-a', 'Aynı İsim'),
            _at('id-c', 'Aynı İsim'),
          ],
          latency: Duration.zero,
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(
            sort: OrganizationSortField.name,
            order: OrganizationSortOrder.descending,
            limit: 10,
          ),
        );

        expect(
          result.items.map((o) => o.id).toList(),
          <String>['id-a', 'id-b', 'id-c'],
          reason: 'only the primary field reverses on DESC; id must not',
        );
      });

      test('createdAt ascending, equal date -> id ascending', () async {
        final DateTime sameDay = DateTime.utc(2026, 3, 1);
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _at('id-b', 'B', createdAt: sameDay),
            _at('id-a', 'A', createdAt: sameDay),
            _at('id-c', 'C', createdAt: sameDay),
          ],
          latency: Duration.zero,
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(
            sort: OrganizationSortField.createdAt,
            order: OrganizationSortOrder.ascending,
            limit: 10,
          ),
        );

        expect(result.items.map((o) => o.id).toList(), <String>[
          'id-a',
          'id-b',
          'id-c',
        ]);
      });

      test('createdAt descending, equal date -> id still ascending', () async {
        final DateTime sameDay = DateTime.utc(2026, 3, 1);
        final repo = OrganizationsMockRepository(
          seed: <Organization>[
            _at('id-b', 'B', createdAt: sameDay),
            _at('id-a', 'A', createdAt: sameDay),
            _at('id-c', 'C', createdAt: sameDay),
          ],
          latency: Duration.zero,
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(
            sort: OrganizationSortField.createdAt,
            order: OrganizationSortOrder.descending,
            limit: 10,
          ),
        );

        expect(result.items.map((o) => o.id).toList(), <String>[
          'id-a',
          'id-b',
          'id-c',
        ]);
      });

      test(
        'no duplicate or skipped rows across pages when many rows tie',
        () async {
          final DateTime sameDay = DateTime.utc(2026, 3, 1);
          final repo = OrganizationsMockRepository(
            seed: List<Organization>.generate(
              10,
              (int i) => _at(
                'id-${(9 - i).toString().padLeft(2, '0')}',
                'Aynı',
                createdAt: sameDay,
              ),
            ),
            latency: Duration.zero,
          );

          final page1 = await repo.listOrganizations(
            const OrganizationListQuery(limit: 4),
          );
          final page2 = await repo.listOrganizations(
            const OrganizationListQuery(limit: 4).withCursor(page1.nextCursor),
          );
          final page3 = await repo.listOrganizations(
            const OrganizationListQuery(limit: 4).withCursor(page2.nextCursor),
          );

          final allIds = <String>[
            ...page1.items.map((o) => o.id),
            ...page2.items.map((o) => o.id),
            ...page3.items.map((o) => o.id),
          ];
          expect(allIds.toSet(), hasLength(10));
          expect(allIds, hasLength(10));
          expect(page3.hasNextPage, isFalse);
        },
      );
    });

    test('sorts by createdAt descending', () async {
      final repo = OrganizationsMockRepository(
        seed: _seed(4),
        latency: Duration.zero,
      );

      final result = await repo.listOrganizations(
        const OrganizationListQuery(
          sort: OrganizationSortField.createdAt,
          order: OrganizationSortOrder.descending,
          limit: 10,
        ),
      );

      expect(result.items.map((o) => o.id).toList(), <String>[
        'id-003',
        'id-002',
        'id-001',
        'id-000',
      ]);
    });

    group('cursor contract (§5.3)', () {
      test('is an opaque, content-free token', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(25),
          latency: Duration.zero,
        );

        final first = await repo.listOrganizations(
          const OrganizationListQuery(limit: 20),
        );

        expect(first.nextCursor, isNotNull);
        expect(
          RegExp(r'^ck_[0-9a-f]{32}$').hasMatch(first.nextCursor!),
          isTrue,
        );
        // No JSON/base64 leakage of the underlying filter/offset state.
        expect(first.nextCursor, isNot(contains('name')));
        expect(first.nextCursor, isNot(contains('limit')));
      });

      test('paginates across pages without loss or duplication', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(25),
          latency: Duration.zero,
        );

        final first = await repo.listOrganizations(
          const OrganizationListQuery(limit: 20),
        );
        expect(first.items, hasLength(20));
        expect(first.hasNextPage, isTrue);
        expect(first.nextCursor, isNotNull);

        final second = await repo.listOrganizations(
          const OrganizationListQuery(limit: 20).withCursor(first.nextCursor),
        );
        expect(second.items, hasLength(5));
        expect(second.hasNextPage, isFalse);
        expect(second.nextCursor, isNull);

        final allIds = <String>{
          ...first.items.map((o) => o.id),
          ...second.items.map((o) => o.id),
        };
        expect(allIds, hasLength(25));
      });

      test('rejects a cursor with a single tampered character', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(25),
          latency: Duration.zero,
        );

        final first = await repo.listOrganizations(
          const OrganizationListQuery(limit: 20),
        );
        final String valid = first.nextCursor!;
        final String tampered =
            '${valid.substring(0, valid.length - 1)}${valid.endsWith('a') ? 'b' : 'a'}';

        expect(
          () => repo.listOrganizations(
            const OrganizationListQuery(limit: 20).withCursor(tampered),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.invalidCursor,
            ),
          ),
        );
      });

      test('rejects a cursor minted for a different actor', () async {
        final repoA = OrganizationsMockRepository(
          seed: _seed(25),
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'actor-a',
          ),
        );
        final repoB = OrganizationsMockRepository(
          seed: _seed(25),
          latency: Duration.zero,
          session: const OrganizationsMockSession.authenticatedPlatformAdmin(
            actorUserId: 'actor-b',
          ),
        );

        final first = await repoA.listOrganizations(
          const OrganizationListQuery(limit: 20),
        );

        // Same underlying dataset/registry-less repo, different session:
        // repoB never minted this token, so it is simply unknown to it.
        expect(
          () => repoB.listOrganizations(
            const OrganizationListQuery(limit: 20).withCursor(first.nextCursor),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.invalidCursor,
            ),
          ),
        );
      });

      for (final MapEntry<
            String,
            OrganizationListQuery Function(OrganizationListQuery)
          >
          mutation
          in <String, OrganizationListQuery Function(OrganizationListQuery)>{
            'status changes': (q) =>
                q.copyWith(status: OrganizationStatus.suspended),
            'search changes': (q) => q.copyWith(search: 'kurum'),
            'sort changes': (q) =>
                q.copyWith(sort: OrganizationSortField.createdAt),
            'order changes': (q) =>
                q.copyWith(order: OrganizationSortOrder.descending),
            'limit changes': (q) => q.copyWith(limit: 5),
          }.entries) {
        test('rejects a replayed cursor when ${mutation.key}', () async {
          final repo = OrganizationsMockRepository(
            seed: _seed(25),
            latency: Duration.zero,
          );
          const OrganizationListQuery original = OrganizationListQuery(
            limit: 20,
          );
          final first = await repo.listOrganizations(original);

          final OrganizationListQuery mutated = mutation
              .value(original)
              .withCursor(first.nextCursor);

          expect(
            () => repo.listOrganizations(mutated),
            throwsA(
              isA<OrganizationsFailure>().having(
                (f) => f.code,
                'code',
                OrganizationsFailureCode.invalidCursor,
              ),
            ),
          );
        });
      }

      test('rejects an unknown cursor', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(5),
          latency: Duration.zero,
        );

        expect(
          () => repo.listOrganizations(
            const OrganizationListQuery(
              limit: 20,
              cursor: 'ck_0000000000000000000000000000ff',
            ),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.invalidCursor,
            ),
          ),
        );
      });

      test('rejects a malformed cursor', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(5),
          latency: Duration.zero,
        );

        expect(
          () => repo.listOrganizations(
            const OrganizationListQuery(limit: 20, cursor: 'not-a-real-cursor'),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.invalidCursor,
            ),
          ),
        );
      });

      test('rejects an empty-string cursor', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(5),
          latency: Duration.zero,
        );

        expect(
          () => repo.listOrganizations(
            const OrganizationListQuery(limit: 20, cursor: ''),
          ),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.invalidCursor,
            ),
          ),
        );
      });

      group('keyset resume under concurrent mutation', () {
        // Deliberately not scope-mismatch-tested via the public API: the
        // mock currently only models one `OrganizationsMockScope` value
        // (`globalPlatformAdmin`, the only scope PLAT-01 ever uses), so a
        // second scope cannot be produced through any public constructor.
        // The registry entry already stores and compares `scope`
        // structurally (see `_CursorEntry.matchesContext`); this becomes
        // exercisable the day a second scope value is added.

        test(
          'an insertion behind the cursor is invisible to later pages, without gaps or duplicates',
          () async {
            final repo = OrganizationsMockRepository(
              seed: <Organization>[
                _at('id-a', 'Kurum A'),
                _at('id-c', 'Kurum C'),
                _at('id-e', 'Kurum E'),
                _at('id-g', 'Kurum G'),
              ],
              latency: Duration.zero,
            );

            final first = await repo.listOrganizations(
              const OrganizationListQuery(limit: 2),
            );
            expect(first.items.map((o) => o.id), <String>['id-a', 'id-c']);

            // Sorts between A and C — behind the cursor's marker (C).
            repo.debugInsert(_at('id-b', 'Kurum B'));

            final second = await repo.listOrganizations(
              const OrganizationListQuery(
                limit: 2,
              ).withCursor(first.nextCursor),
            );

            expect(second.items.map((o) => o.id), <String>['id-e', 'id-g']);
          },
        );

        test(
          'an insertion ahead of the cursor is picked up on the next page',
          () async {
            final repo = OrganizationsMockRepository(
              seed: <Organization>[
                _at('id-a', 'Kurum A'),
                _at('id-c', 'Kurum C'),
                _at('id-e', 'Kurum E'),
                _at('id-g', 'Kurum G'),
              ],
              latency: Duration.zero,
            );

            final first = await repo.listOrganizations(
              const OrganizationListQuery(limit: 2),
            );
            expect(first.items.map((o) => o.id), <String>['id-a', 'id-c']);

            // Sorts between C and E — ahead of the cursor's marker (C).
            repo.debugInsert(_at('id-d', 'Kurum D'));

            final second = await repo.listOrganizations(
              const OrganizationListQuery(
                limit: 2,
              ).withCursor(first.nextCursor),
            );

            expect(second.items.map((o) => o.id), <String>['id-d', 'id-e']);
          },
        );

        test(
          'a deletion of an upcoming row is simply absent, no gap or crash',
          () async {
            final repo = OrganizationsMockRepository(
              seed: <Organization>[
                _at('id-a', 'Kurum A'),
                _at('id-c', 'Kurum C'),
                _at('id-e', 'Kurum E'),
                _at('id-g', 'Kurum G'),
              ],
              latency: Duration.zero,
            );

            final first = await repo.listOrganizations(
              const OrganizationListQuery(limit: 2),
            );
            expect(first.items.map((o) => o.id), <String>['id-a', 'id-c']);

            repo.debugRemove('id-e');

            final second = await repo.listOrganizations(
              const OrganizationListQuery(
                limit: 2,
              ).withCursor(first.nextCursor),
            );

            expect(second.items.map((o) => o.id), <String>['id-g']);
            expect(second.hasNextPage, isFalse);
          },
        );
      });
    });

    group('session / authorization', () {
      test('throws unauthenticated when there is no valid session', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(3),
          latency: Duration.zero,
          session: const OrganizationsMockSession.unauthenticated(),
        );

        expect(
          () => repo.listOrganizations(const OrganizationListQuery()),
          throwsA(
            isA<OrganizationsFailure>()
                .having(
                  (f) => f.code,
                  'code',
                  OrganizationsFailureCode.unauthenticated,
                )
                .having((f) => f.isUnauthorized, 'isUnauthorized', isTrue),
          ),
        );
      });

      test(
        'throws forbidden when the actor lacks platform admin scope',
        () async {
          final repo = OrganizationsMockRepository(
            seed: _seed(3),
            latency: Duration.zero,
            session:
                const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope(
                  actorUserId: 'actor-a',
                ),
          );

          expect(
            () => repo.listOrganizations(const OrganizationListQuery()),
            throwsA(
              isA<OrganizationsFailure>()
                  .having(
                    (f) => f.code,
                    'code',
                    OrganizationsFailureCode.forbidden,
                  )
                  .having((f) => f.isUnauthorized, 'isUnauthorized', isTrue),
            ),
          );
        },
      );
    });

    group('input validation', () {
      test('throws validationFailed when limit exceeds the maximum', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(3),
          latency: Duration.zero,
        );

        expect(
          () => repo.listOrganizations(const OrganizationListQuery(limit: 101)),
          throwsA(
            isA<OrganizationsFailure>().having(
              (f) => f.code,
              'code',
              OrganizationsFailureCode.validationFailed,
            ),
          ),
        );
      });

      test('throws validationFailed when limit is zero or negative', () async {
        final repo = OrganizationsMockRepository(
          seed: _seed(3),
          latency: Duration.zero,
        );

        for (final int badLimit in <int>[0, -1, -100]) {
          expect(
            () =>
                repo.listOrganizations(OrganizationListQuery(limit: badLimit)),
            throwsA(
              isA<OrganizationsFailure>().having(
                (f) => f.code,
                'code',
                OrganizationsFailureCode.validationFailed,
              ),
            ),
            reason: 'limit=$badLimit must be rejected even in release mode',
          );
        }
      });

      test(
        'throws validationFailed when search exceeds the maximum length',
        () async {
          final repo = OrganizationsMockRepository(
            seed: _seed(3),
            latency: Duration.zero,
          );

          expect(
            () => repo.listOrganizations(
              OrganizationListQuery(search: 'a' * 201),
            ),
            throwsA(
              isA<OrganizationsFailure>().having(
                (f) => f.code,
                'code',
                OrganizationsFailureCode.validationFailed,
              ),
            ),
          );
        },
      );

      test(
        'accepts search that is only whitespace, however long, as no filter',
        () async {
          final repo = OrganizationsMockRepository(
            seed: _seed(3),
            latency: Duration.zero,
          );

          // 500 spaces is well past the 200-character limit, but it
          // normalizes to "no search filter" and must not be rejected as an
          // oversized search term.
          final result = await repo.listOrganizations(
            OrganizationListQuery(search: ' ' * 500, limit: 50),
          );

          expect(result.items, hasLength(3));
        },
      );
    });

    group('Turkish-aware search matching', () {
      test(
        'a dotless-I search matches a dotted seed name and vice versa',
        () async {
          final repo = OrganizationsMockRepository(
            seed: <Organization>[_at('id-igdir', 'Iğdır Kur\'an Kursu')],
            latency: Duration.zero,
          );

          final lower = await repo.listOrganizations(
            const OrganizationListQuery(search: 'ığdır', limit: 10),
          );
          final upper = await repo.listOrganizations(
            const OrganizationListQuery(search: 'IĞDIR', limit: 10),
          );

          expect(lower.items.map((o) => o.id), <String>['id-igdir']);
          expect(upper.items.map((o) => o.id), <String>['id-igdir']);
        },
      );

      test('a dotted-İ search matches an "İstanbul" seed name', () async {
        final repo = OrganizationsMockRepository(
          seed: <Organization>[_at('id-ist', 'İstanbul Merkez Kur\'an Kursu')],
          latency: Duration.zero,
        );

        final result = await repo.listOrganizations(
          const OrganizationListQuery(search: 'İSTANBUL', limit: 10),
        );

        expect(result.items.map((o) => o.id), <String>['id-ist']);
      });
    });

    test('defaultSeed exposes more than one page and every status', () {
      final seed = OrganizationsMockRepository.defaultSeed();
      expect(seed.length, greaterThan(20));
      expect(
        seed.map((o) => o.status).toSet(),
        containsAll(<OrganizationStatus>[
          OrganizationStatus.active,
          OrganizationStatus.suspended,
          OrganizationStatus.archived,
        ]),
      );
    });
  });
}
