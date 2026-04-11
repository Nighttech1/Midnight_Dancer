import 'dart:io';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show debugPrint;
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import 'full_backup_save_downloads_stub.dart' show FullBackupSaveResult;

export 'full_backup_save_downloads_stub.dart' show FullBackupSaveResult;

const _kBackupExportChannel = MethodChannel('com.midnightdancer.app/backup_export');

/// Сохраняет ZIP в каталог «Загрузки» (Android — общая папка Download; desktop — системные Downloads;
/// iOS — Documents/MidnightDancerExports, путь показываем пользователю).
Future<FullBackupSaveResult?> saveFullBackupZipToDownloads(
  Uint8List zipBytes,
  String fileName,
) async {
  if (zipBytes.isEmpty) return null;
  final safeName = fileName.trim().isEmpty ? 'midnight-dancer-backup.zip' : fileName.trim();

  final tempDir = await getTemporaryDirectory();
  final tempPath = p.join(
    tempDir.path,
    'md_export_${DateTime.now().millisecondsSinceEpoch}.zip',
  );
  final tempFile = File(tempPath);
  try {
    await tempFile.writeAsBytes(zipBytes, flush: true);

    if (Platform.isAndroid) {
      final map = await _kBackupExportChannel.invokeMapMethod<String, dynamic>(
        'saveZipToDownloads',
        <String, dynamic>{
          'tempPath': tempPath,
          'fileName': safeName,
        },
      );
      if (map == null) return null;
      final folder = map['folderPath'] as String?;
      final name = map['fileName'] as String? ?? safeName;
      if (folder == null || folder.isEmpty) return null;
      return FullBackupSaveResult(folderPath: folder, fileName: name);
    }

    if (Platform.isIOS) {
      final docs = await getApplicationDocumentsDirectory();
      final dir = Directory(p.join(docs.path, 'MidnightDancerExports'));
      if (!await dir.exists()) await dir.create(recursive: true);
      final out = File(p.join(dir.path, safeName));
      await tempFile.copy(out.path);
      return FullBackupSaveResult(folderPath: dir.path, fileName: safeName);
    }

    final downloads = await getDownloadsDirectory();
    if (downloads == null) {
      debugPrint('saveFullBackupZipToDownloads: getDownloadsDirectory() null');
      return null;
    }
    final out = File(p.join(downloads.path, safeName));
    await tempFile.copy(out.path);
    return FullBackupSaveResult(folderPath: downloads.path, fileName: safeName);
  } on PlatformException catch (e, st) {
    debugPrint('saveFullBackupZipToDownloads: $e\n$st');
    return null;
  } catch (e, st) {
    debugPrint('saveFullBackupZipToDownloads: $e\n$st');
    return null;
  } finally {
    try {
      if (await tempFile.exists()) await tempFile.delete();
    } catch (_) {}
  }
}
