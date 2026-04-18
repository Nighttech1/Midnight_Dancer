import 'dart:io';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/services/full_backup_parse_extracted_io.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';
import 'package:midnight_dancer/data/services/secure_zip/secure_zip_extract_io.dart';
import 'package:midnight_dancer/data/services/secure_zip/secure_zip_import_exception.dart';
import 'package:midnight_dancer/data/services/secure_zip/secure_zip_metadata_io.dart';
import 'package:midnight_dancer/data/services/secure_zip/secure_zip_preflight_io.dart';
import 'package:midnight_dancer/data/services/secure_zip/secure_zip_whitelist.dart';

void main() {
  test('preflight sums only whitelisted uncompressed sizes + 10%', () async {
    final dir = await Directory.systemTemp.createTemp('sz_preflight_');
    final zipFile = File('${dir.path}/t.zip');
    try {
      final a = Archive();
      a.addFile(ArchiveFile('metadata.json', 10, Uint8List(10)));
      a.addFile(ArchiveFile('videos/a.mp4', 100, Uint8List(100)));
      a.addFile(ArchiveFile('evil.exe', 9999, Uint8List(100))); // ignored in sum
      final z = ZipEncoder().encode(a);
      await zipFile.writeAsBytes(z!, flush: true);

      final r = await preflightSecureZipWhitelist(
        zipPath: zipFile.path,
        whitelist: SecureZipWhitelist.midnightDancerFullBackup,
      );
      expect(r.whitelistUncompressedSumBytes, 110);
      expect(r.requiredBytesWithTenPercentBuffer, 121);
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('extract rejects Zip Slip path', () async {
    final dir = await Directory.systemTemp.createTemp('sz_slip_');
    final zipFile = File('${dir.path}/t.zip');
    final sandbox = Directory('${dir.path}/sandbox');
    try {
      final a = Archive();
      a.addFile(ArchiveFile('ok/metadata.json', 2, Uint8List.fromList([1, 2])));
      a.addFile(ArchiveFile('../evil.jpg', 3, Uint8List.fromList([1, 2, 3])));
      final z = ZipEncoder().encode(a);
      await zipFile.writeAsBytes(z!, flush: true);

      await expectLater(
        extractSecureZipWhitelistToSandbox(
          zipPath: zipFile.path,
          sandboxDir: sandbox,
          whitelist: SecureZipWhitelist.defaultUserSpec,
        ),
        throwsA(isA<SecureZipImportException>().having((e) => e.code, 'code', 'zip_slip')),
      );
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('extract + metadata validate + parse extracted matches parseZip', () async {
    final dir = await Directory.systemTemp.createTemp('sz_full_');
    final zipFile = File('${dir.path}/full.zip');
    final sandbox = Directory('${dir.path}/sandbox');
    try {
      final appData = AppData();
      final zipBytes = FullBackupService.buildZipBytes(
        appData: appData,
        videoByMoveId: {'m1': Uint8List.fromList([1, 2, 3])},
        musicEntries: [
          (songId: 's1', ext: 'mp3', bytes: Uint8List.fromList([9])),
        ],
      );
      await zipFile.writeAsBytes(zipBytes, flush: true);

      await extractSecureZipWhitelistToSandbox(
        zipPath: zipFile.path,
        sandboxDir: sandbox,
        whitelist: SecureZipWhitelist.midnightDancerFullBackup,
      );
      await validateExtractedMetadataJson(sandbox);

      final fromDir = fullBackupParseExtractedDirectory(sandbox);
      final fromZip = FullBackupService.parseZip(zipBytes);
      expect(fromDir.isOk, isTrue);
      expect(fromZip.isOk, isTrue);
      expect(fromDir.appData?.songs.length, fromZip.appData?.songs.length);
      expect(fromDir.videoByMoveId.length, fromZip.videoByMoveId.length);
    } finally {
      await dir.delete(recursive: true);
    }
  });

  test('metadata validation fails on bad JSON', () async {
    final d = await Directory.systemTemp.createTemp('sz_meta_');
    try {
      final f = File('${d.path}/metadata.json');
      await f.writeAsString('{not valid');
      await expectLater(
        validateExtractedMetadataJson(d),
        throwsA(isA<SecureZipImportException>()),
      );
    } finally {
      await d.delete(recursive: true);
    }
  });
}
