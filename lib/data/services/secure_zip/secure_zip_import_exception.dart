/// Failure during secure ZIP import (preflight, disk space, extraction, validation).
class SecureZipImportException implements Exception {
  SecureZipImportException(this.code, [this.details]);

  /// Machine-readable code, e.g. `invalid_zip`, `zip_slip`, `zip_bomb`.
  final String code;
  final String? details;

  @override
  String toString() =>
      details == null ? 'SecureZipImportException($code)' : 'SecureZipImportException($code: $details)';
}
