import 'package:midnight_dancer/data/models/move.dart';

/// Логика слияния элементов из пакета в существующий стиль (уникальные имена + карта переименований для таймлайна).
class ChoreographyImportMerge {
  ChoreographyImportMerge._();

  /// [newMoveId] — генератор id для каждого нового элемента (индекс с 1).
  static (List<Move> imported, Map<String, String> renameMap) mergeMoves(
    List<Move> existingMoves,
    List<Move> payloadMoves,
    String Function(int oneBasedIndex) newMoveId,
  ) {
    final takenNames = existingMoves.map((m) => m.name).toSet();
    final renameFromOriginal = <String, String>{};

    String uniqueName(String desired) {
      if (!takenNames.contains(desired)) {
        takenNames.add(desired);
        return desired;
      }
      var i = 2;
      while (true) {
        final n = '$desired ($i)';
        if (!takenNames.contains(n)) {
          takenNames.add(n);
          return n;
        }
        i++;
      }
    }

    var moveIndex = 0;
    final newImportedMoves = <Move>[];
    for (final m in payloadMoves) {
      moveIndex++;
      final finalName = uniqueName(m.name);
      if (finalName != m.name) {
        renameFromOriginal[m.name] = finalName;
      }
      newImportedMoves.add(
        Move(
          id: newMoveId(moveIndex),
          name: finalName,
          level: m.level,
          description: m.description,
          videoUri: null,
          masteryPercent: 0,
        ),
      );
    }

    return (newImportedMoves, renameFromOriginal);
  }

  static Map<double, String> remapTimeline(
    Map<double, String> timeline,
    Map<String, String> renameFromOriginal,
  ) {
    return timeline.map((k, v) => MapEntry(k, renameFromOriginal[v] ?? v));
  }
}
