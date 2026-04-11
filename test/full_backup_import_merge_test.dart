import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/full_backup_import_merge.dart';

void main() {
  group('FullBackupImportMerge', () {
    test('keeps local styles and songs; adds only new ids from import', () {
      final localMove = Move(
        id: 'm-local',
        name: 'Local',
        level: 'Beginner',
        videoUri: 'm-local',
        masteryPercent: 10,
      );
      final localStyle = DanceStyle(
        id: 'st-local',
        name: 'LocalStyle',
        moves: [localMove],
      );
      final localSong = Song(
        id: 'sg-local',
        title: 'LocalTrack',
        danceStyle: 'LocalStyle',
        level: 'Beginner',
        fileName: 'a.mp3',
        duration: 60,
        sizeBytes: 100,
      );
      final localChoreo = Choreography(
        id: 'ch-local',
        name: 'LocalCh',
        songId: 'sg-local',
        styleId: 'st-local',
        endTime: 60,
      );
      final local = AppData(
        danceStyles: [localStyle],
        songs: [localSong],
        choreographies: [localChoreo],
        settings: const {'k': 1},
      );

      final impMove = Move(
        id: 'm-new',
        name: 'Imported',
        level: 'Advanced',
        videoUri: 'm-new',
        masteryPercent: 0,
      );
      final impStyle = DanceStyle(
        id: 'st-imported',
        name: 'ImportedStyle',
        moves: [impMove],
      );
      final impSong = Song(
        id: 'sg-new',
        title: 'NewTrack',
        danceStyle: 'ImportedStyle',
        level: 'Advanced',
        fileName: 'b.mp3',
        duration: 90,
        sizeBytes: 200,
      );
      final impChoreo = Choreography(
        id: 'ch-new',
        name: 'ImpCh',
        songId: 'sg-new',
        styleId: 'st-imported',
        endTime: 90,
      );
      final imported = AppData(
        danceStyles: [impStyle],
        songs: [impSong],
        choreographies: [impChoreo],
      );

      final plan = FullBackupImportMerge.mergeInto(local: local, imported: imported);

      expect(plan.merged.danceStyles.length, 2);
      expect(plan.merged.songs.map((s) => s.id).toSet(), {'sg-local', 'sg-new'});
      expect(plan.merged.choreographies.length, 2);
      expect(plan.merged.choreographies.map((c) => c.id).contains('ch-local'), true);
      expect(plan.merged.choreographies.map((c) => c.id).contains('ch-new'), true);

      expect(plan.musicSongIdsToWrite, {'sg-new'});
      expect(plan.videoMoveIdsToWrite, {'m-new'});
    });

    test('merges extra moves into existing style by id', () {
      final localStyle = DanceStyle(
        id: 'st1',
        name: 'S',
        moves: [
          Move(
            id: 'm1',
            name: 'A',
            level: 'Beginner',
            videoUri: 'm1',
            masteryPercent: 0,
          ),
        ],
      );
      final importedStyle = DanceStyle(
        id: 'st1',
        name: 'S',
        moves: [
          Move(
            id: 'm1',
            name: 'A',
            level: 'Beginner',
            videoUri: 'm1',
            masteryPercent: 0,
          ),
          Move(
            id: 'm2',
            name: 'B',
            level: 'Beginner',
            videoUri: 'm2',
            masteryPercent: 0,
          ),
        ],
      );
      final plan = FullBackupImportMerge.mergeInto(
        local: AppData(danceStyles: [localStyle]),
        imported: AppData(danceStyles: [importedStyle]),
      );
      expect(plan.merged.danceStyles.length, 1);
      expect(plan.merged.danceStyles.first.moves.map((m) => m.id).toList(), ['m1', 'm2']);
    });

    test('duplicate choreography id from import gets new id', () {
      final ch = Choreography(
        id: 'same',
        name: 'X',
        songId: 's',
        styleId: 'st',
        endTime: 1,
      );
      final local = AppData(choreographies: [ch]);
      final imported = AppData(choreographies: [ch]);
      final plan = FullBackupImportMerge.mergeInto(local: local, imported: imported);
      expect(plan.merged.choreographies.length, 2);
      expect(plan.merged.choreographies.where((c) => c.id == 'same').length, 1);
      expect(
        plan.merged.choreographies.any((c) => c.id.startsWith('same-import-')),
        true,
      );
    });

    test('does not overwrite local song; skips music write for duplicate id', () {
      final song = Song(
        id: 'sg1',
        title: 'T',
        danceStyle: 'S',
        level: 'Beginner',
        fileName: 'x.mp3',
        duration: 1,
        sizeBytes: 1,
      );
      final plan = FullBackupImportMerge.mergeInto(
        local: AppData(songs: [song]),
        imported: AppData(songs: [song]),
      );
      expect(plan.merged.songs.length, 1);
      expect(plan.musicSongIdsToWrite, isEmpty);
    });

    test('video write for existing move without local video', () {
      final localMove = Move(
        id: 'm1',
        name: 'A',
        level: 'Beginner',
        videoUri: null,
        masteryPercent: 0,
      );
      final impMove = Move(
        id: 'm1',
        name: 'A',
        level: 'Beginner',
        videoUri: 'm1',
        masteryPercent: 0,
      );
      final plan = FullBackupImportMerge.mergeInto(
        local: AppData(
          danceStyles: [DanceStyle(id: 'st', name: 'S', moves: [localMove])],
        ),
        imported: AppData(
          danceStyles: [DanceStyle(id: 'st', name: 'S', moves: [impMove])],
        ),
      );
      expect(plan.videoMoveIdsToWrite, {'m1'});
    });

    test('no video write when local move already has video', () {
      final m = Move(
        id: 'm1',
        name: 'A',
        level: 'Beginner',
        videoUri: 'm1',
        masteryPercent: 0,
      );
      final plan = FullBackupImportMerge.mergeInto(
        local: AppData(danceStyles: [DanceStyle(id: 'st', name: 'S', moves: [m])]),
        imported: AppData(danceStyles: [DanceStyle(id: 'st', name: 'S', moves: [m])]),
      );
      expect(plan.videoMoveIdsToWrite, isEmpty);
    });
  });
}
