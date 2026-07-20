/// Canonical search-text handling for the ORG list surface.
///
/// A single shared source of truth so the controller (before sending a
/// query), the mock repository (before filtering and before binding a
/// cursor), and any future HTTP adapter all treat the exact same input as
/// the exact same logical query. Duplicating this logic per layer is how a
/// cursor minted under one layer's idea of "trimmed" silently stops
/// matching another layer's idea of "trimmed".
library;

/// Maximum accepted search length.
///
/// The ORG API contract does not specify a bound; this mirrors the
/// `name` field's own 1-200 character limit from
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §6.3 as an explicit,
/// documented product-scope choice (a search term can never usefully exceed
/// what it is searching within) rather than an arbitrary guess.
const int organizationSearchMaxLength = 200;

/// Trims [raw] and collapses a blank-only value to `null`.
///
/// `null`, `''` and `'   '` are all treated as "no search filter" — the same
/// canonical form every layer must agree on.
String? normalizeOrganizationSearchText(String? raw) {
  final String? trimmed = raw?.trim();
  return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
}

/// Case-folds [value] for organization search comparisons, Turkish-aware.
///
/// Every organization name in this product is a Turkish course name, so
/// plain Unicode case folding is wrong for the one pair of letters where
/// Turkish casing actually diverges from it: dotless `I`/`ı` and dotted
/// `İ`/`i` are a different letter pair from each other in Turkish, whereas
/// standard Unicode folds both `I` and `İ` down to `i`. Left as plain
/// `toLowerCase()`, a search for `"İstanbul"` would fail to match a stored
/// `"İSTANBUL"` (folds to `"i̇stanbul"` vs `"istanbul"` depending on
/// decomposition) and `"IĞDIR"` would fold to `"iğdir"` instead of the
/// correct `"ığdır"`.
///
/// This is deliberately **not** `String.toLowerCase(languageTag)` or any
/// OS/ICU default-locale API: those depend on the device's active locale,
/// so the exact same input could fold differently on two phones (or in a
/// CI test vs. a Turkish-locale device), breaking the "same input, same
/// query" guarantee every layer relies on. Instead, the two
/// locale-sensitive characters are substituted explicitly first, then the
/// (locale-independent) default Unicode lowercase handles everything else
/// — Ğ, Ş, Ç, Ö, Ü and Latin letters already lower-case identically either
/// way. The result is a pure function: same output on Android, iOS and in
/// every test, regardless of device locale.
String foldForOrganizationSearchComparison(String value) {
  final String turkishSafe = value.replaceAll('İ', 'i').replaceAll('I', 'ı');
  return turkishSafe.toLowerCase();
}
