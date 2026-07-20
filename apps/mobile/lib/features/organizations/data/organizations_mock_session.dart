/// Scopes `OrganizationsMockRepository` understands.
///
/// Mirrors the transaction-scope concept from
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §2.2/§4.1 at the granularity
/// this screen needs. PLAT-01 only ever calls the `GLOBAL` scope `ORG_LIST`
/// path, so a single value is modeled today; the type exists (rather than a
/// bare bool) so a cursor's bound scope and a session's scope are always
/// compared as the same kind of thing, and a second value can be added later
/// without reshaping call sites.
enum OrganizationsMockScope { globalPlatformAdmin }

/// Simulates the caller's authenticated identity and authorization scope for
/// `OrganizationsMockRepository`.
///
/// This is not a general IAM session model (that belongs to IAM-007/008);
/// it exists narrowly so the mock can (a) decide between `401
/// UNAUTHENTICATED` and `403 FORBIDDEN` the way the real
/// `platform_administrators` actor-only `SELECT` RLS check would (§4.1b),
/// and (b) bind a minted cursor to "the actor and scope it was minted for"
/// so a cursor cannot be replayed by a different session (§5.3).
class OrganizationsMockSession {
  const OrganizationsMockSession.authenticatedPlatformAdmin({
    required String actorUserId,
  }) : this._(
         actorUserId: actorUserId,
         authState: _MockAuthState.platformAdmin,
       );

  /// A real, authenticated user without an active
  /// `platform_administrators` row — the `403 FORBIDDEN` case (§4.1b: the
  /// actor-only `SELECT` finds no row).
  const OrganizationsMockSession.authenticatedWithoutPlatformAdminScope({
    required String actorUserId,
  }) : this._(actorUserId: actorUserId, authState: _MockAuthState.forbidden);

  /// No valid session at all — the `401 UNAUTHENTICATED` case.
  const OrganizationsMockSession.unauthenticated()
    : this._(actorUserId: null, authState: _MockAuthState.unauthenticated);

  const OrganizationsMockSession._({
    required this.actorUserId,
    required this._authState,
  });

  /// `null` only when [isAuthenticated] is `false`.
  final String? actorUserId;
  final _MockAuthState _authState;

  bool get isAuthenticated => _authState != _MockAuthState.unauthenticated;

  bool get hasGlobalPlatformAdminScope =>
      _authState == _MockAuthState.platformAdmin;

  /// The scope this session would operate under once authorized. PLAT-01
  /// only ever uses [OrganizationsMockScope.globalPlatformAdmin].
  OrganizationsMockScope get scope =>
      OrganizationsMockScope.globalPlatformAdmin;
}

enum _MockAuthState { platformAdmin, forbidden, unauthenticated }
