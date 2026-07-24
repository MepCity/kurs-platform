import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/application/sign_in_controller.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';

AuthOrganizationMembership _membership(String id) => AuthOrganizationMembership(
  id: id,
  organizationId: 'org-$id',
  organizationName: 'Kurs $id',
  roleCodes: const <String>['ORG_ADMIN'],
);

SecureSession _organizationSession(String membershipId) => SecureSession(
  userId: 'user-1',
  deviceId: 'device-1',
  scope: SecureSessionScope.organization,
  accessToken: 'access-$membershipId',
  refreshToken: 'refresh-$membershipId',
  authenticatedAt: DateTime.utc(2026, 7, 24, 9),
  expiresAt: DateTime.utc(2026, 7, 24, 10),
  refreshExpiresAt: DateTime.utc(2026, 8, 24, 10),
  organizationMembershipId: membershipId,
  organizationId: 'org-$membershipId',
  sessionGeneration: 1,
);

AuthenticatedSessionActivation _organizationActivation(String membershipId) =>
    AuthenticatedSessionActivation(
      session: ActivatedSession(
        scope: ActivatedSessionScope.organization,
        displayName: 'Yasir',
        organizationMembership: _membership(membershipId),
      ),
      secureSession: _organizationSession(membershipId),
    );

class _Repository implements AuthenticationRepository {
  _Repository(
    this.organizationActivation, {
    this.platformActivation,
    AuthContextChoices? choices,
  }) : choices =
           choices ??
           AuthContextChoices(
             displayName: 'Yasir',
             memberships: <AuthOrganizationMembership>[
               _membership('A'),
               _membership('B'),
             ],
             canActivatePlatformAdministrator: false,
           );
  final Future<AuthenticatedSessionActivation> Function()
  organizationActivation;
  final Future<AuthenticatedSessionActivation> Function()? platformActivation;
  final AuthContextChoices choices;
  int organizationCalls = 0;

  @override
  Future<AuthenticatedSessionActivation> activateOrganization(
    String membershipId,
  ) {
    organizationCalls++;
    return organizationActivation();
  }

  @override
  Future<AuthenticatedSessionActivation> activatePlatformAdministrator() =>
      platformActivation?.call() ??
      Future<AuthenticatedSessionActivation>.error(UnimplementedError());

  @override
  Future<AuthContextChoices> beginSignIn() => Future.value(choices);
}

class _CountingStore implements SecureSessionStore {
  int _next = 0;
  int commits = 0;
  SecureSession? value;

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      SecureSessionWriteLease(++_next);

  @override
  void abandonActivation(SecureSessionWriteLease lease) {}

  @override
  Future<void> clear() async => value = null;

  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async {
    commits++;
    value = session;
    return true;
  }

  @override
  Future<SecureSession?> read() async => value;
}

class _LatestStore extends _CountingStore {
  int _latest = 0;

  @override
  Future<SecureSessionWriteLease> beginActivation() async {
    final lease = await super.beginActivation();
    _latest = lease.value;
    return lease;
  }

  @override
  void abandonActivation(SecureSessionWriteLease lease) {
    if (lease.value == _latest) _latest++;
  }

  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async {
    if (lease.value != _latest) return false;
    return super.commit(lease, session);
  }
}

void main() {
  test(
    'seçilen membership A, repository sonucu B: write ve UI aktivasyonu sıfır',
    () async {
      final repository = _Repository(() async => _organizationActivation('B'));
      final store = _CountingStore();
      final controller = SignInController(
        repository: repository,
        secureSessionStore: store,
      );

      final result = await controller.activateOrganization('A');

      expect(result, isNull);
      expect(store.commits, 0);
    },
  );

  test('choices yoksa repository ve secure write çağrılmaz', () async {
    final repository = _Repository(() async => _organizationActivation('A'));
    final store = _CountingStore();
    final controller = SignInController(
      repository: repository,
      secureSessionStore: store,
    );
    expect(await controller.activateOrganization('A'), isNull);
    expect(repository.organizationCalls, 0);
    expect(store.commits, 0);
  });

  test('çelişkili aynı membership seçimleri fail closed reddedilir', () async {
    final repository = _Repository(
      () async => _organizationActivation('A'),
      choices: const AuthContextChoices(
        displayName: 'Yasir',
        memberships: <AuthOrganizationMembership>[
          AuthOrganizationMembership(
            id: 'A',
            organizationId: 'org-1',
            organizationName: 'Bir',
            roleCodes: <String>[],
          ),
          AuthOrganizationMembership(
            id: 'A',
            organizationId: 'org-2',
            organizationName: 'İki',
            roleCodes: <String>[],
          ),
        ],
        canActivatePlatformAdministrator: false,
      ),
    );
    final store = _CountingStore();
    final controller = SignInController(
      repository: repository,
      secureSessionStore: store,
    );
    await controller.begin();
    expect(await controller.activateOrganization('A'), isNull);
    expect(repository.organizationCalls, 0);
    expect(store.commits, 0);
  });

  test('repository beklerken controller dispose edilir: write sıfır', () async {
    final result = Completer<AuthenticatedSessionActivation>();
    final repository = _Repository(() => result.future);
    final store = _CountingStore();
    final controller = SignInController(
      repository: repository,
      secureSessionStore: store,
    );
    final activation = controller.activateOrganization('A');

    controller.dispose();
    result.complete(_organizationActivation('A'));

    expect(await activation, isNull);
    expect(store.commits, 0);
  });

  test('retry bekleyen aktivasyonu geçersizleştirir: write sıfır', () async {
    final result = Completer<AuthenticatedSessionActivation>();
    final store = _LatestStore();
    final controller = SignInController(
      repository: _Repository(() => result.future),
      secureSessionStore: store,
    );
    final activation = controller.activateOrganization('A');

    controller.retry();
    result.complete(_organizationActivation('A'));

    expect(await activation, isNull);
    expect(store.commits, 0);
  });

  test('aynı controller çift tetiklemede tek aktivasyon üretir', () async {
    final result = Completer<AuthenticatedSessionActivation>();
    final repository = _Repository(() => result.future);
    final store = _CountingStore();
    final controller = SignInController(
      repository: repository,
      secureSessionStore: store,
    );
    await controller.begin();

    final first = controller.activateOrganization('A');
    final second = controller.activateOrganization('A');
    result.complete(_organizationActivation('A'));

    expect(await second, isNull);
    expect((await first)?.organizationMembership?.id, 'A');
    expect(repository.organizationCalls, 1);
    expect(store.commits, 1);
  });

  test('eski controller sonucu yeni controller oturumunu ezemez', () async {
    final oldResult = Completer<AuthenticatedSessionActivation>();
    final sharedStore = _LatestStore();
    final oldController = SignInController(
      repository: _Repository(() => oldResult.future),
      secureSessionStore: sharedStore,
    );
    final newController = SignInController(
      repository: _Repository(() async => _organizationActivation('B')),
      secureSessionStore: sharedStore,
    );
    await oldController.begin();
    await newController.begin();

    final oldActivation = oldController.activateOrganization('A');
    final newActivation = await newController.activateOrganization('B');
    oldResult.complete(_organizationActivation('A'));

    expect(newActivation?.organizationMembership?.id, 'B');
    expect(await oldActivation, isNull);
    expect(sharedStore.value?.organizationMembershipId, 'B');
    expect(sharedStore.commits, 1);
  });

  test(
    'aynı membership farklı organizationId token yazmadan reddedilir',
    () async {
      final store = _CountingStore();
      final controller = SignInController(
        repository: _Repository(
          () async => _organizationActivation('A'),
          choices: AuthContextChoices(
            displayName: 'Yasir',
            memberships: <AuthOrganizationMembership>[
              const AuthOrganizationMembership(
                id: 'A',
                organizationId: 'org-selected',
                organizationName: 'Seçilen',
                roleCodes: <String>['ORG_ADMIN'],
              ),
            ],
            canActivatePlatformAdministrator: false,
          ),
        ),
        secureSessionStore: store,
      );

      await controller.begin();
      expect(await controller.activateOrganization('A'), isNull);
      expect(store.commits, 0);
    },
  );

  test(
    'seçilen organizationId güvenli oturumla eşleşince aktivasyon başarılı',
    () async {
      final store = _CountingStore();
      final controller = SignInController(
        repository: _Repository(
          () async => _organizationActivation('A'),
          choices: AuthContextChoices(
            displayName: 'Yasir',
            memberships: <AuthOrganizationMembership>[_membership('A')],
            canActivatePlatformAdministrator: false,
          ),
        ),
        secureSessionStore: store,
      );

      await controller.begin();
      expect(
        (await controller.activateOrganization(
          'A',
        ))?.organizationMembership?.id,
        'A',
      );
      expect(store.commits, 1);
    },
  );

  test(
    'organization/global activation invariantları token yazılmadan reddedilir',
    () async {
      final organizationStore = _CountingStore();
      final organizationController = SignInController(
        repository: _Repository(
          () => Future<AuthenticatedSessionActivation>.sync(
            () => AuthenticatedSessionActivation(
              session: const ActivatedSession(
                scope: ActivatedSessionScope.organization,
                displayName: 'Yasir',
              ),
              secureSession: _organizationSession('A'),
            ),
          ),
        ),
        secureSessionStore: organizationStore,
      );
      expect(await organizationController.activateOrganization('A'), isNull);
      expect(organizationStore.commits, 0);

      final globalStore = _CountingStore();
      final globalController = SignInController(
        repository: _Repository(
          () async => _organizationActivation('unused'),
          platformActivation: () => Future<AuthenticatedSessionActivation>.sync(
            () => AuthenticatedSessionActivation(
              session: ActivatedSession(
                scope: ActivatedSessionScope.globalPlatformAdministrator,
                displayName: 'Yasir',
                organizationMembership: _membership('A'),
              ),
              secureSession: SecureSession(
                userId: 'user-1',
                deviceId: 'device-1',
                scope: SecureSessionScope.globalPlatformAdministrator,
                accessToken: 'access',
                refreshToken: 'refresh',
                authenticatedAt: DateTime.utc(2026, 7, 24, 9),
                expiresAt: DateTime.utc(2026, 7, 24, 10),
                refreshExpiresAt: DateTime.utc(2026, 8, 24, 10),
              ),
            ),
          ),
        ),
        secureSessionStore: globalStore,
      );
      expect(await globalController.activatePlatformAdministrator(), isNull);
      expect(globalStore.commits, 0);

      final blankIdStore = _CountingStore();
      final blankIdController = SignInController(
        repository: _Repository(
          () => Future<AuthenticatedSessionActivation>.sync(
            () => _organizationActivation(' '),
          ),
        ),
        secureSessionStore: blankIdStore,
      );
      expect(await blankIdController.activateOrganization(' '), isNull);
      expect(blankIdStore.commits, 0);
    },
  );
}
