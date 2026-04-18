import 'package:archive/archive_io.dart';

import 'secure_zip_import_exception.dart';
import 'secure_zip_whitelist.dart';
import 'zip_central_directory_headers.dart';

/// Result of STEP 1: sizes from central directory only (no extraction).
class SecureZipPreflightResult {
  SecureZipPreflightResult({
    required this.whitelistUncompressedSumBytes,
    required this.requiredBytesWithTenPercentBuffer,
    required this.whitelistedEntryCount,
    required this.totalZipEntries,
  });

  /// Sum of declared uncompressed sizes for whitelisted members only.
  final int whitelistUncompressedSumBytes;

  /// STEP 1 rule: sum + 10% safety margin (rounded up).
  final int requiredBytesWithTenPercentBuffer;

  final int whitelistedEntryCount;
  final int totalZipEntries;
}

/// STEP 1 — reads only central directory headers; never decompresses payload.
Future<SecureZipPreflightResult> preflightSecureZipWhitelist({
  required String zipPath,
  required SecureZipWhitelist whitelist,
  int maxCentralDirectoryBytes = 32 * 1024 * 1024,
  int maxTotalEntries = 200000,
}) async {
  InputFileStream? stream;
  try {
    stream = openZipInputStream(zipPath);
    final headers = readZipCentralDirectoryHeadersOnly(
      stream,
      maxCentralDirectoryBytes: maxCentralDirectoryBytes,
    );
    if (headers.length > maxTotalEntries) {
      throw SecureZipImportException('too_many_entries', '${headers.length}');
    }

    var sum = 0;
    var count = 0;
    for (final h in headers) {
      final name = h.filename.replaceAll('\\', '/');
      if (!whitelist.matchesEntryPath(name)) {
        continue;
      }
      final sz = h.uncompressedSize;
      if (sz == null || sz < 0) {
        throw SecureZipImportException('bad_header_size', name);
      }
      sum += sz;
      count++;
    }

    // Integer ceil(sum * 1.1) — avoids double rounding (e.g. 110*1.1 → 122).
    final withBuffer = (sum * 11 + 9) ~/ 10;

    return SecureZipPreflightResult(
      whitelistUncompressedSumBytes: sum,
      requiredBytesWithTenPercentBuffer: withBuffer < sum ? sum : withBuffer,
      whitelistedEntryCount: count,
      totalZipEntries: headers.length,
    );
  } on ArchiveException catch (e) {
    throw SecureZipImportException('invalid_zip', e.message);
  } finally {
    await stream?.close();
  }
}
