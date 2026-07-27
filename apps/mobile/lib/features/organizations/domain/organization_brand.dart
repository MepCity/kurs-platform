import 'dart:math' as math;

/// Dosyasız ORG-002 marka verisi ve istemci doğrulaması.
class OrganizationBrand {
  const OrganizationBrand({
    required this.primaryColor,
    required this.secondaryColor,
    required this.rowVersion,
  });

  final String primaryColor;
  final String secondaryColor;
  final int rowVersion;

  OrganizationBrand copyWith({int? rowVersion}) => OrganizationBrand(
    primaryColor: primaryColor,
    secondaryColor: secondaryColor,
    rowVersion: rowVersion ?? this.rowVersion,
  );

  bool sameValues(OrganizationBrand other) =>
      primaryColor == other.primaryColor &&
      secondaryColor == other.secondaryColor;

  @override
  bool operator ==(Object other) =>
      other is OrganizationBrand &&
      rowVersion == other.rowVersion &&
      sameValues(other);

  @override
  int get hashCode => Object.hash(primaryColor, secondaryColor, rowVersion);
}

/// `GET`/`PUT /brand-colors` bağımsız yanıtı; brand içine gömülmez.
class OrganizationBrandColors {
  OrganizationBrandColors({
    required this.rowVersion,
    required List<OrganizationBrandColor> items,
  }) : items = List.unmodifiable(items);
  final int rowVersion;
  final List<OrganizationBrandColor> items;

  OrganizationBrandColors copyWith({int? rowVersion}) =>
      OrganizationBrandColors(
        rowVersion: rowVersion ?? this.rowVersion,
        items: items,
      );

  bool sameValues(OrganizationBrandColors other) =>
      _listEquals(items, other.items);

  @override
  bool operator ==(Object other) =>
      other is OrganizationBrandColors &&
      rowVersion == other.rowVersion &&
      sameValues(other);

  @override
  int get hashCode => Object.hash(rowVersion, Object.hashAll(items));
}

class OrganizationBrandColor {
  const OrganizationBrandColor({
    required this.colorHex,
    required this.sortOrder,
  });
  final String colorHex;
  final int sortOrder;

  @override
  bool operator ==(Object other) =>
      other is OrganizationBrandColor &&
      colorHex == other.colorHex &&
      sortOrder == other.sortOrder;

  @override
  int get hashCode => Object.hash(colorHex, sortOrder);
}

enum OrganizationModuleCode { att, program, content, progress, export, audit }

extension OrganizationModuleCodeLabel on OrganizationModuleCode {
  String get wireName => switch (this) {
    OrganizationModuleCode.att => 'ATT',
    OrganizationModuleCode.program => 'PROGRAM',
    OrganizationModuleCode.content => 'CONTENT',
    OrganizationModuleCode.progress => 'PROGRESS',
    OrganizationModuleCode.export => 'EXPORT',
    OrganizationModuleCode.audit => 'AUDIT',
  };
  String get label => switch (this) {
    OrganizationModuleCode.att => 'Yoklama',
    OrganizationModuleCode.program => 'Program',
    OrganizationModuleCode.content => 'İçerik',
    OrganizationModuleCode.progress => 'İlerleme',
    OrganizationModuleCode.export => 'Dışa Aktarma',
    OrganizationModuleCode.audit => 'İşlem Geçmişi',
  };
}

class OrganizationModule {
  const OrganizationModule({
    required this.code,
    required this.isEnabled,
    required this.sortOrder,
  });
  final OrganizationModuleCode code;
  final bool isEnabled;
  final int sortOrder;
  OrganizationModule copyWith({bool? isEnabled, int? sortOrder}) =>
      OrganizationModule(
        code: code,
        isEnabled: isEnabled ?? this.isEnabled,
        sortOrder: sortOrder ?? this.sortOrder,
      );

  @override
  bool operator ==(Object other) =>
      other is OrganizationModule &&
      code == other.code &&
      isEnabled == other.isEnabled &&
      sortOrder == other.sortOrder;

  @override
  int get hashCode => Object.hash(code, isEnabled, sortOrder);
}

class OrganizationModules {
  OrganizationModules({
    required this.rowVersion,
    required List<OrganizationModule> items,
  }) : items = List.unmodifiable(items);
  final int rowVersion;
  final List<OrganizationModule> items;

  OrganizationModules copyWith({int? rowVersion}) => OrganizationModules(
    rowVersion: rowVersion ?? this.rowVersion,
    items: items,
  );

  bool sameValues(OrganizationModules other) => _listEquals(items, other.items);

  @override
  bool operator ==(Object other) =>
      other is OrganizationModules &&
      rowVersion == other.rowVersion &&
      sameValues(other);

  @override
  int get hashCode => Object.hash(rowVersion, Object.hashAll(items));
}

bool _listEquals<T>(List<T> left, List<T> right) {
  if (identical(left, right)) return true;
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}

/// ORG-002 §2.4.2 ile aynı istemci ön doğrulaması.
String? validateBrandHex(String field, String value) {
  final normalized = value.trim().toUpperCase();
  if (!RegExp(r'^#[0-9A-F]{6}$').hasMatch(normalized)) {
    return '$field için #RRGGBB biçimini kullanın.';
  }
  if (normalized == '#000000' || normalized == '#FFFFFF') {
    return '$field siyah veya beyaz olamaz.';
  }
  final r = int.parse(normalized.substring(1, 3), radix: 16) / 255;
  final g = int.parse(normalized.substring(3, 5), radix: 16) / 255;
  final b = int.parse(normalized.substring(5, 7), radix: 16) / 255;
  double linear(double c) =>
      c <= .03928 ? c / 12.92 : math.pow((c + .055) / 1.055, 2.4).toDouble();
  final luminance = .2126 * linear(r) + .7152 * linear(g) + .0722 * linear(b);
  final graphicalContrast = 1.05 / (luminance + .05);
  if (graphicalContrast < 3) {
    return '$field beyaz arka plan üzerinde yeterli kontrasta sahip değil.';
  }
  return null;
}

String normalizeBrandHex(String value) => value.trim().toUpperCase();
