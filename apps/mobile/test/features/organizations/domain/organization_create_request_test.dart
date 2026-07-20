import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';

void main() {
  group('OrganizationCreateRequest.validate', () {
    test('accepts a minimal valid request', () {
      const request = OrganizationCreateRequest(
        name: 'Fındıklı Kur\'an Kursu',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
    });

    test('rejects a blank name', () {
      const request = OrganizationCreateRequest(
        name: '   ',
        clientMutationId: 'cm_1',
      );

      final errors = request.validate();
      expect(errors.hasErrors, isTrue);
      expect(errors.name, isNotNull);
    });

    test('rejects a name over the max length', () {
      final request = OrganizationCreateRequest(
        name: 'a' * (organizationNameMaxLength + 1),
        clientMutationId: 'cm_1',
      );

      expect(request.validate().name, isNotNull);
    });

    test('accepts a name at exactly the max length', () {
      final request = OrganizationCreateRequest(
        name: 'a' * organizationNameMaxLength,
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
    });

    test('rejects a shortName over the max length', () {
      final request = OrganizationCreateRequest(
        name: 'Kurum',
        shortName: 'a' * (organizationShortNameMaxLength + 1),
        clientMutationId: 'cm_1',
      );

      expect(request.validate().shortName, isNotNull);
    });

    test('treats a blank shortName as absent (no error)', () {
      const request = OrganizationCreateRequest(
        name: 'Kurum',
        shortName: '   ',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
      expect(request.normalizedShortName, isNull);
    });

    test('accepts a well-shaped IANA timezone', () {
      const request = OrganizationCreateRequest(
        name: 'Kurum',
        defaultTimezone: 'Europe/Istanbul',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
    });

    test('accepts UTC as a timezone', () {
      const request = OrganizationCreateRequest(
        name: 'Kurum',
        defaultTimezone: 'UTC',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
    });

    test('rejects a malformed timezone', () {
      const request = OrganizationCreateRequest(
        name: 'Kurum',
        defaultTimezone: 'not a timezone!!',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().defaultTimezone, isNotNull);
    });

    for (final String zone in <String>[
      'Etc/GMT+3',
      'Etc/GMT-3',
      'America/Port-au-Prince',
      'GMT',
      'CET',
      'America/Argentina/Buenos_Aires',
    ]) {
      test('accepts the well-shaped real-world zone "$zone"', () {
        final request = OrganizationCreateRequest(
          name: 'Kurum',
          defaultTimezone: zone,
          clientMutationId: 'cm_1',
        );

        expect(request.validate().hasErrors, isFalse);
      });
    }

    test(
      'accepts a well-shaped but nonexistent zone (server validates existence)',
      () {
        const request = OrganizationCreateRequest(
          name: 'Kurum',
          defaultTimezone: 'Europe/Neverland',
          clientMutationId: 'cm_1',
        );

        expect(request.validate().hasErrors, isFalse);
      },
    );

    for (final String zone in <String>[
      'Europe//Istanbul',
      'Europe/',
      '/Europe',
      'Europe/Istan bul',
      'Europe/Istan\nbul',
      'Europe/Istan\tbul',
      '../etc/passwd',
      'Europe/../Istanbul',
      'Europe/Istanbul/Extra/Segment',
    ]) {
      test('rejects the unsafe/malformed timezone value "$zone"', () {
        final request = OrganizationCreateRequest(
          name: 'Kurum',
          defaultTimezone: zone,
          clientMutationId: 'cm_1',
        );

        expect(request.validate().defaultTimezone, isNotNull);
      });
    }

    test('falls back to Europe/Istanbul when defaultTimezone is blank', () {
      const request = OrganizationCreateRequest(
        name: 'Kurum',
        defaultTimezone: '  ',
        clientMutationId: 'cm_1',
      );

      expect(request.validate().hasErrors, isFalse);
      expect(
        request.normalizedDefaultTimezone,
        organizationDefaultTimezoneFallback,
      );
    });

    test('normalizedName trims surrounding whitespace', () {
      const request = OrganizationCreateRequest(
        name: '  Kurum  ',
        clientMutationId: 'cm_1',
      );

      expect(request.normalizedName, 'Kurum');
    });
  });

  group('OrganizationCreateFieldErrors', () {
    test('hasErrors is false when every field is null', () {
      expect(const OrganizationCreateFieldErrors().hasErrors, isFalse);
    });

    test('firstMessage returns fields in name/shortName/timezone order', () {
      const errors = OrganizationCreateFieldErrors(
        shortName: 'kısa ad hatası',
        defaultTimezone: 'saat dilimi hatası',
      );

      expect(errors.firstMessage, 'kısa ad hatası');
    });

    test('copyWith(field: null) clears that field without touching others', () {
      const errors = OrganizationCreateFieldErrors(
        name: 'ad hatası',
        shortName: 'kısa ad hatası',
      );

      final cleared = errors.copyWith(name: null);

      expect(cleared.name, isNull);
      expect(cleared.shortName, 'kısa ad hatası');
    });

    group('fromServerFieldErrors', () {
      test('maps the three recognized fields by wire name', () {
        final errors = OrganizationCreateFieldErrors.fromServerFieldErrors(
          const <String, String>{
            'name': 'Kurum adı geçersiz.',
            'shortName': 'Kısa ad geçersiz.',
            'defaultTimezone': 'Saat dilimi geçersiz.',
          },
        );

        expect(errors.name, 'Kurum adı geçersiz.');
        expect(errors.shortName, 'Kısa ad geçersiz.');
        expect(errors.defaultTimezone, 'Saat dilimi geçersiz.');
      });

      test('silently drops an unrecognized field', () {
        final errors = OrganizationCreateFieldErrors.fromServerFieldErrors(
          const <String, String>{
            'name': 'Kurum adı geçersiz.',
            'unknownField': 'Bu istemcinin bilmediği bir alan.',
          },
        );

        expect(errors.name, 'Kurum adı geçersiz.');
        expect(errors.hasErrors, isTrue);
        // Only the recognized field is present — there is no getter that
        // could surface `unknownField`, so absence of an error is the
        // assertion here.
      });

      test('an entirely unrecognized-field map has no errors', () {
        final errors = OrganizationCreateFieldErrors.fromServerFieldErrors(
          const <String, String>{'someOtherField': 'x'},
        );

        expect(errors.hasErrors, isFalse);
      });
    });
  });
}
