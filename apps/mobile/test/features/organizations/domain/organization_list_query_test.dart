import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';

void main() {
  group('OrganizationListQuery', () {
    test('defaults to name/ascending, no filter, limit 20', () {
      const OrganizationListQuery query = OrganizationListQuery();
      expect(query.status, isNull);
      expect(query.search, isNull);
      expect(query.sort, OrganizationSortField.name);
      expect(query.order, OrganizationSortOrder.ascending);
      expect(query.limit, 20);
      expect(query.cursor, isNull);
    });

    test('copyWith replaces only the given fields', () {
      const OrganizationListQuery query = OrganizationListQuery(
        search: 'fındıklı',
        limit: 5,
      );
      final OrganizationListQuery updated = query.copyWith(
        status: OrganizationStatus.active,
      );

      expect(updated.status, OrganizationStatus.active);
      expect(updated.search, 'fındıklı');
      expect(updated.limit, 5);
    });

    test('copyWith clearStatus resets status to null', () {
      const OrganizationListQuery query = OrganizationListQuery(
        status: OrganizationStatus.suspended,
      );
      final OrganizationListQuery updated = query.copyWith(clearStatus: true);
      expect(updated.status, isNull);
    });

    test('withCursor preserves every other filter field', () {
      const OrganizationListQuery query = OrganizationListQuery(
        status: OrganizationStatus.active,
        search: 'kur',
        sort: OrganizationSortField.createdAt,
        order: OrganizationSortOrder.descending,
        limit: 7,
      );
      final OrganizationListQuery next = query.withCursor('abc');

      expect(next.cursor, 'abc');
      expect(next.status, query.status);
      expect(next.search, query.search);
      expect(next.sort, query.sort);
      expect(next.order, query.order);
      expect(next.limit, query.limit);
    });

    test('hasSameFilterContext ignores cursor but not limit', () {
      const OrganizationListQuery a = OrganizationListQuery(
        status: OrganizationStatus.active,
        search: 'x',
        limit: 5,
      );
      final OrganizationListQuery sameLimit = a.withCursor('cursor-1');
      final OrganizationListQuery differentLimit = a.copyWith(limit: 50);

      expect(a.hasSameFilterContext(sameLimit), isTrue);
      expect(
        a.hasSameFilterContext(differentLimit),
        isFalse,
        reason:
            'limit is part of the cursor-binding context per §5.3; a page '
            'size change must not silently replay an old cursor',
      );
    });

    test('hasSameFilterContext detects a changed filter', () {
      const OrganizationListQuery a = OrganizationListQuery(search: 'x');
      const OrganizationListQuery b = OrganizationListQuery(search: 'y');

      expect(a.hasSameFilterContext(b), isFalse);
    });

    test('normalized() trims and blank-collapses search', () {
      const OrganizationListQuery padded = OrganizationListQuery(
        search: '  kurs  ',
      );
      const OrganizationListQuery blankOnly = OrganizationListQuery(
        search: '   ',
      );
      const OrganizationListQuery noSearch = OrganizationListQuery();

      expect(padded.normalized().search, 'kurs');
      expect(blankOnly.normalized().search, isNull);
      expect(noSearch.normalized().search, isNull);
    });

    test('normalized() is a no-op when search is already canonical', () {
      const OrganizationListQuery query = OrganizationListQuery(
        search: 'kurs',
        limit: 5,
      );
      final OrganizationListQuery result = query.normalized();

      expect(identical(result, query), isTrue);
    });
  });
}
