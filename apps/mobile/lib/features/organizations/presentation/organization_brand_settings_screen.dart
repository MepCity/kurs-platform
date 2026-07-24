import 'package:flutter/material.dart';
import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_spacing.dart';
import '../../../core/theme/app_theme_provider.dart';
import '../application/organization_brand_controller.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';

/// ORG-008: dosya kabul etmeyen kurum marka, palet ve modül ayarları.
class OrganizationBrandSettingsScreen extends StatefulWidget {
  const OrganizationBrandSettingsScreen({
    super.key,
    required this.organizationId,
    required this.repository,
  });
  final String organizationId;
  final OrganizationBrandRepository repository;
  @override
  State<OrganizationBrandSettingsScreen> createState() =>
      _OrganizationBrandSettingsScreenState();
}

class _OrganizationBrandSettingsScreenState
    extends State<OrganizationBrandSettingsScreen> {
  late final OrganizationBrandController _controller;
  final _primary = TextEditingController();
  final _secondary = TextEditingController();
  List<OrganizationBrandColor> _colors = [];
  List<OrganizationModule> _modules = [];
  @override
  void initState() {
    super.initState();
    _controller = OrganizationBrandController(
      organizationId: widget.organizationId,
      repository: widget.repository,
    )..addListener(_changed);
    _controller.load();
  }

  void _changed() {
    if (_controller.status == OrganizationBrandStatus.ready &&
        _controller.brand != null &&
        _primary.text.isEmpty) {
      final b = _controller.brand!;
      _primary.text = b.primaryColor;
      _secondary.text = b.secondaryColor;
      _colors = List.of(b.colors);
      _modules = List.of(_controller.modules!.items);
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

  Future<void> _save() async {
    final saved = await _controller.save(
      primary: _primary.text,
      secondary: _secondary.text,
      colors: _colors,
      modules: _modules,
    );
    if (saved && mounted) {
      AppThemeScope.maybeOf(context)?.updateInstitutionColorsFromHex(
        primaryHex: _controller.brand!.primaryColor,
        secondaryHex: _controller.brand!.secondaryColor,
      );
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Marka ayarları kaydedildi.')),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final status = _controller.status;
    return Scaffold(
      appBar: const AppTopBar(title: 'Marka Ayarları', showBackButton: true),
      body: SafeArea(
        top: false,
        child: switch (status) {
          OrganizationBrandStatus.loading => const AppLoadingState(
            label: 'Marka ayarları yükleniyor…',
          ),
          OrganizationBrandStatus.unauthorized => const AppUnauthorizedState(
            message: 'Bu ayarları değiştirme yetkiniz yok.',
          ),
          OrganizationBrandStatus.error => _Error(
            message: _controller.message ?? 'Bir hata oluştu.',
            onRetry: _controller.load,
          ),
          _ => _Form(
            primary: _primary,
            secondary: _secondary,
            colors: _colors,
            modules: _modules,
            saving: status == OrganizationBrandStatus.saving,
            message: _controller.message,
            onChanged: () => setState(() {}),
            onSave: _save,
          ),
        },
      ),
    );
  }
}

class _Error extends StatelessWidget {
  const _Error({required this.message, required this.onRetry});
  final String message;
  final VoidCallback onRetry;
  @override
  Widget build(BuildContext context) =>
      AppErrorState(message: message, onRetry: onRetry);
}

class _Form extends StatelessWidget {
  const _Form({
    required this.primary,
    required this.secondary,
    required this.colors,
    required this.modules,
    required this.saving,
    required this.message,
    required this.onChanged,
    required this.onSave,
  });
  final TextEditingController primary, secondary;
  final List<OrganizationBrandColor> colors;
  final List<OrganizationModule> modules;
  final bool saving;
  final String? message;
  final VoidCallback onChanged;
  final VoidCallback onSave;
  @override
  Widget build(BuildContext context) => Column(
    children: [
      Expanded(
        child: ListView(
          padding: const EdgeInsets.all(AppSpacing.space4),
          children: [
            Text(
              'Kurum renkleri',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.space3),
            AppTextField(
              key: const Key('brand_primary'),
              controller: primary,
              label: 'Ana renk',
              hint: '#2E7D32',
              enabled: !saving,
              onChanged: (_) => onChanged(),
            ),
            const SizedBox(height: AppSpacing.space3),
            AppTextField(
              key: const Key('brand_secondary'),
              controller: secondary,
              label: 'Yardımcı renk',
              hint: '#E65100',
              enabled: !saving,
              onChanged: (_) => onChanged(),
            ),
            const SizedBox(height: AppSpacing.space5),
            Text(
              'Yardımcı palet',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            ...colors.indexed.map(
              (e) => ListTile(
                title: Text(e.$2.colorHex),
                trailing: IconButton(
                  key: Key('brand_color_remove_${e.$1}'),
                  icon: const Icon(Icons.delete_outline),
                  onPressed: saving
                      ? null
                      : () {
                          colors.removeAt(e.$1);
                          onChanged();
                        },
                ),
              ),
            ),
            TextButton.icon(
              key: const Key('brand_color_add'),
              onPressed: saving || colors.length >= 20
                  ? null
                  : () {
                      colors.add(
                        OrganizationBrandColor(
                          colorHex: '#FFC107',
                          sortOrder: colors.length,
                        ),
                      );
                      onChanged();
                    },
              icon: const Icon(Icons.add),
              label: const Text('Renk ekle'),
            ),
            const SizedBox(height: AppSpacing.space5),
            Text(
              'Etkin modüller',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            ...modules.indexed.map(
              (e) => SwitchListTile(
                key: Key('brand_module_${e.$2.code.wireName}'),
                title: Text(e.$2.code.label),
                value: e.$2.isEnabled,
                onChanged: saving
                    ? null
                    : (v) {
                        modules[e.$1] = e.$2.copyWith(isEnabled: v);
                        onChanged();
                      },
              ),
            ),
            if (message != null)
              Padding(
                padding: const EdgeInsets.only(top: AppSpacing.space3),
                child: Text(
                  message!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
          ],
        ),
      ),
      AppBottomActionArea(
        child: AppButton.filled(
          label: saving ? 'Kaydediliyor…' : 'Kaydet',
          onPressed: saving ? null : onSave,
        ),
      ),
    ],
  );
}
