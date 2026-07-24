import 'dart:math';
import 'package:flutter/foundation.dart';
import '../domain/organization_brand.dart';
import '../domain/organization_brand_repository.dart';
import '../domain/organizations_failure.dart';

enum OrganizationBrandStatus { loading, ready, saving, unauthorized, error }

class OrganizationBrandController extends ChangeNotifier {
  OrganizationBrandController({
    required this.organizationId,
    required this._repository,
  });
  final String organizationId;
  final OrganizationBrandRepository _repository;
  OrganizationBrandStatus _status = OrganizationBrandStatus.loading;
  OrganizationBrand? _brand;
  OrganizationModules? _modules;
  String? _message;
  OrganizationBrandStatus get status => _status;
  OrganizationBrand? get brand => _brand;
  OrganizationModules? get modules => _modules;
  String? get message => _message;
  static final Random _random = Random.secure();
  String _mutationId() =>
      'cm_${List<int>.generate(16, (_) => _random.nextInt(256)).map((v) => v.toRadixString(16).padLeft(2, '0')).join()}';
  Future<void> load() async {
    _status = OrganizationBrandStatus.loading;
    _message = null;
    notifyListeners();
    try {
      _brand = await _repository.getBrand(organizationId);
      _modules = await _repository.getModules(organizationId);
      _status = OrganizationBrandStatus.ready;
    } on OrganizationsFailure catch (e) {
      _message = e.message;
      _status = e.isUnauthorized
          ? OrganizationBrandStatus.unauthorized
          : OrganizationBrandStatus.error;
    } catch (_) {
      _message = 'Marka ayarları yüklenemedi.';
      _status = OrganizationBrandStatus.error;
    }
    notifyListeners();
  }

  Future<bool> save({
    required String primary,
    required String secondary,
    required List<OrganizationBrandColor> colors,
    required List<OrganizationModule> modules,
  }) async {
    final primaryError = validateBrandHex('Ana renk', primary);
    final secondaryError = validateBrandHex('Yardımcı renk', secondary);
    if (primaryError != null || secondaryError != null) {
      _message = primaryError ?? secondaryError;
      notifyListeners();
      return false;
    }
    if (_brand == null || _modules == null) return false;
    _status = OrganizationBrandStatus.saving;
    _message = null;
    notifyListeners();
    try {
      final updatedBrand = await _repository.updateBrand(
        organizationId,
        OrganizationBrand(
          primaryColor: primary,
          secondaryColor: secondary,
          rowVersion: _brand!.rowVersion,
          colors: colors,
        ),
        _mutationId(),
      );
      final updatedModules = await _repository.updateModules(
        organizationId,
        // `organizations.rowVersion` iki uç için ortak optimistic-concurrency
        // kaynağıdır. Marka yazımı başarılıysa modül yazımı güncel sürümü taşır.
        OrganizationModules(
          rowVersion: updatedBrand.rowVersion,
          items: modules,
        ),
        _mutationId(),
      );
      _brand = updatedBrand;
      _modules = updatedModules;
      _status = OrganizationBrandStatus.ready;
      notifyListeners();
      return true;
    } on OrganizationsFailure catch (e) {
      _message = e.code == OrganizationsFailureCode.versionConflict
          ? '${e.message} Güncel değerler yeniden yükleniyor.'
          : e.message;
      _status = e.isUnauthorized
          ? OrganizationBrandStatus.unauthorized
          : OrganizationBrandStatus.error;
      notifyListeners();
      if (e.code == OrganizationsFailureCode.versionConflict) await load();
      return false;
    } catch (_) {
      _message = 'Marka ayarları kaydedilemedi.';
      _status = OrganizationBrandStatus.error;
      notifyListeners();
      return false;
    }
  }
}
