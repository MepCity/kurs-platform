import 'dart:math';
import 'package:flutter/foundation.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';
import '../domain/organizations_failure.dart';

enum OrganizationBrandStatus {
  loading,
  ready,
  savingBrand,
  savingColors,
  savingModules,
  unauthorized,
  error,
}

/// Bir yüzeyin sunucu snapshot'ı ve ekrandaki taslağı birbirinden ayrıdır.
class OrganizationBrandSection<T> {
  OrganizationBrandSection({
    this.snapshot,
    this.draft,
    this.error,
    this.success,
    this.pendingKey,
  });
  T? snapshot;
  T? draft;
  String? error;
  String? success;
  String? pendingKey;
  bool saving = false;
  bool get dirty =>
      snapshot != null && draft != null && '$snapshot' != '$draft';
}

/// Her uç için ayrı idempotency anahtarı ve generation koruması taşır.
class OrganizationBrandController extends ChangeNotifier {
  OrganizationBrandController({
    required this.organizationId,
    required this._repository,
  });
  final String organizationId;
  final OrganizationBrandRepository _repository;
  bool _disposed = false;
  int _generation = 0;
  OrganizationBrandStatus _status = OrganizationBrandStatus.loading;
  final brandSection = OrganizationBrandSection<OrganizationBrand>();
  final colorsSection = OrganizationBrandSection<OrganizationBrandColors>();
  final modulesSection = OrganizationBrandSection<OrganizationModules>();
  String? _message;
  final Map<String, String> _keys = <String, String>{};
  OrganizationBrandStatus get status => _status;
  OrganizationBrand? get brand => brandSection.snapshot;
  OrganizationBrandColors? get colors => colorsSection.snapshot;
  OrganizationModules? get modules => modulesSection.snapshot;
  String? get message => _message;
  static final Random _random = Random.secure();
  String _key(String operation, String fingerprint) => _keys.putIfAbsent(
    '$operation:$fingerprint',
    () =>
        'cm_${List<int>.generate(16, (_) => _random.nextInt(256)).map((n) => n.toRadixString(16).padLeft(2, '0')).join()}',
  );
  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load({String? conflictMessage}) async {
    final g = ++_generation;
    _status = OrganizationBrandStatus.loading;
    _message = conflictMessage;
    _notify();
    try {
      final brand = await _repository.getBrand(organizationId);
      final colors = await _repository.getBrandColors(organizationId);
      final modules = await _repository.getModules(organizationId);
      if (_disposed || g != _generation) return;
      // Aynı organizations.rowVersion üç kaynak için ortak snapshot olmalıdır.
      if (brand.rowVersion != colors.rowVersion ||
          brand.rowVersion != modules.rowVersion) {
        _message = 'Ayarların sürümü tutarsız; lütfen yeniden deneyin.';
        _status = OrganizationBrandStatus.error;
        _notify();
        return;
      }
      _mergeBrand(brand);
      _mergeColors(colors);
      _mergeModules(modules);
      _status = OrganizationBrandStatus.ready;
    } on OrganizationsFailure catch (e) {
      if (_disposed || g != _generation) return;
      _message = e.message;
      _status = e.isUnauthorized
          ? OrganizationBrandStatus.unauthorized
          : OrganizationBrandStatus.error;
    } catch (_) {
      if (_disposed || g != _generation) return;
      _message = 'Marka ayarları yüklenemedi.';
      _status = OrganizationBrandStatus.error;
    }
    _notify();
  }

  void _mergeBrand(OrganizationBrand value) {
    brandSection.snapshot = value;
    brandSection.draft ??= value;
  }

  void _mergeColors(OrganizationBrandColors value) {
    colorsSection.snapshot = value;
    colorsSection.draft ??= value;
  }

  void _mergeModules(OrganizationModules value) {
    modulesSection.snapshot = value;
    modulesSection.draft ??= value;
  }

  void setBrandDraft(String primary, String secondary) =>
      brandSection.draft = OrganizationBrand(
        primaryColor: primary,
        secondaryColor: secondary,
        rowVersion: brandSection.snapshot?.rowVersion ?? 0,
      );
  void setColorsDraft(List<OrganizationBrandColor> items) =>
      colorsSection.draft = OrganizationBrandColors(
        rowVersion: colorsSection.snapshot?.rowVersion ?? 0,
        items: items,
      );
  void setModulesDraft(List<OrganizationModule> items) =>
      modulesSection.draft = OrganizationModules(
        rowVersion: modulesSection.snapshot?.rowVersion ?? 0,
        items: items,
      );

  Future<bool> saveBrand(String primary, String secondary) => _mutate(
    'brand',
    brandSection,
    OrganizationBrandStatus.savingBrand,
    () {
      final error =
          validateBrandHex('Ana renk', primary) ??
          validateBrandHex('Yardımcı renk', secondary);
      if (error != null) {
        throw OrganizationsFailure(
          OrganizationsFailureCode.validationFailed,
          error,
        );
      }
      final value = OrganizationBrand(
        primaryColor: primary,
        secondaryColor: secondary,
        rowVersion: brandSection.snapshot!.rowVersion,
      );
      return _repository.updateBrand(
        organizationId,
        value,
        _key('brand', '$primary|$secondary|${value.rowVersion}'),
      );
    },
    (v) => v as OrganizationBrand,
  );
  Future<bool> saveColors(List<OrganizationBrandColor> items) => _mutate(
    'palette',
    colorsSection,
    OrganizationBrandStatus.savingColors,
    () {
      final normalized = items
          .map((e) => normalizeBrandHex(e.colorHex))
          .toList();
      if (items.length > 20 ||
          items.any(
            (e) =>
                !RegExp(
                  r'^#[0-9A-F]{6}$',
                ).hasMatch(normalizeBrandHex(e.colorHex)) ||
                e.sortOrder < 0 ||
                e.sortOrder > 999,
          ) ||
          normalized.toSet().length != normalized.length) {
        throw const OrganizationsFailure(
          OrganizationsFailureCode.validationFailed,
          'Her renk #RRGGBB, sıra 0–999 ve benzersiz olmalıdır.',
        );
      }
      final value = OrganizationBrandColors(
        rowVersion: colorsSection.snapshot!.rowVersion,
        items: List.unmodifiable(items),
      );
      final fingerprint = value.items
          .map((e) => '${normalizeBrandHex(e.colorHex)}:${e.sortOrder}')
          .join(',');
      return _repository.replaceBrandColors(
        organizationId,
        value,
        _key('palette', '$fingerprint|${value.rowVersion}'),
      );
    },
    (v) => v as OrganizationBrandColors,
  );
  Future<bool> saveModules(List<OrganizationModule> items) => _mutate(
    'modules',
    modulesSection,
    OrganizationBrandStatus.savingModules,
    () {
      final value = OrganizationModules(
        rowVersion: modulesSection.snapshot!.rowVersion,
        items: List.unmodifiable(items),
      );
      final fingerprint = value.items
          .map((e) => '${e.code.wireName}:${e.isEnabled}:${e.sortOrder}')
          .join(',');
      return _repository.updateModules(
        organizationId,
        value,
        _key('modules', '$fingerprint|${value.rowVersion}'),
      );
    },
    (v) => v as OrganizationModules,
  );
  Future<bool> _mutate(
    String operation,
    OrganizationBrandSection<Object> target,
    OrganizationBrandStatus pending,
    Future<Object> Function() call,
    Object Function(Object) accept,
  ) async {
    if (_disposed ||
        _status.name.startsWith('saving') ||
        target.snapshot == null ||
        target.saving) {
      return false;
    }
    final g = _generation;
    _status = pending;
    target.saving = true;
    target.error = null;
    _notify();
    try {
      final value = await call();
      if (_disposed || g != _generation) return false;
      final saved = accept(value);
      target.snapshot = saved;
      target.draft = saved;
      target.pendingKey = null;
      target.success = 'Kaydedildi.';
      target.saving = false;
      _status = OrganizationBrandStatus.ready;
      _notify();
      return true;
    } on OrganizationsFailure catch (e) {
      if (_disposed || g != _generation) return false;
      if (e.code == OrganizationsFailureCode.versionConflict) {
        target.saving = false;
        target.error =
            '$operation bölümünde sürüm çakışması oluştu; taslağınız korundu.';
        _status = OrganizationBrandStatus.ready;
        _notify();
      } else {
        target.saving = false;
        target.error = e.message;
        _status = e.isUnauthorized
            ? OrganizationBrandStatus.unauthorized
            : OrganizationBrandStatus.ready;
        _notify();
      }
      return false;
    } catch (_) {
      if (!_disposed && g == _generation) {
        target.saving = false;
        target.error = 'Ayarlar kaydedilemedi.';
        _status = OrganizationBrandStatus.ready;
        _notify();
      }
      return false;
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}
