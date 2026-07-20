import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/organizations/application/organization_create_controller.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_create_request.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_query.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_list_result.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organization_status.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_failure.dart';
import 'package:kurs_platform_mobile/features/organizations/domain/organizations_repository.dart';

class _QueuedResponse {
  _QueuedResponse.success(this.organization) : failure = null;
  _QueuedResponse.failure(this.failure) : organization = null;

  final Organization? organization;
  final OrganizationsFailure? failure;
}

class _FakeOrganizationsRepository implements OrganizationsRepository {
  final List<_QueuedResponse> _queue = <_QueuedResponse>[];
  final List<OrganizationCreateRequest> calls = <OrganizationCreateRequest>[];

  void queueSuccess(Organization organization) {
    _queue.add(_QueuedResponse.success(organization));
  }

  void queueFailure(OrganizationsFailure failure) {
    _queue.add(_QueuedResponse.failure(failure));
  }

  @override
  Future<Organization> createOrganization(
    OrganizationCreateRequest request,
  ) async {
    calls.add(request);
    if (_queue.isEmpty) {
      throw StateError('Sıraya konmuş yanıt yok (çağrı #${calls.length}).');
    }
    final _QueuedResponse entry = _queue.removeAt(0);
    if (entry.failure != null) {
      throw entry.failure!;
    }
    return entry.organization!;
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) => throw UnimplementedError('Not exercised by PLAT-02 tests.');
}

Organization _org(String id, String name) {
  final DateTime now = DateTime.utc(2026, 1, 1);
  return Organization(
    id: id,
    name: name,
    defaultTimezone: 'Europe/Istanbul',
    status: OrganizationStatus.active,
    createdAt: now,
    updatedAt: now,
    rowVersion: 1,
  );
}

void main() {
  group('OrganizationCreateController', () {
    test('starts in the editing state with a default timezone', () {
      final repo = _FakeOrganizationsRepository();
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      expect(controller.status, OrganizationCreateStatus.editing);
      expect(controller.defaultTimezone, organizationDefaultTimezoneFallback);
      expect(controller.fieldErrors.hasErrors, isFalse);
    });

    test(
      'submit() with an empty name sets a field error without calling the repository',
      () async {
        final repo = _FakeOrganizationsRepository();
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        await controller.submit();

        expect(controller.fieldErrors.name, isNotNull);
        expect(controller.status, OrganizationCreateStatus.editing);
        expect(repo.calls, isEmpty);
      },
    );

    test(
      'submit() moves to success and exposes the created organization',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(_org('org-1', 'Kurum'));
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();

        expect(controller.status, OrganizationCreateStatus.success);
        expect(controller.created?.id, 'org-1');
      },
    );

    test(
      'every submit() attempt uses the same Idempotency-Key while content is unchanged',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueFailure(
          const OrganizationsFailure(
            OrganizationsFailureCode.internalError,
            'Sunucu geçici olarak yanıt vermiyor.',
          ),
        );
        repo.queueSuccess(_org('org-1', 'Kurum'));
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();
        expect(controller.status, OrganizationCreateStatus.editing);
        expect(controller.bannerErrorMessage, isNotNull);

        await controller.submit();

        expect(controller.status, OrganizationCreateStatus.success);
        expect(repo.calls, hasLength(2));
        expect(repo.calls[1].clientMutationId, repo.calls[0].clientMutationId);
      },
    );

    test(
      'editing a field after a failed attempt mints a new Idempotency-Key',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueFailure(
          const OrganizationsFailure(
            OrganizationsFailureCode.internalError,
            'Sunucu geçici olarak yanıt vermiyor.',
          ),
        );
        repo.queueSuccess(_org('org-1', 'Kurum 2'));
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();

        controller.setName('Kurum 2');
        await controller.submit();

        expect(repo.calls, hasLength(2));
        expect(
          repo.calls[1].clientMutationId,
          isNot(repo.calls[0].clientMutationId),
        );
      },
    );

    test('an unauthorized failure moves to the unauthorized state', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.forbidden,
          'Bu işlem için platform yöneticisi yetkisi gerekir.',
        ),
      );
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum');
      await controller.submit();

      expect(controller.status, OrganizationCreateStatus.unauthorized);
      expect(controller.bannerErrorMessage, isNotNull);
    });

    test('setName() clears a prior name field error', () async {
      final repo = _FakeOrganizationsRepository();
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      await controller.submit();
      expect(controller.fieldErrors.name, isNotNull);

      controller.setName('Kurum');

      expect(controller.fieldErrors.name, isNull);
    });

    test(
      'startNewCreation() resets fields and mints a new Idempotency-Key',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(_org('org-1', 'Kurum'));
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();
        final String firstMutationId = repo.calls.single.clientMutationId;

        controller.startNewCreation();

        expect(controller.status, OrganizationCreateStatus.editing);
        expect(controller.name, isEmpty);
        expect(controller.created, isNull);

        repo.queueSuccess(_org('org-2', 'Kurum 2'));
        controller.setName('Kurum 2');
        await controller.submit();

        expect(repo.calls, hasLength(2));
        expect(repo.calls.last.clientMutationId, isNot(firstMutationId));
      },
    );

    test('a failed A, an invalid intermediate submit, then a retried A all '
        'share the original Idempotency-Key', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'Sunucu geçici olarak yanıt vermiyor.',
        ),
      );
      repo.queueSuccess(_org('org-1', 'Kurum A'));
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum A');
      await controller.submit();
      expect(controller.status, OrganizationCreateStatus.editing);
      final String firstKey = repo.calls.single.clientMutationId;

      // Make the form momentarily invalid — this must never reach the
      // repository, and must not touch the Idempotency-Key.
      controller.setName('   ');
      await controller.submit();
      expect(controller.fieldErrors.name, isNotNull);
      expect(repo.calls, hasLength(1));

      // Restore the original content and retry: the server may have
      // already processed the first attempt under `firstKey`, so this
      // retry must reuse it rather than mint a new one.
      controller.setName('Kurum A');
      await controller.submit();

      expect(controller.status, OrganizationCreateStatus.success);
      expect(repo.calls, hasLength(2));
      expect(repo.calls[1].clientMutationId, firstKey);
    });

    test('a failed A submission followed by a valid, different B submission '
        'mints a new Idempotency-Key', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'Sunucu geçici olarak yanıt vermiyor.',
        ),
      );
      repo.queueSuccess(_org('org-2', 'Kurum B'));
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum A');
      await controller.submit();
      final String firstKey = repo.calls.single.clientMutationId;

      controller.setName('Kurum B');
      await controller.submit();

      expect(controller.status, OrganizationCreateStatus.success);
      expect(repo.calls, hasLength(2));
      expect(repo.calls[1].clientMutationId, isNot(firstKey));
    });

    test('an invalid first-ever submit() does not consume or change the '
        'initial Idempotency-Key', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueSuccess(_org('org-1', 'Kurum A'));
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      await controller.submit();
      expect(controller.fieldErrors.name, isNotNull);
      expect(repo.calls, isEmpty);

      controller.setName('Kurum A');
      await controller.submit();

      expect(controller.status, OrganizationCreateStatus.success);
      expect(repo.calls, hasLength(1));
    });

    test('a server VALIDATION_FAILED with a defaultTimezone field error is '
        'shown on the timezone field', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        OrganizationsFailure(
          OrganizationsFailureCode.validationFailed,
          'Saat dilimi geçersiz.',
          fieldErrors: const OrganizationCreateFieldErrors(
            defaultTimezone: 'Saat dilimi geçersiz.',
          ),
        ),
      );
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum');
      await controller.submit();

      expect(controller.status, OrganizationCreateStatus.editing);
      expect(controller.fieldErrors.defaultTimezone, isNotNull);
      expect(controller.fieldErrors.name, isNull);
      expect(controller.bannerErrorMessage, isNull);
    });

    test('a server VALIDATION_FAILED with name and shortName field errors '
        'routes each to its own field', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        OrganizationsFailure(
          OrganizationsFailureCode.validationFailed,
          'Alanlar geçersiz.',
          fieldErrors: const OrganizationCreateFieldErrors(
            name: 'Kurum adı geçersiz.',
            shortName: 'Kısa ad geçersiz.',
          ),
        ),
      );
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum');
      await controller.submit();

      expect(controller.fieldErrors.name, 'Kurum adı geçersiz.');
      expect(controller.fieldErrors.shortName, 'Kısa ad geçersiz.');
    });

    test('a server VALIDATION_FAILED with no recognized field falls back to '
        'the banner message', () async {
      final repo = _FakeOrganizationsRepository();
      repo.queueFailure(
        const OrganizationsFailure(
          OrganizationsFailureCode.validationFailed,
          'Bilinmeyen bir alan geçersiz.',
        ),
      );
      final controller = OrganizationCreateController(repository: repo);
      addTearDown(controller.dispose);

      controller.setName('Kurum');
      await controller.submit();

      expect(controller.fieldErrors.hasErrors, isFalse);
      expect(controller.bannerErrorMessage, 'Bilinmeyen bir alan geçersiz.');
    });

    test(
      'editing the field with a server-side error clears only that field',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueFailure(
          OrganizationsFailure(
            OrganizationsFailureCode.validationFailed,
            'Alanlar geçersiz.',
            fieldErrors: const OrganizationCreateFieldErrors(
              name: 'Kurum adı geçersiz.',
              shortName: 'Kısa ad geçersiz.',
            ),
          ),
        );
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();
        expect(controller.fieldErrors.name, isNotNull);
        expect(controller.fieldErrors.shortName, isNotNull);

        controller.setShortName('Yeni Kısa Ad');

        expect(controller.fieldErrors.shortName, isNull);
        expect(controller.fieldErrors.name, isNotNull);
      },
    );

    test(
      'an ORGANIZATION_CONTEXT_REQUIRED failure moves to the unauthorized state',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueFailure(
          const OrganizationsFailure(
            OrganizationsFailureCode.organizationContextRequired,
            'Kurum oluşturma yalnızca platform genel bağlamında yapılabilir.',
          ),
        );
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        await controller.submit();

        expect(controller.status, OrganizationCreateStatus.unauthorized);
      },
    );

    test(
      'submit() is a no-op while a submission is already in flight',
      () async {
        final repo = _FakeOrganizationsRepository();
        repo.queueSuccess(_org('org-1', 'Kurum'));
        final controller = OrganizationCreateController(repository: repo);
        addTearDown(controller.dispose);

        controller.setName('Kurum');
        // submit() runs synchronously up to its first `await`, so by the time
        // this second call executes, `_status` is already `submitting` and it
        // returns immediately without a second repository call.
        final Future<void> firstSubmit = controller.submit();
        final Future<void> secondSubmit = controller.submit();

        await Future.wait(<Future<void>>[firstSubmit, secondSubmit]);

        expect(repo.calls, hasLength(1));
      },
    );
  });
}
