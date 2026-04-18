import 'dart:typed_data';

import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';

/// Музыкальный файл для сборки ZIP во вторичном изоляте (тяжёлый CPU — [ZipEncoder]).
class FullBackupIsolateMusicEntry {
  const FullBackupIsolateMusicEntry({
    required this.songId,
    required this.ext,
    required this.bytes,
  });

  final String songId;
  final String ext;
  final Uint8List bytes;
}

class FullBackupIsolateZipArgs {
  const FullBackupIsolateZipArgs({
    required this.exportData,
    required this.videoByMoveId,
    required this.musicEntries,
  });

  final AppData exportData;
  final Map<String, Uint8List> videoByMoveId;
  final List<FullBackupIsolateMusicEntry> musicEntries;
}

/// Вызывать только через [Isolate.run] — не блокирует UI-поток.
Uint8List fullBackupBuildZipInIsolate(FullBackupIsolateZipArgs args) {
  final musicEntries = <({String songId, String ext, Uint8List bytes})>[
    for (final m in args.musicEntries)
      (songId: m.songId, ext: m.ext, bytes: m.bytes),
  ];
  return FullBackupService.buildZipBytes(
    appData: args.exportData,
    videoByMoveId: args.videoByMoveId,
    musicEntries: musicEntries,
  );
}
