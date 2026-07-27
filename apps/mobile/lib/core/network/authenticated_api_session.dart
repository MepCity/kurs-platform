/// Token-bearing HTTP work is executed through this capability so opaque
/// platform tokens never leave the data/application boundary.
abstract interface class AuthenticatedApiSession {
  String get identityKey;

  Future<T> run<T>(Future<T> Function(String bearerToken) operation);

  /// Refreshes the current platform session and executes [operation] with the
  /// replacement access token. Implementations coalesce parallel refreshes.
  Future<T> refreshAndRun<T>(Future<T> Function(String bearerToken) operation);

  /// Reconciles a terminal business-API response with secure session state.
  Future<void> terminate();
}

class AuthenticatedApiSessionUnavailable implements Exception {
  const AuthenticatedApiSessionUnavailable({this.terminal = false});

  final bool terminal;
}
