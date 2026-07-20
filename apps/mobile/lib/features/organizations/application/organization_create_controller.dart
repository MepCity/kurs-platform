import 'dart:math';

import 'package:flutter/foundation.dart';

import '../domain/organization.dart';
import '../domain/organization_create_request.dart';
import '../domain/organizations_failure.dart';
import '../domain/organizations_repository.dart';

/// Screen-level status for PLAT-02, aligned with the states
/// `EKRAN_ENVANTERI.md` §4 requires for the Kurum Oluştur form: `Y`
/// (submitting), `H` (generic error banner), `Z` (unauthorized) and `E`
/// (submit sync indicator).
enum OrganizationCreateStatus {
  /// Idle/editing — no request in flight. The default state, and the state
  /// returned to after a recoverable failure so the user can correct input
  /// and retry.
  editing,

  /// `Y`/`E` — the create request is in flight.
  submitting,

  /// The create request succeeded; [OrganizationCreateController.created]
  /// holds the new organization.
  success,

  /// `Z` — the actor is not an authenticated/valid platform administrator
  /// (401/403/`ORGANIZATION_CONTEXT_REQUIRED` from §7.4).
  unauthorized,
}

/// Orchestrates PLAT-02 (Kurum Oluştur): field-level validation, the
/// `Idempotency-Key` lifecycle, and the four screen states, against
/// [OrganizationsRepository].
///
/// This controller does not create its own repository; the concrete adapter
/// (mock today, real HTTP client once `ORG-003`/`ORG-004` land) is injected
/// by the composition root — the same pattern `OrganizationListController`
/// (`ORG-006`) uses.
class OrganizationCreateController extends ChangeNotifier {
  OrganizationCreateController({
    required this._repository,
    String Function()? clientMutationIdGenerator,
  }) : _generateClientMutationId =
           clientMutationIdGenerator ?? _defaultClientMutationIdGenerator,
       _clientMutationId =
           (clientMutationIdGenerator ?? _defaultClientMutationIdGenerator)();

  final OrganizationsRepository _repository;
  final String Function() _generateClientMutationId;

  static final Random _secureRandom = Random.secure();

  static String _defaultClientMutationIdGenerator() {
    final List<int> bytes = List<int>.generate(
      16,
      (_) => _secureRandom.nextInt(256),
    );
    final String hex = bytes
        .map((int b) => b.toRadixString(16).padLeft(2, '0'))
        .join();
    return 'cm_$hex';
  }

  bool _disposed = false;

  OrganizationCreateStatus _status = OrganizationCreateStatus.editing;
  String _name = '';
  String _shortName = '';
  String _defaultTimezone = organizationDefaultTimezoneFallback;
  String _clientMutationId;

  /// Fingerprint of the field values as of the last attempt actually sent to
  /// the repository. `null` until the first attempt. Used to decide whether
  /// the next submit reuses [_clientMutationId] (unmodified retry) or mints a
  /// new one (content changed since the last attempt) — see
  /// `SENKRONIZASYON_VE_CAKISMA.md` §2, line 90.
  (String, String?, String)? _lastAttemptFingerprint;

  OrganizationCreateFieldErrors _fieldErrors =
      const OrganizationCreateFieldErrors();
  String? _bannerErrorMessage;
  Organization? _created;

  OrganizationCreateStatus get status => _status;
  String get name => _name;
  String get shortName => _shortName;
  String get defaultTimezone => _defaultTimezone;
  OrganizationCreateFieldErrors get fieldErrors => _fieldErrors;

  /// Set only when [status] is [OrganizationCreateStatus.editing] (a
  /// recoverable, non-field-specific failure) or
  /// [OrganizationCreateStatus.unauthorized].
  String? get bannerErrorMessage => _bannerErrorMessage;

  /// Set only when [status] is [OrganizationCreateStatus.success].
  Organization? get created => _created;

  bool get isSubmitting => _status == OrganizationCreateStatus.submitting;

  void setName(String value) {
    if (_disposed || value == _name) {
      return;
    }
    _name = value;
    _clearFieldError((errors) => errors.copyWith(name: null));
  }

  void setShortName(String value) {
    if (_disposed || value == _shortName) {
      return;
    }
    _shortName = value;
    _clearFieldError((errors) => errors.copyWith(shortName: null));
  }

  void setDefaultTimezone(String value) {
    if (_disposed || value == _defaultTimezone) {
      return;
    }
    _defaultTimezone = value;
    _clearFieldError((errors) => errors.copyWith(defaultTimezone: null));
  }

  void _clearFieldError(
    OrganizationCreateFieldErrors Function(OrganizationCreateFieldErrors) clear,
  ) {
    _bannerErrorMessage = null;
    if (_fieldErrors.hasErrors) {
      _fieldErrors = clear(_fieldErrors);
    }
    notifyListeners();
  }

  /// Validates the current fields and, if valid, submits
  /// `POST /api/v1/organizations`.
  ///
  /// No-ops while a submission is already in flight or after a successful
  /// create (start a new [OrganizationCreateController] for a second
  /// creation instead of reusing this one).
  Future<void> submit() async {
    if (_disposed ||
        _status == OrganizationCreateStatus.submitting ||
        _status == OrganizationCreateStatus.success) {
      return;
    }

    // Validate against the *current* mutation ID first — validation never
    // depends on it, and this order guarantees an invalid attempt (one that
    // never reaches the repository) leaves [_clientMutationId] and
    // [_lastAttemptFingerprint] completely untouched. Regenerating the key
    // before this check would let an invalid intermediate edit burn the key
    // bound to a still-unresolved prior attempt, so a later retry of that
    // same prior content would use a *new* key and risk a duplicate create.
    final OrganizationCreateFieldErrors validation = OrganizationCreateRequest(
      name: _name,
      shortName: _shortName,
      defaultTimezone: _defaultTimezone,
      clientMutationId: _clientMutationId,
    ).validate();
    if (validation.hasErrors) {
      _fieldErrors = validation;
      _bannerErrorMessage = null;
      notifyListeners();
      return;
    }

    final (String, String?, String) fingerprint = (
      _name.trim(),
      _normalizeOptional(_shortName),
      _normalizeOptional(_defaultTimezone) ??
          organizationDefaultTimezoneFallback,
    );
    // A field changed since the last attempt actually sent: reusing the old
    // Idempotency-Key would make the server see "same key, different
    // content" and reject with 409 IDEMPOTENCY_KEY_REUSED instead of
    // evaluating the new input. An unmodified retry (fingerprint unchanged)
    // intentionally keeps the same key.
    if (_lastAttemptFingerprint != null &&
        _lastAttemptFingerprint != fingerprint) {
      _clientMutationId = _generateClientMutationId();
    }

    final request = OrganizationCreateRequest(
      name: _name,
      shortName: _shortName,
      defaultTimezone: _defaultTimezone,
      clientMutationId: _clientMutationId,
    );

    _fieldErrors = const OrganizationCreateFieldErrors();
    _bannerErrorMessage = null;
    _status = OrganizationCreateStatus.submitting;
    notifyListeners();
    _lastAttemptFingerprint = fingerprint;

    try {
      final Organization organization = await _repository.createOrganization(
        request,
      );
      if (_disposed) {
        return;
      }
      _created = organization;
      _status = OrganizationCreateStatus.success;
      notifyListeners();
    } on OrganizationsFailure catch (failure) {
      if (_disposed) {
        return;
      }
      if (failure.isUnauthorized) {
        _status = OrganizationCreateStatus.unauthorized;
        _bannerErrorMessage = failure.message;
        notifyListeners();
        return;
      }
      if (failure.code == OrganizationsFailureCode.validationFailed &&
          (failure.fieldErrors?.hasErrors ?? false)) {
        _status = OrganizationCreateStatus.editing;
        _fieldErrors = failure.fieldErrors!;
        _bannerErrorMessage = null;
        notifyListeners();
        return;
      }
      _status = OrganizationCreateStatus.editing;
      _bannerErrorMessage = failure.message;
      notifyListeners();
    } catch (_) {
      if (_disposed) {
        return;
      }
      _status = OrganizationCreateStatus.editing;
      _bannerErrorMessage = 'Kurum oluşturulurken beklenmeyen bir hata oluştu.';
      notifyListeners();
    }
  }

  /// Resets the form to create another organization after a success.
  ///
  /// Mints a fresh [_clientMutationId] — the previous one is permanently
  /// bound to the organization already created under it (§5.2) — and clears
  /// [created] so the caller's success view is dismissed. No-ops outside
  /// [OrganizationCreateStatus.success].
  void startNewCreation() {
    if (_disposed || _status != OrganizationCreateStatus.success) {
      return;
    }
    _status = OrganizationCreateStatus.editing;
    _name = '';
    _shortName = '';
    _defaultTimezone = organizationDefaultTimezoneFallback;
    _clientMutationId = _generateClientMutationId();
    _lastAttemptFingerprint = null;
    _fieldErrors = const OrganizationCreateFieldErrors();
    _bannerErrorMessage = null;
    _created = null;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}

String? _normalizeOptional(String? raw) {
  final String? trimmed = raw?.trim();
  return (trimmed == null || trimmed.isEmpty) ? null : trimmed;
}
