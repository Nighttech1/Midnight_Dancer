import 'dart:async';
import 'dart:isolate';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show debugPrint;
import 'package:midnight_dancer/core/utils/file_copy_platform.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/file_copy_platform_io.dart'
    as file_copy;
import 'package:midnight_dancer/core/utils/read_bytes.dart'
    if (dart.library.io) 'package:midnight_dancer/core/utils/read_bytes_io.dart'
    as read_bytes;
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/full_backup_zip_isolate.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';

/// Чуть уступить UI isolate между тяжёлыми шагами чтения файлов.
Future<void> _yieldUi() =>
    Future<void>.delayed(const Duration(milliseconds: 12));

Future<Uint8List?> collectAndBuildFullBackupZip(
  StorageService storage,
  AppData data,
) async {
  final videoByMoveId = <String, Uint8List>{};
  final packagedMoveIds = <String>{};

  for (final style in data.danceStyles) {
    for (final move in style.moves) {
      await _yieldUi();
      final uri = move.videoUri;
      if (uri == null || uri.isEmpty) continue;
      Uint8List? bytes;
      if (uri.startsWith('content:')) {
        final cached = await file_copy.copyPickedFileToCache(uri);
        if (cached != null && cached.isNotEmpty) {
          bytes = await read_bytes.readBytesFromPath(cached);
        }
      } else if (uri.startsWith('/')) {
        bytes = await read_bytes.readBytesFromPath(uri);
      } else {
        bytes = await storage.loadMediaFile(uri, 'video');
      }
      if (bytes != null && bytes.isNotEmpty) {
        videoByMoveId[move.id] = bytes;
        packagedMoveIds.add(move.id);
      } else {
        debugPrint(
          'Full backup: skip video for move ${move.id} (unreadable)',
        );
      }
    }
  }

  String extFromSong(Song s) {
    final fn = s.fileName;
    final i = fn.lastIndexOf('.');
    if (i >= 0 && i < fn.length - 1) {
      return fn.substring(i + 1).toLowerCase();
    }
    return 'mp3';
  }

  final musicEntries = <({String songId, String ext, Uint8List bytes})>[];
  for (final song in data.songs) {
    await _yieldUi();
    final ext = extFromSong(song);
    final bytes = await storage.loadMediaFile(
      song.id,
      'music',
      musicExtension: ext,
    );
    if (bytes != null && bytes.isNotEmpty) {
      musicEntries.add((songId: song.id, ext: ext, bytes: bytes));
    } else {
      debugPrint('Full backup: skip music for song ${song.id} (missing file)');
    }
  }

  await _yieldUi();

  final exportStyles = data.danceStyles.map((s) {
    final moves = s.moves.map((m) {
      if (packagedMoveIds.contains(m.id)) {
        return m.copyWith(videoUri: m.id);
      }
      return m.copyWith(videoUri: null);
    }).toList();
    return s.copyWith(moves: moves);
  }).toList();

  final exportData = data.copyWith(danceStyles: exportStyles);

  await _yieldUi();

  final isolateMusic = <FullBackupIsolateMusicEntry>[
    for (final e in musicEntries)
      FullBackupIsolateMusicEntry(songId: e.songId, ext: e.ext, bytes: e.bytes),
  ];

  final zip = await Isolate.run(
    () => fullBackupBuildZipInIsolate(
      FullBackupIsolateZipArgs(
        exportData: exportData,
        videoByMoveId: videoByMoveId,
        musicEntries: isolateMusic,
      ),
    ),
  );
  await _yieldUi();
  if (zip.isEmpty) return null;
  return zip;
}
