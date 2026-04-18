import 'dart:io';
import 'dart:math';
import 'dart:typed_data';

import 'package:archive/archive_io.dart';
import 'package:path/path.dart' as p;

import 'secure_zip_import_exception.dart';
import 'secure_zip_whitelist.dart';
import 'zip_central_directory_headers.dart';

/// Rejects `..`, absolute paths, and Windows drive paths inside ZIP entry names.
bool entryPathFailsZipSlipChecks(String entryPath) {
  final norm = entryPath.replaceAll('\\', '/').trim();
  if (norm.isEmpty) {
    return true;
  }
  if (p.isAbsolute(norm)) {
    return true;
  }
  if (norm.startsWith('/')) {
    return true;
  }
  final lower = norm.toLowerCase();
  if (lower.startsWith('//') || lower.contains(':')) {
    return true;
  }
  final segments = norm.split('/')..removeWhere((s) => s.isEmpty);
  return segments.any((s) => s == '..');
}

bool _isInsideSandbox(Directory sandbox, String relativeZipPath) {
  final base = p.normalize(sandbox.absolute.path);
  final joined = p.normalize(p.join(base, relativeZipPath));
  if (joined == base) {
    return false;
  }
  final sep = p.separator;
  final prefix = base.endsWith(sep) ? base : base + sep;
  return joined.startsWith(prefix);
}

/// Parses local file header; returns compressed payload start offset and compression method.
Future<({int dataStart, int compressionMethod})> _readLocalHeaderMeta(
  RandomAccessFile zipRaf,
  ZipFileHeader header,
) async {
  final localOff = header.localHeaderOffset;
  if (localOff == null) {
    throw SecureZipImportException('missing_local_offset');
  }
  await zipRaf.setPosition(localOff);
  final head = await zipRaf.read(30);
  if (head.length < 30) {
    throw SecureZipImportException('truncated_local_header');
  }
  final view = ByteData.sublistView(head);
  final sig = view.getUint32(0, Endian.little);
  if (sig != 0x04034b50) {
    throw SecureZipImportException('bad_local_signature');
  }
  final compressionMethod = view.getUint16(8, Endian.little);
  if (compressionMethod != header.compressionMethod) {
    throw SecureZipImportException('cd_local_compression_mismatch', header.filename);
  }
  final flags = view.getUint16(6, Endian.little);
  if ((flags & 0x1) != 0) {
    throw SecureZipImportException('encrypted_entry');
  }
  if ((flags & 0x8) != 0) {
    throw SecureZipImportException('data_descriptor_not_supported');
  }
  final fnLen = view.getUint16(26, Endian.little);
  final exLen = view.getUint16(28, Endian.little);
  final restLen = fnLen + exLen;
  final rest = await zipRaf.read(restLen);
  if (rest.length < restLen) {
    throw SecureZipImportException('truncated_local_header');
  }
  return (
    dataStart: localOff + 30 + restLen,
    compressionMethod: compressionMethod,
  );
}

Future<void> _writeStoredCapped({
  required RandomAccessFile zipRaf,
  required int dataStart,
  required int compressedSize,
  required int maxUncompressedBytes,
  required RandomAccessFile outRaf,
}) async {
  var read = 0;
  var written = 0;
  const chunk = 256 * 1024;
  while (read < compressedSize) {
    final n = min(chunk, compressedSize - read);
    await zipRaf.setPosition(dataStart + read);
    final buf = await zipRaf.read(n);
    if (buf.length != n) {
      throw SecureZipImportException('unexpected_eof', 'stored');
    }
    read += n;
    written += buf.length;
    if (written > maxUncompressedBytes) {
      throw SecureZipImportException('zip_bomb');
    }
    await outRaf.writeFrom(buf);
  }
}

Future<void> _writeDeflatedCapped({
  required RandomAccessFile zipRaf,
  required int dataStart,
  required int compressedSize,
  required int maxUncompressedBytes,
  required RandomAccessFile outRaf,
}) async {
  final inflater = RawZLibFilter.inflateFilter(raw: true);
  var read = 0;
  var written = 0;

  Future<void> pullOutput({required bool flush, required bool end}) async {
    while (true) {
      final chunk = inflater.processed(flush: flush, end: end);
      if (chunk == null || chunk.isEmpty) {
        break;
      }
      written += chunk.length;
      if (written > maxUncompressedBytes) {
        throw SecureZipImportException('zip_bomb');
      }
      await outRaf.writeFrom(chunk);
    }
  }

  const block = 64 * 1024;
  while (read < compressedSize) {
    final take = min(block, compressedSize - read);
    await zipRaf.setPosition(dataStart + read);
    final buf = await zipRaf.read(take);
    if (buf.length != take) {
      throw SecureZipImportException('unexpected_eof', 'deflate');
    }
    read += take;
    inflater.process(buf, 0, buf.length);
    await pullOutput(flush: false, end: false);
  }
  inflater.process(const <int>[], 0, 0);
  await pullOutput(flush: true, end: true);
}

/// STEP 3 — extract only whitelisted entries into [sandboxDir] with Zip Slip / bomb guards.
Future<void> extractSecureZipWhitelistToSandbox({
  required String zipPath,
  required Directory sandboxDir,
  required SecureZipWhitelist whitelist,
  int maxCentralDirectoryBytes = 32 * 1024 * 1024,
  int maxTotalEntries = 200000,
}) async {
  InputFileStream? stream;
  final headers = <ZipFileHeader>[];
  try {
    stream = openZipInputStream(zipPath);
    headers.addAll(
      readZipCentralDirectoryHeadersOnly(
        stream,
        maxCentralDirectoryBytes: maxCentralDirectoryBytes,
      ),
    );
  } on ArchiveException catch (e) {
    throw SecureZipImportException('invalid_zip', e.message);
  } finally {
    await stream?.close();
  }

  if (headers.length > maxTotalEntries) {
    throw SecureZipImportException('too_many_entries', '${headers.length}');
  }

  await sandboxDir.create(recursive: true);
  final zipRaf = await File(zipPath).open(mode: FileMode.read);
  try {
    for (final h in headers) {
      final name = h.filename.replaceAll('\\', '/');
      if (!whitelist.matchesEntryPath(name)) {
        continue;
      }
      if (name.endsWith('/')) {
        continue;
      }
      if (entryPathFailsZipSlipChecks(name)) {
        throw SecureZipImportException('zip_slip', name);
      }
      if (!_isInsideSandbox(sandboxDir, name)) {
        throw SecureZipImportException('zip_slip', name);
      }

      final uncomp = h.uncompressedSize;
      final comp = h.compressedSize;
      if (uncomp == null || comp == null || uncomp < 0 || comp < 0) {
        throw SecureZipImportException('bad_header_size', name);
      }

      final localMeta = await _readLocalHeaderMeta(zipRaf, h);
      final method = localMeta.compressionMethod;
      final dataStart = localMeta.dataStart;
      final outFile = File(p.join(sandboxDir.path, name));
      await outFile.parent.create(recursive: true);
      final outRaf = await outFile.open(mode: FileMode.write);
      try {
        if (method == 0) {
          if (comp != uncomp) {
            throw SecureZipImportException('store_size_mismatch', name);
          }
          await _writeStoredCapped(
            zipRaf: zipRaf,
            dataStart: dataStart,
            compressedSize: comp,
            maxUncompressedBytes: uncomp,
            outRaf: outRaf,
          );
        } else if (method == 8) {
          await _writeDeflatedCapped(
            zipRaf: zipRaf,
            dataStart: dataStart,
            compressedSize: comp,
            maxUncompressedBytes: uncomp,
            outRaf: outRaf,
          );
        } else {
          throw SecureZipImportException('unsupported_compression', '$method');
        }
      } finally {
        await outRaf.close();
      }
      final outLen = await outFile.length();
      if (outLen != uncomp) {
        throw SecureZipImportException('size_mismatch_after_extract', name);
      }
    }
  } finally {
    await zipRaf.close();
  }
}
