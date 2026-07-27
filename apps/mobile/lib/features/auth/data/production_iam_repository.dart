import '../domain/authentication_repository.dart';
import '../domain/secure_session_store.dart';
import '../domain/session_repository.dart';
import 'appauth_provider_client.dart';
import 'device_identity.dart';
import 'iam_http_client.dart';

class ProductionIamRepository
    implements AuthenticationRepository, SessionRepository {
  ProductionIamRepository({
    required ProviderAuthorizationClient provider,
    required IamHttpClient client,
    required DeviceIdentity deviceIdentity,
    DateTime Function()? now,
    // ignore: prefer_initializing_formals
  }) : _provider = provider,
       // ignore: prefer_initializing_formals
       _client = client,
       // ignore: prefer_initializing_formals
       _deviceIdentity = deviceIdentity,
       _now = now ?? DateTime.now;

  final ProviderAuthorizationClient _provider;
  final IamHttpClient _client;
  final DeviceIdentity _deviceIdentity;
  final DateTime Function() _now;
  _PendingContext? _pendingContext;
  final Map<String, Future<SecureSession>> _refreshes =
      <String, Future<SecureSession>>{};
  final Map<String, String> _refreshKeys = <String, String>{};
  final Map<String, String> _logoutKeys = <String, String>{};
  int _contextOperation = 0;

  @override
  Future<AuthContextChoices> beginSignIn() async {
    final operation = ++_contextOperation;
    _pendingContext = null;
    try {
      final device = await _deviceIdentity.get();
      final providerAccessToken = await _provider.authorize();
      final exchange = await _client.exchangeProviderToken(
        providerAccessToken,
        device,
      );
      if (exchange.deviceIdentifier != device.identifier ||
          !exchange.expiresAt.isAfter(_now().toUtc())) {
        throw const FormatException();
      }
      final memberships = await _client.contextSelections(
        exchange.contextToken,
      );
      if (operation != _contextOperation) {
        throw const AuthenticationFailure(
          AuthenticationFailureCode.cancelled,
          'Daha yeni bir giriş isteği başlatıldı.',
        );
      }
      final uniqueIds = memberships.map((item) => item.id).toSet();
      if (uniqueIds.length != memberships.length) throw const FormatException();
      _pendingContext = _PendingContext(exchange, memberships);
      return AuthContextChoices(
        displayName: exchange.displayName,
        memberships: memberships
            .map(
              (item) => AuthOrganizationMembership(
                id: item.id,
                organizationId: item.organizationId,
                organizationName: item.organizationName,
                roleCodes: item.roleCodes,
              ),
            )
            .toList(growable: false),
        canActivatePlatformAdministrator:
            exchange.canActivatePlatformAdministrator,
      );
    } on AuthenticationFailure {
      rethrow;
    } on IamApiException catch (error) {
      if (operation == _contextOperation) _pendingContext = null;
      throw _authenticationFailure(error);
    } on IamTransportException {
      if (operation == _contextOperation) _pendingContext = null;
      throw const AuthenticationFailure(
        AuthenticationFailureCode.unavailable,
        'Giriş servisine şu anda ulaşılamıyor.',
      );
    } on Object {
      if (operation == _contextOperation) _pendingContext = null;
      throw const AuthenticationFailure(
        AuthenticationFailureCode.internalError,
        'Giriş yanıtı güvenli biçimde doğrulanamadı.',
      );
    }
  }

  @override
  Future<AuthenticatedSessionActivation> activateOrganization(
    String membershipId,
  ) async {
    final pending = _validPending();
    final matches = pending.memberships
        .where((item) => item.id == membershipId)
        .toList();
    if (matches.length != 1) throw const FormatException();
    return _activate(
      pending,
      () => _client.activateOrganization(
        pending.exchange.contextToken,
        membershipId,
        idempotencyKey: pending.commandKey(
          'organization:$membershipId',
          _client,
        ),
      ),
      expectedMembership: matches.single,
    );
  }

  @override
  Future<AuthenticatedSessionActivation> activatePlatformAdministrator() {
    final pending = _validPending();
    if (!pending.exchange.canActivatePlatformAdministrator) {
      throw const AuthenticationFailure(
        AuthenticationFailureCode.forbidden,
        'Platform yöneticisi bağlamı kullanılamıyor.',
      );
    }
    return _activate(
      pending,
      () => _client.activatePlatformAdministrator(
        pending.exchange.contextToken,
        idempotencyKey: pending.commandKey('global', _client),
      ),
    );
  }

  Future<AuthenticatedSessionActivation> _activate(
    _PendingContext pending,
    Future<PlatformSessionDto> Function() action, {
    MembershipDto? expectedMembership,
  }) async {
    try {
      final dto = await action();
      if (!identical(_pendingContext, pending)) {
        throw const AuthenticationFailure(
          AuthenticationFailureCode.cancelled,
          'Daha yeni bir giriş bağlamı etkin.',
        );
      }
      final secure = _secureSession(
        dto,
        expectedUserId: pending.exchange.userId,
        expectedDeviceId: pending.exchange.deviceIdentifier,
        expectedMembership: expectedMembership,
      );
      _invalidatePending(pending);
      return AuthenticatedSessionActivation(
        session: _activatedSession(dto),
        secureSession: secure,
      );
    } on IamApiException catch (error) {
      if (error.isTerminalSession) _invalidatePending(pending);
      throw _authenticationFailure(error);
    } on IamTransportException {
      throw const AuthenticationFailure(
        AuthenticationFailureCode.unavailable,
        'Oturum servisine şu anda ulaşılamıyor.',
      );
    } on AuthenticationFailure {
      rethrow;
    } on Object {
      _invalidatePending(pending);
      throw const AuthenticationFailure(
        AuthenticationFailureCode.internalError,
        'Oturum yanıtı güvenli biçimde doğrulanamadı.',
      );
    }
  }

  @override
  Future<ActivatedSession> validate(SecureSession candidate) async {
    try {
      final dto = await _client.sessionMe(candidate.accessToken);
      _assertCanonical(candidate, dto);
      return _activatedSession(dto);
    } on IamApiException catch (error) {
      throw SessionFailure(
        error.isTerminalSession
            ? SessionFailureKind.terminal
            : SessionFailureKind.transient,
        'Oturum doğrulanamadı.',
      );
    } on IamTransportException {
      throw const SessionFailure(
        SessionFailureKind.transient,
        'Oturum doğrulanamadı.',
      );
    } on Object {
      throw const SessionFailure(
        SessionFailureKind.malformed,
        'Oturum yanıtı geçersiz.',
      );
    }
  }

  @override
  Future<SecureSession> refresh(SecureSession candidate) {
    final existing = _refreshes[candidate.refreshToken];
    if (existing != null) return existing;
    final key = _refreshKeys.putIfAbsent(
      candidate.refreshToken,
      _client.createIdempotencyKey,
    );
    late Future<SecureSession> future;
    future = _refresh(candidate, key).whenComplete(() {
      if (identical(_refreshes[candidate.refreshToken], future)) {
        _refreshes.remove(candidate.refreshToken);
      }
    });
    _refreshes[candidate.refreshToken] = future;
    return future;
  }

  Future<SecureSession> _refresh(
    SecureSession candidate,
    String idempotencyKey,
  ) async {
    try {
      final dto = await _client.refresh(
        candidate.refreshToken,
        idempotencyKey: idempotencyKey,
      );
      final result = _secureSession(
        dto,
        expectedUserId: candidate.userId,
        expectedDeviceId: candidate.deviceId,
        expectedMembership: candidate.scope == SecureSessionScope.organization
            ? MembershipDto(
                id: candidate.organizationMembershipId!,
                organizationId: candidate.organizationId!,
                organizationName: dto.membership?.organizationName ?? '',
                roleCodes: dto.membership?.roleCodes ?? const <String>[],
                sessionGeneration: candidate.sessionGeneration!,
              )
            : null,
        expectedAuthenticatedAt: candidate.authenticatedAt,
        responseIdentityRequired: false,
      );
      _refreshKeys.remove(candidate.refreshToken);
      return result;
    } on IamApiException catch (error) {
      if (error.isTerminalSession) {
        _refreshKeys.remove(candidate.refreshToken);
      }
      throw SessionFailure(
        error.isTerminalSession
            ? SessionFailureKind.terminal
            : SessionFailureKind.transient,
        'Oturum yenilenemedi.',
      );
    } on IamTransportException {
      throw const SessionFailure(
        SessionFailureKind.transient,
        'Oturum yenilenemedi.',
      );
    } on Object {
      _refreshKeys.remove(candidate.refreshToken);
      throw const SessionFailure(
        SessionFailureKind.malformed,
        'Yenileme yanıtı geçersiz.',
      );
    }
  }

  @override
  Future<void> logout(SecureSession candidate) async {
    final key = _logoutKeys.putIfAbsent(
      candidate.refreshToken,
      _client.createIdempotencyKey,
    );
    try {
      await _client.logout(
        candidate.accessToken,
        candidate.refreshToken,
        idempotencyKey: key,
      );
      _logoutKeys.remove(candidate.refreshToken);
    } on IamApiException catch (error) {
      if (error.code == IamErrorCode.sessionRevoked) {
        _logoutKeys.remove(candidate.refreshToken);
        return;
      }
      if (error.isTerminalSession) {
        _logoutKeys.remove(candidate.refreshToken);
      }
      throw SessionFailure(
        error.isTerminalSession
            ? SessionFailureKind.terminal
            : SessionFailureKind.transient,
        'Çıkış tamamlanamadı.',
      );
    } on Object {
      throw const SessionFailure(
        SessionFailureKind.transient,
        'Çıkış tamamlanamadı.',
      );
    }
  }

  _PendingContext _validPending() {
    final pending = _pendingContext;
    if (pending == null ||
        !pending.exchange.expiresAt.isAfter(_now().toUtc())) {
      _pendingContext = null;
      throw const AuthenticationFailure(
        AuthenticationFailureCode.unauthenticated,
        'Giriş bağlamının süresi doldu.',
      );
    }
    return pending;
  }

  void _invalidatePending(_PendingContext pending) {
    if (!identical(_pendingContext, pending)) return;
    _pendingContext = null;
    _contextOperation++;
  }

  SecureSession _secureSession(
    PlatformSessionDto dto, {
    required String expectedUserId,
    required String expectedDeviceId,
    MembershipDto? expectedMembership,
    DateTime? expectedAuthenticatedAt,
    bool responseIdentityRequired = true,
  }) {
    final membership = dto.membership;
    if ((responseIdentityRequired &&
            (dto.userId != expectedUserId ||
                dto.deviceIdentifier != expectedDeviceId)) ||
        (expectedMembership == null) != (membership == null) ||
        (membership != null &&
            (membership.id != expectedMembership!.id ||
                membership.organizationId !=
                    expectedMembership.organizationId ||
                membership.sessionGeneration !=
                    expectedMembership.sessionGeneration)) ||
        (expectedAuthenticatedAt != null &&
            dto.authenticatedAt != expectedAuthenticatedAt)) {
      throw const FormatException();
    }
    return SecureSession(
      userId: responseIdentityRequired ? dto.userId : expectedUserId,
      deviceId: responseIdentityRequired
          ? dto.deviceIdentifier
          : expectedDeviceId,
      scope: dto.scope == 'ORGANIZATION'
          ? SecureSessionScope.organization
          : SecureSessionScope.globalPlatformAdministrator,
      accessToken: dto.accessToken!,
      refreshToken: dto.refreshToken!,
      expiresAt: dto.expiresAt,
      refreshExpiresAt: dto.refreshExpiresAt!,
      authenticatedAt: dto.authenticatedAt,
      organizationMembershipId: membership?.id,
      organizationId: membership?.organizationId,
      sessionGeneration: membership?.sessionGeneration,
    );
  }

  void _assertCanonical(SecureSession candidate, PlatformSessionDto dto) {
    final membership = dto.membership;
    final expectedOrganization =
        candidate.scope == SecureSessionScope.organization;
    if (dto.userId != candidate.userId ||
        dto.deviceIdentifier != candidate.deviceId ||
        dto.expiresAt != candidate.expiresAt ||
        dto.authenticatedAt != candidate.authenticatedAt ||
        (dto.scope == 'ORGANIZATION') != expectedOrganization ||
        expectedOrganization != (membership != null) ||
        (membership != null &&
            (membership.id != candidate.organizationMembershipId ||
                membership.organizationId != candidate.organizationId ||
                membership.sessionGeneration != candidate.sessionGeneration))) {
      throw const FormatException();
    }
  }

  ActivatedSession _activatedSession(PlatformSessionDto dto) =>
      ActivatedSession(
        scope: dto.scope == 'ORGANIZATION'
            ? ActivatedSessionScope.organization
            : ActivatedSessionScope.globalPlatformAdministrator,
        displayName: dto.displayName,
        organizationMembership: dto.membership == null
            ? null
            : AuthOrganizationMembership(
                id: dto.membership!.id,
                organizationId: dto.membership!.organizationId,
                organizationName: dto.membership!.organizationName,
                roleCodes: dto.membership!.roleCodes,
              ),
      );
}

class _PendingContext {
  _PendingContext(this.exchange, this.memberships);
  final ProviderExchange exchange;
  final List<MembershipDto> memberships;
  final Map<String, String> _commandKeys = <String, String>{};

  String commandKey(String operation, IamHttpClient client) =>
      _commandKeys.putIfAbsent(operation, client.createIdempotencyKey);
}

AuthenticationFailure _authenticationFailure(IamApiException error) {
  final code = switch (error.code) {
    IamErrorCode.invalidRequest => AuthenticationFailureCode.invalidRequest,
    IamErrorCode.unauthenticated => AuthenticationFailureCode.unauthenticated,
    IamErrorCode.forbidden => AuthenticationFailureCode.forbidden,
    IamErrorCode.organizationContextRequired =>
      AuthenticationFailureCode.organizationContextRequired,
    IamErrorCode.sessionRevoked => AuthenticationFailureCode.sessionRevoked,
    IamErrorCode.accountNotReady => AuthenticationFailureCode.accountNotReady,
    IamErrorCode.reauthenticationRequired =>
      AuthenticationFailureCode.reauthenticationRequired,
    IamErrorCode.resourceNotFound => AuthenticationFailureCode.resourceNotFound,
    IamErrorCode.stateConflict => AuthenticationFailureCode.stateConflict,
    IamErrorCode.idempotencyKeyReused =>
      AuthenticationFailureCode.idempotencyKeyReused,
    IamErrorCode.rateLimited => AuthenticationFailureCode.rateLimited,
    IamErrorCode.providerUnavailable =>
      AuthenticationFailureCode.providerUnavailable,
    IamErrorCode.internalError => AuthenticationFailureCode.internalError,
  };
  return AuthenticationFailure(code, 'Kimlik doğrulama isteği tamamlanamadı.');
}
