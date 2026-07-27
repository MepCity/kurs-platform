import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_theme_provider.dart';
import '../application/organization_brand_controller.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';

/// Marka yüzeyinde görüntüleme ve güncelleme yetkilerini ayrı ifade eder.
///
/// Platform-support, arşivlenmiş bir kurumda ayarları görebilir ancak
/// değiştiremez. Bu tip, o salt-okunur yüzeyi eski `canManage*` ikililerinden
/// ayırt eder; eski çağrı noktaları geriye dönük olarak yönetim yetkisini hem
/// görüntüleme hem güncelleme saymaya devam eder.
@immutable
class OrganizationBrandSettingsCapabilities {
  const OrganizationBrandSettingsCapabilities({
    required this.canViewBrand,
    required this.canUpdateBrand,
    required this.canViewModules,
    required this.canUpdateModules,
  });

  final bool canViewBrand;
  final bool canUpdateBrand;
  final bool canViewModules;
  final bool canUpdateModules;
}

class OrganizationBrandSettingsScreen extends StatefulWidget {
  const OrganizationBrandSettingsScreen({
    super.key,
    required this.organizationId,
    required this.repository,
    required this.canManageBrand,
    required this.canManageModules,
    this.capabilities,
  });
  final String organizationId;
  final OrganizationBrandRepository repository;
  final bool canManageBrand, canManageModules;
  final OrganizationBrandSettingsCapabilities? capabilities;

  OrganizationBrandSettingsCapabilities get resolvedCapabilities =>
      capabilities ??
      OrganizationBrandSettingsCapabilities(
        canViewBrand: canManageBrand,
        canUpdateBrand: canManageBrand,
        canViewModules: canManageModules,
        canUpdateModules: canManageModules,
      );
  @override
  State<OrganizationBrandSettingsScreen> createState() =>
      _OrganizationBrandSettingsScreenState();
}

class _OrganizationBrandSettingsScreenState
    extends State<OrganizationBrandSettingsScreen> {
  late OrganizationBrandController _controller;
  final _primary = TextEditingController();
  final _secondary = TextEditingController();
  final List<_PaletteRow> _palette = [];
  List<OrganizationModule> _modules = [];
  bool _brandHydrated = false,
      _colorsHydrated = false,
      _modulesHydrated = false;

  @override
  void initState() {
    super.initState();
    _start();
  }

  void _start() {
    _controller = OrganizationBrandController(
      organizationId: widget.organizationId,
      repository: widget.repository,
      loadBrandSettings: widget.resolvedCapabilities.canViewBrand,
      loadModuleSettings: widget.resolvedCapabilities.canViewModules,
    )..addListener(_changed);
    _controller.load();
  }

  @override
  void didUpdateWidget(covariant OrganizationBrandSettingsScreen old) {
    super.didUpdateWidget(old);
    if (old.organizationId != widget.organizationId ||
        old.repository != widget.repository ||
        old.canManageBrand != widget.canManageBrand ||
        old.canManageModules != widget.canManageModules ||
        old.capabilities != widget.capabilities) {
      _controller.removeListener(_changed);
      _controller.dispose();
      _clearLocalDrafts();
      _start();
    }
  }

  void _changed() {
    if (_controller.status == OrganizationBrandStatus.unauthorized) {
      _clearLocalDrafts();
      if (mounted) setState(() {});
      return;
    }
    final brand = _controller.brand;
    if (brand != null && (!_brandHydrated || !_controller.brandSection.dirty)) {
      if (_primary.text != brand.primaryColor) {
        _primary.text = brand.primaryColor;
      }
      if (_secondary.text != brand.secondaryColor) {
        _secondary.text = brand.secondaryColor;
      }
      _brandHydrated = true;
    }
    final colors = _controller.colors;
    if (colors != null) {
      if ((!_colorsHydrated || !_controller.colorsSection.dirty) &&
          !_samePaletteValues(colors.items)) {
        _disposePalette();
        _palette.addAll(colors.items.map(_PaletteRow.fromColor));
      }
      _colorsHydrated = true;
    }
    final modules = _controller.modules;
    if (modules != null) {
      if ((!_modulesHydrated || !_controller.modulesSection.dirty) &&
          !_sameModuleValues(modules.items)) {
        _modules = List.of(modules.items);
      }
      _modulesHydrated = true;
    }
    if (mounted) setState(() {});
  }

  bool _samePaletteValues(List<OrganizationBrandColor> items) {
    final draft = _colorDraft;
    if (draft.length != items.length) return false;
    for (var index = 0; index < items.length; index++) {
      if (draft[index] != items[index]) return false;
    }
    return true;
  }

  bool _sameModuleValues(List<OrganizationModule> items) {
    if (_modules.length != items.length) return false;
    for (var index = 0; index < items.length; index++) {
      if (_modules[index] != items[index]) return false;
    }
    return true;
  }

  void _clearLocalDrafts() {
    _primary.clear();
    _secondary.clear();
    _disposePalette();
    _modules = [];
    _brandHydrated = _colorsHydrated = _modulesHydrated = false;
  }

  void _disposePalette() {
    for (final row in _palette) {
      row.dispose();
    }
    _palette.clear();
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _controller.dispose();
    _primary.dispose();
    _secondary.dispose();
    _disposePalette();
    super.dispose();
  }

  List<OrganizationBrandColor> get _colorDraft => _palette
      .map(
        (r) => OrganizationBrandColor(
          colorHex: r.hex.text,
          sortOrder: int.tryParse(r.order.text) ?? -1,
        ),
      )
      .toList();
  void _changeColors() {
    _controller.setColorsDraft(_colorDraft);
    setState(() {});
  }

  Future<void> _saveBrand() async {
    if (await _controller.saveBrand(_primary.text, _secondary.text) &&
        mounted) {
      final b = _controller.brand!;
      AppThemeScope.maybeOf(context)?.updateInstitutionColorsFromHex(
        primaryHex: b.primaryColor,
        secondaryHex: b.secondaryColor,
      );
    }
  }

  Future<void> _saveColors() => _controller.saveColors(_colorDraft);
  Future<void> _saveModules() => _controller.saveModules(_modules);

  @override
  Widget build(BuildContext context) {
    final status = _controller.status;
    final capabilities = widget.resolvedCapabilities;
    if (!capabilities.canViewBrand && !capabilities.canViewModules) {
      return const Scaffold(body: AppUnauthorizedState());
    }
    if (status == OrganizationBrandStatus.loading) {
      return const Scaffold(
        body: AppLoadingState(label: 'Marka ayarları yükleniyor…'),
      );
    }
    if (status == OrganizationBrandStatus.unauthorized) {
      return Scaffold(
        body: AppUnauthorizedState(
          message: _controller.message ?? 'Bu işlem için yetkiniz yok.',
        ),
      );
    }
    if (status == OrganizationBrandStatus.error) {
      return Scaffold(
        body: AppErrorState(
          message: _controller.message ?? 'Kurum ayarları yüklenemedi.',
          onRetry: _controller.load,
        ),
      );
    }
    final brandSaving = _controller.brandSection.saving;
    final colorSaving = _controller.colorsSection.saving;
    final moduleSaving = _controller.modulesSection.saving;
    final anySaving = _controller.anySaving;
    return Scaffold(
      appBar: const AppTopBar(title: 'Kurum Ayarları', showBackButton: true),
      body: SafeArea(
        top: false,
        child: ListView(
          padding: EdgeInsets.fromLTRB(
            AppSpacing.space4,
            AppSpacing.space4,
            AppSpacing.space4,
            AppSpacing.space4 + MediaQuery.viewInsetsOf(context).bottom,
          ),
          children: [
            if (capabilities.canViewBrand) ...[
              _BrandSection(
                primary: _primary,
                secondary: _secondary,
                saving: brandSaving,
                blocked: anySaving || !capabilities.canUpdateBrand,
                error: _controller.brandSection.error,
                success: _controller.brandSection.success,
                onChanged: () =>
                    _controller.setBrandDraft(_primary.text, _secondary.text),
                onSave: _saveBrand,
              ),
              const SizedBox(height: AppSpacing.space5),
              _PaletteSection(
                rows: _palette,
                saving: colorSaving,
                blocked: anySaving || !capabilities.canUpdateBrand,
                error: _controller.colorsSection.error,
                success: _controller.colorsSection.success,
                onChanged: _changeColors,
                onAdd: () {
                  _palette.add(
                    _PaletteRow('#2E7D32', _palette.length.toString()),
                  );
                  _changeColors();
                },
                onRemove: (index) {
                  final row = _palette.removeAt(index);
                  row.dispose();
                  _changeColors();
                },
                onSave: _saveColors,
              ),
            ],
            if (capabilities.canViewModules) ...[
              const SizedBox(height: AppSpacing.space5),
              _ModulesSection(
                items: _modules,
                saving: moduleSaving,
                blocked: anySaving || !capabilities.canUpdateModules,
                error: _controller.modulesSection.error,
                success: _controller.modulesSection.success,
                onChanged: () {
                  _controller.setModulesDraft(_modules);
                  setState(() {});
                },
                onSave: _saveModules,
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _PaletteRow {
  _PaletteRow(String hex, String order)
    : hex = TextEditingController(text: hex),
      order = TextEditingController(text: order);
  factory _PaletteRow.fromColor(OrganizationBrandColor c) =>
      _PaletteRow(c.colorHex, c.sortOrder.toString());
  final TextEditingController hex, order;
  void dispose() {
    hex.dispose();
    order.dispose();
  }
}

class _SectionMessage extends StatelessWidget {
  const _SectionMessage({this.error, this.success});
  final String? error, success;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      if (error != null)
        Semantics(
          liveRegion: true,
          child: Text(
            error!,
            key: const Key('section_error'),
            style: TextStyle(color: Theme.of(c).colorScheme.error),
          ),
        ),
      if (success != null)
        Semantics(
          liveRegion: true,
          child: Text(success!, key: const Key('section_success')),
        ),
    ],
  );
}

class _BrandSection extends StatelessWidget {
  const _BrandSection({
    required this.primary,
    required this.secondary,
    required this.saving,
    required this.blocked,
    required this.error,
    required this.success,
    required this.onChanged,
    required this.onSave,
  });
  final TextEditingController primary, secondary;
  final bool saving, blocked;
  final String? error, success;
  final VoidCallback onChanged, onSave;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Marka renkleri', style: Theme.of(c).textTheme.titleMedium),
      AppTextField(
        key: const Key('brand_primary'),
        controller: primary,
        label: 'Ana renk',
        hint: '#2E7D32',
        enabled: !blocked,
        onChanged: (_) => onChanged(),
      ),
      AppTextField(
        key: const Key('brand_secondary'),
        controller: secondary,
        label: 'Yardımcı renk',
        hint: '#E65100',
        enabled: !blocked,
        onChanged: (_) => onChanged(),
      ),
      AppButton.filled(
        key: const Key('brand_save'),
        label: saving ? 'Kaydediliyor…' : 'Renkleri Kaydet',
        onPressed: blocked ? null : onSave,
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}

class _PaletteSection extends StatelessWidget {
  const _PaletteSection({
    required this.rows,
    required this.saving,
    required this.blocked,
    required this.error,
    required this.success,
    required this.onChanged,
    required this.onAdd,
    required this.onRemove,
    required this.onSave,
  });
  final List<_PaletteRow> rows;
  final bool saving, blocked;
  final String? error, success;
  final VoidCallback onChanged, onAdd;
  final ValueChanged<int> onRemove;
  final Future<void> Function() onSave;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Yardımcı palet', style: Theme.of(c).textTheme.titleMedium),
      if (rows.isEmpty)
        AppEmptyState(
          key: const Key('palette_empty'),
          title: 'Yardımcı renk yok',
          description:
              'İhtiyaç duyduğunuz yardımcı renkleri ekleyip sıralayabilirsiniz.',
          icon: Icons.palette_outlined,
          actionLabel: blocked ? null : 'İlk rengi ekle',
          onAction: blocked ? null : onAdd,
        )
      else
        ...rows.indexed.map(
          (entry) => _PaletteEditorRow(
            index: entry.$1,
            row: entry.$2,
            enabled: !blocked,
            onChanged: onChanged,
            onRemove: () => onRemove(entry.$1),
          ),
        ),
      TextButton.icon(
        key: const Key('palette_add'),
        onPressed: blocked || rows.length >= 20 ? null : onAdd,
        icon: const Icon(Icons.add),
        label: const Text('Renk ekle'),
      ),
      AppButton.filled(
        key: const Key('palette_save'),
        label: saving ? 'Kaydediliyor…' : 'Paleti Kaydet',
        onPressed: blocked ? null : () => onSave(),
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}

class _ModulesSection extends StatelessWidget {
  const _ModulesSection({
    required this.items,
    required this.saving,
    required this.blocked,
    required this.error,
    required this.success,
    required this.onChanged,
    required this.onSave,
  });
  final List<OrganizationModule> items;
  final bool saving, blocked;
  final String? error, success;
  final VoidCallback onChanged;
  final Future<void> Function() onSave;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Etkin modüller', style: Theme.of(c).textTheme.titleMedium),
      ...items.indexed.map(
        (e) => SwitchListTile(
          key: Key('module_${e.$2.code.wireName}'),
          title: Text(e.$2.code.label),
          value: e.$2.isEnabled,
          onChanged: blocked
              ? null
              : (v) {
                  items[e.$1] = e.$2.copyWith(isEnabled: v);
                  onChanged();
                },
        ),
      ),
      AppButton.filled(
        key: const Key('modules_save'),
        label: saving ? 'Kaydediliyor…' : 'Modülleri Kaydet',
        onPressed: blocked ? null : () => onSave(),
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}

class _PaletteEditorRow extends StatelessWidget {
  const _PaletteEditorRow({
    required this.index,
    required this.row,
    required this.enabled,
    required this.onChanged,
    required this.onRemove,
  });

  final int index;
  final _PaletteRow row;
  final bool enabled;
  final VoidCallback onChanged;
  final VoidCallback onRemove;

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final color = AppTextField(
        key: Key('palette_hex_$index'),
        controller: row.hex,
        label: 'Renk ${index + 1}',
        enabled: enabled,
        onChanged: (_) => onChanged(),
      );
      final order = AppTextField(
        key: Key('palette_order_$index'),
        controller: row.order,
        label: 'Sıra',
        keyboardType: TextInputType.number,
        enabled: enabled,
        onChanged: (_) => onChanged(),
      );
      final remove = IconButton(
        key: Key('palette_remove_$index'),
        tooltip: 'Rengi sil',
        constraints: const BoxConstraints(minWidth: 48, minHeight: 48),
        onPressed: enabled ? onRemove : null,
        icon: const Icon(Icons.delete_outline),
      );
      if (constraints.maxWidth < 400) {
        return Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            color,
            Row(
              children: [
                Expanded(child: order),
                remove,
              ],
            ),
          ],
        );
      }
      return Row(
        children: [
          Expanded(child: color),
          const SizedBox(width: AppSpacing.space2),
          SizedBox(width: 112, child: order),
          remove,
        ],
      );
    },
  );
}
