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
  OrganizationBrand? _brand;
  OrganizationBrandColors? _colors;
  OrganizationModules? _modules;
  String? _message;
  final Map<String, String> _keys = <String, String>{};
  OrganizationBrandStatus get status => _status;
  OrganizationBrand? get brand => _brand;
  OrganizationBrandColors? get colors => _colors;
  OrganizationModules? get modules => _modules;
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
        await load(
          conflictMessage: 'Ayarlar yenilendi; güncel sürüm tekrar yükleniyor.',
        );
        return;
      }
      _brand = brand;
      _colors = colors;
      _modules = modules;
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

  Future<bool> saveBrand(String primary, String secondary) => _mutate(
    'brand',
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
        rowVersion: _brand!.rowVersion,
      );
      return _repository.updateBrand(
        organizationId,
        value,
        _key('brand', '$primary|$secondary|${value.rowVersion}'),
      );
    },
    (v) => _brand = v as OrganizationBrand,
  );
  Future<bool> saveColors(List<OrganizationBrandColor> items) => _mutate(
    'palette',
    OrganizationBrandStatus.savingColors,
    () {
      final value = OrganizationBrandColors(
        rowVersion: _colors!.rowVersion,
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
    (v) => _colors = v as OrganizationBrandColors,
  );
  Future<bool> saveModules(List<OrganizationModule> items) => _mutate(
    'modules',
    OrganizationBrandStatus.savingModules,
    () {
      final value = OrganizationModules(
        rowVersion: _modules!.rowVersion,
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
    (v) => _modules = v as OrganizationModules,
  );
  Future<bool> _mutate(
    String section,
    OrganizationBrandStatus pending,
    Future<Object> Function() call,
    void Function(Object) accept,
  ) async {
    if (_disposed ||
        _status.name.startsWith('saving') ||
        _brand == null ||
        _colors == null ||
        _modules == null) {
      return false;
    }
    final g = _generation;
    _status = pending;
    _message = null;
    _notify();
    try {
      final value = await call();
      if (_disposed || g != _generation) return false;
      accept(value);
      _status = OrganizationBrandStatus.ready;
      _notify();
      return true;
    } on OrganizationsFailure catch (e) {
      if (_disposed || g != _generation) return false;
      if (e.code == OrganizationsFailureCode.versionConflict) {
        await load(
          conflictMessage:
              '$section bölümünde sürüm çakışması oluştu; güncel değerler yüklendi.',
        );
      } else {
        _message = e.message;
        _status = e.isUnauthorized
            ? OrganizationBrandStatus.unauthorized
            : OrganizationBrandStatus.error;
        _notify();
      }
      return false;
    } catch (_) {
      if (!_disposed && g == _generation) {
        _message = 'Ayarlar kaydedilemedi.';
        _status = OrganizationBrandStatus.error;
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
