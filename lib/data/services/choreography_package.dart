import 'dart:convert';
import 'dart:typed_data';

import 'package:archive/archive.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';

/// Ошибка разбора или сборки ZIP-пакета хореографии Midnight Dancer.
class ChoreographyPackageException implements Exception {
  ChoreographyPackageException(this.message);
  final String message;

  @override
  String toString() => message;
}

/// Данные из импортированного пакета (ещё без подстановки новых id в приложении).
class ChoreographyPackagePayload {
  ChoreographyPackagePayload({
    required this.choreography,
    required this.song,
    required this.styleName,
    required this.moves,
    required this.musicBytes,
  });

  final Choreography choreography;
  final Song song;
  final String styleName;
  final List<Move> moves;
  final Uint8List musicBytes;
}

/// ZIP-пакет: manifest, choreography.json, song.json, style.json, music/audio.<ext>
class ChoreographyPackage {
  ChoreographyPackage._();

  static const formatId = 'midnight-dancer-choreo';
  static const formatVersion = 1;

  static const _manifest = 'manifest.json';
  static const _choreography = 'choreography.json';
  static const _song = 'song.json';
  static const _style = 'style.json';
  static const _musicDir = 'music/';

  /// Сброс видео для пакета ([Move.copyWith] не обнуляет null — собираем заново).
  static Move _moveWithoutVideo(Move m) => Move(
        id: m.id,
        name: m.name,
        level: m.level,
        description: m.description,
        videoUri: null,
        masteryPercent: 0,
      );

  /// Элементы для выгрузки: по значениям из таймлайна (имя элемента **или** его id);
  /// если таймлайн пуст — все элементы стиля. Видео в пакет не входят.
  static List<Move> movesForExport(Choreography choreography, List<Move> styleMoves) {
    final keys = choreography.timeline.values.where((n) => n.isNotEmpty).toSet();
    if (keys.isEmpty) {
      return styleMoves.map(_moveWithoutVideo).toList();
    }
    final sortedKeys = keys.toList()..sort();
    final out = <Move>[];
    final seenIds = <String>{};
    for (final key in sortedKeys) {
      Move? found;
      for (final mv in styleMoves) {
        if (mv.name == key || mv.id == key) {
          found = mv;
          break;
        }
      }
      if (found != null && !seenIds.contains(found.id)) {
        seenIds.add(found.id);
        out.add(_moveWithoutVideo(found));
      }
    }
    return out;
  }

  /// В таймлайне подставляем имена вместо id (для импорта на другом устройстве id не совпадут).
  static Choreography _choreographyTimelineIdsToNames(
    Choreography c,
    List<Move> resolverMoves,
  ) {
    if (c.timeline.isEmpty || resolverMoves.isEmpty) return c;
    final idToName = {for (final m in resolverMoves) m.id: m.name};
    var changed = false;
    final newT = <double, String>{};
    for (final e in c.timeline.entries) {
      final v = e.value;
      final resolved = idToName[v] ?? v;
      if (resolved != v) changed = true;
      newT[e.key] = resolved;
    }
    return changed ? c.copyWith(timeline: newT) : c;
  }

  static String _musicFileName(Song song) {
    final ext = _extensionFromFileName(song.fileName);
    return 'audio.$ext';
  }

  static String _extensionFromFileName(String fileName) {
    final i = fileName.lastIndexOf('.');
    if (i >= 0 && i < fileName.length - 1) {
      return fileName.substring(i + 1).toLowerCase();
    }
    return 'mp3';
  }

  static Uint8List encode({
    required Choreography choreography,
    required Song song,
    required String styleName,
    required List<Move> moves,
    required Uint8List musicBytes,
    List<Move>? timelineResolverMoves,
  }) {
    final archive = Archive();
    void addUtf8(String path, String text) {
      final b = utf8.encode(text);
      archive.addFile(ArchiveFile(path, b.length, b));
    }

    final resolver = timelineResolverMoves ?? moves;
    final choreoForZip = _choreographyTimelineIdsToNames(choreography, resolver);

    addUtf8(
      _manifest,
      jsonEncode({'format': formatId, 'version': formatVersion}),
    );
    addUtf8(_choreography, jsonEncode(choreoForZip.toJson()));
    addUtf8(_song, jsonEncode(song.toJson()));
    addUtf8(
      _style,
      jsonEncode({
        'name': styleName,
        'moves': moves.map((m) => _moveWithoutVideo(m).toJson()).toList(),
      }),
    );

    final musicPath = '$_musicDir${_musicFileName(song)}';
    archive.addFile(ArchiveFile(musicPath, musicBytes.length, musicBytes));

    final zip = ZipEncoder().encode(archive);
    if (zip == null) {
      throw ChoreographyPackageException('ZIP encode failed');
    }
    return Uint8List.fromList(zip);
  }

  static Map<String, Uint8List> _indexFiles(Archive archive) {
    final map = <String, Uint8List>{};
    for (final f in archive.files) {
      if (!f.isFile) continue;
      final name = f.name.replaceAll('\\', '/');
      final raw = f.content;
      final bytes = raw is List<int>
          ? Uint8List.fromList(raw)
          : raw is Uint8List
              ? raw
              : null;
      if (bytes != null) map[name] = bytes;
    }
    return map;
  }

  static Uint8List? _require(Map<String, Uint8List> files, String path) {
    final b = files[path];
    if (b == null || b.isEmpty) return null;
    return b;
  }

  /// Разбор ZIP; бросает [ChoreographyPackageException] при неверном формате.
  static ChoreographyPackagePayload decode(Uint8List zipBytes) {
    if (zipBytes.isEmpty) {
      throw ChoreographyPackageException('empty_archive');
    }
    final Archive archive;
    try {
      archive = ZipDecoder().decodeBytes(zipBytes);
    } catch (_) {
      throw ChoreographyPackageException('invalid_zip');
    }
    final files = _indexFiles(archive);

    final manifestRaw = _require(files, _manifest);
    if (manifestRaw == null) {
      throw ChoreographyPackageException('missing_manifest');
    }
    Map<String, dynamic> manifest;
    try {
      manifest = jsonDecode(utf8.decode(manifestRaw)) as Map<String, dynamic>;
    } catch (_) {
      throw ChoreographyPackageException('bad_manifest_json');
    }
    if (manifest['format'] != formatId) {
      throw ChoreographyPackageException('wrong_format');
    }
    final ver = manifest['version'];
    if (ver is! int || ver != formatVersion) {
      throw ChoreographyPackageException('unsupported_version');
    }

    final choreoRaw = _require(files, _choreography);
    final songRaw = _require(files, _song);
    final styleRaw = _require(files, _style);
    if (choreoRaw == null || songRaw == null || styleRaw == null) {
      throw ChoreographyPackageException('missing_json');
    }

    late final Choreography choreography;
    late final Song song;
    late final String styleName;
    late final List<Move> moves;
    try {
      choreography = Choreography.fromJson(
        jsonDecode(utf8.decode(choreoRaw)) as Map<String, dynamic>,
      );
      song = Song.fromJson(
        jsonDecode(utf8.decode(songRaw)) as Map<String, dynamic>,
      );
      final styleMap = jsonDecode(utf8.decode(styleRaw)) as Map<String, dynamic>;
      styleName = styleMap['name'] as String? ?? '';
      final movesJson = styleMap['moves'] as List<dynamic>? ?? [];
      moves = movesJson
          .map((e) => Move.fromJson(e as Map<String, dynamic>))
          .map(_moveWithoutVideo)
          .toList();
    } catch (_) {
      throw ChoreographyPackageException('bad_json');
    }

    if (styleName.isEmpty) {
      throw ChoreographyPackageException('empty_style_name');
    }

    final ext = _extensionFromFileName(song.fileName);
    final expectedMusic = '$_musicDir${_musicFileName(song)}';
    Uint8List? music = files[expectedMusic];
    if (music == null || music.isEmpty) {
      for (final e in files.entries) {
        if (e.key.startsWith(_musicDir) && e.key != _musicDir && e.value.isNotEmpty) {
          music = e.value;
          break;
        }
      }
    }
    if (music == null || music.isEmpty) {
      throw ChoreographyPackageException('missing_music');
    }
    if (!['mp3', 'm4a', 'wav'].contains(ext)) {
      throw ChoreographyPackageException('bad_music_extension');
    }

    final choreoNormalized = _choreographyTimelineIdsToNames(choreography, moves);

    return ChoreographyPackagePayload(
      choreography: choreoNormalized,
      song: song,
      styleName: styleName,
      moves: moves,
      musicBytes: music,
    );
  }
}
