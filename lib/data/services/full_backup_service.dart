import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:midnight_dancer/data/models/app_data.dart';

/// Полный бэкап приложения: ZIP с manifest, metadata.json, videos/, music/.
class FullBackupService {
  FullBackupService._();

  static const formatId = 'midnight-dancer-full-backup';
  static const formatVersion = 1;

  static const manifestName = 'manifest.json';
  static const metadataName = 'metadata.json';
  static const videosPrefix = 'videos/';
  static const musicPrefix = 'music/';

  /// Собрать ZIP из уже прочитанных байтов (удобно для тестов).
  static Uint8List buildZipBytes({
    required AppData appData,
    required Map<String, Uint8List> videoByMoveId,
    required List<({String songId, String ext, Uint8List bytes})> musicEntries,
  }) {
    final archive = Archive();
    void addUtf8(String path, String text) {
      final b = utf8.encode(text);
      archive.addFile(ArchiveFile(path, b.length, b));
    }

    addUtf8(
      manifestName,
      jsonEncode({'format': formatId, 'version': formatVersion}),
    );
    addUtf8(metadataName, jsonEncode(appData.toJson()));
    for (final e in videoByMoveId.entries) {
      final name = '$videosPrefix${e.key}.mp4';
      archive.addFile(ArchiveFile(name, e.value.length, e.value));
    }
    for (final m in musicEntries) {
      final ext = m.ext.toLowerCase();
      final name = '$musicPrefix${m.songId}.$ext';
      archive.addFile(ArchiveFile(name, m.bytes.length, m.bytes));
    }
    final encoded = ZipEncoder().encode(archive);
    if (encoded == null) {
      return Uint8List(0);
    }
    return Uint8List.fromList(encoded);
  }

  /// Разобрать ZIP полного бэкапа. [error] заполнен при ошибке.
  static FullBackupParseResult parseZip(Uint8List zipBytes) {
    try {
      if (zipBytes.isEmpty) {
        return FullBackupParseResult.error('empty_archive');
      }
      final archive = ZipDecoder().decodeBytes(zipBytes);

      Uint8List? rawContent(ArchiveFile f) {
        final raw = f.content;
        if (raw is Uint8List) return raw;
        if (raw is List<int>) return Uint8List.fromList(raw);
        return null;
      }

      ArchiveFile? file(String name) {
        for (final f in archive.files) {
          if (f.isFile && f.name.replaceAll('\\', '/') == name) return f;
        }
        return null;
      }

      final manFile = file(manifestName);
      if (manFile == null) {
        return FullBackupParseResult.error('missing_manifest');
      }
      final manBytes = rawContent(manFile);
      if (manBytes == null) {
        return FullBackupParseResult.error('bad_manifest');
      }
      final man = jsonDecode(utf8.decode(manBytes)) as Map<String, dynamic>;
      if (man['format'] != formatId) {
        return FullBackupParseResult.error('bad_format');
      }
      final ver = man['version'];
      if (ver is! int || ver != formatVersion) {
        return FullBackupParseResult.error('bad_version');
      }

      final metaFile = file(metadataName);
      if (metaFile == null) {
        return FullBackupParseResult.error('missing_metadata');
      }
      final metaBytes = rawContent(metaFile);
      if (metaBytes == null) {
        return FullBackupParseResult.error('bad_metadata');
      }
      final metaMap = jsonDecode(utf8.decode(metaBytes)) as Map<String, dynamic>;
      final appData = AppData.fromJson(metaMap);

      final videoByMoveId = <String, Uint8List>{};
      final musicEntries = <({String songId, String ext, Uint8List bytes})>[];

      for (final f in archive.files) {
        if (!f.isFile) continue;
        final n = f.name.replaceAll('\\', '/');
        final content = rawContent(f);
        if (content == null) continue;
        if (n.startsWith(videosPrefix) && n.toLowerCase().endsWith('.mp4')) {
          final id = n.substring(videosPrefix.length, n.length - 4);
          if (id.isEmpty) continue;
          videoByMoveId[id] = content;
        } else if (n.startsWith(musicPrefix)) {
          final rest = n.substring(musicPrefix.length);
          final dot = rest.lastIndexOf('.');
          if (dot <= 0 || dot >= rest.length - 1) continue;
          final songId = rest.substring(0, dot);
          final ext = rest.substring(dot + 1).toLowerCase();
          if (songId.isEmpty) continue;
          musicEntries.add((
            songId: songId,
            ext: ext,
            bytes: content,
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
}

class FullBackupParseResult {
  FullBackupParseResult._({
    this.error,
    this.appData,
    this.videoByMoveId = const {},
    this.musicEntries = const [],
  });

  factory FullBackupParseResult.error(String message) =>
      FullBackupParseResult._(error: message);

  factory FullBackupParseResult.ok({
    required AppData appData,
    required Map<String, Uint8List> videoByMoveId,
    required List<({String songId, String ext, Uint8List bytes})> musicEntries,
  }) =>
      FullBackupParseResult._(
        appData: appData,
        videoByMoveId: videoByMoveId,
        musicEntries: musicEntries,
      );

  final String? error;
  final AppData? appData;
  final Map<String, Uint8List> videoByMoveId;
  final List<({String songId, String ext, Uint8List bytes})> musicEntries;

  bool get isOk => error == null && appData != null;
}
