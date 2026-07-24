import 'package:flutter/foundation.dart';

import '../domain/authentication_repository.dart';
import '../domain/secure_session_store.dart';

enum SignInStatus { ready, authenticating, choosingContext, activating, error }

/// Owns the AUTH-01 → CTX-01 transition without exposing token values to UI.
class SignInController extends ChangeNotifier {
  SignInController({
    required this.repository,
    required this.secureSessionStore,
  });

  final AuthenticationRepository repository;
  final SecureSessionStore secureSessionStore;
  SignInStatus _status = SignInStatus.ready;
  AuthContextChoices? _choices;
  String? _message;
  bool _disposed = false;
  SecureSessionWriteLease? _activeLease;
  int _activationAttempt = 0;

  SignInStatus get status => _status;
  AuthContextChoices? get choices => _choices;
  String? get message => _message;
  bool get isBusy =>
      _status == SignInStatus.authenticating ||
      _status == SignInStatus.activating;

  Future<void> begin() async {
    if (_disposed || isBusy) return;
    _status = SignInStatus.authenticating;
    _message = null;
    notifyListeners();
    try {
      final choices = await repository.beginSignIn();
      if (_disposed) return;
      _choices = choices;
      if (choices.selectableCount == 0) {
        _status = SignInStatus.error;
        _message =
            'Bu hesap için seçilebilir bir kurum veya platform bağlamı yok.';
      } else {
        _status = SignInStatus.choosingContext;
      }
      notifyListeners();
    } on AuthenticationFailure catch (failure) {
      if (_disposed || failure.code == AuthenticationFailureCode.cancelled) {
        if (!_disposed) {
          _status = SignInStatus.ready;
          notifyListeners();
        }
        return;
      }
      _showFailure(failure);
    } catch (_) {
      _showFailure(
        const AuthenticationFailure(
          AuthenticationFailureCode.unavailable,
          'Giriş başlatılırken beklenmeyen bir hata oluştu.',
        ),
      );
    }
  }

  Future<ActivatedSession?> activateOrganization(String membershipId) {
    final selected = _choices?.memberships
        .where((membership) => membership.id == membershipId)
        .firstOrNull;
    return _activate(
      () => repository.activateOrganization(membershipId),
      requestedOrganizationMembershipId: membershipId,
      requestedOrganizationId: selected?.organizationId,
    );
  }

  Future<ActivatedSession?> activatePlatformAdministrator() =>
      _activate(repository.activatePlatformAdministrator);

  Future<ActivatedSession?> _activate(
    Future<AuthenticatedSessionActivation> Function() action, {
    String? requestedOrganizationMembershipId,
    String? requestedOrganizationId,
  }) async {
    if (_disposed || isBusy) return null;
    final attempt = ++_activationAttempt;
    _status = SignInStatus.activating;
    _message = null;
    notifyListeners();
    SecureSessionWriteLease? lease;
    try {
      lease = await secureSessionStore.beginActivation();
      if (_disposed || attempt != _activationAttempt) {
        secureSessionStore.abandonActivation(lease);
        return null;
      }
      _activeLease = lease;
      final activation = await action();
      if (!_isCurrent(lease, attempt)) return null;
      if (requestedOrganizationMembershipId != null &&
          activation.session.organizationMembership?.id !=
              requestedOrganizationMembershipId) {
        _abandonActiveLease();
        _returnToContextChoice();
        return null;
      }
      if (requestedOrganizationId != null &&
          activation.session.organizationMembership?.organizationId !=
              requestedOrganizationId) {
        _abandonActiveLease();
        _returnToContextChoice();
        return null;
      }
      final committed = await secureSessionStore.commit(
        lease,
        activation.secureSession,
      );
      if (!_isCurrent(lease, attempt)) return null;
      if (!committed) {
        _activeLease = null;
        _returnToContextChoice();
        return null;
      }
      _activeLease = null;
      _status = SignInStatus.choosingContext;
      notifyListeners();
      return activation.session;
    } on AuthenticationFailure catch (failure) {
      if (lease != null && _isCurrent(lease, attempt)) _abandonActiveLease();
      _showFailure(failure);
      return null;
    } catch (_) {
      if (lease != null && _isCurrent(lease, attempt)) _abandonActiveLease();
      _showFailure(
        const AuthenticationFailure(
          AuthenticationFailureCode.unavailable,
          'Oturum açılırken beklenmeyen bir hata oluştu.',
        ),
      );
      return null;
    }
  }

  void _showFailure(AuthenticationFailure failure) {
    if (_disposed) return;
    _status = SignInStatus.error;
    _message = failure.message;
    notifyListeners();
  }

  void _returnToContextChoice() {
    if (_disposed) return;
    _status = SignInStatus.choosingContext;
    notifyListeners();
  }

  void retry() {
    if (_disposed) return;
    _activationAttempt++;
    _abandonActiveLease();
    _status = _choices == null
        ? SignInStatus.ready
        : SignInStatus.choosingContext;
    _message = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _activationAttempt++;
    _abandonActiveLease();
    super.dispose();
  }

  bool _isCurrent(SecureSessionWriteLease lease, int attempt) =>
      !_disposed &&
      attempt == _activationAttempt &&
      identical(_activeLease, lease);

  void _abandonActiveLease() {
    final lease = _activeLease;
    _activeLease = null;
    if (lease != null) secureSessionStore.abandonActivation(lease);
  }
}
