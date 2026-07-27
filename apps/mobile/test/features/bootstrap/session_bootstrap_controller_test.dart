import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:kurs_platform_mobile/features/auth/domain/authentication_repository.dart';
import 'package:kurs_platform_mobile/features/auth/domain/secure_session_store.dart';
import 'package:kurs_platform_mobile/features/auth/domain/session_repository.dart';
import 'package:kurs_platform_mobile/features/bootstrap/application/session_bootstrap_controller.dart';

SecureSession _session(String suffix, {required bool expired}) => SecureSession(
  userId: 'user-$suffix',
  deviceId: 'device-$suffix',
  scope: SecureSessionScope.organization,
  accessToken: 'access-$suffix',
  refreshToken: 'refresh-$suffix',
  expiresAt: expired
      ? DateTime.utc(2026, 7, 27, 8)
      : DateTime.utc(2026, 7, 27, 10),
  refreshExpiresAt: DateTime.utc(2026, 8, 27, 10),
  authenticatedAt: DateTime.utc(2026, 7, 27, 7),
  organizationMembershipId: 'membership-$suffix',
  organizationId: 'organization-$suffix',
  sessionGeneration: 2,
);

ActivatedSession _activated(SecureSession session) => ActivatedSession(
  scope: ActivatedSessionScope.organization,
  displayName: session.userId,
  organizationMembership: AuthOrganizationMembership(
    id: session.organizationMembershipId!,
    organizationId: session.organizationId!,
    organizationName: 'Kurs',
    roleCodes: const <String>['ORG_ADMIN'],
  ),
);

class _Store implements SecureSessionStore, AtomicSecureSessionStore {
  SecureSession? value;
  bool corrupted = false;
  bool unavailable = false;
  bool clearFails = false;
  Object? isCurrentError;
  Object? replaceIfCurrentError;
  Object? clearIfCurrentError;
  int replacements = 0;
  int clears = 0;
  int _lease = 0;

  @override
  Future<SecureSession?> read() async {
    if (unavailable) {
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
    if (corrupted) {
      corrupted = false;
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.corrupted,
      );
    }
    return value;
  }

  @override
  Future<bool> isCurrent(SecureSession expected) async {
    if (isCurrentError case final error?) throw error;
    return _same(value, expected);
  }

  @override
  Future<void> clear() async {
    if (clearFails) {
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );
    }
    value = null;
  }

  @override
  Future<bool> clearIfCurrent(SecureSession expected) async {
    if (clearIfCurrentError case final error?) throw error;
    if (!_same(value, expected)) return false;
    clears++;
    value = null;
    return true;
  }

  @override
  Future<bool> replaceIfCurrent(
    SecureSession expected,
    SecureSession replacement,
  ) async {
    if (replaceIfCurrentError case final error?) throw error;
    if (!_same(value, expected)) return false;
    replacements++;
    value = replacement;
    return true;
  }

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      SecureSessionWriteLease(++_lease);

  @override
  void abandonActivation(SecureSessionWriteLease lease) {}

  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async {
    value = session;
    return true;
  }
}

class _NonAtomicStore implements SecureSessionStore {
  _NonAtomicStore(this.value);

  SecureSession? value;
  int reads = 0;

  @override
  Future<SecureSession?> read() async {
    reads++;
    return value;
  }

  @override
  Future<void> clear() async => value = null;

  @override
  Future<SecureSessionWriteLease> beginActivation() async =>
      const SecureSessionWriteLease(1);

  @override
  Future<bool> commit(
    SecureSessionWriteLease lease,
    SecureSession session,
  ) async {
    value = session;
    return true;
  }

  @override
  void abandonActivation(SecureSessionWriteLease lease) {}
}

bool _same(SecureSession? first, SecureSession second) =>
    first?.accessToken == second.accessToken &&
    first?.refreshToken == second.refreshToken;

class _Repository implements SessionRepository {
  int validations = 0;
  int refreshes = 0;
  int logouts = 0;
  SessionFailure? validateFailure;
  SessionFailure? refreshFailure;
  SessionFailure? logoutFailure;
  Completer<void>? logoutBlock;
  SecureSession? replacement;

  @override
  Future<ActivatedSession> validate(SecureSession candidate) async {
    validations++;
    if (validateFailure != null) throw validateFailure!;
    return _activated(candidate);
  }

  @override
  Future<SecureSession> refresh(SecureSession candidate) async {
    refreshes++;
    if (refreshFailure != null) throw refreshFailure!;
    return replacement!;
  }

  @override
  Future<void> logout(SecureSession candidate) async {
    logouts++;
    await logoutBlock?.future;
    if (logoutFailure != null) throw logoutFailure!;
  }
}

SessionBootstrapController _controller(
  SecureSessionStore store,
  _Repository repository,
) => SessionBootstrapController(
  repository: repository,
  sessionStore: store,
  now: () => DateTime.utc(2026, 7, 27, 9),
);

void main() {
  test('no session and corrupted session fail closed to login', () async {
    final empty = _controller(_Store(), _Repository());
    await empty.start();
    expect(empty.status, BootstrapStatus.unauthenticated);

    final corruptedStore = _Store()
      ..value = _session('old', expired: false)
      ..corrupted = true;
    final corrupted = _controller(corruptedStore, _Repository());
    await corrupted.start();
    expect(corrupted.status, BootstrapStatus.unauthenticated);
    expect(corruptedStore.value, isNull);
  });

  test(
    'temporarily unavailable storage preserves the refresh candidate',
    () async {
      final candidate = _session('kept', expired: false);
      final store = _Store()
        ..value = candidate
        ..unavailable = true;
      final controller = _controller(store, _Repository());

      await controller.start();

      expect(controller.status, BootstrapStatus.retryableError);
      expect(store.value, same(candidate));
      expect(store.clears, 0);
    },
  );

  test('valid access is canonicalized through sessions/me', () async {
    final store = _Store()..value = _session('valid', expired: false);
    final repository = _Repository();
    final controller = _controller(store, repository);

    await controller.start();

    expect(controller.status, BootstrapStatus.authenticated);
    expect(repository.validations, 1);
    expect(repository.refreshes, 0);
  });

  test(
    'sessions/me sonrası isCurrent storage hatası retryableError üretir',
    () async {
      final candidate = _session('is-current-error', expired: false);
      final store = _Store()
        ..value = candidate
        ..isCurrentError = const SecureSessionStoreFailure(
          SecureSessionStoreFailureReason.unavailable,
        );
      final repository = _Repository();
      final controller = _controller(store, repository);

      await expectLater(controller.start(), completes);

      expect(repository.validations, 1);
      expect(controller.status, BootstrapStatus.retryableError);
      expect(store.value, same(candidate));
    },
  );

  test('expired access refreshes, CAS replaces, then validates', () async {
    final old = _session('old', expired: true);
    final replacement = _session('new', expired: false);
    final store = _Store()..value = old;
    final repository = _Repository()..replacement = replacement;
    final controller = _controller(store, repository);

    await Future.wait(<Future<void>>[controller.start(), controller.start()]);

    expect(controller.status, BootstrapStatus.authenticated);
    expect(repository.refreshes, 1);
    expect(store.replacements, 1);
    expect(store.value?.refreshToken, 'refresh-new');
    expect(repository.validations, 1);
  });

  test(
    'refresh replaceIfCurrent storage hatası adayı korur ve retryableError üretir',
    () async {
      final candidate = _session('replace-error', expired: true);
      final replacement = _session('replacement', expired: false);
      final store = _Store()
        ..value = candidate
        ..replaceIfCurrentError = StateError('secure storage write failed');
      final repository = _Repository()..replacement = replacement;
      final controller = _controller(store, repository);

      await expectLater(controller.start(), completes);

      expect(controller.status, BootstrapStatus.retryableError);
      expect(store.value, same(candidate));
      expect(store.replacements, 0);
    },
  );

  test('non-atomic store bootstrap sonsuz yeniden başlatma yapmaz', () async {
    final store = _NonAtomicStore(_session('non-atomic', expired: false));
    final repository = _Repository();
    final controller = _controller(store, repository);

    await expectLater(controller.start(), completes);

    expect(controller.status, BootstrapStatus.retryableError);
    expect(store.reads, 1);
    expect(repository.validations, 1);
  });

  test(
    'terminal failure clears while transient failure preserves candidate',
    () async {
      final terminalStore = _Store()
        ..value = _session('terminal', expired: false);
      final terminalRepository = _Repository()
        ..validateFailure = const SessionFailure(
          SessionFailureKind.terminal,
          'revoked',
        );
      final terminal = _controller(terminalStore, terminalRepository);
      await terminal.start();
      expect(terminal.status, BootstrapStatus.unauthenticated);
      expect(terminalStore.value, isNull);

      final transientCandidate = _session('transient', expired: true);
      final transientStore = _Store()..value = transientCandidate;
      final transientRepository = _Repository()
        ..refreshFailure = const SessionFailure(
          SessionFailureKind.transient,
          'network',
        );
      final transient = _controller(transientStore, transientRepository);
      await transient.start();
      expect(transient.status, BootstrapStatus.retryableError);
      expect(transientStore.value, same(transientCandidate));
    },
  );

  test('successful logout clears and transient logout does not', () async {
    final successStore = _Store()..value = _session('success', expired: false);
    final successRepository = _Repository();
    final success = _controller(successStore, successRepository);
    await success.start();
    await success.logout();
    expect(success.status, BootstrapStatus.unauthenticated);
    expect(successStore.value, isNull);

    final transientSession = _session('kept', expired: false);
    final transientStore = _Store()..value = transientSession;
    final transientRepository = _Repository()
      ..logoutFailure = const SessionFailure(
        SessionFailureKind.transient,
        'network',
      );
    final transient = _controller(transientStore, transientRepository);
    await transient.start();
    await transient.logout();
    expect(transient.status, BootstrapStatus.retryableError);
    expect(transientStore.value, same(transientSession));
  });

  test(
    'sunucu logout sonrası clearIfCurrent storage hatası başarı göstermez',
    () async {
      final candidate = _session('logout-clear-error', expired: false);
      final store = _Store()..value = candidate;
      final repository = _Repository();
      final controller = _controller(store, repository);
      await controller.start();
      store.clearIfCurrentError = const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.unavailable,
      );

      await expectLater(controller.logout(), completes);

      expect(repository.logouts, 1);
      expect(controller.status, BootstrapStatus.retryableError);
      expect(store.value, same(candidate));
    },
  );

  test('stale logout cannot clear a newer login', () async {
    final old = _session('old', expired: false);
    final newer = _session('new', expired: false);
    final store = _Store()..value = old;
    final block = Completer<void>();
    final repository = _Repository()..logoutBlock = block;
    final controller = _controller(store, repository);
    await controller.start();

    final logout = controller.logout();
    store.value = newer;
    block.complete();
    await logout;

    expect(store.value, same(newer));
    expect(controller.status, BootstrapStatus.authenticated);
    expect(controller.session?.displayName, 'user-new');
  });
}
