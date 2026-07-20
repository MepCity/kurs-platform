import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';

void main() {
  group('OrganizationListResult.isEnvelopeConsistent', () {
    test('hasNextPage: true with a null cursor is inconsistent', () {
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: null,
        hasNextPage: true,
      );

      expect(result.isEnvelopeConsistent, isFalse);
    });

    test('hasNextPage: false with a non-null cursor is inconsistent', () {
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: 'dangling-cursor',
        hasNextPage: false,
      );

      expect(
        result.isEnvelopeConsistent,
        isFalse,
        reason:
            'a cursor that will never be replayed is just as broken as a '
            'missing one — this is an equivalence, not a one-directional '
            'implication',
      );
    });

    test('hasNextPage: true with an empty-string cursor is inconsistent', () {
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: '',
        hasNextPage: true,
      );

      expect(result.isEnvelopeConsistent, isFalse);
    });

    test('hasNextPage: false with an empty-string cursor is inconsistent', () {
      // An empty-string cursor is never valid, regardless of hasNextPage.
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: '',
        hasNextPage: false,
      );

      expect(result.isEnvelopeConsistent, isFalse);
    });

    test('hasNextPage: true with a real cursor is consistent', () {
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: 'ck_abc123',
        hasNextPage: true,
      );

      expect(result.isEnvelopeConsistent, isTrue);
    });

    test('hasNextPage: false with a null cursor is consistent', () {
      const OrganizationListResult result = OrganizationListResult(
        items: <Organization>[],
        nextCursor: null,
        hasNextPage: false,
      );

      expect(result.isEnvelopeConsistent, isTrue);
    });
  });
}
