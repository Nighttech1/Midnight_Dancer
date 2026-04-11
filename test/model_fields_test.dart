import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';

void main() {
  test('Move JSON keeps masteryPercent and defaults for old data', () {
    final m = Move.fromJson({
      'id': '1',
      'name': 'Test',
      'level': 'Beginner',
    });
    expect(m.masteryPercent, 0);

    final m2 = Move.fromJson({
      'id': '1',
      'name': 'Test',
      'level': 'Beginner',
      'masteryPercent': 73,
    });
    expect(m2.masteryPercent, 73);
  });

  test('Song JSON keeps playbackSpeed', () {
    final s = Song.fromJson({
      'id': 's',
      'title': 'T',
      'danceStyle': 'Salsa',
      'level': 'Beginner',
      'fileName': 's.mp3',
      'duration': 60.0,
      'sizeBytes': 1000,
      'playbackSpeed': 0.75,
    });
    expect(s.playbackSpeed, 0.75);

    final s2 = Song.fromJson({
      'id': 's',
      'title': 'T',
      'danceStyle': 'Salsa',
      'level': 'Beginner',
      'fileName': 's.mp3',
      'duration': 60.0,
      'sizeBytes': 1000,
    });
    expect(s2.playbackSpeed, 1.0);
  });

  test('DanceStyle JSON keeps currentMoveId', () {
    final d = DanceStyle.fromJson({
      'id': 'st',
      'name': 'Style',
      'moves': [],
      'currentMoveId': 'move-1',
    });
    expect(d.currentMoveId, 'move-1');
  });
}
