import 'dart:async';

import 'package:flutter/foundation.dart';

import '../domain/organization.dart';
import '../domain/organization_list_query.dart';
import '../domain/organization_list_result.dart';
import '../domain/organization_search_normalization.dart';
import '../domain/organization_status.dart';
import '../domain/organizations_failure.dart';
import '../domain/organizations_repository.dart';

/// Screen-level view status for PLAT-01, aligned with the Y/B/H/Z states
/// required by `EKRAN_ENVANTERI.md` §1.2 for the Kurum Listesi screen.
enum OrganizationListViewStatus {
  /// Y — initial or filter-triggered load in flight; no content to show yet.
  loading,

  /// Successful load with at least one item.
  content,

  /// B — successful load with zero items (no organizations, or none match
  /// the active search/status filter).
  empty,

  /// H — the load failed for a reason other than authorization.
  error,

  /// Z — the load failed because the actor is not an authenticated/valid
  /// platform administrator (401/403 from the ORG contract). Reaching this
  /// state always clears any previously loaded page data — see
  /// [_clearForUnauthorized].
  unauthorized,
}

/// Orchestrates PLAT-01 (Kurum Listesi): search, status filter, cursor
/// pagination and the four screen states, against [OrganizationsRepository].
///
/// This controller does not create its own repository; the concrete adapter
/// (mock today, real HTTP client once ORG-003/ORG-004 land) is injected by
/// the composition root, keeping this layer independent of `data`.
///
/// The caller is responsible for invoking [load] once after construction
/// (e.g. from `initState`); the controller does not auto-load so that tests
/// can observe the pre-load state deterministically.
class OrganizationListController extends ChangeNotifier {
  OrganizationListController({
    required this._repository,
    this._searchDebounce = const Duration(milliseconds: 350),
  });

  final OrganizationsRepository _repository;
  final Duration _searchDebounce;

  Timer? _debounceTimer;

  /// Incremented on every fresh (non-loadMore) request so that a response
  /// from a superseded request (e.g. an old search) is discarded instead of
  /// clobbering newer state — the same stale-response guard the sync
  /// contract uses for concurrent writes (`SENKRONIZASYON_VE_CAKISMA.md`).
  int _requestGeneration = 0;
  bool _disposed = false;

  OrganizationListViewStatus _viewStatus = OrganizationListViewStatus.loading;
  List<Organization> _items = const <Organization>[];
  OrganizationStatus? _statusFilter;
  String _searchText = '';
  OrganizationListQuery _lastQuery = const OrganizationListQuery();
  String? _nextCursor;
  bool _hasNextPage = false;
  bool _isLoadingMore = false;
  String? _errorMessage;
  String? _loadMoreErrorMessage;

  OrganizationListViewStatus get viewStatus => _viewStatus;
  List<Organization> get items => List<Organization>.unmodifiable(_items);
  OrganizationStatus? get statusFilter => _statusFilter;
  String get searchText => _searchText;
  bool get hasNextPage => _hasNextPage;
  bool get isLoadingMore => _isLoadingMore;

  /// Set only when [viewStatus] is [OrganizationListViewStatus.error] or
  /// [OrganizationListViewStatus.unauthorized].
  String? get errorMessage => _errorMessage;

  /// Set when a [loadMore] page request fails for a reason other than
  /// authorization loss; existing [items] remain visible. The presentation
  /// layer should surface this once (e.g. via a snackbar) and then call
  /// [acknowledgeLoadMoreError]. Never set together with
  /// [OrganizationListViewStatus.unauthorized] — an authorization failure
  /// during `loadMore` clears the page instead (see
  /// [_clearForUnauthorized]).
  String? get loadMoreErrorMessage => _loadMoreErrorMessage;

  /// Whether a non-default search or status filter is active. Drives the
  /// empty-state copy ("no matches" vs. "no organizations yet").
  bool get isFiltered => _statusFilter != null || _searchText.isNotEmpty;

  /// Loads the first page for the current filters.
  ///
  /// Safe to call repeatedly; each call supersedes any in-flight load, and a
  /// response for a superseded call is discarded when it arrives.
  Future<void> load() async {
    if (_disposed) {
      return;
    }
    _debounceTimer?.cancel();
    final int generation = ++_requestGeneration;
    _viewStatus = OrganizationListViewStatus.loading;
    _errorMessage = null;
    _loadMoreErrorMessage = null;
    notifyListeners();

    final OrganizationListQuery query = OrganizationListQuery(
      status: _statusFilter,
      search: _searchText.isEmpty ? null : _searchText,
    ).normalized();

    await _runQuery(query, generation: generation, append: false);
  }

  /// Re-runs [load] for the current filters, e.g. from the error state's
  /// retry action.
  Future<void> retry() => load();

  /// Updates the search text and reloads after a debounce period of
  /// inactivity.
  ///
  /// [text] is canonicalized immediately (trimmed, blank collapsed to "no
  /// filter") so `'kurs'` and `'  kurs  '` are recognized as the same query
  /// and neither restart the debounce nor bump [_requestGeneration]. Calling
  /// this again before the debounce fires cancels the previous timer, so
  /// only the last keystroke's canonical value triggers a load.
  ///
  /// A canonical change immediately invalidates the current generation —
  /// *before* the debounce fires — so an in-flight `loadMore()` for the
  /// filter context being replaced cannot append a page to a list that is
  /// about to be reloaded, and cannot surface its eventual failure as a
  /// snackbar either.
  void search(String text) {
    if (_disposed) {
      return;
    }
    final String canonical = normalizeOrganizationSearchText(text) ?? '';
    if (canonical == _searchText) {
      return;
    }
    _searchText = canonical;
    // Supersede whatever is in flight (a load or a loadMore) right away.
    // Waiting for the debounce to fire would let a same-generation
    // `loadMore()` that happens to resolve first append a stale page, or
    // surface a stale failure, for a query this search change is about to
    // replace.
    _requestGeneration++;
    _isLoadingMore = false;
    _loadMoreErrorMessage = null;
    notifyListeners();

    _debounceTimer?.cancel();
    _debounceTimer = Timer(_searchDebounce, () {
      unawaited(load());
    });
  }

  /// Applies a status filter (or clears it with `null`) and reloads
  /// immediately — filter chips are a deliberate action, not typing, so no
  /// debounce applies.
  void filterByStatus(OrganizationStatus? status) {
    if (_disposed || _statusFilter == status) {
      return;
    }
    _statusFilter = status;
    unawaited(load());
  }

  /// Fetches the next page and appends it to [items].
  ///
  /// No-ops when there is no next page, a page fetch is already in flight,
  /// the current state is not [OrganizationListViewStatus.content], or a
  /// debounced search reload is about to fire — starting a `loadMore` for a
  /// filter context that is already about to be replaced would either waste
  /// the request or append a page that belongs to the wrong query.
  Future<void> loadMore() async {
    if (_disposed ||
        !_hasNextPage ||
        _isLoadingMore ||
        _viewStatus != OrganizationListViewStatus.content ||
        (_debounceTimer?.isActive ?? false)) {
      return;
    }
    final int generation = _requestGeneration;
    _isLoadingMore = true;
    _loadMoreErrorMessage = null;
    notifyListeners();

    final OrganizationListQuery query = _lastQuery.withCursor(_nextCursor);
    await _runQuery(query, generation: generation, append: true);
  }

  /// Clears [loadMoreErrorMessage] after the presentation layer has shown it.
  void acknowledgeLoadMoreError() {
    if (_disposed || _loadMoreErrorMessage == null) {
      return;
    }
    _loadMoreErrorMessage = null;
    notifyListeners();
  }

  Future<void> _runQuery(
    OrganizationListQuery query, {
    required int generation,
    required bool append,
  }) async {
    try {
      final OrganizationListResult result = await _repository.listOrganizations(
        query,
      );
      if (_disposed || generation != _requestGeneration) {
        return;
      }
      if (!result.isEnvelopeConsistent) {
        // A repository/adapter bug (hasNextPage: true with no cursor) is a
        // protocol failure, not "no more data" — route it through the same
        // handling as a generic server error rather than trusting it.
        throw const OrganizationsFailure(
          OrganizationsFailureCode.internalError,
          'Sunucu geçersiz sayfalama bilgisi döndürdü.',
        );
      }
      _lastQuery = query;
      _nextCursor = result.nextCursor;
      _hasNextPage = result.hasNextPage;
      _isLoadingMore = false;
      _items = append
          ? <Organization>[..._items, ...result.items]
          : List<Organization>.of(result.items);
      _viewStatus = _items.isEmpty
          ? OrganizationListViewStatus.empty
          : OrganizationListViewStatus.content;
      notifyListeners();
    } on OrganizationsFailure catch (failure) {
      if (_disposed || generation != _requestGeneration) {
        return;
      }
      _isLoadingMore = false;
      if (failure.isUnauthorized) {
        // Whether this was the first page or a loadMore page, a 401/403
        // means the platform-wide organization data on screen (and in this
        // controller's state) is no longer something this actor is entitled
        // to see. Drop it entirely instead of leaving it reachable via
        // `items` or partially visible behind a snackbar.
        _clearForUnauthorized(failure.message);
        return;
      }
      if (append) {
        _loadMoreErrorMessage = failure.message;
        notifyListeners();
        return;
      }
      _errorMessage = failure.message;
      _viewStatus = OrganizationListViewStatus.error;
      notifyListeners();
    } catch (_) {
      if (_disposed || generation != _requestGeneration) {
        return;
      }
      _isLoadingMore = false;
      const String genericMessage =
          'Kurumlar yüklenirken beklenmeyen bir hata oluştu.';
      if (append) {
        _loadMoreErrorMessage = genericMessage;
        notifyListeners();
        return;
      }
      _errorMessage = genericMessage;
      _viewStatus = OrganizationListViewStatus.error;
      notifyListeners();
    }
  }

  void _clearForUnauthorized(String message) {
    _items = const <Organization>[];
    _nextCursor = null;
    _hasNextPage = false;
    _isLoadingMore = false;
    _loadMoreErrorMessage = null;
    _errorMessage = message;
    _viewStatus = OrganizationListViewStatus.unauthorized;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _debounceTimer?.cancel();
    super.dispose();
  }
}
