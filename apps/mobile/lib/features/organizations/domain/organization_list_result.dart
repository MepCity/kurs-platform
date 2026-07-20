import 'organization.dart';

/// One page of the `GLOBAL` scope organization list envelope (§6.2, §8.3).
class OrganizationListResult {
  const OrganizationListResult({
    required this.items,
    required this.nextCursor,
    required this.hasNextPage,
  });

  final List<Organization> items;

  /// Opaque cursor for the next page, or `null` when there is none.
  ///
  /// Callers must not parse this value; it is only ever replayed verbatim.
  final String? nextCursor;

  final bool hasNextPage;

  /// Whether this envelope's own invariant holds: `hasNextPage` must be
  /// exactly equivalent to "a [nextCursor] is present" (§6.2, §8.3) — not
  /// merely a one-directional implication. `hasNextPage: false` with a
  /// non-null cursor is just as broken as `hasNextPage: true` with none: a
  /// cursor that will never be replayed is either dead weight or a sign the
  /// page count is wrong. An empty-string cursor is never valid, regardless
  /// of `hasNextPage`. A repository/adapter that violates this has returned
  /// a broken page — callers must treat it as a protocol failure rather than
  /// silently trusting `hasNextPage` or fabricating pagination behavior from
  /// it.
  bool get isEnvelopeConsistent {
    if (nextCursor == '') {
      return false;
    }
    return hasNextPage == (nextCursor != null);
  }
}
