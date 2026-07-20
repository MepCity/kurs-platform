import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/application/organization_list_controller.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_repository.dart';

class _QueuedResponse {
  _QueuedResponse.success(this.result, {this.delay = Duration.zero})
    : failure = null;
  _QueuedResponse.failure(this.failure, {this.delay = Duration.zero})
    : result = null;

  final OrganizationListResult? result;
  final OrganizationsFailure? failure;
  final Duration delay;
}

class _FakeOrganizationsRepository implements OrganizationsRepository {
  final List<_QueuedResponse> _queue = <_QueuedResponse>[];
  final List<OrganizationListQuery> calls = <OrganizationListQuery>[];

  void queueSuccess(
    OrganizationListResult result, {
    Duration delay = Duration.zero,
  }) {
    _queue.add(_QueuedResponse.success(result, delay: delay));
  }

  void queueFailure(
    OrganizationsFailure failure, {
    Duration delay = Duration.zero,
  }) {
    _queue.add(_QueuedResponse.failure(failure, delay: delay));
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) async {
    calls.add(query);
    if (_queue.isEmpty) {
      throw StateError('Sıraya konmuş yanıt yok (çağrı #${calls.length}).');
    }
    final _QueuedResponse entry = _queue.removeAt(0);
    if (entry.delay > Duration.zero) {
      await Future<void>.delayed(entry.delay);
    }
    if (entry.failure != null) {
      throw entry.failure!;
    }
    return entry.result!;
  }
}

Organization _org(
  String id, {
  OrganizationStatus status = OrganizationStatus.active,
}) {
  final DateTime now = DateTime.utc(2026, 1, 1);
  return Organization(
    id: id,
    name: 'Kurum $id',
    defaultTimezone: 'Europe/Istanbul',
    status: status,
    createdAt: now,
    updatedAt: now,
    rowVersion: 1,
  );
}

Future<void> _settle([Duration duration = const Duration(milliseconds: 20)]) {
  return Future<void>.delayed(duration);
}

void main() {
  group('OrganizationListController', () {
    test('starts in loading state before load() is called', () {
      final repo = _FakeOrganizationsRepository();
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      expect(controller.viewStatus, OrganizationListViewStatus.loading);
      expect(controller.items, isEmpty);
      expect(repo.calls, isEmpty);
    });

    test('load() populates items and moves to content on success', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('1'), _org('2')],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      await controller.load();

      expect(controller.viewStatus, OrganizationListViewStatus.content);
      expect(controller.items.map((o) => o.id), <String>['1', '2']);
      expect(controller.hasNextPage, isFalse);
    });

    test('load() moves to empty state when there are no items', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        const OrganizationListResult(
          items: <Organization>[],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      await controller.load();

      expect(controller.viewStatus, OrganizationListViewStatus.empty);
    });

    test('load() moves to error state for a non-auth failure', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'sunucu hatası',
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      await controller.load();

      expect(controller.viewStatus, OrganizationListViewStatus.error);
      expect(controller.errorMessage, 'sunucu hatası');
    });

    for (final code in <OrganizationsFailureCode>[
      OrganizationsFailureCode.forbidden,
      OrganizationsFailureCode.unauthenticated,
    ]) {
      test('load() moves to unauthorized state for ${code.name}', () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueFailure(OrganizationsFailure(code, 'yetkisiz'));
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);

        await controller.load();

        expect(controller.viewStatus, OrganizationListViewStatus.unauthorized);
      });
    }

    test(
      'an unexpected exception is treated as a generic error, not a crash',
      () async {
        final repo = _FakeOrganizationsRepository();
        // No queued response: the fake throws StateError, exercising the
        // controller's catch-all branch.
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);

        await controller.load();

        expect(controller.viewStatus, OrganizationListViewStatus.error);
        expect(controller.errorMessage, isNotNull);
      },
    );

    test('retry() re-issues the load with the same filters', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'ilk deneme başarısız',
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);
      await controller.load();
      expect(controller.viewStatus, OrganizationListViewStatus.error);

      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('1')],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      await controller.retry();

      expect(controller.viewStatus, OrganizationListViewStatus.content);
      expect(repo.calls, hasLength(2));
    });

    test('search() debounces rapid input into a single request', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('1')],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(
        repository: repo,
        searchDebounce: const Duration(milliseconds: 10),
      );
      addTearDown(controller.dispose);

      controller.search('a');
      controller.search('ab');
      controller.search('abc');
      expect(repo.calls, isEmpty, reason: 'debounce henüz dolmadı');

      await _settle(const Duration(milliseconds: 40));

      expect(repo.calls, hasLength(1));
      expect(repo.calls.single.search, 'abc');
    });

    test('filterByStatus() reloads immediately without debounce', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        const OrganizationListResult(
          items: <Organization>[],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      controller.filterByStatus(OrganizationStatus.suspended);
      await _settle();

      expect(repo.calls, hasLength(1));
      expect(repo.calls.single.status, OrganizationStatus.suspended);
    });

    test('filterByStatus() is a no-op when the value is unchanged', () async {
      final repo = _FakeOrganizationsRepository();
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      controller.filterByStatus(null);
      await _settle();

      expect(repo.calls, isEmpty);
    });

    test('isFiltered reflects active search text and status filter', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        const OrganizationListResult(
          items: <Organization>[],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(
        repository: repo,
        searchDebounce: const Duration(milliseconds: 5),
      );
      addTearDown(controller.dispose);

      expect(controller.isFiltered, isFalse);

      controller.search('kurs');
      await _settle(const Duration(milliseconds: 20));
      expect(controller.isFiltered, isTrue);
    });

    test(
      'loadMore() appends the next page and stays in content state',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1'), _org('2')],
            nextCursor: 'cursor-1',
            hasNextPage: true,
          ),
        );
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);
        await controller.load();

        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('3')],
            nextCursor: null,
            hasNextPage: false,
          ),
        );
        await controller.loadMore();

        expect(controller.items.map((o) => o.id), <String>['1', '2', '3']);
        expect(controller.viewStatus, OrganizationListViewStatus.content);
        expect(controller.hasNextPage, isFalse);
        expect(repo.calls, hasLength(2));
        expect(repo.calls.last.cursor, 'cursor-1');
      },
    );

    test('loadMore() is a no-op when there is no next page', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('1')],
          nextCursor: null,
          hasNextPage: false,
        ),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);
      await controller.load();

      await controller.loadMore();

      expect(repo.calls, hasLength(1));
    });

    test(
      'loadMore() ignores a second call while one is already in flight',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: 'cursor-1',
            hasNextPage: true,
          ),
        );
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);
        await controller.load();

        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('2')],
            nextCursor: null,
            hasNextPage: false,
          ),
          delay: const Duration(milliseconds: 30),
        );

        final Future<void> firstCall = controller.loadMore();
        final Future<void> secondCall = controller.loadMore();
        await Future.wait(<Future<void>>[firstCall, secondCall]);

        expect(
          repo.calls,
          hasLength(2),
          reason: 'yalnız 1 loadMore isteği gitmeli',
        );
        expect(controller.items.map((o) => o.id), <String>['1', '2']);
      },
    );

    test(
      'a failed loadMore() surfaces loadMoreErrorMessage but keeps existing items',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: 'cursor-1',
            hasNextPage: true,
          ),
        );
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);
        await controller.load();

        repo.queueFailure(
          const OrganizationsFailure(
            OrganizationsFailureCode.internalError,
            'sayfa yüklenemedi',
          ),
        );
        await controller.loadMore();

        expect(controller.viewStatus, OrganizationListViewStatus.content);
        expect(controller.items.map((o) => o.id), <String>['1']);
        expect(controller.loadMoreErrorMessage, 'sayfa yüklenemedi');
        expect(controller.hasNextPage, isTrue);

        controller.acknowledgeLoadMoreError();
        expect(controller.loadMoreErrorMessage, isNull);
      },
    );

    test('a stale (superseded) load response is discarded', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('stale')],
          nextCursor: null,
          hasNextPage: false,
        ),
        delay: const Duration(milliseconds: 40),
      );
      repo.queueSuccess(
        OrganizationListResult(
          items: <Organization>[_org('fresh')],
          nextCursor: null,
          hasNextPage: false,
        ),
        delay: const Duration(milliseconds: 5),
      );
      final controller = OrganizationListController(repository: repo);
      addTearDown(controller.dispose);

      final Future<void> firstLoad = controller.load();
      final Future<void> secondLoad = controller.load();
      await Future.wait(<Future<void>>[firstLoad, secondLoad]);
      await _settle(const Duration(milliseconds: 60));

      expect(controller.items.map((o) => o.id), <String>['fresh']);
      expect(controller.viewStatus, OrganizationListViewStatus.content);
    });

    group('authorization loss clears stale data', () {
      for (final code in <OrganizationsFailureCode>[
        OrganizationsFailureCode.unauthenticated,
        OrganizationsFailureCode.forbidden,
      ]) {
        test(
          'a loadMore() ${code.name} failure clears items and goes straight to unauthorized',
          () async {
            final repo = _FakeOrganizationsRepository();
            repo.queueSuccess(
              OrganizationListResult(
                items: <Organization>[_org('1'), _org('2')],
                nextCursor: 'cursor-1',
                hasNextPage: true,
              ),
            );
            final controller = OrganizationListController(repository: repo);
            addTearDown(controller.dispose);
            await controller.load();
            expect(controller.items, hasLength(2));

            repo.queueFailure(OrganizationsFailure(code, 'oturum geçersiz'));
            await controller.loadMore();

            expect(
              controller.viewStatus,
              OrganizationListViewStatus.unauthorized,
            );
            expect(
              controller.items,
              isEmpty,
              reason: 'stale platform-wide data must not remain reachable',
            );
            expect(controller.hasNextPage, isFalse);
            expect(controller.isLoadingMore, isFalse);
            expect(
              controller.loadMoreErrorMessage,
              isNull,
              reason: 'an auth failure must not surface as a loadMore snackbar',
            );
          },
        );
      }

      test(
        'a normal (non-auth) loadMore() failure is not confused with an authorization failure',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: 'cursor-1',
              hasNextPage: true,
            ),
          );
          final controller = OrganizationListController(repository: repo);
          addTearDown(controller.dispose);
          await controller.load();

          repo.queueFailure(
            const OrganizationsFailure(
              OrganizationsFailureCode.internalError,
              'geçici sunucu hatası',
            ),
          );
          await controller.loadMore();

          expect(controller.viewStatus, OrganizationListViewStatus.content);
          expect(controller.items.map((o) => o.id), <String>['1']);
          expect(controller.loadMoreErrorMessage, 'geçici sunucu hatası');
        },
      );

      test(
        'an unauthorized initial load() also clears any previously loaded items',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          final controller = OrganizationListController(repository: repo);
          addTearDown(controller.dispose);
          await controller.load();
          expect(controller.items, hasLength(1));

          repo.queueFailure(
            const OrganizationsFailure(
              OrganizationsFailureCode.forbidden,
              'yetki iptal edildi',
            ),
          );
          controller.filterByStatus(OrganizationStatus.suspended);
          await _settle();

          expect(
            controller.viewStatus,
            OrganizationListViewStatus.unauthorized,
          );
          expect(controller.items, isEmpty);
        },
      );
    });

    test(
      'an inconsistent envelope (hasNextPage without a cursor) is treated as an error, not trusted',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: null,
            hasNextPage: true,
          ),
        );
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);

        await controller.load();

        expect(controller.viewStatus, OrganizationListViewStatus.error);
      },
    );

    test(
      'loadMore() no-ops while a debounced search reload is pending',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: 'cursor-1',
            hasNextPage: true,
          ),
        );
        final controller = OrganizationListController(
          repository: repo,
          searchDebounce: const Duration(milliseconds: 30),
        );
        addTearDown(controller.dispose);
        await controller.load();

        controller.search('kurs');
        await controller.loadMore();

        expect(
          repo.calls,
          hasLength(1),
          reason:
              'loadMore must not fire while a reload is about to replace '
              'the current filter context',
        );

        await _settle(const Duration(milliseconds: 50));
        expect(repo.calls, hasLength(2));
        expect(repo.calls.last.search, 'kurs');
      },
    );

    test(
      'calling load()/search()/filterByStatus()/loadMore() after dispose is a safe no-op',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: null,
            hasNextPage: false,
          ),
        );
        final controller = OrganizationListController(
          repository: repo,
          searchDebounce: const Duration(milliseconds: 10),
        );
        await controller.load();
        controller.search('pending');

        controller.dispose();

        expect(() => controller.load(), returnsNormally);
        expect(() => controller.search('after-dispose'), returnsNormally);
        expect(
          () => controller.filterByStatus(OrganizationStatus.active),
          returnsNormally,
        );
        expect(() => controller.loadMore(), returnsNormally);

        // The debounce timer scheduled before dispose must not fire and call
        // notifyListeners() on a disposed ChangeNotifier.
        await _settle(const Duration(milliseconds: 40));
      },
    );

    group('search() invalidates an in-flight loadMore() immediately', () {
      test(
        'a stale loadMore() that succeeds after the search change does not append',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: 'cursor-1',
              hasNextPage: true,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 30),
          );
          addTearDown(controller.dispose);
          await controller.load();

          // loadMore() starts and is in flight (not yet resolved)...
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('stale-page-2')],
              nextCursor: null,
              hasNextPage: false,
            ),
            delay: const Duration(milliseconds: 20),
          );
          final Future<void> pendingLoadMore = controller.loadMore();
          expect(controller.isLoadingMore, isTrue);

          // ...then the user types, which must supersede it immediately,
          // before the debounce (30ms) or the stale loadMore (20ms) fire.
          controller.search('kurs');
          expect(
            controller.isLoadingMore,
            isFalse,
            reason: 'search() must clear a superseded loadMore in flight',
          );

          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('fresh')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );

          await pendingLoadMore;
          await _settle(const Duration(milliseconds: 60));

          expect(
            controller.items.map((o) => o.id),
            <String>['fresh'],
            reason:
                "the stale loadMore's page-2 item must never have been "
                'appended',
          );
          expect(repo.calls.last.search, 'kurs');
        },
      );

      test(
        'a stale loadMore() that fails after the search change does not surface a snackbar',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: 'cursor-1',
              hasNextPage: true,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 30),
          );
          addTearDown(controller.dispose);
          await controller.load();

          repo.queueFailure(
            const OrganizationsFailure(
              OrganizationsFailureCode.internalError,
              'stale sayfa hatası',
            ),
            delay: const Duration(milliseconds: 20),
          );
          final Future<void> pendingLoadMore = controller.loadMore();

          controller.search('kurs');
          expect(controller.loadMoreErrorMessage, isNull);

          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('fresh')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );

          await pendingLoadMore;
          await _settle(const Duration(milliseconds: 60));

          expect(
            controller.loadMoreErrorMessage,
            isNull,
            reason: "a superseded loadMore's failure must not surface",
          );
          expect(controller.items.map((o) => o.id), <String>['fresh']);
        },
      );

      test(
        'search("kurs") then search("  kurs  ") does not issue a second request',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 10),
          );
          addTearDown(controller.dispose);

          controller.search('kurs');
          await _settle(const Duration(milliseconds: 30));
          expect(repo.calls, hasLength(1));

          controller.search('  kurs  ');
          await _settle(const Duration(milliseconds: 30));

          expect(
            repo.calls,
            hasLength(1),
            reason:
                '"kurs" and "  kurs  " are the same canonical query — no '
                'second request, no generation bump',
          );
        },
      );

      test(
        'three rapid searches only ever load the final canonical value',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 20),
          );
          addTearDown(controller.dispose);

          controller.search('k');
          controller.search('ku');
          controller.search('  kurs  ');
          await _settle(const Duration(milliseconds: 50));

          expect(repo.calls, hasLength(1));
          expect(repo.calls.single.search, 'kurs');
        },
      );
    });

    group('canonical search state', () {
      // A blank search from the pristine state (search text already '') is
      // a same-canonical no-op and issues no request at all — these tests
      // instead exercise the meaningful transition: a real filter, cleared
      // back to blank.
      test(
        'a blank-only search clears a previous filter back to unfiltered',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 10),
          );
          addTearDown(controller.dispose);
          await controller.load();

          repo.queueSuccess(
            const OrganizationListResult(
              items: <Organization>[],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          controller.search('kurs');
          await _settle(const Duration(milliseconds: 30));
          expect(controller.isFiltered, isTrue);

          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          controller.search('   ');
          await _settle(const Duration(milliseconds: 30));

          expect(controller.isFiltered, isFalse);
          expect(repo.calls.last.search, isNull);
        },
      );

      test(
        'empty results after clearing a search back to blank show the unfiltered empty copy',
        () async {
          final repo = _FakeOrganizationsRepository();
          repo.queueSuccess(
            OrganizationListResult(
              items: <Organization>[_org('1')],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          final controller = OrganizationListController(
            repository: repo,
            searchDebounce: const Duration(milliseconds: 10),
          );
          addTearDown(controller.dispose);
          await controller.load();

          repo.queueSuccess(
            const OrganizationListResult(
              items: <Organization>[],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          controller.search('kurs');
          await _settle(const Duration(milliseconds: 30));

          // Clearing the search back to blank, with no organizations at
          // all, must land on the *unfiltered* empty copy.
          repo.queueSuccess(
            const OrganizationListResult(
              items: <Organization>[],
              nextCursor: null,
              hasNextPage: false,
            ),
          );
          controller.search('   ');
          await _settle(const Duration(milliseconds: 30));

          expect(controller.viewStatus, OrganizationListViewStatus.empty);
          expect(
            controller.isFiltered,
            isFalse,
            reason:
                'presentation renders "Henüz kurum yok", not "Sonuç '
                'bulunamadı", only when isFiltered is false',
          );
        },
      );
    });

    test(
      'an inconsistent envelope (hasNextPage: false with a non-null cursor) is treated as an error',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(
          OrganizationListResult(
            items: <Organization>[_org('1')],
            nextCursor: 'dangling-cursor',
            hasNextPage: false,
          ),
        );
        final controller = OrganizationListController(repository: repo);
        addTearDown(controller.dispose);

        await controller.load();

        expect(controller.viewStatus, OrganizationListViewStatus.error);
      },
    );
  });
}
