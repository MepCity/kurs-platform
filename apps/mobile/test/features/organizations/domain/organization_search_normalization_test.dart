import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_search_normalization.dart';

void main() {
  group('normalizeOrganizationSearchText', () {
    test('trims surrounding whitespace', () {
      expect(normalizeOrganizationSearchText('  kurs  '), 'kurs');
    });

    test('collapses null/empty/blank-only to null', () {
      expect(normalizeOrganizationSearchText(null), isNull);
      expect(normalizeOrganizationSearchText(''), isNull);
      expect(normalizeOrganizationSearchText('   '), isNull);
      expect(normalizeOrganizationSearchText(' ' * 500), isNull);
    });
  });

  group('foldForOrganizationSearchComparison (Turkish-aware)', () {
    test('dotless I/ı fold to the same value regardless of case', () {
      expect(
        foldForOrganizationSearchComparison('IĞDIR'),
        foldForOrganizationSearchComparison('ığdır'),
      );
    });

    test('dotted İ/i fold to the same value regardless of case', () {
      expect(
        foldForOrganizationSearchComparison('İSTANBUL'),
        foldForOrganizationSearchComparison('istanbul'),
      );
    });

    test('a full Turkish word with three dotless I folds correctly', () {
      expect(
        foldForOrganizationSearchComparison('FINDIKLI'),
        foldForOrganizationSearchComparison('Fındıklı'),
      );
      expect(foldForOrganizationSearchComparison('FINDIKLI'), 'fındıklı');
    });

    test('does not conflate dotted and dotless I with each other', () {
      // "İ" folds to "i", "I" folds to "ı" — they must not collapse into
      // the same result as each other.
      expect(
        foldForOrganizationSearchComparison('İ'),
        isNot(foldForOrganizationSearchComparison('I')),
      );
    });

    test('other Turkish letters fold the same as standard lowercase', () {
      expect(foldForOrganizationSearchComparison('ÇÖŞÜĞ'), 'çöşüğ');
    });

    test(
      'is a pure function: identical input always yields identical output',
      () {
        // Guards the "deterministic, not device-locale-dependent" contract —
        // there is no OS/ICU call here that could vary between Android, iOS
        // and the test VM.
        final String first = foldForOrganizationSearchComparison('IĞDIR Kursu');
        final String second = foldForOrganizationSearchComparison(
          'IĞDIR Kursu',
        );
        expect(first, second);
        expect(first, 'ığdır kursu');
      },
    );
  });
}
