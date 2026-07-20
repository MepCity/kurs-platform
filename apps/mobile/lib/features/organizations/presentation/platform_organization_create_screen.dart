import 'package:flutter/material.dart';

import '../../../core/presentation/widgets/widgets.dart';
import '../../../core/theme/app_semantic_colors.dart';
import '../../../core/theme/app_spacing.dart';
import '../application/organization_create_controller.dart';
import '../domain/organization.dart';
import '../domain/organization_create_request.dart';
import '../domain/organizations_repository.dart';

/// PLAT-02 — Kurum Oluştur.
///
/// Platform yöneticisinin yeni kurum kaydı açma formu (`EKRAN_ENVANTERI.md`
/// §4). Handles the `Y` (submitting), `H` (banner error), `Z` (unauthorized)
/// and `E` (submit sync indicator) states this screen requires.
///
/// This widget only depends on the [OrganizationsRepository] port from
/// `domain`; the caller (composition root — `main.dart` today, the UI-004
/// navigation shell later) supplies the concrete adapter and decides how to
/// reach this screen from PLAT-01, so swapping the mock for a real HTTP
/// client requires no change here.
class PlatformOrganizationCreateScreen extends StatefulWidget {
  const PlatformOrganizationCreateScreen({
    required this.repository,
    super.key,
    this.onCreated,
  });

  final OrganizationsRepository repository;

  /// Invoked once, right after a successful create, with the new
  /// organization. The caller decides what happens next (e.g. pop back to
  /// PLAT-01 and refresh); this screen does not navigate on its own.
  final ValueChanged<Organization>? onCreated;

  @override
  State<PlatformOrganizationCreateScreen> createState() =>
      _PlatformOrganizationCreateScreenState();
}

class _PlatformOrganizationCreateScreenState
    extends State<PlatformOrganizationCreateScreen> {
  late OrganizationCreateController _controller;
  late final TextEditingController _nameController;
  late final TextEditingController _shortNameController;
  late final TextEditingController _timezoneController;
  bool _onCreatedFired = false;

  @override
  void initState() {
    super.initState();
    _nameController = TextEditingController();
    _shortNameController = TextEditingController();
    _timezoneController = TextEditingController(
      text: organizationDefaultTimezoneFallback,
    );
    _controller = _buildController();
  }

  @override
  void didUpdateWidget(covariant PlatformOrganizationCreateScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.repository != oldWidget.repository) {
      // A repository swap means a new caller identity/authorization context
      // (e.g. IAM-007/008 wiring a real session in place of the mock, or a
      // test swapping an authorized session for an unauthorized one). None
      // of the old context — in-flight request, field values, field errors,
      // banner message, or the Idempotency-Key bound to the old actor —
      // belongs in the new one, so this rebuilds from a clean slate rather
      // than reusing the old controller/text values.
      //
      // Disposing the old controller first flips its internal `_disposed`
      // flag, so a response the old repository is still resolving in the
      // background is discarded on arrival instead of clobbering the new
      // controller's state.
      _controller.removeListener(_handleControllerChanged);
      _controller.dispose();
      _onCreatedFired = false;
      _nameController.clear();
      _shortNameController.clear();
      _timezoneController.text = organizationDefaultTimezoneFallback;
      _controller = _buildController();
    }
  }

  OrganizationCreateController _buildController() {
    return OrganizationCreateController(repository: widget.repository)
      ..addListener(_handleControllerChanged);
  }

  @override
  void dispose() {
    _controller.removeListener(_handleControllerChanged);
    _controller.dispose();
    _nameController.dispose();
    _shortNameController.dispose();
    _timezoneController.dispose();
    super.dispose();
  }

  void _handleControllerChanged() {
    if (_controller.status == OrganizationCreateStatus.success &&
        !_onCreatedFired) {
      _onCreatedFired = true;
      final Organization? created = _controller.created;
      if (created != null) {
        widget.onCreated?.call(created);
      }
    }
    setState(() {});
  }

  void _startNewCreation() {
    _onCreatedFired = false;
    _nameController.clear();
    _shortNameController.clear();
    _timezoneController.text = organizationDefaultTimezoneFallback;
    _controller.startNewCreation();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppTopBarFactory.platform(
        title: 'Kurum Oluştur',
        showBackButton: true,
      ),
      body: SafeArea(
        top: false,
        child: switch (_controller.status) {
          // `AppUnauthorizedState` (UI-003) is a fixed, non-scrolling
          // `Padding`+`Column` — on a short landscape viewport at a large
          // text scale it can overflow just like the old `_SuccessView` did.
          // Wrapped here (this screen's own file) rather than changed inside
          // the shared widget, whose layout is UI-003's to own.
          OrganizationCreateStatus.unauthorized => const _ScrollSafeCentered(
            child: AppUnauthorizedState(
              message: 'Bu ekrana yalnızca platform yöneticileri erişebilir.',
            ),
          ),
          OrganizationCreateStatus.success => _SuccessView(
            organization: _controller.created!,
            onCreateAnother: _startNewCreation,
          ),
          OrganizationCreateStatus.editing ||
          OrganizationCreateStatus.submitting => _CreateForm(
            controller: _controller,
            nameController: _nameController,
            shortNameController: _shortNameController,
            timezoneController: _timezoneController,
          ),
        },
      ),
    );
  }
}

class _CreateForm extends StatelessWidget {
  const _CreateForm({
    required this.controller,
    required this.nameController,
    required this.shortNameController,
    required this.timezoneController,
  });

  final OrganizationCreateController controller;
  final TextEditingController nameController;
  final TextEditingController shortNameController;
  final TextEditingController timezoneController;

  @override
  Widget build(BuildContext context) {
    final bool isSubmitting = controller.isSubmitting;
    return Column(
      children: <Widget>[
        Expanded(
          child: ListView(
            padding: const EdgeInsets.all(AppSpacing.space4),
            children: <Widget>[
              AppTextField(
                key: const Key('organization_create_name_field'),
                controller: nameController,
                label: 'Kurum Adı',
                hint: 'Örn. Fındıklı Kur\'an Kursu',
                error: controller.fieldErrors.name,
                maxLength: organizationNameMaxLength,
                enabled: !isSubmitting,
                textInputAction: TextInputAction.next,
                onChanged: controller.setName,
              ),
              const SizedBox(height: AppSpacing.space4),
              AppTextField(
                key: const Key('organization_create_short_name_field'),
                controller: shortNameController,
                label: 'Kısa Ad (opsiyonel)',
                hint: 'Örn. Fındıklı',
                error: controller.fieldErrors.shortName,
                maxLength: organizationShortNameMaxLength,
                enabled: !isSubmitting,
                textInputAction: TextInputAction.next,
                onChanged: controller.setShortName,
              ),
              const SizedBox(height: AppSpacing.space4),
              AppTextField(
                key: const Key('organization_create_timezone_field'),
                controller: timezoneController,
                label: 'Saat Dilimi',
                hint: organizationDefaultTimezoneFallback,
                helper:
                    'Boş bırakılırsa $organizationDefaultTimezoneFallback kullanılır.',
                error: controller.fieldErrors.defaultTimezone,
                enabled: !isSubmitting,
                textInputAction: TextInputAction.done,
                onChanged: controller.setDefaultTimezone,
              ),
              if (controller.bannerErrorMessage != null) ...<Widget>[
                const SizedBox(height: AppSpacing.space4),
                _ErrorBanner(message: controller.bannerErrorMessage!),
              ],
            ],
          ),
        ),
        AppBottomActionArea(
          child: Row(
            children: <Widget>[
              Expanded(
                child: AppButton.filled(
                  label: isSubmitting ? 'Oluşturuluyor…' : 'Kurumu Oluştur',
                  onPressed: isSubmitting ? null : controller.submit,
                ),
              ),
              if (isSubmitting) ...<Widget>[
                const SizedBox(width: AppSpacing.space2),
                const AppSyncIndicator(status: AppSyncStatus.syncing),
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Semantics(
      liveRegion: true,
      child: Container(
        padding: const EdgeInsets.all(AppSpacing.space3),
        decoration: BoxDecoration(
          color: scheme.errorContainer,
          borderRadius: BorderRadius.circular(AppSpacing.radiusMd),
        ),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Icon(Icons.error_outline, color: scheme.onErrorContainer),
            const SizedBox(width: AppSpacing.space2),
            Expanded(
              child: Text(
                message,
                style: TextStyle(color: scheme.onErrorContainer),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _SuccessView extends StatelessWidget {
  const _SuccessView({
    required this.organization,
    required this.onCreateAnother,
  });

  final Organization organization;
  final VoidCallback onCreateAnother;

  @override
  Widget build(BuildContext context) {
    final AppSemanticColors semantic = AppSemanticColors.of(context);
    return _ScrollSafeCentered(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.space8),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: <Widget>[
            Icon(
              Icons.check_circle,
              size: AppSpacing.icon2Xl,
              color: semantic.success,
            ),
            const SizedBox(height: AppSpacing.space6),
            Text(
              '"${organization.name}" oluşturuldu.',
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: AppSpacing.textLg,
                fontWeight: FontWeight.w600,
                color: semantic.neutral800,
              ),
            ),
            const SizedBox(height: AppSpacing.space6),
            AppButton.outlined(
              label: 'Yeni Kurum Ekle',
              onPressed: onCreateAnother,
            ),
          ],
        ),
      ),
    );
  }
}

/// Makes a fixed-height, center-aligned status view (this screen's success
/// view, and the shared `AppUnauthorizedState`) safe on a short viewport.
///
/// A short landscape viewport combined with a large text scale (and, for the
/// success view, a long organization name) can make content like this taller
/// than the available height. `LayoutBuilder` + `SingleChildScrollView` +
/// `ConstrainedBox(minHeight: ...)` keeps the short-content case centered —
/// the box is forced to at least fill the viewport, so the child's own
/// `mainAxisAlignment.center` still centers within it — while letting the
/// tall-content case scroll instead of overflowing.
class _ScrollSafeCentered extends StatelessWidget {
  const _ScrollSafeCentered({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        return SingleChildScrollView(
          child: ConstrainedBox(
            constraints: BoxConstraints(minHeight: constraints.maxHeight),
            child: child,
          ),
        );
      },
    );
  }
}
