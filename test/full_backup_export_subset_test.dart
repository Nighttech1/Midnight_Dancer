import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/full_backup_export_subset.dart';

void main() {
  test('buildFullBackupExportSubset adds moves and songs from selected choreography', () {
    final m1 = Move(id: 'm1', name: 'A', level: '1');
    final m2 = Move(id: 'm2', name: 'B', level: '1');
    final style = DanceStyle(id: 'st1', name: 'Salsa', moves: [m1, m2]);
    final song = Song(
      id: 'song1',
      title: 'T',
      danceStyle: 'Salsa',
      level: '1',
      fileName: 'a.mp3',
      duration: 60,
      sizeBytes: 1,
    );
    final choreo = Choreography(
      id: 'c1',
      name: 'C',
      songId: 'song1',
      styleId: 'st1',
      timeline: {0.0: 'm1'},
      endTime: 60,
    );
    final source = AppData(
      danceStyles: [style],
      songs: [song],
      choreographies: [choreo],
    );

    final subset = buildFullBackupExportSubset(
      source: source,
      selectedMoveIds: {},
      selectedSongIds: {},
      selectedChoreographyIds: {'c1'},
    );

    expect(subset.choreographies.map((c) => c.id), ['c1']);
    expect(subset.songs.map((s) => s.id), ['song1']);
    expect(subset.danceStyles.single.moves.map((m) => m.id), contains('m1'));
    expect(isFullBackupExportSubsetEmpty(subset), isFalse);
  });

  test('selectedSongIds includes chosen tracks without choreography', () {
    final song = Song(
      id: 's1',
      title: 'Track',
      danceStyle: 'Salsa',
      level: '1',
      fileName: 'a.mp3',
      duration: 60,
      sizeBytes: 1,
    );
    final source = AppData(
      danceStyles: const [],
      songs: [song],
      choreographies: const [],
    );
    final subset = buildFullBackupExportSubset(
      source: source,
      selectedMoveIds: {},
      selectedSongIds: {'s1'},
      selectedChoreographyIds: {},
    );
    expect(subset.songs.map((s) => s.id), ['s1']);
    expect(isFullBackupExportSubsetEmpty(subset), isFalse);
  });

  test('isFullBackupExportSubsetEmpty is true for empty selection on empty-like subset', () {
    final source = AppData(
      danceStyles: [
        DanceStyle(id: 'st', name: 'X', moves: const []),
      ],
    );
    final subset = buildFullBackupExportSubset(
      source: source,
      selectedMoveIds: {},
      selectedSongIds: {},
      selectedChoreographyIds: {},
    );
    expect(isFullBackupExportSubsetEmpty(subset), isTrue);
  });
}
