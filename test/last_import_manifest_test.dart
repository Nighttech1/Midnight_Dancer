import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/last_import_manifest.dart';

void main() {
  test('computeFullBackupDelta: new style and moves are tracked', () {
    final local = AppData();
    final merged = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'S',
          moves: [
            Move(id: 'm1', name: 'a', level: 'Beginner'),
          ],
        ),
      ],
      songs: [
        Song(
          id: 'song1',
          title: 't',
          danceStyle: 'S',
          level: 'Beginner',
          fileName: 'x.mp3',
          duration: 1,
          sizeBytes: 1,
        ),
      ],
      choreographies: [
        Choreography(
          id: 'c1',
          name: 'n',
          songId: 'song1',
          styleId: 's1',
          endTime: 1,
        ),
      ],
    );
    final m = LastImportManifest.computeFullBackupDelta(local, merged);
    expect(m.wholeStyleIds, contains('s1'));
    expect(m.moveIds, contains('m1'));
    expect(m.songIds, contains('song1'));
    expect(m.choreographyIds, contains('c1'));
  });

  test('computeFullBackupDelta: merged move into existing style only flags new move', () {
    final local = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'S',
          moves: [
            Move(id: 'm1', name: 'a', level: 'Beginner'),
          ],
        ),
      ],
    );
    final merged = AppData(
      danceStyles: [
        DanceStyle(
          id: 's1',
          name: 'S',
          moves: [
            Move(id: 'm1', name: 'a', level: 'Beginner'),
            Move(id: 'm2', name: 'b', level: 'Beginner'),
          ],
        ),
      ],
    );
    final m = LastImportManifest.computeFullBackupDelta(local, merged);
    expect(m.wholeStyleIds, isEmpty);
    expect(m.moveIds, equals({'m2'}));
  });

  test('fromJson roundtrip', () {
    final a = LastImportManifest(
      wholeStyleIds: {'a'},
      moveIds: {'b'},
      songIds: {'c'},
      choreographyIds: {'d'},
    );
    final b = LastImportManifest.fromJson(a.toJson());
    expect(b.wholeStyleIds, equals({'a'}));
    expect(b.moveIds, equals({'b'}));
    expect(b.songIds, equals({'c'}));
    expect(b.choreographyIds, equals({'d'}));
  });

  test('embedInSettings removes key when empty', () {
    final s = LastImportManifest.embedInSettings(
      {'last_import_manifest': <String, dynamic>{}},
      LastImportManifest.empty(),
    );
    expect(s.containsKey(LastImportManifest.settingsKey), isFalse);
  });
}
