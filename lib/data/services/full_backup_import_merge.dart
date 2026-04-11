import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';

/// План слияния полного бэкапа: новые метаданные и какие медиафайлы из архива записать.
class FullBackupMergePlan {
  FullBackupMergePlan({
    required this.merged,
    required this.videoMoveIdsToWrite,
    required this.musicSongIdsToWrite,
  });

  final AppData merged;
  final Set<String> videoMoveIdsToWrite;
  final Set<String> musicSongIdsToWrite;
}

/// Слияние импортированных метаданных с локальными без удаления существующего.
class FullBackupImportMerge {
  FullBackupImportMerge._();

  static Map<String, Move> _localMovesById(AppData local) {
    final map = <String, Move>{};
    for (final s in local.danceStyles) {
      for (final m in s.moves) {
        map[m.id] = m;
      }
    }
    return map;
  }

  /// Локальные настройки имеют приоритет; из архива подмешиваются только отсутствующие ключи.
  static Map<String, dynamic> _mergeSettings(
    Map<String, dynamic> local,
    Map<String, dynamic> imported,
  ) {
    return {...imported, ...local};
  }

  /// Объединить [imported] в [local]. Существующие сущности по id не перезаписываются;
  /// новые стили/элементы/треки/хореографии добавляются. При конфликте id хореографии —
  /// импорт копируется с новым id.
  static FullBackupMergePlan mergeInto({
    required AppData local,
    required AppData imported,
  }) {
    final localMovesById = _localMovesById(local);
    final localSongIds = local.songs.map((s) => s.id).toSet();

    final mergedStyles = <DanceStyle>[];
    final importedStyleById = {for (final s in imported.danceStyles) s.id: s};

    for (final locStyle in local.danceStyles) {
      final imp = importedStyleById[locStyle.id];
      if (imp == null) {
        mergedStyles.add(locStyle);
        continue;
      }
      final localMoveIds = locStyle.moves.map((m) => m.id).toSet();
      final extra = imp.moves.where((m) => !localMoveIds.contains(m.id)).toList();
      mergedStyles.add(locStyle.copyWith(moves: [...locStyle.moves, ...extra]));
    }

    final localStyleIds = local.danceStyles.map((s) => s.id).toSet();
    for (final impStyle in imported.danceStyles) {
      if (!localStyleIds.contains(impStyle.id)) {
        mergedStyles.add(impStyle);
      }
    }

    final mergedSongs = <Song>[...local.songs];
    for (final s in imported.songs) {
      if (!localSongIds.contains(s.id)) {
        mergedSongs.add(s);
      }
    }

    final mergedChoreos = <Choreography>[...local.choreographies];
    final knownChoreoIds = mergedChoreos.map((c) => c.id).toSet();
    final base = DateTime.now().millisecondsSinceEpoch;
    var choreoSuffix = 0;
    for (final c in imported.choreographies) {
      if (!knownChoreoIds.contains(c.id)) {
        mergedChoreos.add(c);
        knownChoreoIds.add(c.id);
      } else {
        choreoSuffix++;
        final newId = '${c.id}-import-$base-$choreoSuffix';
        mergedChoreos.add(c.copyWith(id: newId));
        knownChoreoIds.add(newId);
      }
    }

    final merged = AppData(
      danceStyles: mergedStyles,
      songs: mergedSongs,
      choreographies: mergedChoreos,
      settings: _mergeSettings(local.settings, imported.settings),
    );

    final videoMoveIdsToWrite = <String>{};
    for (final style in imported.danceStyles) {
      for (final m in style.moves) {
        final loc = localMovesById[m.id];
        if (loc == null) {
          videoMoveIdsToWrite.add(m.id);
        } else {
          final hadVideo = loc.videoUri != null && loc.videoUri!.isNotEmpty;
          if (!hadVideo) videoMoveIdsToWrite.add(m.id);
        }
      }
    }

    final musicSongIdsToWrite =
        imported.songs.map((s) => s.id).where((id) => !localSongIds.contains(id)).toSet();

    return FullBackupMergePlan(
      merged: merged,
      videoMoveIdsToWrite: videoMoveIdsToWrite,
      musicSongIdsToWrite: musicSongIdsToWrite,
    );
  }
}
