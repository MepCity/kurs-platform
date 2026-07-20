import 'dart:math';

import '../domain/organization.dart';
import '../domain/organization_create_request.dart';
import '../domain/organization_list_query.dart';
import '../domain/organization_list_result.dart';
import '../domain/organization_search_normalization.dart';
import '../domain/organization_status.dart';
import '../domain/organizations_failure.dart';
import '../domain/organizations_repository.dart';
import 'organizations_mock_session.dart';

/// In-memory stand-in for `GET /api/v1/organizations` (`GLOBAL` scope).
///
/// `AGENTS.md` allows mobile screens to be developed against a mock API
/// before the real backend lands, provided the mock's behavior matches the
/// approved contract. This adapter mirrors
/// `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §8: status/search filtering,
/// `name`/`createdAt` sort with an `id` tie-break that is never reversed by
/// `order` (§4.1), an opaque cursor bound to the actor, scope, and full
/// filter/sort/page-size context it was minted for (§5.3), and the
/// `401 UNAUTHENTICATED` / `403 FORBIDDEN` / `422 VALIDATION_FAILED` /
/// `400 INVALID_CURSOR` error cases relevant to this screen. It is a
/// `data`-layer adapter behind the `OrganizationsRepository` port — swapping
/// it for a real HTTP client later requires no change to `application` or
/// `presentation`.
///
/// ### Cursor design
///
/// The cursor a caller sees is a random, content-free opaque token (`ck_` +
/// 32 hex characters). The actual pagination position — actor, scope,
/// normalized filters, sort, page size, and a keyset marker (last item's
/// sort key + id, not a raw offset) — lives only in [_cursorRegistry],
/// server-side. This gives three properties for free, without a signing
/// library:
///
/// - The client can neither fabricate nor derive a cursor's meaning from its
///   text — there is none to derive.
/// - Changing a single character invalidates it (it simply will not be a key
///   in the registry).
/// - Resuming by keyset (not offset) means a record inserted or removed
///   ahead of the cursor's position cannot cause a skipped or repeated row.
class OrganizationsMockRepository implements OrganizationsRepository {
  OrganizationsMockRepository({
    List<Organization>? seed,
    this.latency = const Duration(milliseconds: 400),
    this.session = const OrganizationsMockSession.authenticatedPlatformAdmin(
      actorUserId: 'mock-platform-admin-1',
    ),
    DateTime Function()? now,
  }) : _organizations = List<Organization>.of(seed ?? defaultSeed()),
       _now = now ?? DateTime.now;

  /// Simulated network latency for every call.
  final Duration latency;

  /// Injectable clock so `createOrganization` tests can assert on
  /// `createdAt`/`updatedAt` deterministically instead of racing the wall
  /// clock.
  final DateTime Function() _now;

  /// The simulated caller identity/authorization. Until IAM-007/008 wire a
  /// real session, tests and callers can construct an unauthenticated or
  /// non-admin session to exercise the screen's unauthorized (Z) state
  /// exactly as a `401`/`403` response would.
  final OrganizationsMockSession session;

  final List<Organization> _organizations;

  /// Server-side cursor registry: opaque token → the position and context it
  /// was minted for. Never serialized into the token itself.
  final Map<String, _CursorEntry> _cursorRegistry = <String, _CursorEntry>{};

  /// Server-side idempotency registry for `createOrganization`, keyed by
  /// `actorUserId:clientMutationId` (§5.2 `GLOBAL` scope key). Mirrors the
  /// same "replay same content, reject different content" contract the real
  /// `idempotency_keys` table enforces.
  ///
  /// Deliberately unbounded — unlike [_cursorRegistry] (a resumption cache
  /// where evicting an old entry only makes that one cursor go stale), an
  /// evicted idempotency key here would let a replay of an already-completed
  /// key fall through to `_organizations.add(...)` again and mint a second
  /// organization for the same logical create. This mock's process lifetime
  /// (tests, a dev session) never holds enough entries for that trade-off to
  /// matter.
  final Map<String, _CreateIdempotencyEntry> _createIdempotencyRegistry =
      <String, _CreateIdempotencyEntry>{};

  static const int _maxCursorRegistrySize = 500;
  static const int _maxLimit = 100;
  static final Random _secureRandom = Random.secure();

  /// Test-only hook that simulates another actor creating an organization
  /// while this session is mid-pagination. Deliberately not part of the
  /// `OrganizationsRepository` port — only this concrete mock exposes it, so
  /// `application`/`presentation` code (which only ever holds the abstract
  /// type) cannot see or depend on it.
  void debugInsert(Organization organization) {
    _organizations.add(organization);
  }

  /// Test-only hook that simulates another actor archiving/removing an
  /// organization while this session is mid-pagination. See [debugInsert].
  void debugRemove(String organizationId) {
    _organizations.removeWhere((Organization org) => org.id == organizationId);
  }

  @override
  Future<OrganizationListResult> listOrganizations(
    OrganizationListQuery query,
  ) async {
    await Future<void>.delayed(latency);

    if (!session.isAuthenticated) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.unauthenticated,
        'Oturum geçersiz veya süresi dolmuş.',
      );
    }
    if (!session.hasGlobalPlatformAdminScope) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.forbidden,
        'Bu işlem için platform yöneticisi yetkisi gerekir.',
      );
    }

    if (query.limit <= 0 || query.limit > _maxLimit) {
      throw OrganizationsFailure(
        OrganizationsFailureCode.validationFailed,
        'limit 1-$_maxLimit aralığında olmalı.',
      );
    }
    if (query.cursor != null && query.cursor!.isEmpty) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.invalidCursor,
        'Sayfalama bağlantısı boş olamaz.',
      );
    }

    // Length validation runs *after* normalization: a search that is only
    // whitespace (however long) is "no filter" once trimmed, not an
    // oversized search term. Validating the raw, un-normalized value here
    // would reject e.g. 500 spaces even though it is equivalent to no
    // search at all.
    final OrganizationListQuery normalizedQuery = query.normalized();
    if ((normalizedQuery.search?.length ?? 0) > organizationSearchMaxLength) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.validationFailed,
        'Arama metni çok uzun.',
      );
    }

    final List<Organization> sorted = _filterAndSort(normalizedQuery);

    _CursorEntry? cursorEntry;
    if (normalizedQuery.cursor != null) {
      cursorEntry = _cursorRegistry[normalizedQuery.cursor];
      if (cursorEntry == null ||
          !cursorEntry.matchesContext(
            actorUserId: session.actorUserId!,
            scope: session.scope,
            query: normalizedQuery,
          )) {
        throw const OrganizationsFailure(
          OrganizationsFailureCode.invalidCursor,
          'Sayfalama bağlantısı bilinmiyor, süresi geçmiş veya bu sorgu '
          'bağlamına ait değil.',
        );
      }
    }

    final int startIndex = cursorEntry == null
        ? 0
        : _indexAfterMarker(sorted, cursorEntry, normalizedQuery);
    final int end = min(startIndex + normalizedQuery.limit, sorted.length);
    final List<Organization> page = startIndex >= sorted.length
        ? const <Organization>[]
        : sorted.sublist(startIndex, end);
    final bool hasNextPage = end < sorted.length;

    String? nextCursor;
    if (hasNextPage && page.isNotEmpty) {
      final Organization last = page.last;
      nextCursor = _mintCursor(
        actorUserId: session.actorUserId!,
        scope: session.scope,
        query: normalizedQuery,
        lastSortKey: _sortKeyOf(last, normalizedQuery),
        lastId: last.id,
      );
    }

    return OrganizationListResult(
      items: List<Organization>.unmodifiable(page),
      nextCursor: nextCursor,
      hasNextPage: hasNextPage,
    );
  }

  @override
  Future<Organization> createOrganization(
    OrganizationCreateRequest request,
  ) async {
    await Future<void>.delayed(latency);

    if (!session.isAuthenticated) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.unauthenticated,
        'Oturum geçersiz veya süresi dolmuş.',
      );
    }
    if (session.hasOrganizationContextToken) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.organizationContextRequired,
        'Kurum oluşturma yalnızca platform genel bağlamında yapılabilir.',
      );
    }
    if (!session.hasGlobalPlatformAdminScope) {
      throw const OrganizationsFailure(
        OrganizationsFailureCode.forbidden,
        'Bu işlem için platform yöneticisi yetkisi gerekir.',
      );
    }

    final fieldErrors = request.validate();
    if (fieldErrors.hasErrors) {
      throw OrganizationsFailure(
        OrganizationsFailureCode.validationFailed,
        fieldErrors.firstMessage!,
        fieldErrors: fieldErrors,
      );
    }

    final String normalizedName = request.normalizedName;
    final String? normalizedShortName = request.normalizedShortName;
    final String normalizedTimezone = request.normalizedDefaultTimezone;

    final String idempotencyKey =
        '${session.actorUserId}:${request.clientMutationId}';
    final _CreateIdempotencyEntry? existing =
        _createIdempotencyRegistry[idempotencyKey];
    if (existing != null) {
      if (existing.matches(
        name: normalizedName,
        shortName: normalizedShortName,
        defaultTimezone: normalizedTimezone,
      )) {
        return existing.organization;
      }
      throw const OrganizationsFailure(
        OrganizationsFailureCode.idempotencyKeyReused,
        'Bu istek anahtarı farklı bir içerikle daha önce kullanılmış.',
      );
    }

    final DateTime createdAt = _now();
    final Organization created = Organization(
      id: _generateOrganizationId(),
      name: normalizedName,
      shortName: normalizedShortName,
      defaultTimezone: normalizedTimezone,
      status: OrganizationStatus.active,
      createdAt: createdAt,
      updatedAt: createdAt,
      rowVersion: 1,
    );

    _organizations.add(created);
    _createIdempotencyRegistry[idempotencyKey] = _CreateIdempotencyEntry(
      name: normalizedName,
      shortName: normalizedShortName,
      defaultTimezone: normalizedTimezone,
      organization: created,
    );
    return created;
  }

  static String _generateOrganizationId() {
    final List<int> bytes = List<int>.generate(
      8,
      (_) => _secureRandom.nextInt(256),
    );
    final String hex = bytes
        .map((int b) => b.toRadixString(16).padLeft(2, '0'))
        .join();
    return 'org-$hex';
  }

  List<Organization> _filterAndSort(OrganizationListQuery normalizedQuery) {
    final String? needle = normalizedQuery.search == null
        ? null
        : foldForOrganizationSearchComparison(normalizedQuery.search!);

    final List<Organization> filtered = _organizations.where((
      Organization org,
    ) {
      if (normalizedQuery.status != null &&
          org.status != normalizedQuery.status) {
        return false;
      }
      if (needle != null) {
        final bool matchesName = foldForOrganizationSearchComparison(
          org.name,
        ).contains(needle);
        final bool matchesShort = foldForOrganizationSearchComparison(
          org.shortName ?? '',
        ).contains(needle);
        if (!matchesName && !matchesShort) {
          return false;
        }
      }
      return true;
    }).toList();

    filtered.sort((a, b) => _compareOrganizations(a, b, normalizedQuery));
    return filtered;
  }

  /// Primary `sort`/`order` comparison, then an `id` tie-break that is
  /// **always ascending** — never reversed by `order` — per §4.1.
  static int _compareOrganizations(
    Organization a,
    Organization b,
    OrganizationListQuery query,
  ) {
    int primary = switch (query.sort) {
      OrganizationSortField.name => a.name.compareTo(b.name),
      OrganizationSortField.createdAt => a.createdAt.compareTo(b.createdAt),
    };
    if (query.order == OrganizationSortOrder.descending) {
      primary = -primary;
    }
    if (primary != 0) {
      return primary;
    }
    return a.id.compareTo(b.id);
  }

  /// Same ordering rule as [_compareOrganizations], comparing a live
  /// [Organization] against a stored keyset marker instead of another
  /// [Organization]. Returns > 0 when [o] sorts strictly after the marker.
  static int _compareToMarker(
    Organization o,
    _CursorEntry marker,
    OrganizationListQuery query,
  ) {
    int primary = switch (query.sort) {
      OrganizationSortField.name => o.name.compareTo(marker.lastSortKey),
      OrganizationSortField.createdAt => o.createdAt.compareTo(
        DateTime.parse(marker.lastSortKey),
      ),
    };
    if (query.order == OrganizationSortOrder.descending) {
      primary = -primary;
    }
    if (primary != 0) {
      return primary;
    }
    return o.id.compareTo(marker.lastId);
  }

  /// Keyset resume: the first index whose item sorts strictly after
  /// [marker]. Unlike a raw offset, this is correct even if items were
  /// inserted or removed ahead of the marker's position between pages.
  static int _indexAfterMarker(
    List<Organization> sorted,
    _CursorEntry marker,
    OrganizationListQuery query,
  ) {
    final int index = sorted.indexWhere(
      (Organization o) => _compareToMarker(o, marker, query) > 0,
    );
    return index == -1 ? sorted.length : index;
  }

  static String _sortKeyOf(Organization org, OrganizationListQuery query) {
    return switch (query.sort) {
      OrganizationSortField.name => org.name,
      OrganizationSortField.createdAt => org.createdAt.toIso8601String(),
    };
  }

  String _mintCursor({
    required String actorUserId,
    required OrganizationsMockScope scope,
    required OrganizationListQuery query,
    required String lastSortKey,
    required String lastId,
  }) {
    if (_cursorRegistry.length >= _maxCursorRegistrySize) {
      _cursorRegistry.remove(_cursorRegistry.keys.first);
    }
    final String token = _generateOpaqueToken();
    _cursorRegistry[token] = _CursorEntry(
      actorUserId: actorUserId,
      scope: scope,
      status: query.status,
      normalizedSearch: query.search,
      sort: query.sort,
      order: query.order,
      limit: query.limit,
      lastSortKey: lastSortKey,
      lastId: lastId,
    );
    return token;
  }

  static String _generateOpaqueToken() {
    final List<int> bytes = List<int>.generate(
      16,
      (_) => _secureRandom.nextInt(256),
    );
    final String hex = bytes
        .map((int b) => b.toRadixString(16).padLeft(2, '0'))
        .join();
    return 'ck_$hex';
  }

  /// Deterministic seed data spanning all three statuses so both the status
  /// filter and cursor pagination (24 items, default page size 20) can be
  /// exercised without a real backend.
  static List<Organization> defaultSeed() {
    const List<String> names = <String>[
      'Fındıklı Kur\'an Kursu',
      'Bahçelievler Kur\'an Kursu',
      'Yeşilyurt Kur\'an Kursu',
      'Selimiye Kur\'an Kursu',
      'Nurtepe Kur\'an Kursu',
      'Karacaahmet Kur\'an Kursu',
      'Çamlıca Kur\'an Kursu',
      'Ulucami Kur\'an Kursu',
      'Ahi Evran Kur\'an Kursu',
      'Sancaktepe Kur\'an Kursu',
      'Kayabaşı Kur\'an Kursu',
      'Merkez Kur\'an Kursu',
      'Güzelbahçe Kur\'an Kursu',
      'Hacıbayram Kur\'an Kursu',
      'Emirgan Kur\'an Kursu',
      'Kızılay Kur\'an Kursu',
      'Sultanbeyli Kur\'an Kursu',
      'Yıldıztepe Kur\'an Kursu',
      'Gülbahçe Kur\'an Kursu',
      'Pınarbaşı Kur\'an Kursu',
      'Değirmendere Kur\'an Kursu',
      'Akpınar Kur\'an Kursu',
      'Beyazıt Kur\'an Kursu',
      'Kocatepe Kur\'an Kursu',
    ];

    return List<Organization>.generate(names.length, (int index) {
      final OrganizationStatus status = switch (index % 6) {
        5 => OrganizationStatus.archived,
        3 => OrganizationStatus.suspended,
        _ => OrganizationStatus.active,
      };
      final DateTime createdAt = DateTime.utc(
        2026,
        1,
        1,
      ).add(Duration(days: index * 3));
      return Organization(
        id: 'org-${(index + 1).toString().padLeft(4, '0')}',
        name: names[index],
        defaultTimezone: 'Europe/Istanbul',
        status: status,
        createdAt: createdAt,
        updatedAt: createdAt,
        rowVersion: 1,
      );
    });
  }
}

/// Server-side idempotency record for one `createOrganization` attempt. See
/// `_createIdempotencyRegistry` on [OrganizationsMockRepository].
class _CreateIdempotencyEntry {
  const _CreateIdempotencyEntry({
    required this.name,
    required this.shortName,
    required this.defaultTimezone,
    required this.organization,
  });

  final String name;
  final String? shortName;
  final String defaultTimezone;
  final Organization organization;

  bool matches({
    required String name,
    required String? shortName,
    required String defaultTimezone,
  }) {
    return this.name == name &&
        this.shortName == shortName &&
        this.defaultTimezone == defaultTimezone;
  }
}

/// Server-side-only cursor payload. Never encoded into the token the client
/// holds — see the class doc on [OrganizationsMockRepository].
class _CursorEntry {
  const _CursorEntry({
    required this.actorUserId,
    required this.scope,
    required this.status,
    required this.normalizedSearch,
    required this.sort,
    required this.order,
    required this.limit,
    required this.lastSortKey,
    required this.lastId,
  });

  final String actorUserId;
  final OrganizationsMockScope scope;
  final OrganizationStatus? status;
  final String? normalizedSearch;
  final OrganizationSortField sort;
  final OrganizationSortOrder order;
  final int limit;
  final String lastSortKey;
  final String lastId;

  bool matchesContext({
    required String actorUserId,
    required OrganizationsMockScope scope,
    required OrganizationListQuery query,
  }) {
    return this.actorUserId == actorUserId &&
        this.scope == scope &&
        status == query.status &&
        normalizedSearch == query.search &&
        sort == query.sort &&
        order == query.order &&
        limit == query.limit;
  }
}
