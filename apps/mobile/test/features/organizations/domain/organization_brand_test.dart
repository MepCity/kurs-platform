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

  test('domain collections are immutable and compare structurally', () {
    final source = <OrganizationBrandColor>[
      const OrganizationBrandColor(colorHex: '#ABCDEF', sortOrder: 0),
    ];
    final first = OrganizationBrandColors(rowVersion: 1, items: source);
    source.clear();
    final second = OrganizationBrandColors(
      rowVersion: 1,
      items: const [OrganizationBrandColor(colorHex: '#ABCDEF', sortOrder: 0)],
    );

    expect(first, second);
    expect(first.items, hasLength(1));
    expect(() => first.items.add(second.items.single), throwsUnsupportedError);
  });
}
