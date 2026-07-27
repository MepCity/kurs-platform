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
  bool clearFails = false;
  int replacements = 0;
  int clears = 0;
  int _lease = 0;

  @override
  Future<SecureSession?> read() async {
    if (corrupted) {
      corrupted = false;
      throw const SecureSessionStoreFailure(
        SecureSessionStoreFailureReason.corrupted,
      );
    }
    return value;
  }

  @override
  Future<bool> isCurrent(SecureSession expected) async =>
      _same(value, expected);

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

SessionBootstrapController _controller(_Store store, _Repository repository) =>
    SessionBootstrapController(
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

  test('valid access is canonicalized through sessions/me', () async {
    final store = _Store()..value = _session('valid', expired: false);
    final repository = _Repository();
    final controller = _controller(store, repository);

    await controller.start();

    expect(controller.status, BootstrapStatus.authenticated);
    expect(repository.validations, 1);
    expect(repository.refreshes, 0);
  });

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
