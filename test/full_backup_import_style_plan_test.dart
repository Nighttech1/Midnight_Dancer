import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';
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

  test('applyStyleRedirectBeforeMerge rewrites timeline styleId::moveId refs', () {
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
          timeline: {
            5.0: ChoreographyTimelineRef.encode('arch-style', 'mv1'),
            10.0: ChoreographyTimelineRef.encode('other-arch', 'x'),
          },
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
    final ch = adjusted.appData!.choreographies.single;
    expect(ch.styleId, 'loc-hip');
    expect(ch.timeline[5.0], ChoreographyTimelineRef.encode('loc-hip', 'mv1'));
    expect(ch.timeline[10.0], ChoreographyTimelineRef.encode('other-arch', 'x'));
  });

  test('remapImportedSubtreeToNewIds rewrites timeline so moves resolve', () {
    final imported = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'S',
          moves: [Move(id: 'm1', name: 'A', level: 'Beginner')],
        ),
      ],
      songs: [
        Song(
          id: 'song1',
          title: 'T',
          danceStyle: 'S',
          level: 'Beginner',
          fileName: 'a.mp3',
          duration: 60,
          sizeBytes: 1,
        ),
      ],
      choreographies: [
        Choreography(
          id: 'c1',
          name: 'Ch',
          songId: 'song1',
          styleId: 's1',
          timeline: {
            1.0: ChoreographyTimelineRef.encode('s1', 'm1'),
            2.0: 'm1',
          },
          endTime: 10,
        ),
      ],
    );
    final parsed = FullBackupParseResult.ok(
      appData: imported,
      videoByMoveId: const {},
      musicEntries: const [],
    );
    final remapped = remapImportedSubtreeToNewIds(parsed);
    final styles = remapped.appData!.danceStyles;
    final ch = remapped.appData!.choreographies.single;
    final r1 = ChoreographyTimelineRef.resolveMove(styles, ch.styleId, ch.timeline[1.0]!);
    final r2 = ChoreographyTimelineRef.resolveMove(styles, ch.styleId, ch.timeline[2.0]!);
    expect(r1, isNotNull);
    expect(r2, isNotNull);
    expect(r1!.id, r2!.id);
    expect(r1.name, 'A');
  });
}
