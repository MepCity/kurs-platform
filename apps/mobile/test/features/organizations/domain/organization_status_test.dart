import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';

void main() {
  group('OrganizationStatus', () {
    test('fromWire parses the three contract values', () {
      expect(OrganizationStatus.fromWire('ACTIVE'), OrganizationStatus.active);
      expect(
        OrganizationStatus.fromWire('SUSPENDED'),
        OrganizationStatus.suspended,
      );
      expect(
        OrganizationStatus.fromWire('ARCHIVED'),
        OrganizationStatus.archived,
      );
    });

    test('fromWire rejects unknown values', () {
      expect(
        () => OrganizationStatus.fromWire('DELETED'),
        throwsFormatException,
      );
      expect(() => OrganizationStatus.fromWire(''), throwsFormatException);
    });

    test('toWire round-trips through fromWire for every value', () {
      for (final OrganizationStatus status in OrganizationStatus.values) {
        expect(OrganizationStatus.fromWire(status.toWire()), status);
      }
    });
  });
}
