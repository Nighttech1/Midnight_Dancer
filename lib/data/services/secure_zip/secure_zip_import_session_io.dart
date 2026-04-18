import 'dart:io';

import 'package:path_provider/path_provider.dart';

import 'device_free_space_io.dart';
import 'secure_zip_extract_io.dart';
import 'secure_zip_insufficient_space_exception.dart';
import 'secure_zip_metadata_io.dart';
import 'secure_zip_preflight_io.dart';
import 'secure_zip_sandbox_io.dart';
import 'secure_zip_whitelist.dart';

/// Runs STEP 1–3 (+ STEP 4 metadata validation): preflight, free-space check, sandbox extract.
///
/// The sandbox directory is **always** deleted in [finally] after [work] completes
/// (success or failure). Do not use the sandbox path outside this call.
///
/// If [copyExtractedTo] is set, every extracted file is copied there (STEP 4 “move”
/// into app space) and [work] receives that directory; otherwise [work] receives the
/// live sandbox (still deleted right after).
Future<T> withSecureZipExtractedSandbox<T>({
  required String zipPath,
  required SecureZipWhitelist whitelist,
  required Future<T> Function(Directory extractedRoot) work,
  Directory? copyExtractedTo,
  Future<int?> Function(String pathForQuotaCheck)? freeSpaceForPath,
  int maxCentralDirectoryBytes = 32 * 1024 * 1024,
  int maxTotalEntries = 200000,
}) async {
  final preflight = await preflightSecureZipWhitelist(
    zipPath: zipPath,
    whitelist: whitelist,
    maxCentralDirectoryBytes: maxCentralDirectoryBytes,
    maxTotalEntries: maxTotalEntries,
  );

  final freeResolver = freeSpaceForPath ?? getFreeDiskSpaceBytesForPath;
  final tempRoot = await getTemporaryDirectory();
  final free = await freeResolver(tempRoot.path);
  if (free != null && free < preflight.requiredBytesWithTenPercentBuffer) {
    throw SecureZipInsufficientSpaceException(
      requiredBytes: preflight.requiredBytesWithTenPercentBuffer,
      freeBytes: free,
    );
  }

  final sandbox = await Directory.systemTemp.createTemp('midnight_secure_zip_');
  try {
    await extractSecureZipWhitelistToSandbox(
      zipPath: zipPath,
      sandboxDir: sandbox,
      whitelist: whitelist,
      maxCentralDirectoryBytes: maxCentralDirectoryBytes,
      maxTotalEntries: maxTotalEntries,
    );
    await validateExtractedMetadataJson(sandbox);
    if (copyExtractedTo != null) {
      await copyExtractedTo.create(recursive: true);
      await copyDirectoryContentsTo(sandbox, copyExtractedTo);
      return await work(copyExtractedTo);
    }
    return await work(sandbox);
  } finally {
    await wipeDirectorySilently(sandbox);
  }
}
