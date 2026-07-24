import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_theme_provider.dart';
import '../application/organization_brand_controller.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';

class OrganizationBrandSettingsScreen extends StatefulWidget {
  const OrganizationBrandSettingsScreen({
    super.key,
    required this.organizationId,
    required this.repository,
    required this.canManageBrand,
    required this.canManageModules,
  });
  final String organizationId;
  final OrganizationBrandRepository repository;
  final bool canManageBrand, canManageModules;
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
    )..addListener(_changed);
    _controller.load();
  }

  @override
  void didUpdateWidget(covariant OrganizationBrandSettingsScreen old) {
    super.didUpdateWidget(old);
    if (old.organizationId != widget.organizationId ||
        old.repository != widget.repository) {
      _controller.removeListener(_changed);
      _controller.dispose();
      _primary.clear();
      _secondary.clear();
      _disposePalette();
      _modules = [];
      _brandHydrated = _colorsHydrated = _modulesHydrated = false;
      _start();
    }
  }

  void _changed() {
    final brand = _controller.brand;
    if (brand != null && !_brandHydrated) {
      _primary.text = brand.primaryColor;
      _secondary.text = brand.secondaryColor;
      _brandHydrated = true;
    }
    final colors = _controller.colors;
    if (colors != null && !_colorsHydrated) {
      _palette.addAll(colors.items.map(_PaletteRow.fromColor));
      _colorsHydrated = true;
    }
    final modules = _controller.modules;
    if (modules != null && !_modulesHydrated) {
      _modules = List.of(modules.items);
      _modulesHydrated = true;
    }
    if (mounted) setState(() {});
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
    if (status == OrganizationBrandStatus.loading) {
      return const Scaffold(
        body: AppLoadingState(label: 'Marka ayarları yükleniyor…'),
      );
    }
    if (status == OrganizationBrandStatus.unauthorized ||
        (!widget.canManageBrand && !widget.canManageModules)) {
      return const Scaffold(body: AppUnauthorizedState());
    }
    final brandSaving = _controller.brandSection.saving;
    final colorSaving = _controller.colorsSection.saving;
    final moduleSaving = _controller.modulesSection.saving;
    return Scaffold(
      appBar: const AppTopBar(title: 'Kurum Ayarları', showBackButton: true),
      body: SafeArea(
        top: false,
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.space4),
          children: [
            if (widget.canManageBrand) ...[
              _BrandSection(
                primary: _primary,
                secondary: _secondary,
                saving: brandSaving,
                error: _controller.brandSection.error,
                success: _controller.brandSection.success,
                onSave: _saveBrand,
              ),
              const SizedBox(height: AppSpacing.space5),
              _PaletteSection(
                rows: _palette,
                saving: colorSaving,
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
            if (widget.canManageModules) ...[
              const SizedBox(height: AppSpacing.space5),
              _ModulesSection(
                items: _modules,
                saving: moduleSaving,
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
        Text(
          error!,
          key: const Key('section_error'),
          style: TextStyle(color: Theme.of(c).colorScheme.error),
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
    required this.error,
    required this.success,
    required this.onSave,
  });
  final TextEditingController primary, secondary;
  final bool saving;
  final String? error, success;
  final VoidCallback onSave;
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
        enabled: !saving,
      ),
      AppTextField(
        key: const Key('brand_secondary'),
        controller: secondary,
        label: 'Yardımcı renk',
        hint: '#E65100',
        enabled: !saving,
      ),
      AppButton.filled(
        label: saving ? 'Kaydediliyor…' : 'Renkleri Kaydet',
        onPressed: saving ? null : onSave,
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}

class _PaletteSection extends StatelessWidget {
  const _PaletteSection({
    required this.rows,
    required this.saving,
    required this.error,
    required this.success,
    required this.onChanged,
    required this.onAdd,
    required this.onRemove,
    required this.onSave,
  });
  final List<_PaletteRow> rows;
  final bool saving;
  final String? error, success;
  final VoidCallback onChanged, onAdd;
  final ValueChanged<int> onRemove;
  final Future<void> Function() onSave;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Yardımcı palet', style: Theme.of(c).textTheme.titleMedium),
      ...rows.indexed.map(
        (e) => Row(
          children: [
            Expanded(
              child: AppTextField(
                key: Key('palette_hex_${e.$1}'),
                controller: e.$2.hex,
                label: 'Renk ${e.$1 + 1}',
                enabled: !saving,
                onChanged: (_) => onChanged(),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 96,
              child: AppTextField(
                key: Key('palette_order_${e.$1}'),
                controller: e.$2.order,
                label: 'Sıra',
                enabled: !saving,
                onChanged: (_) => onChanged(),
              ),
            ),
            IconButton(
              key: Key('palette_remove_${e.$1}'),
              tooltip: 'Rengi sil',
              onPressed: saving ? null : () => onRemove(e.$1),
              icon: const Icon(Icons.delete_outline),
            ),
          ],
        ),
      ),
      TextButton.icon(
        key: const Key('palette_add'),
        onPressed: saving || rows.length >= 20 ? null : onAdd,
        icon: const Icon(Icons.add),
        label: const Text('Renk ekle'),
      ),
      AppButton.filled(
        label: saving ? 'Kaydediliyor…' : 'Paleti Kaydet',
        onPressed: saving ? null : () => onSave(),
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}

class _ModulesSection extends StatelessWidget {
  const _ModulesSection({
    required this.items,
    required this.saving,
    required this.error,
    required this.success,
    required this.onChanged,
    required this.onSave,
  });
  final List<OrganizationModule> items;
  final bool saving;
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
          onChanged: saving
              ? null
              : (v) {
                  items[e.$1] = e.$2.copyWith(isEnabled: v);
                  onChanged();
                },
        ),
      ),
      AppButton.filled(
        label: saving ? 'Kaydediliyor…' : 'Modülleri Kaydet',
        onPressed: saving ? null : () => onSave(),
      ),
      _SectionMessage(error: error, success: success),
    ],
  );
}
