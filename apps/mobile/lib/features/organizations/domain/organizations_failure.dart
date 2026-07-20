/// Error codes the ORG list surface can produce.
///
/// Restricted to the subset relevant to `GET /api/v1/organizations` (§8.4):
/// `401 UNAUTHENTICATED`, `403 FORBIDDEN`, `400 INVALID_CURSOR`,
/// `422 VALIDATION_FAILED`, `500 INTERNAL_ERROR`, `429 RATE_LIMITED`.
enum OrganizationsFailureCode {
  unauthenticated,
  forbidden,
  invalidCursor,
  validationFailed,
  internalError,
  rateLimited,
}

/// Domain-level failure raised by [OrganizationsRepository.listOrganizations].
///
/// [code] drives whether the presentation layer renders the unauthorized (Z)
/// or generic error (H) state; [message] is a safe, user-facing description.
class OrganizationsFailure implements Exception {
  const OrganizationsFailure(this.code, this.message);

  final OrganizationsFailureCode code;
  final String message;

  /// Whether this failure should surface as the "Z" (yetkisiz) screen state
  /// rather than the generic "H" (hata) state.
  bool get isUnauthorized =>
      code == OrganizationsFailureCode.unauthenticated ||
      code == OrganizationsFailureCode.forbidden;

  @override
  String toString() => 'OrganizationsFailure(${code.name}): $message';
}
