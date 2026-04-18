/// Non-IO platforms: secure ZIP session is unavailable.
Future<T> withSecureZipExtractedSandbox<T>({
  required String zipPath,
  required dynamic whitelist,
  required Future<T> Function(dynamic extractedRoot) work,
  dynamic copyExtractedTo,
  Future<int?> Function(String pathForQuotaCheck)? freeSpaceForPath,
  int maxCentralDirectoryBytes = 32 * 1024 * 1024,
  int maxTotalEntries = 200000,
}) async {
  throw UnsupportedError('withSecureZipExtractedSandbox requires dart:io');
}
