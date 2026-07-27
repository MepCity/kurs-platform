import 'package:flutter/foundation.dart';

import '../../auth/domain/authentication_repository.dart';
import '../../auth/domain/secure_session_store.dart';
import '../../auth/domain/session_repository.dart';

enum BootstrapStatus { loading, unauthenticated, authenticated, retryableError }

class SessionBootstrapController extends ChangeNotifier {
  SessionBootstrapController({
    required this.repository,
    required this.sessionStore,
    DateTime Function()? now,
  }) : _now = now ?? DateTime.now;

  final SessionRepository repository;
  final SecureSessionStore sessionStore;
  final DateTime Function() _now;
  BootstrapStatus _status = BootstrapStatus.loading;
  ActivatedSession? _session;
  SecureSession? _candidate;
  String? _message;
  int _operation = 0;
  bool _disposed = false;
  Future<void>? _bootstrapInFlight;

  BootstrapStatus get status => _status;
  ActivatedSession? get session => _session;
  String? get message => _message;

  Future<void> start() => _bootstrapInFlight ??= _start().whenComplete(
    () => _bootstrapInFlight = null,
  );

  Future<void> _start() async {
    final operation = ++_operation;
    _set(BootstrapStatus.loading);
    SecureSession? candidate;
    try {
      candidate = await sessionStore.read();
    } on SecureSessionStoreFailure catch (failure) {
      if (failure.reason == SecureSessionStoreFailureReason.unavailable) {
        if (_current(operation)) {
          _set(
            BootstrapStatus.retryableError,
            message: 'Güvenli oturum alanına erişilemiyor. Tekrar deneyin.',
          );
        }
        return;
      }
      try {
        await sessionStore.clear();
      } on Object {
        if (_current(operation)) {
          _set(
            BootstrapStatus.retryableError,
            message: 'Güvenli oturum alanına erişilemiyor. Tekrar deneyin.',
          );
        }
        return;
      }
    }
    if (!_current(operation)) return;
    if (candidate == null) {
      _candidate = null;
      _set(BootstrapStatus.unauthenticated);
      return;
    }
    _candidate = candidate;
    if (candidate.expiresAt.isAfter(_now().toUtc())) {
      await _validate(operation, candidate);
    } else {
      await _refresh(operation, candidate);
    }
  }

  Future<void> _validate(int operation, SecureSession candidate) async {
    try {
      final session = await repository.validate(candidate);
      if (!_current(operation)) return;
      if (sessionStore is! AtomicSecureSessionStore ||
          !await (sessionStore as AtomicSecureSessionStore).isCurrent(
            candidate,
          )) {
        if (_current(operation)) await _start();
        return;
      }
      if (!_current(operation)) return;
      _candidate = candidate;
      _session = session;
      _set(BootstrapStatus.authenticated);
    } on SessionFailure catch (failure) {
      if (!_current(operation)) return;
      if (failure.kind == SessionFailureKind.transient) {
        _set(
          BootstrapStatus.retryableError,
          message:
              'Oturum doğrulanamadı. Bağlantınızı kontrol edip tekrar deneyin.',
        );
      } else {
        await _terminalClear(operation, candidate);
      }
    }
  }

  Future<void> _refresh(int operation, SecureSession candidate) async {
    try {
      final replacement = await repository.refresh(candidate);
      if (!_current(operation)) return;
      if (sessionStore is! AtomicSecureSessionStore) {
        _set(
          BootstrapStatus.retryableError,
          message: 'Güvenli oturum yenilenemedi. Tekrar deneyin.',
        );
        return;
      }
      final atomic = sessionStore as AtomicSecureSessionStore;
      final replaced = await atomic.replaceIfCurrent(candidate, replacement);
      if (!_current(operation)) return;
      if (!replaced) {
        await _start();
        return;
      }
      _candidate = replacement;
      await _validate(operation, replacement);
    } on SessionFailure catch (failure) {
      if (!_current(operation)) return;
      if (failure.kind == SessionFailureKind.transient) {
        _set(
          BootstrapStatus.retryableError,
          message:
              'Oturum yenilenemedi. Bağlantınızı kontrol edip tekrar deneyin.',
        );
      } else {
        await _terminalClear(operation, candidate);
      }
    }
  }

  Future<void> logout() async {
    if (_status != BootstrapStatus.authenticated || _candidate == null) return;
    final operation = ++_operation;
    final candidate = _candidate!;
    _set(BootstrapStatus.loading);
    try {
      await repository.logout(candidate);
      if (!_current(operation)) return;
      if (sessionStore is! AtomicSecureSessionStore) {
        await _start();
        return;
      }
      final atomic = sessionStore as AtomicSecureSessionStore;
      if (!await atomic.clearIfCurrent(candidate)) {
        await _start();
        return;
      }
      if (!_current(operation)) return;
      _candidate = null;
      _session = null;
      _set(BootstrapStatus.unauthenticated);
    } on SessionFailure {
      if (!_current(operation)) return;
      _candidate = candidate;
      _set(
        BootstrapStatus.retryableError,
        message: 'Çıkış tamamlanamadı. Oturumunuz bu cihazda korunuyor.',
      );
    }
  }

  Future<void> _terminalClear(int operation, SecureSession candidate) async {
    try {
      if (sessionStore is! AtomicSecureSessionStore) {
        throw const SecureSessionStoreFailure(
          SecureSessionStoreFailureReason.unavailable,
        );
      }
      final atomic = sessionStore as AtomicSecureSessionStore;
      await atomic.clearIfCurrent(candidate);
      if (!_current(operation)) return;
      _candidate = null;
      _session = null;
      _set(BootstrapStatus.unauthenticated);
    } on Object {
      if (_current(operation)) {
        _set(
          BootstrapStatus.retryableError,
          message: 'Geçersiz oturum güvenli biçimde temizlenemedi.',
        );
      }
    }
  }

  void _set(BootstrapStatus value, {String? message}) {
    if (_disposed) return;
    _status = value;
    _message = message;
    if (value != BootstrapStatus.authenticated) _session = null;
    notifyListeners();
  }

  bool _current(int operation) => !_disposed && operation == _operation;

  @override
  void dispose() {
    _disposed = true;
    _operation++;
    super.dispose();
  }
}
