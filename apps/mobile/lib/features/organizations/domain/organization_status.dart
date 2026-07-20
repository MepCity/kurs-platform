/// Organization lifecycle status.
///
/// Wire values match `ORG_KURUM_YASAM_DONGUSU_API_SOZLESMESI.md` §6.3: `ACTIVE`,
/// `SUSPENDED`, `ARCHIVED`. `ARCHIVED` is terminal.
enum OrganizationStatus {
  active,
  suspended,
  archived;

  /// Parses the wire value used by the ORG API contract.
  ///
  /// Throws [FormatException] for any value outside the closed set; the
  /// contract does not define a fourth status.
  static OrganizationStatus fromWire(String value) {
    return switch (value) {
      'ACTIVE' => OrganizationStatus.active,
      'SUSPENDED' => OrganizationStatus.suspended,
      'ARCHIVED' => OrganizationStatus.archived,
      _ => throw FormatException('Bilinmeyen kurum durumu: $value'),
    };
  }

  /// Serializes back to the wire value used by the ORG API contract.
  String toWire() {
    return switch (this) {
      OrganizationStatus.active => 'ACTIVE',
      OrganizationStatus.suspended => 'SUSPENDED',
      OrganizationStatus.archived => 'ARCHIVED',
    };
  }
}
