import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_brand.dart';

void main() {
  group('validateBrandHex', () {
    test('normalizes contract-valid colours and rejects forbidden values', () {
      expect(validateBrandHex('Ana renk', '#2e7d32'), isNull);
      expect(normalizeBrandHex(' #2e7d32 '), '#2E7D32');
      expect(validateBrandHex('Ana renk', '#FFFFFF'), isNotNull);
      expect(validateBrandHex('Ana renk', '#E0E0E0'), isNotNull);
      expect(validateBrandHex('Ana renk', '#123'), isNotNull);
    });
  });
}
