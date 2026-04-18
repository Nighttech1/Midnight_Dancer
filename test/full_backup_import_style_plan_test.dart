import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/services/full_backup_import_merge.dart';
import 'package:midnight_dancer/data/services/full_backup_import_style_plan.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';

void main() {
  test('remapImportedSubtreeToNewIds avoids id collision merge', () {
    final local = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'Local',
          moves: [Move(id: 'm1', name: 'A', level: 'Beginner')],
        ),
      ],
      songs: const [],
      choreographies: const [],
    );
    final imported = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'Archive same id',
          moves: [Move(id: 'm1', name: 'B', level: 'Beginner')],
        ),
      ],
      songs: const [],
      choreographies: const [],
    );
    final parsed = FullBackupParseResult.ok(
      appData: imported,
      videoByMoveId: {'m1': Uint8List.fromList([1, 2])},
      musicEntries: const [],
    );
    final remapped = remapImportedSubtreeToNewIds(parsed);
    expect(remapped.isOk, true);
    final imp2 = remapped.appData!;
    expect(imp2.danceStyles.first.id, isNot('s1'));
    expect(imp2.danceStyles.first.moves.first.id, isNot('m1'));

    final plan = FullBackupImportMerge.mergeInto(local: local, imported: imp2);
    expect(plan.merged.danceStyles.length, 2);
  });

  test('applyStyleRedirectBeforeMerge folds archive style into local target id', () {
    final local = AppData(
      danceStyles: [
        DanceStyle(id: 'loc-hip', name: 'Hip', moves: []),
      ],
      songs: const [],
      choreographies: const [],
    );
    final imported = AppData(
      danceStyles: [
        DanceStyle(
          id: 'arch-style',
          name: 'Archive Hip',
          moves: [Move(id: 'mv1', name: 'Kick', level: 'Beginner')],
        ),
      ],
      songs: const [],
      choreographies: [
        Choreography(
          id: 'ch1',
          name: 'C',
          songId: 'so1',
          styleId: 'arch-style',
          endTime: 60,
        ),
      ],
    );
    final parsed = FullBackupParseResult.ok(
      appData: imported,
      videoByMoveId: const {},
      musicEntries: const [],
    );
    final adjusted = applyStyleRedirectBeforeMerge(
      parsed,
      local,
      const FullBackupStyleImportPlan(
        mergeArchiveStyleIntoLocalId: {'arch-style': 'loc-hip'},
      ),
    );
    expect(adjusted.appData!.danceStyles.single.id, 'loc-hip');
    expect(adjusted.appData!.danceStyles.single.moves.single.name, 'Kick');
    expect(adjusted.appData!.choreographies.single.styleId, 'loc-hip');

    final plan = FullBackupImportMerge.mergeInto(local: local, imported: adjusted.appData!);
    expect(plan.merged.danceStyles.where((s) => s.id == 'loc-hip').single.moves.single.name, 'Kick');
  });

  test('importAsSeparateOnly remaps then merges as additive', () {
    final local = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'L',
          moves: [Move(id: 'm1', name: 'X', level: 'Beginner')],
        ),
      ],
      songs: const [],
      choreographies: const [],
    );
    final imported = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'Dup id',
          moves: [Move(id: 'm2', name: 'Y', level: 'Beginner')],
        ),
      ],
      songs: const [],
      choreographies: const [],
    );
    final parsed = FullBackupParseResult.ok(
      appData: imported,
      videoByMoveId: const {},
      musicEntries: const [],
    );
    final adjusted = applyStyleRedirectBeforeMerge(
      parsed,
      local,
      const FullBackupStyleImportPlan(importAsSeparateOnly: true),
    );
    final plan = FullBackupImportMerge.mergeInto(local: local, imported: adjusted.appData!);
    expect(plan.merged.danceStyles.length, 2);
  });
}
