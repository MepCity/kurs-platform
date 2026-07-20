import 'organization_create_request.dart';

/// Error codes the ORG list and create surfaces can produce.
///
/// Covers `GET /api/v1/organizations` (§8.4): `401 UNAUTHENTICATED`,
/// `403 FORBIDDEN`, `400 INVALID_CURSOR`, `422 VALIDATION_FAILED`,
/// `500 INTERNAL_ERROR`, `429 RATE_LIMITED`; and the two additional cases
/// `POST /api/v1/organizations` (§7.4) can produce that `LIST` cannot:
/// `403 ORGANIZATION_CONTEXT_REQUIRED` and `409 IDEMPOTENCY_KEY_REUSED`.
enum OrganizationsFailureCode {
  unauthenticated,
  forbidden,

  /// `403 ORGANIZATION_CONTEXT_REQUIRED` (§7.4) — the caller presented a
  /// `contextSelectionToken` instead of a plain `GLOBAL_PLATFORM_ADMIN`
  /// token.
  organizationContextRequired,
  invalidCursor,
  validationFailed,

  /// `409 IDEMPOTENCY_KEY_REUSED` (§7.4) — the same `Idempotency-Key` was
  /// replayed with a different request fingerprint.
  idempotencyKeyReused,
  internalError,
  rateLimited,
}

/// Domain-level failure raised by [OrganizationsRepository.listOrganizations]
/// and [OrganizationsRepository.createOrganization].
///
/// [code] drives whether the presentation layer renders the unauthorized (Z)
/// or generic error (H) state; [message] is a safe, user-facing description.
class OrganizationsFailure implements Exception {
  const OrganizationsFailure(this.code, this.message, {this.fieldErrors});

  final OrganizationsFailureCode code;
  final String message;

  /// Structured per-field detail for a `422 VALIDATION_FAILED`
  /// [OrganizationsFailureCode.validationFailed] response
  /// (`API_GENEL_KURALLARI.md` field validation contract). `null` for every
  /// other [code], and may also be `null` (or hold no errors) for a
  /// `validationFailed` failure whose cause is not one of the three known
  /// create fields — the presentation layer falls back to showing [message]
  /// as a banner in that case.
  final OrganizationCreateFieldErrors? fieldErrors;

  /// Whether this failure should surface as the "Z" (yetkisiz) screen state
  /// rather than the generic "H" (hata) state.
  bool get isUnauthorized =>
      code == OrganizationsFailureCode.unauthenticated ||
      code == OrganizationsFailureCode.forbidden ||
      code == OrganizationsFailureCode.organizationContextRequired;

  @override
  String toString() => 'OrganizationsFailure(${code.name}): $message';
}
