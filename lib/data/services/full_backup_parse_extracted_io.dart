import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';

import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';
import 'package:path/path.dart' as p;

/// Same as [FullBackupService.parseZip], but reads an already-extracted tree (secure sandbox).
FullBackupParseResult fullBackupParseExtractedDirectory(Directory root) {
  try {
    final manFile = File(p.join(root.path, FullBackupService.manifestName));
    if (!manFile.existsSync()) {
      return FullBackupParseResult.error('missing_manifest');
    }
    final manBytes = manFile.readAsBytesSync();
    if (manBytes.isEmpty) {
      return FullBackupParseResult.error('bad_manifest');
    }
    final man = jsonDecode(utf8.decode(manBytes)) as Map<String, dynamic>;
    if (man['format'] != FullBackupService.formatId) {
      return FullBackupParseResult.error('bad_format');
    }
    final ver = man['version'];
    if (ver is! int || ver != FullBackupService.formatVersion) {
      return FullBackupParseResult.error('bad_version');
    }

    final metaFile = File(p.join(root.path, FullBackupService.metadataName));
    if (!metaFile.existsSync()) {
      return FullBackupParseResult.error('missing_metadata');
    }
    final metaBytes = metaFile.readAsBytesSync();
    if (metaBytes.isEmpty) {
      return FullBackupParseResult.error('bad_metadata');
    }
    final metaMap = jsonDecode(utf8.decode(metaBytes)) as Map<String, dynamic>;
    final appData = AppData.fromJson(metaMap);

    final videoByMoveId = <String, Uint8List>{};
    final musicEntries = <({String songId, String ext, Uint8List bytes})>[];

    final videosDir = Directory(
      p.join(root.path, FullBackupService.videosPrefix.replaceAll('/', p.separator)),
    );
    if (videosDir.existsSync()) {
      for (final f in videosDir.listSync(followLinks: false)) {
        if (f is! File) continue;
        final n = p.basename(f.path);
        if (!n.toLowerCase().endsWith('.mp4')) continue;
        final id = n.substring(0, n.length - 4);
        if (id.isEmpty) continue;
        videoByMoveId[id] = f.readAsBytesSync();
      }
    }

    final musicDir = Directory(
      p.join(root.path, FullBackupService.musicPrefix.replaceAll('/', p.separator)),
    );
    if (musicDir.existsSync()) {
      for (final f in musicDir.listSync(followLinks: false)) {
        if (f is! File) continue;
        final n = p.basename(f.path);
        final dot = n.lastIndexOf('.');
        if (dot <= 0 || dot >= n.length - 1) continue;
        final songId = n.substring(0, dot);
        final ext = n.substring(dot + 1).toLowerCase();
        if (songId.isEmpty) continue;
        musicEntries.add((
          songId: songId,
          ext: ext,
          bytes: f.readAsBytesSync(),
        ));
      }
    }

    return FullBackupParseResult.ok(
      appData: appData,
      videoByMoveId: videoByMoveId,
      musicEntries: musicEntries,
    );
  } catch (e) {
    return FullBackupParseResult.error(e.toString());
  }
}
