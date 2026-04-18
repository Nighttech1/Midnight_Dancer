import 'dart:typed_data';

import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';

/// Настройки ручного сопоставления стилей при импорте полного архива.
class FullBackupStyleImportPlan {
  const FullBackupStyleImportPlan({
    this.importAsSeparateOnly = false,
    this.mergeArchiveStyleIntoLocalId = const {},
  });

  /// Все содержимое архива получает новые id; ничего не сливается с локальными по совпадению id.
  final bool importAsSeparateOnly;

  /// Для стиля из архива (ключ) — id **локального** стиля, в который перенаправить содержимое
  /// перед слиянием. Пустое значение / отсутствие ключа — вести себя как «по id из архива».
  final Map<String, String?> mergeArchiveStyleIntoLocalId;

  /// План по умолчанию (прежнее поведение): только автослияние по совпадению id.
  static const FullBackupStyleImportPlan automatic = FullBackupStyleImportPlan();
}

/// Переназначить id у всего импортируемого графа, чтобы [FullBackupImportMerge.mergeInto]
/// добавил данные как новые, без слияния по совпадению id.
FullBackupParseResult remapImportedSubtreeToNewIds(FullBackupParseResult parsed) {
  if (!parsed.isOk || parsed.appData == null) return parsed;
  final app = parsed.appData!;
  final base = DateTime.now().millisecondsSinceEpoch;
  var counter = 0;
  String nid(String prefix) => '$prefix-$base-${counter++}';

  final styleOldToNew = <String, String>{for (final s in app.danceStyles) s.id: nid('style-imp')};
  final moveOldToNew = <String, String>{};
  for (final s in app.danceStyles) {
    for (final m in s.moves) {
      moveOldToNew[m.id] = nid('move-imp');
    }
  }
  final songOldToNew = <String, String>{for (final s in app.songs) s.id: nid('song-imp')};
  final choreoOldToNew = <String, String>{for (final c in app.choreographies) c.id: nid('choreo-imp')};

  final newStyles = app.danceStyles.map((s) {
    final newMoves = s.moves.map((m) => m.copyWith(id: moveOldToNew[m.id]!)).toList();
    String? newCurrent;
    final cm = s.currentMoveId;
    if (cm != null && cm.isNotEmpty) {
      newCurrent = moveOldToNew[cm];
    }
    return s.copyWith(
      id: styleOldToNew[s.id]!,
      moves: newMoves,
      currentMoveId: newCurrent,
    );
  }).toList();

  final newSongs = app.songs.map((s) => s.copyWith(id: songOldToNew[s.id]!)).toList();

  final newChoreos = app.choreographies.map((c) {
    return c.copyWith(
      id: choreoOldToNew[c.id]!,
      styleId: styleOldToNew[c.styleId]!,
      songId: songOldToNew[c.songId]!,
    );
  }).toList();

  final newVideo = <String, Uint8List>{};
  for (final e in parsed.videoByMoveId.entries) {
    final n = moveOldToNew[e.key];
    if (n != null) newVideo[n] = e.value;
  }

  final newMusic = <({String songId, String ext, Uint8List bytes})>[];
  for (final m in parsed.musicEntries) {
    final sid = songOldToNew[m.songId];
    if (sid != null) {
      newMusic.add((songId: sid, ext: m.ext, bytes: m.bytes));
    }
  }

  return FullBackupParseResult.ok(
    appData: AppData(
      danceStyles: newStyles,
      songs: newSongs,
      choreographies: newChoreos,
      settings: app.settings,
    ),
    videoByMoveId: newVideo,
    musicEntries: newMusic,
  );
}

List<Move> _mergeMoveListsById(List<Move> base, List<Move> extra) {
  final ids = base.map((m) => m.id).toSet();
  return [...base, ...extra.where((m) => !ids.contains(m.id))];
}

List<Move> _dedupeMovesById(List<Move> moves) {
  final seen = <String>{};
  final out = <Move>[];
  for (final m in moves) {
    if (seen.contains(m.id)) continue;
    seen.add(m.id);
    out.add(m);
  }
  return out;
}

/// Применить ручное сопоставление «стиль из архива → локальный стиль» к разобранному архиву.
FullBackupParseResult applyStyleRedirectBeforeMerge(
  FullBackupParseResult parsed,
  AppData local,
  FullBackupStyleImportPlan plan,
) {
  if (!parsed.isOk || parsed.appData == null) return parsed;

  if (plan.importAsSeparateOnly) {
    return remapImportedSubtreeToNewIds(parsed);
  }

  if (plan.mergeArchiveStyleIntoLocalId.isEmpty) {
    return parsed;
  }

  final redirect = <String, String>{};
  for (final e in plan.mergeArchiveStyleIntoLocalId.entries) {
    final target = e.value;
    if (target == null || target.isEmpty) continue;
    if (e.key == target) continue;
    redirect[e.key] = target;
  }
  if (redirect.isEmpty) return parsed;

  var imp = parsed.appData!;
  final foldedArchiveIds = redirect.keys.toSet();

  final movesByTarget = <String, List<Move>>{};
  final styleMap = {for (final s in imp.danceStyles) s.id: s};

  for (final archiveId in foldedArchiveIds) {
    final s = styleMap[archiveId];
    if (s == null) continue;
    final target = redirect[archiveId]!;
    movesByTarget.putIfAbsent(target, () => []).addAll(s.moves);
  }

  final newStyles = <DanceStyle>[];
  for (final s in imp.danceStyles) {
    if (foldedArchiveIds.contains(s.id)) continue;
    newStyles.add(s);
  }

  for (final e in movesByTarget.entries) {
    final targetId = e.key;
    final extraMoves = _dedupeMovesById(e.value);
    final idx = newStyles.indexWhere((s) => s.id == targetId);
    if (idx >= 0) {
      final existing = newStyles[idx];
      newStyles[idx] = existing.copyWith(
        moves: _mergeMoveListsById(existing.moves, extraMoves),
      );
    } else {
      String name;
      try {
        name = local.danceStyles.firstWhere((s) => s.id == targetId).name;
      } catch (_) {
        name = targetId;
      }
      newStyles.add(DanceStyle(
        id: targetId,
        name: name,
        moves: extraMoves,
        currentMoveId: null,
      ));
    }
  }

  final newChoreos = imp.choreographies.map((c) {
    final r = redirect[c.styleId];
    if (r != null) return c.copyWith(styleId: r);
    return c;
  }).toList();

  final newImp = AppData(
    danceStyles: newStyles,
    songs: imp.songs,
    choreographies: newChoreos,
    settings: imp.settings,
  );

  return FullBackupParseResult.ok(
    appData: newImp,
    videoByMoveId: parsed.videoByMoveId,
    musicEntries: parsed.musicEntries,
  );
}
