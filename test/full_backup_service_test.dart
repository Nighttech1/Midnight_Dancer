import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/app_data.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/full_backup_service.dart';

void main() {
  group('FullBackupService', () {
    test('buildZip + parseZip roundtrip preserves metadata and media', () {
      final move = Move(
        id: 'move-a',
        name: 'Step',
        level: 'Beginner',
        videoUri: 'move-a',
        masteryPercent: 50,
      );
      final style = DanceStyle(id: 'style-1', name: 'Salsa', moves: [move]);
      final song = Song(
        id: 'song-1',
        title: 'Track',
        danceStyle: 'Salsa',
        level: 'Beginner',
        fileName: 'song-1.mp3',
        duration: 120,
        sizeBytes: 1000,
      );
      final data = AppData(
        danceStyles: [style],
        songs: [song],
        choreographies: const [],
      );
      final vBytes = Uint8List.fromList([0x00, 0x01, 0x02]);
      final mBytes = Uint8List.fromList([0xAA, 0xBB]);

      final zip = FullBackupService.buildZipBytes(
        appData: data,
        videoByMoveId: {'move-a': vBytes},
        musicEntries: [
          (songId: 'song-1', ext: 'mp3', bytes: mBytes),
        ],
      );

      expect(zip.isNotEmpty, true);

      final parsed = FullBackupService.parseZip(zip);
      expect(parsed.isOk, true, reason: parsed.error);
      expect(parsed.appData!.danceStyles.length, 1);
      expect(parsed.appData!.danceStyles.first.moves.first.id, 'move-a');
      expect(parsed.appData!.songs.first.id, 'song-1');
      expect(parsed.videoByMoveId['move-a'], vBytes);
      expect(parsed.musicEntries.length, 1);
      expect(parsed.musicEntries.first.songId, 'song-1');
      expect(parsed.musicEntries.first.ext, 'mp3');
      expect(parsed.musicEntries.first.bytes, mBytes);
    });

    test('parseZip rejects empty input', () {
      final r = FullBackupService.parseZip(Uint8List(0));
      expect(r.isOk, false);
      expect(r.error, isNotNull);
    });

    test('parseZip rejects garbage bytes', () {
      final r = FullBackupService.parseZip(Uint8List.fromList([1, 2, 3, 4, 5]));
      expect(r.isOk, false);
    });
  });
}
