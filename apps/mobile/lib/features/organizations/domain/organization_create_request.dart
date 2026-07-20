/// Maximum accepted `name` length, mirroring
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §6.3/§7.2.
const int organizationNameMaxLength = 200;

/// Maximum accepted `shortName` length (§6.3/§7.2).
const int organizationShortNameMaxLength = 50;

/// Default `defaultTimezone` applied when the field is left blank, matching
/// §2.1: "`defaultTimezone` gönderilmezse `Europe/Istanbul` kullanılır."
const String organizationDefaultTimezoneFallback = 'Europe/Istanbul';

/// A conservative IANA-identifier *shape* check (1–3 `/`-separated
/// segments of letters/digits/`+`/`-`/`_`, e.g. `Europe/Istanbul`, `UTC`,
/// `GMT`, `CET`, `Etc/GMT+3`, `America/Port-au-Prince`,
/// `America/Argentina/Buenos_Aires`). This is a client-side sanity check
/// only, meant to reject unsafe/malformed input (empty segments, whitespace,
/// control characters, `.`-based path-traversal-like values) — it does not
/// claim to embed a full IANA database, so a well-shaped but nonexistent
/// zone is intentionally left for the server's `422` to catch.
final RegExp _ianaLikeTimezonePattern = RegExp(
  r'^[A-Za-z0-9+_-]+(?:/[A-Za-z0-9+_-]+){0,2}$',
);

/// Field-level validation errors for [OrganizationCreateRequest].
///
/// `null` means that field is valid. Kept as a plain value type (rather than
/// throwing per-field) so the presentation layer can show every invalid
/// field at once instead of one at a time.
class OrganizationCreateFieldErrors {
  const OrganizationCreateFieldErrors({
    this.name,
    this.shortName,
    this.defaultTimezone,
  });

  final String? name;
  final String? shortName;
  final String? defaultTimezone;

  bool get hasErrors =>
      name != null || shortName != null || defaultTimezone != null;

  /// First non-null message, in field order — used to build a single banner
  /// message when the caller does not render field-level errors.
  String? get firstMessage => name ?? shortName ?? defaultTimezone;

  /// Returns a copy with the given fields replaced. Unlike a typical
  /// `copyWith`, passing an explicit `null` clears that field's error — the
  /// sentinel [_unset] is what "leave unchanged" defaults to instead.
  OrganizationCreateFieldErrors copyWith({
    Object? name = _unset,
    Object? shortName = _unset,
    Object? defaultTimezone = _unset,
  }) {
    return OrganizationCreateFieldErrors(
      name: name == _unset ? this.name : name as String?,
      shortName: shortName == _unset ? this.shortName : shortName as String?,
      defaultTimezone: defaultTimezone == _unset
          ? this.defaultTimezone
          : defaultTimezone as String?,
    );
  }

  /// Builds field errors from a server `422 VALIDATION_FAILED` field-error
  /// map (`API_GENEL_KURALLARI.md` field validation contract), keyed by the
  /// wire field name.
  ///
  /// Only the three fields this form renders (`name`, `shortName`,
  /// `defaultTimezone`) are recognized. Any other key — a future field this
  /// client does not know about, or a server bug — is silently dropped
  /// rather than surfaced, since there is no safe place in this UI to show
  /// it. This is the single seam a real HTTP adapter calls when decoding a
  /// `422` response, so this drop behavior is enforced in one place instead
  /// of at every call site.
  factory OrganizationCreateFieldErrors.fromServerFieldErrors(
    Map<String, String> raw,
  ) {
    return OrganizationCreateFieldErrors(
      name: raw['name'],
      shortName: raw['shortName'],
      defaultTimezone: raw['defaultTimezone'],
    );
  }
}

const Object _unset = Object();

/// Client-side representation of the `POST /api/v1/organizations` request
/// body (§7.2) plus the `Idempotency-Key` the mobile client must attach.
///
/// This mirrors only the `CREATE` (`GLOBAL` scope) surface; `PATCH` and the
/// status commands are out of scope for `ORG-007`.
class OrganizationCreateRequest {
  const OrganizationCreateRequest({
    required this.name,
    required this.clientMutationId,
    this.shortName,
    this.defaultTimezone,
  });

  final String name;
  final String? shortName;
  final String? defaultTimezone;

  /// The value sent as `Idempotency-Key`. Retries of the exact same logical
  /// attempt must reuse this value; a change to [name], [shortName] or
  /// [defaultTimezone] requires a new one
  /// (`SENKRONIZASYON_VE_CAKISMA.md` §2, line 90).
  final String clientMutationId;

  /// Validates field shape per §6.3/§7.2, independent of any network call.
  OrganizationCreateFieldErrors validate() {
    final String trimmedName = name.trim();
    String? nameError;
    if (trimmedName.isEmpty) {
      nameError = 'Kurum adı boş olamaz.';
    } else if (trimmedName.length > organizationNameMaxLength) {
      nameError =
          'Kurum adı en fazla $organizationNameMaxLength karakter olabilir.';
    }

    final String? trimmedShortName = _normalizeOptional(shortName);
    String? shortNameError;
    if (trimmedShortName != null &&
        trimmedShortName.length > organizationShortNameMaxLength) {
      shortNameError =
          'Kısa ad en fazla $organizationShortNameMaxLength karakter olabilir.';
    }

    final String? trimmedTimezone = _normalizeOptional(defaultTimezone);
    String? timezoneError;
    if (trimmedTimezone != null &&
        !_ianaLikeTimezonePattern.hasMatch(trimmedTimezone)) {
      timezoneError = 'Saat dilimi geçerli bir IANA tanımlayıcısı olmalı.';
    }

    return OrganizationCreateFieldErrors(
      name: nameError,
      shortName: shortNameError,
      defaultTimezone: timezoneError,
    );
  }

  /// Normalized `name`: trimmed, never blank when [validate] reports no
  /// error.
  String get normalizedName => name.trim();

  /// Normalized `shortName`: trimmed, blank collapsed to `null`.
  String? get normalizedShortName => _normalizeOptional(shortName);

  /// Normalized `defaultTimezone`: trimmed, blank collapsed to the §2.1
  /// fallback.
  String get normalizedDefaultTimezone =>
      _normalizeOptional(defaultTimezone) ??
      organizationDefaultTimezoneFallback;
}

String? _normalizeOptional(String? raw) {
  final String? trimmed = raw?.trim();
  return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
}
