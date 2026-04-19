import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

void main() {
  test('deleteSong clears songId in choreographies referencing it', () {
    final c1 = Choreography(
      id: 'ch1',
      name: 'A',
      songId: 'song-1',
      styleId: 'st',
      timeline: const {},
      startTime: 0,
      endTime: 60,
    );
    final song = Song(
      id: 'song-1',
      title: 'T',
      danceStyle: 'General',
      level: 'Beginner',
      fileName: 'song-1.mp3',
      duration: 60,
      sizeBytes: 100,
    );
    // Simulate notifier logic (same as AppDataNotifier.deleteSong)
    final choreosAfter = [c1].map((c) {
      if (c.songId != 'song-1') return c;
      return c.copyWith(songId: '');
    }).toList();
    expect(choreosAfter.first.songId, '');
    expect(choreosAfter.first.name, 'A');
  });

  test('timeline strips encoded and plain refs when move deleted', () {
    const styleId = 'st1';
    final m1 = Move(id: 'mv1', name: 'Spin', level: 'Beginner');
    final enc = ChoreographyTimelineRef.encode(styleId, 'mv1');
    final c = Choreography(
      id: 'ch',
      name: 'X',
      songId: 'sg',
      styleId: styleId,
      timeline: {
        1.0: enc,
        2.0: 'Spin',
        3.0: 'mv1',
      },
      startTime: 0,
      endTime: 10,
    );
    // Same rules as AppDataNotifier.deleteMove:
    final tl2 = Map<double, String>.from(c.timeline);
    tl2.removeWhere((_, val) {
      final dec = ChoreographyTimelineRef.decode(val);
      if (dec != null) {
        return dec.styleId == styleId && dec.moveId == 'mv1';
      }
      if (c.styleId != styleId) return false;
      return val == m1.name || val == m1.id;
    });
    expect(tl2.isEmpty, true);
  });
}
