import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

/// Собрать [AppData] для выгрузки по выбору пользователя.
///
/// Для выбранных хореографий в архив автоматически попадают связанные элементы (по таймлайну)
/// и треки, даже если они не отмечены вручную.
AppData buildFullBackupExportSubset({
  required AppData source,
  required Set<String> selectedMoveIds,
  required Set<String> selectedSongIds,
  required Set<String> selectedChoreographyIds,
}) {
  final styles = source.danceStyles;
  final moveIdsFromChoreo = <String>{};
  final selectedChoreos = <Choreography>[
    for (final c in source.choreographies)
      if (selectedChoreographyIds.contains(c.id)) c,
  ];

  for (final c in selectedChoreos) {
    final timelineKeys = c.timeline.values.where((v) => v.trim().isNotEmpty).toSet();
    if (timelineKeys.isEmpty) {
      for (final s in styles) {
        if (s.id != c.styleId) continue;
        for (final m in s.moves) {
          moveIdsFromChoreo.add(m.id);
        }
        break;
      }
    } else {
      for (final ref in timelineKeys) {
        final m = ChoreographyTimelineRef.resolveMove(styles, c.styleId, ref);
        if (m != null) moveIdsFromChoreo.add(m.id);
      }
    }
  }

  final allMoveIds = {...selectedMoveIds, ...moveIdsFromChoreo};
  final songIdsFromChoreo = selectedChoreos.map((c) => c.songId).toSet();
  final songIdsForMetadata = {...selectedSongIds, ...songIdsFromChoreo};

  final outStyles = <DanceStyle>[];
  for (final s in styles) {
    final moves = s.moves.where((m) => allMoveIds.contains(m.id)).toList();
    if (moves.isEmpty) continue;
    var cur = s.currentMoveId;
    if (cur != null && !allMoveIds.contains(cur)) {
      cur = moves.first.id;
    }
    outStyles.add(s.copyWith(moves: moves, currentMoveId: cur));
  }

  final outSongs = source.songs.where((s) => songIdsForMetadata.contains(s.id)).toList();

  return source.copyWith(
    danceStyles: outStyles,
    songs: outSongs,
    choreographies: selectedChoreos,
  );
}

/// Пустой ли результат (нечего класть в архив).
bool isFullBackupExportSubsetEmpty(AppData subset) {
  if (subset.choreographies.isNotEmpty) return false;
  if (subset.songs.isNotEmpty) return false;
  for (final s in subset.danceStyles) {
    if (s.moves.isNotEmpty) return false;
  }
  return true;
}
