/// STEP 2 — not enough free disk space for import (preflight + 10% buffer).
class SecureZipInsufficientSpaceException implements Exception {
  SecureZipInsufficientSpaceException({
    required this.requiredBytes,
    required this.freeBytes,
  });

  final int requiredBytes;
  final int freeBytes;

  @override
  String toString() =>
      'SecureZipInsufficientSpaceException(required: $requiredBytes, free: $freeBytes)';
}
