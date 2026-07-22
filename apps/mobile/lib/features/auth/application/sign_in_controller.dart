import 'package:flutter/foundation.dart';

import '../domain/authentication_repository.dart';

enum SignInStatus { ready, authenticating, choosingContext, activating, error }

/// Owns the AUTH-01 → CTX-01 transition without exposing token values to UI.
class SignInController extends ChangeNotifier {
  SignInController({required this.repository});

  final AuthenticationRepository repository;
  SignInStatus _status = SignInStatus.ready;
  AuthContextChoices? _choices;
  String? _message;
  bool _disposed = false;

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

  Future<ActivatedSession?> activateOrganization(String membershipId) =>
      _activate(() => repository.activateOrganization(membershipId));

  Future<ActivatedSession?> activatePlatformAdministrator() =>
      _activate(repository.activatePlatformAdministrator);

  Future<ActivatedSession?> _activate(
    Future<ActivatedSession> Function() action,
  ) async {
    if (_disposed || isBusy) return null;
    _status = SignInStatus.activating;
    _message = null;
    notifyListeners();
    try {
      final session = await action();
      if (_disposed) return null;
      _status = SignInStatus.choosingContext;
      notifyListeners();
      return session;
    } on AuthenticationFailure catch (failure) {
      _showFailure(failure);
      return null;
    } catch (_) {
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

  void retry() {
    if (_disposed || isBusy) return;
    _status = _choices == null
        ? SignInStatus.ready
        : SignInStatus.choosingContext;
    _message = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
