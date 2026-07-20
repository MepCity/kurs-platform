import 'organization_search_normalization.dart';
import 'organization_status.dart';

/// Sortable fields for `GET /api/v1/organizations` (GLOBAL scope only).
///
/// Matches `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §8.2.
enum OrganizationSortField { name, createdAt }

enum OrganizationSortOrder { ascending, descending }

/// Query parameters for the platform-admin (`GLOBAL` scope) organization list.
///
/// PLAT-01 is a platform-yönetici-only screen (EKRAN_ENVANTERI.md §4), so this
/// type only models the `GLOBAL` scope parameter set from §8.2. The
/// `ORGANIZATION` scope variant (org actor, no query parameters) is out of
/// scope for this screen.
class OrganizationListQuery {
  // Deliberately not `assert`-validated: asserts are stripped from release
  // builds, so an invalid `limit` (e.g. from a future non-UI caller) would
  // silently pass through in production. The authoritative, always-active
  // check lives where the query is actually executed
  // (`OrganizationsMockRepository`, and the real HTTP adapter later),
  // exactly like a server would validate an incoming request.
  const OrganizationListQuery({
    this.status,
    this.search,
    this.sort = OrganizationSortField.name,
    this.order = OrganizationSortOrder.ascending,
    this.limit = 20,
    this.cursor,
  });

  final OrganizationStatus? status;
  final String? search;
  final OrganizationSortField sort;
  final OrganizationSortOrder order;
  final int limit;
  final String? cursor;

  /// Returns a copy with the given fields replaced.
  ///
  /// Passing [clearCursor] resets [cursor] to `null` even though `copyWith`
  /// otherwise cannot distinguish "not provided" from "set to null".
  OrganizationListQuery copyWith({
    OrganizationStatus? status,
    bool clearStatus = false,
    String? search,
    OrganizationSortField? sort,
    OrganizationSortOrder? order,
    int? limit,
    String? cursor,
    bool clearCursor = false,
  }) {
    return OrganizationListQuery(
      status: clearStatus ? null : (status ?? this.status),
      search: search ?? this.search,
      sort: sort ?? this.sort,
      order: order ?? this.order,
      limit: limit ?? this.limit,
      cursor: clearCursor ? null : (cursor ?? this.cursor),
    );
  }

  /// Returns a copy with [cursor] replaced and every other filter untouched.
  ///
  /// Distinct from [copyWith] so call sites that only page forward cannot
  /// accidentally drop the active search/status/sort context.
  OrganizationListQuery withCursor(String? cursor) {
    return OrganizationListQuery(
      status: status,
      search: search,
      sort: sort,
      order: order,
      limit: limit,
      cursor: cursor,
    );
  }

  /// Whether this query's filter/sort/page-size context matches [other].
  ///
  /// Used to validate that a cursor is only replayed against the same
  /// aktör/scope/filter/sort/limit context it was minted for (§5.3). Compares
  /// [search] as given — callers must normalize both sides first (see
  /// [normalized]) so `'kurs'` and `' kurs '` are recognized as the same
  /// context.
  bool hasSameFilterContext(OrganizationListQuery other) {
    return status == other.status &&
        search == other.search &&
        sort == other.sort &&
        order == other.order &&
        limit == other.limit;
  }

  /// Returns the canonical form of this query: [search] trimmed, with a
  /// blank-only value collapsed to `null`.
  ///
  /// Every layer (controller, mock repository, future HTTP adapter) must
  /// normalize before comparing or binding a cursor, so `'kurs'`, `' kurs '`
  /// and `'kurs '` are always treated as the exact same query.
  OrganizationListQuery normalized() {
    final String? normalizedSearch = normalizeOrganizationSearchText(search);
    if (normalizedSearch == search) {
      return this;
    }
    return OrganizationListQuery(
      status: status,
      search: normalizedSearch,
      sort: sort,
      order: order,
      limit: limit,
      cursor: cursor,
    );
  }
}
