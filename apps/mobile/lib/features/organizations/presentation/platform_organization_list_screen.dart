import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../application/organization_list_controller.dart';
import '../domain/organization.dart';
import '../domain/organization_status.dart';
import '../domain/organizations_repository.dart';

/// PLAT-01 — Kurum Listesi.
///
/// Platform yöneticisinin varsayılan giriş ekranı (`EKRAN_ENVANTERI.md` §4):
/// platform genelindeki kurumların arama ve durum filtresiyle listesi. Handles
/// all four required screen states (Y/B/H/Z) plus incremental (cursor-based)
/// pagination.
///
/// This widget only depends on the [OrganizationsRepository] port from
/// `domain`; the caller (composition root — `main.dart` today, the UI-004
/// navigation shell later) supplies the concrete adapter, so swapping the
/// mock for a real HTTP client requires no change here.
class PlatformOrganizationListScreen extends StatefulWidget {
  const PlatformOrganizationListScreen({
    required this.repository,
    super.key,
    this.onOrganizationTap,
    this.searchDebounce = const Duration(milliseconds: 350),
  });

  final OrganizationsRepository repository;

  /// Invoked when a row is tapped. `null` (the default) disables the tap
  /// target — PLAT-03 (Kurum Detayı) is a separate, not-yet-scheduled task,
  /// so this is a forward-compatible hook rather than a built-in navigation.
  final ValueChanged<Organization>? onOrganizationTap;

  /// Debounce applied to search input before a reload is triggered.
  /// Overridable so widget tests do not need to wait 350 ms per keystroke.
  final Duration searchDebounce;

  @override
  State<PlatformOrganizationListScreen> createState() =>
      _PlatformOrganizationListScreenState();
}

class _PlatformOrganizationListScreenState
    extends State<PlatformOrganizationListScreen> {
  late OrganizationListController _controller;
  late final ScrollController _scrollController;
  static const double _loadMoreTriggerOffset = 240;

  @override
  void initState() {
    super.initState();
    _controller = _buildController();
    // The ScrollController is a UI/scroll-position concern independent of
    // the data source, so it is created once here and reused across any
    // later repository swap in [didUpdateWidget] — recreating it would lose
    // the user's scroll position and load-more listener for no reason.
    _scrollController = ScrollController()..addListener(_handleScroll);
    _controller.load();
  }

  @override
  void didUpdateWidget(covariant PlatformOrganizationListScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.repository != oldWidget.repository ||
        widget.searchDebounce != oldWidget.searchDebounce) {
      // Dispose the old controller before creating the new one: `dispose()`
      // flips its internal `_disposed` flag, so any response the old
      // repository is still resolving in the background is discarded on
      // arrival instead of clobbering the new controller's state.
      _controller.removeListener(_handleControllerChanged);
      _controller.dispose();
      _controller = _buildController();
      _controller.load();
    }
  }

  OrganizationListController _buildController() {
    return OrganizationListController(
      repository: widget.repository,
      searchDebounce: widget.searchDebounce,
    )..addListener(_handleControllerChanged);
  }

  @override
  void dispose() {
    _controller.removeListener(_handleControllerChanged);
    _controller.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _handleControllerChanged() {
    final String? loadMoreError = _controller.loadMoreErrorMessage;
    if (loadMoreError == null) {
      return;
    }
    _controller.acknowledgeLoadMoreError();
    final ScaffoldMessengerState? messenger = ScaffoldMessenger.maybeOf(
      context,
    );
    messenger?.showSnackBar(AppSnackBar.error(context, message: loadMoreError));
  }

  void _handleScroll() {
    if (!_scrollController.hasClients) {
      return;
    }
    final double remaining =
        _scrollController.position.maxScrollExtent -
        _scrollController.position.pixels;
    if (remaining <= _loadMoreTriggerOffset) {
      _controller.loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppTopBarFactory.platform(title: 'Kurumlar'),
      body: SafeArea(
        top: false,
        child: ListenableBuilder(
          listenable: _controller,
          builder: (BuildContext context, Widget? child) {
            return Column(
              children: <Widget>[
                _SearchField(onChanged: _controller.search),
                _StatusFilterRow(
                  selected: _controller.statusFilter,
                  onSelected: _controller.filterByStatus,
                ),
                Expanded(child: _buildBody(context)),
              ],
            );
          },
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context) {
    switch (_controller.viewStatus) {
      case OrganizationListViewStatus.loading:
        return const AppLoadingState(label: 'Kurumlar yükleniyor…');
      case OrganizationListViewStatus.unauthorized:
        return const AppUnauthorizedState(
          message: 'Bu ekrana yalnızca platform yöneticileri erişebilir.',
        );
      case OrganizationListViewStatus.error:
        return AppErrorState(
          message:
              _controller.errorMessage ?? 'Kurumlar yüklenirken hata oluştu.',
          onRetry: _controller.retry,
        );
      case OrganizationListViewStatus.empty:
        return AppEmptyState(
          icon: Icons.business_outlined,
          title: _controller.isFiltered
              ? 'Sonuç bulunamadı'
              : 'Henüz kurum yok',
          description: _controller.isFiltered
              ? 'Farklı bir arama terimi veya durum filtresi deneyin.'
              : 'Platform üzerinde henüz kayıtlı bir kurum bulunmuyor.',
        );
      case OrganizationListViewStatus.content:
        return _OrganizationListView(
          items: _controller.items,
          isLoadingMore: _controller.isLoadingMore,
          scrollController: _scrollController,
          onTap: widget.onOrganizationTap,
        );
    }
  }
}

class _SearchField extends StatelessWidget {
  const _SearchField({required this.onChanged});

  final ValueChanged<String> onChanged;

  @override
  Widget build(BuildContext context) {
    // The visible hint text is not reliably exposed as an accessible label
    // through `InputDecoration.hintText` alone, so an explicit `Semantics`
    // label is added rather than depending on that merge behavior.
    return Semantics(
      label: 'Kurum adına göre ara',
      textField: true,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(
          AppSpacing.space4,
          AppSpacing.space4,
          AppSpacing.space4,
          AppSpacing.space2,
        ),
        child: AppTextField(
          hint: 'Kurum adına göre ara',
          prefixIcon: const Icon(Icons.search),
          textInputAction: TextInputAction.search,
          onChanged: onChanged,
        ),
      ),
    );
  }
}

class _StatusFilterRow extends StatelessWidget {
  const _StatusFilterRow({required this.selected, required this.onSelected});

  final OrganizationStatus? selected;
  final ValueChanged<OrganizationStatus?> onSelected;

  static const List<(OrganizationStatus?, String)> _options =
      <(OrganizationStatus?, String)>[
        (null, 'Tümü'),
        (OrganizationStatus.active, 'Aktif'),
        (OrganizationStatus.suspended, 'Askıda'),
        (OrganizationStatus.archived, 'Arşiv'),
      ];

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      height: AppSpacing.chipHeight + AppSpacing.space4,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: AppSpacing.space4),
        itemCount: _options.length,
        separatorBuilder: (BuildContext context, int index) =>
            const SizedBox(width: AppSpacing.space2),
        itemBuilder: (BuildContext context, int index) {
          final (OrganizationStatus? value, String label) = _options[index];
          return ChoiceChip(
            label: Text(label),
            selected: selected == value,
            onSelected: (_) => onSelected(value),
          );
        },
      ),
    );
  }
}

class _OrganizationListView extends StatelessWidget {
  const _OrganizationListView({
    required this.items,
    required this.isLoadingMore,
    required this.scrollController,
    required this.onTap,
  });

  final List<Organization> items;
  final bool isLoadingMore;
  final ScrollController scrollController;
  final ValueChanged<Organization>? onTap;

  @override
  Widget build(BuildContext context) {
    final int itemCount = items.length + (isLoadingMore ? 1 : 0);
    return ListView.builder(
      key: const Key('organization_list_view'),
      controller: scrollController,
      itemCount: itemCount,
      itemBuilder: (BuildContext context, int index) {
        if (index >= items.length) {
          return const Padding(
            padding: EdgeInsets.all(AppSpacing.space4),
            child: Center(
              child: SizedBox(
                width: AppSpacing.iconMd,
                height: AppSpacing.iconMd,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            ),
          );
        }
        final Organization organization = items[index];
        return AppListItem(
          title: organization.name,
          subtitle: organization.shortName ?? organization.defaultTimezone,
          trailing: _StatusChip(status: organization.status),
          onTap: onTap == null ? null : () => onTap!(organization),
        );
      },
    );
  }
}

class _StatusChip extends StatelessWidget {
  const _StatusChip({required this.status});

  final OrganizationStatus status;

  @override
  Widget build(BuildContext context) {
    final (String label, AppStatusType type) = switch (status) {
      OrganizationStatus.active => ('Aktif', AppStatusType.success),
      OrganizationStatus.suspended => ('Askıda', AppStatusType.warning),
      OrganizationStatus.archived => ('Arşiv', AppStatusType.neutral),
    };
    return AppStatusChip(label: label, type: type);
  }
}
