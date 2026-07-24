import 'dart:math';

import 'package:flutter/foundation.dart';

import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';
import '../domain/organizations_failure.dart';

enum OrganizationBrandStatus { loading, ready, unauthorized, error }

enum OrganizationBrandOperation { brand, palette, modules }

@immutable
class OrganizationBrandAttempt {
  const OrganizationBrandAttempt({
    required this.operation,
    required this.idempotencyKey,
    required this.fingerprint,
  });

  final OrganizationBrandOperation operation;
  final String idempotencyKey;
  final String fingerprint;
}

/// Bir bölümün sunucu snapshot'ı, kullanıcı taslağı ve istek yaşam döngüsü.
class OrganizationBrandSection<T extends Object> {
  OrganizationBrandSection({
    required this._sameValues,
    required this._withRowVersion,
  });

  final bool Function(T left, T right) _sameValues;
  final T Function(T value, int rowVersion) _withRowVersion;

  T? snapshot;
  T? draft;
  bool saving = false;
  String? error;
  String? success;
  OrganizationBrandAttempt? activeAttempt;

  bool get dirty {
    final currentSnapshot = snapshot;
    final currentDraft = draft;
    return currentSnapshot != null &&
        currentDraft != null &&
        !_sameValues(currentSnapshot, currentDraft);
  }

  void mergeSnapshot(T value, int rowVersion) {
    final preserveDraft = dirty;
    snapshot = _withRowVersion(value, rowVersion);
    draft = preserveDraft && draft != null
        ? _withRowVersion(draft as T, rowVersion)
        : _withRowVersion(value, rowVersion);
  }

  void reconcileRowVersion(int rowVersion) {
    if (snapshot case final value?) {
      snapshot = _withRowVersion(value, rowVersion);
    }
    if (draft case final value?) {
      draft = _withRowVersion(value, rowVersion);
    }
  }

  void clearSensitiveState() {
    snapshot = null;
    draft = null;
    saving = false;
    error = null;
    success = null;
    activeAttempt = null;
  }
}

/// ORG-008 ekranındaki bağımsız marka, palet ve modül yüzeylerini yönetir.
class OrganizationBrandController extends ChangeNotifier {
  OrganizationBrandController({
    required this.organizationId,
    required this.repository,
    this.loadBrandSettings = true,
    this.loadModuleSettings = true,
  });

  final String organizationId;
  final OrganizationBrandRepository repository;
  final bool loadBrandSettings;
  final bool loadModuleSettings;

  bool _disposed = false;
  int _generation = 0;
  OrganizationBrandStatus _status = OrganizationBrandStatus.loading;
  String? _message;
  final Map<String, String> _keys = <String, String>{};

  final brandSection = OrganizationBrandSection<OrganizationBrand>(
    sameValues: (left, right) => left.sameValues(right),
    withRowVersion: (value, rowVersion) =>
        value.copyWith(rowVersion: rowVersion),
  );
  final colorsSection = OrganizationBrandSection<OrganizationBrandColors>(
    sameValues: (left, right) => left.sameValues(right),
    withRowVersion: (value, rowVersion) =>
        value.copyWith(rowVersion: rowVersion),
  );
  final modulesSection = OrganizationBrandSection<OrganizationModules>(
    sameValues: (left, right) => left.sameValues(right),
    withRowVersion: (value, rowVersion) =>
        value.copyWith(rowVersion: rowVersion),
  );

  OrganizationBrandStatus get status => _status;
  OrganizationBrand? get brand => brandSection.snapshot;
  OrganizationBrandColors? get colors => colorsSection.snapshot;
  OrganizationModules? get modules => modulesSection.snapshot;
  String? get message => _message;
  bool get anySaving =>
      brandSection.saving || colorsSection.saving || modulesSection.saving;

  static final Random _random = Random.secure();

  String _key(
    OrganizationBrandOperation operation,
    String fingerprint,
  ) => _keys.putIfAbsent(
    '${operation.name}:$fingerprint',
    () =>
        'cm_${List<int>.generate(16, (_) => _random.nextInt(256)).map((value) => value.toRadixString(16).padLeft(2, '0')).join()}',
  );

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  Future<void> load({String? conflictMessage}) async {
    final generation = ++_generation;
    _status = OrganizationBrandStatus.loading;
    _message = conflictMessage;
    _notify();
    try {
      OrganizationBrand? brand;
      OrganizationBrandColors? colors;
      OrganizationModules? modules;
      if (loadBrandSettings) {
        brand = await repository.getBrand(organizationId);
        if (_disposed || generation != _generation) return;
        colors = await repository.getBrandColors(organizationId);
        if (_disposed || generation != _generation) return;
      }
      if (loadModuleSettings) {
        modules = await repository.getModules(organizationId);
      }
      if (_disposed || generation != _generation) return;

      final versions = <int>{
        if (brand != null) brand.rowVersion,
        if (colors != null) colors.rowVersion,
        if (modules != null) modules.rowVersion,
      };
      if (versions.length > 1) {
        _message = 'Ayarların sürümü tutarsız; lütfen yeniden deneyin.';
        _status = OrganizationBrandStatus.error;
        _notify();
        return;
      }
      final rowVersion = versions.isEmpty ? 0 : versions.first;
      if (brand != null) brandSection.mergeSnapshot(brand, rowVersion);
      if (colors != null) colorsSection.mergeSnapshot(colors, rowVersion);
      if (modules != null) modulesSection.mergeSnapshot(modules, rowVersion);
      _status = OrganizationBrandStatus.ready;
    } on OrganizationsFailure catch (failure) {
      if (_disposed || generation != _generation) return;
      _message = failure.message;
      if (failure.isUnauthorized) {
        _clearSensitiveState();
        _status = OrganizationBrandStatus.unauthorized;
      } else {
        _status = OrganizationBrandStatus.error;
      }
    } catch (_) {
      if (_disposed || generation != _generation) return;
      _message = 'Kurum ayarları yüklenemedi.';
      _status = OrganizationBrandStatus.error;
    }
    _notify();
  }

  void setBrandDraft(String primary, String secondary) {
    final snapshot = brandSection.snapshot;
    if (snapshot == null) return;
    brandSection
      ..draft = OrganizationBrand(
        primaryColor: primary,
        secondaryColor: secondary,
        rowVersion: snapshot.rowVersion,
      )
      ..error = null
      ..success = null;
    _notify();
  }

  void setColorsDraft(List<OrganizationBrandColor> items) {
    final snapshot = colorsSection.snapshot;
    if (snapshot == null) return;
    colorsSection
      ..draft = OrganizationBrandColors(
        rowVersion: snapshot.rowVersion,
        items: items,
      )
      ..error = null
      ..success = null;
    _notify();
  }

  void setModulesDraft(List<OrganizationModule> items) {
    final snapshot = modulesSection.snapshot;
    if (snapshot == null) return;
    modulesSection
      ..draft = OrganizationModules(
        rowVersion: snapshot.rowVersion,
        items: items,
      )
      ..error = null
      ..success = null;
    _notify();
  }

  Future<bool> saveBrand(String primary, String secondary) async {
    setBrandDraft(primary, secondary);
    final snapshot = brandSection.snapshot;
    if (snapshot == null) return false;
    final error =
        validateBrandHex('Ana renk', primary) ??
        validateBrandHex('Yardımcı renk', secondary);
    if (error != null) {
      brandSection
        ..error = error
        ..success = null;
      _notify();
      return false;
    }
    final value = OrganizationBrand(
      primaryColor: normalizeBrandHex(primary),
      secondaryColor: normalizeBrandHex(secondary),
      rowVersion: snapshot.rowVersion,
    );
    final fingerprint =
        '${value.primaryColor}|${value.secondaryColor}|${value.rowVersion}';
    return _mutate(
      operation: OrganizationBrandOperation.brand,
      target: brandSection,
      fingerprint: fingerprint,
      call: (key) => repository.updateBrand(organizationId, value, key),
    );
  }

  Future<bool> saveColors(List<OrganizationBrandColor> items) async {
    setColorsDraft(items);
    final snapshot = colorsSection.snapshot;
    if (snapshot == null) return false;
    final normalized = items
        .map((item) => normalizeBrandHex(item.colorHex))
        .toList();
    if (items.length > 20 ||
        items.any(
          (item) =>
              !RegExp(
                r'^#[0-9A-F]{6}$',
              ).hasMatch(normalizeBrandHex(item.colorHex)) ||
              item.sortOrder < 0 ||
              item.sortOrder > 999,
        ) ||
        normalized.toSet().length != normalized.length) {
      colorsSection
        ..error = 'Her renk #RRGGBB, sıra 0–999 ve benzersiz olmalıdır.'
        ..success = null;
      _notify();
      return false;
    }
    final canonicalItems =
        items
            .map(
              (item) => OrganizationBrandColor(
                colorHex: normalizeBrandHex(item.colorHex),
                sortOrder: item.sortOrder,
              ),
            )
            .toList()
          ..sort(
            (left, right) => left.sortOrder != right.sortOrder
                ? left.sortOrder.compareTo(right.sortOrder)
                : left.colorHex.compareTo(right.colorHex),
          );
    final value = OrganizationBrandColors(
      rowVersion: snapshot.rowVersion,
      items: canonicalItems,
    );
    final fingerprint =
        '${value.rowVersion}:${value.items.map((item) => '${item.colorHex}:${item.sortOrder}').join(',')}';
    return _mutate(
      operation: OrganizationBrandOperation.palette,
      target: colorsSection,
      fingerprint: fingerprint,
      call: (key) => repository.replaceBrandColors(organizationId, value, key),
    );
  }

  Future<bool> saveModules(List<OrganizationModule> items) async {
    setModulesDraft(items);
    final snapshot = modulesSection.snapshot;
    if (snapshot == null) return false;
    if (items.length != OrganizationModuleCode.values.length ||
        items.map((item) => item.code).toSet().length !=
            OrganizationModuleCode.values.length ||
        items.any((item) => item.sortOrder < 0 || item.sortOrder > 999)) {
      modulesSection
        ..error = 'Modül ayarları geçersiz.'
        ..success = null;
      _notify();
      return false;
    }
    final value = OrganizationModules(
      rowVersion: snapshot.rowVersion,
      items: List<OrganizationModule>.unmodifiable(items),
    );
    final fingerprint =
        '${value.rowVersion}:${value.items.map((item) => '${item.code.wireName}:${item.isEnabled}:${item.sortOrder}').join(',')}';
    return _mutate(
      operation: OrganizationBrandOperation.modules,
      target: modulesSection,
      fingerprint: fingerprint,
      call: (key) => repository.updateModules(organizationId, value, key),
    );
  }

  Future<bool> _mutate<T extends Object>({
    required OrganizationBrandOperation operation,
    required OrganizationBrandSection<T> target,
    required String fingerprint,
    required Future<T> Function(String key) call,
  }) async {
    if (_disposed || anySaving || target.snapshot == null) return false;
    final generation = _generation;
    final key = _key(operation, fingerprint);
    target
      ..saving = true
      ..error = null
      ..success = null
      ..activeAttempt = OrganizationBrandAttempt(
        operation: operation,
        idempotencyKey: key,
        fingerprint: fingerprint,
      );
    _notify();
    try {
      final saved = await call(key);
      if (_disposed || generation != _generation) return false;
      final rowVersion = _rowVersionOf(saved);
      target
        ..snapshot = saved
        ..draft = saved
        ..saving = false
        ..activeAttempt = null
        ..error = null
        ..success = 'Kaydedildi.';
      _reconcileAllRowVersions(rowVersion);
      _status = OrganizationBrandStatus.ready;
      _notify();
      return true;
    } on OrganizationsFailure catch (failure) {
      if (_disposed || generation != _generation) return false;
      target
        ..saving = false
        ..activeAttempt = null
        ..success = null;
      if (failure.code == OrganizationsFailureCode.versionConflict) {
        final operationLabel = switch (operation) {
          OrganizationBrandOperation.brand => 'Marka',
          OrganizationBrandOperation.palette => 'Palet',
          OrganizationBrandOperation.modules => 'Modül',
        };
        await load(
          conflictMessage:
              '$operationLabel ayarlarında sürüm çakışması oluştu; '
              'taslağınız korundu.',
        );
        if (!_disposed && _status == OrganizationBrandStatus.ready) {
          target.error =
              '$operationLabel ayarları yenilendi. Taslağınızı kontrol edip '
              'yeniden kaydedebilirsiniz.';
          _notify();
        }
      } else if (failure.isUnauthorized) {
        _message = failure.message;
        _clearSensitiveState();
        _status = OrganizationBrandStatus.unauthorized;
        _notify();
      } else {
        target.error = failure.message;
        _status = OrganizationBrandStatus.ready;
        _notify();
      }
      return false;
    } catch (_) {
      if (!_disposed && generation == _generation) {
        target
          ..saving = false
          ..activeAttempt = null
          ..success = null
          ..error = 'Ayarlar kaydedilemedi.';
        _status = OrganizationBrandStatus.ready;
        _notify();
      }
      return false;
    }
  }

  int _rowVersionOf(Object value) => switch (value) {
    OrganizationBrand value => value.rowVersion,
    OrganizationBrandColors value => value.rowVersion,
    OrganizationModules value => value.rowVersion,
    _ => throw StateError('Desteklenmeyen marka ayarı sonucu.'),
  };

  void _reconcileAllRowVersions(int rowVersion) {
    brandSection.reconcileRowVersion(rowVersion);
    colorsSection.reconcileRowVersion(rowVersion);
    modulesSection.reconcileRowVersion(rowVersion);
  }

  void _clearSensitiveState() {
    brandSection.clearSensitiveState();
    colorsSection.clearSensitiveState();
    modulesSection.clearSensitiveState();
  }

  @override
  void dispose() {
    _disposed = true;
    _generation++;
    super.dispose();
  }
}
