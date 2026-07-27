import 'authentication_repository.dart';
import 'secure_session_store.dart';

abstract interface class SessionRepository {
  Future<ActivatedSession> validate(SecureSession candidate);
  Future<SecureSession> refresh(SecureSession candidate);
  Future<void> logout(SecureSession candidate);
}

enum SessionFailureKind { terminal, transient, malformed }

class SessionFailure implements Exception {
  const SessionFailure(this.kind, this.message);
  final SessionFailureKind kind;
  final String message;
}
