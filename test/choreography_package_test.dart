import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/models/song.dart';
import 'package:midnight_dancer/data/services/choreography_package.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

DanceStyle _st(String id, List<Move> moves) => DanceStyle(id: id, name: 'n$id', moves: moves);

void main() {
  test('movesForExport: timeline names pick matching moves, strip video', () {
    final choreo = Choreography(
      id: 'c1',
      name: 'Test',
      songId: 's1',
      styleId: 'st1',
      timeline: {1.0: 'A', 2.0: 'B'},
      startTime: 0,
      endTime: 10,
    );
    final moves = [
      Move(id: 'm1', name: 'A', level: 'Beginner', videoUri: 'vid', masteryPercent: 50),
      Move(id: 'm2', name: 'B', level: 'Advanced', videoUri: 'x', masteryPercent: 10),
      Move(id: 'm3', name: 'Unused', level: 'Beginner'),
    ];
    final exp = ChoreographyPackage.movesForExport(choreo, [_st('st1', moves)]);
    expect(exp.length, 2);
    expect(exp.any((m) => m.name == 'A' && m.videoUri == null && m.masteryPercent == 0), isTrue);
    expect(exp.any((m) => m.name == 'B' && m.videoUri == null), isTrue);
  });

  test('movesForExport: timeline values can be move ids (trainer mode)', () {
    final choreo = Choreography(
      id: 'c1',
      name: 'T',
      songId: 's1',
      styleId: 'st1',
      timeline: {1.0: 'mv1', 2.0: 'mv2'},
      startTime: 0,
      endTime: 10,
    );
    final moves = [
      Move(id: 'mv1', name: 'Jump', level: 'Beginner', videoUri: 'x'),
      Move(id: 'mv2', name: 'Spin', level: 'Advanced'),
    ];
    final exp = ChoreographyPackage.movesForExport(choreo, [_st('st1', moves)]);
    expect(exp.length, 2);
    expect(exp.map((m) => m.name).toSet(), {'Jump', 'Spin'});
  });

  test('movesForExport: empty timeline exports all style moves without video', () {
    final choreo = Choreography(
      id: 'c1',
      name: 'Empty',
      songId: 's1',
      styleId: 'st1',
      timeline: {},
      startTime: 0,
      endTime: 5,
    );
    final moves = [
      Move(id: 'm1', name: 'X', level: 'Beginner', videoUri: 'v'),
    ];
    final exp = ChoreographyPackage.movesForExport(choreo, [_st('st1', moves)]);
    expect(exp.length, 1);
    expect(exp.first.videoUri, isNull);
  });

  test('encode/decode roundtrip preserves choreography timeline and style', () {
    final choreo = Choreography(
      id: 'old-ch',
      name: 'My dance',
      songId: 'old-song',
      styleId: 'old-style',
      timeline: {0.5: 'Step one', 3.0: 'Step two'},
      startTime: 0,
      endTime: 120,
    );
    final song = Song(
      id: 'old-song',
      title: 'Track',
      danceStyle: 'Salsa',
      level: 'Beginner',
      fileName: 'beat.mp3',
      duration: 120,
      sizeBytes: 999,
      playbackSpeed: 1.0,
    );
    final moves = [
      Move(id: 'x1', name: 'Step one', level: 'Beginner', description: 'd1', videoUri: 'should-strip'),
      Move(id: 'x2', name: 'Step two', level: 'Intermediate'),
    ];
    final music = Uint8List.fromList([0xFF, 0xD3, 0x00]);

    final zip = ChoreographyPackage.encode(
      choreography: choreo,
      song: song,
      styleName: 'Kizomba',
      moves: moves.map((m) => m.copyWith(videoUri: null, masteryPercent: 0)).toList(),
      timelineResolverMoves: moves,
      musicBytes: music,
    );

    final payload = ChoreographyPackage.decode(zip);
    expect(payload.choreography.timeline, choreo.timeline);
    expect(payload.choreography.name, choreo.name);
    expect(payload.song.title, song.title);
    expect(payload.song.fileName, song.fileName);
    expect(payload.styleName, 'Kizomba');
    expect(payload.moves.length, 2);
    expect(payload.moves.every((m) => m.videoUri == null), isTrue);
    expect(payload.musicBytes, music);
  });

  test('encode/decode: timeline with move ids is stored as names in archive', () {
    final choreo = Choreography(
      id: 'old-ch',
      name: 'Combo',
      songId: 'old-song',
      styleId: 'old-style',
      timeline: {0.5: 'x1', 3.0: 'x2'},
      startTime: 0,
      endTime: 120,
    );
    final song = Song(
      id: 'old-song',
      title: 'Track',
      danceStyle: 'Salsa',
      level: 'Beginner',
      fileName: 'beat.mp3',
      duration: 120,
      sizeBytes: 999,
      playbackSpeed: 1.0,
    );
    final moves = [
      Move(id: 'x1', name: 'Step one', level: 'Beginner'),
      Move(id: 'x2', name: 'Step two', level: 'Intermediate'),
    ];
    final music = Uint8List.fromList([0xFF, 0xD3, 0x00]);

    final zip = ChoreographyPackage.encode(
      choreography: choreo,
      song: song,
      styleName: 'Kizomba',
      moves: ChoreographyPackage.movesForExport(choreo, [_st('old-style', moves)]),
      timelineResolverMoves: moves,
      musicBytes: music,
    );

    final payload = ChoreographyPackage.decode(zip);
    expect(payload.choreography.timeline[0.5], 'Step one');
    expect(payload.choreography.timeline[3.0], 'Step two');
    expect(payload.moves.length, 2);
  });

  test('movesForExport: styleId::moveId pulls move from another style', () {
    final choreo = Choreography(
      id: 'c1',
      name: 'Mix',
      songId: 's1',
      styleId: 'st1',
      timeline: {1.0: ChoreographyTimelineRef.encode('st2', 'mZ')},
      startTime: 0,
      endTime: 10,
    );
    final st1Moves = [Move(id: 'onlyIn1', name: 'Local', level: 'Beginner')];
    final st2Moves = [Move(id: 'mZ', name: 'FromTwo', level: 'Intermediate', videoUri: 'v')];
    final exp = ChoreographyPackage.movesForExport(choreo, [_st('st1', st1Moves), _st('st2', st2Moves)]);
    expect(exp.length, 1);
    expect(exp.first.name, 'FromTwo');
    expect(exp.first.videoUri, isNull);
  });

  test('decode rejects wrong format', () {
    expect(
      () => ChoreographyPackage.decode(Uint8List.fromList([1, 2, 3])),
      throwsA(isA<ChoreographyPackageException>()),
    );
  });
}
