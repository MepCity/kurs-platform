import 'package:flutter/material.dart';
import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_theme_provider.dart';
import '../application/organization_brand_controller.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';

/// BRAND_MANAGE ve MODULE_MANAGE birbirinden bağımsız render sınırlarıdır.
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
  List<OrganizationBrandColor> _colors = [];
  List<OrganizationModule> _modules = [];
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
      _colors = [];
      _modules = [];
      _start();
    }
  }

  void _changed() {
    final b = _controller.brand;
    final c = _controller.colors;
    final m = _controller.modules;
    if (b != null &&
        c != null &&
        m != null &&
        _controller.status == OrganizationBrandStatus.ready) {
      _primary.text = b.primaryColor;
      _secondary.text = b.secondaryColor;
      _colors = List.of(c.items);
      _modules = List.of(m.items);
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller.removeListener(_changed);
    _controller.dispose();
    _primary.dispose();
    _secondary.dispose();
    super.dispose();
  }

  Future<void> _brand() async {
    if (await _controller.saveBrand(_primary.text, _secondary.text) &&
        mounted) {
      final b = _controller.brand!;
      AppThemeScope.maybeOf(context)?.updateInstitutionColorsFromHex(
        primaryHex: b.primaryColor,
        secondaryHex: b.secondaryColor,
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final s = _controller.status;
    if (s == OrganizationBrandStatus.loading) {
      return const Scaffold(
        body: AppLoadingState(label: 'Marka ayarları yükleniyor…'),
      );
    }
    if (s == OrganizationBrandStatus.unauthorized) {
      return const Scaffold(body: AppUnauthorizedState());
    }
    if (s == OrganizationBrandStatus.error) {
      return Scaffold(
        body: AppErrorState(
          message: _controller.message ?? 'Hata',
          onRetry: _controller.load,
        ),
      );
    }
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
                saving: s == OrganizationBrandStatus.savingBrand,
                onSave: _brand,
              ),
              const SizedBox(height: AppSpacing.space5),
              _PaletteSection(
                items: _colors,
                saving: s == OrganizationBrandStatus.savingColors,
                onChanged: () => setState(() {}),
                onSave: () => _controller.saveColors(_colors),
              ),
            ],
            if (widget.canManageModules) ...[
              const SizedBox(height: AppSpacing.space5),
              _ModulesSection(
                items: _modules,
                saving: s == OrganizationBrandStatus.savingModules,
                onChanged: () => setState(() {}),
                onSave: () => _controller.saveModules(_modules),
              ),
            ],
            if (!widget.canManageBrand && !widget.canManageModules)
              const AppUnauthorizedState(),
            if (_controller.message != null)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(
                  _controller.message!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _BrandSection extends StatelessWidget {
  const _BrandSection({
    required this.primary,
    required this.secondary,
    required this.saving,
    required this.onSave,
  });
  final TextEditingController primary, secondary;
  final bool saving;
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
    ],
  );
}

class _PaletteSection extends StatelessWidget {
  const _PaletteSection({
    required this.items,
    required this.saving,
    required this.onChanged,
    required this.onSave,
  });
  final List<OrganizationBrandColor> items;
  final bool saving;
  final VoidCallback onChanged;
  final Future<bool> Function() onSave;
  @override
  Widget build(BuildContext c) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text('Yardımcı palet', style: Theme.of(c).textTheme.titleMedium),
      ...items.indexed.map(
        (e) => Row(
          children: [
            Expanded(
              child: AppTextField(
                key: Key('palette_hex_${e.$1}'),
                controller: TextEditingController(text: e.$2.colorHex),
                label: 'Renk ${e.$1 + 1}',
                enabled: !saving,
                onChanged: (v) {
                  items[e.$1] = OrganizationBrandColor(
                    colorHex: v,
                    sortOrder: e.$2.sortOrder,
                  );
                  onChanged();
                },
              ),
            ),
            IconButton(
              key: Key('palette_remove_${e.$1}'),
              onPressed: saving
                  ? null
                  : () {
                      items.removeAt(e.$1);
                      onChanged();
                    },
              icon: const Icon(Icons.delete_outline),
            ),
          ],
        ),
      ),
      TextButton.icon(
        key: const Key('palette_add'),
        onPressed: saving || items.length >= 20
            ? null
            : () {
                items.add(
                  OrganizationBrandColor(
                    colorHex: '#000000',
                    sortOrder: items.length,
                  ),
                );
                onChanged();
              },
        icon: const Icon(Icons.add),
        label: const Text('Renk ekle'),
      ),
      AppButton.filled(
        label: saving ? 'Kaydediliyor…' : 'Paleti Kaydet',
        onPressed: saving ? null : () => onSave(),
      ),
    ],
  );
}

class _ModulesSection extends StatelessWidget {
  const _ModulesSection({
    required this.items,
    required this.saving,
    required this.onChanged,
    required this.onSave,
  });
  final List<OrganizationModule> items;
  final bool saving;
  final VoidCallback onChanged;
  final Future<bool> Function() onSave;
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
    ],
  );
}
