import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_moves.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

void main() {
  test('distinctMoveIdsReferenced matches by name or id', () {
    final moves = [
      Move(id: 'm-a', name: 'Kick', level: 'Beginner'),
      Move(id: 'm-b', name: 'Spin', level: 'Beginner'),
    ];
    final c1 = Choreography(
      id: 'c1',
      name: 'Test',
      songId: 's1',
      styleId: 'st1',
      timeline: {0.0: 'Kick', 1.0: 'm-b'},
      endTime: 60,
    );
    final styles = [DanceStyle(id: 'st1', name: 'S', moves: moves)];
    final ids = ChoreographyTimelineMoves.distinctMoveIdsReferenced(c1, styles);
    expect(ids.toSet(), {'m-a', 'm-b'});
  });

  test('distinctMoveIdsReferenced dedupes same move', () {
    final moves = [Move(id: 'm1', name: 'A', level: 'Beginner')];
    final c = Choreography(
      id: 'c1',
      name: 'T',
      songId: 's1',
      styleId: 'st1',
      timeline: {0.0: 'A', 2.0: 'm1'},
      endTime: 60,
    );
    final styles = [DanceStyle(id: 'st1', name: 'S', moves: moves)];
    final ids = ChoreographyTimelineMoves.distinctMoveIdsReferenced(c, styles);
    expect(ids, ['m1']);
  });

  test('distinctMoveIdsReferenced resolves styleId::moveId from another style', () {
    final st2Move = Move(id: 'z1', name: 'Zed', level: 'Beginner');
    final styles = [
      DanceStyle(id: 'st1', name: 'One', moves: const []),
      DanceStyle(id: 'st2', name: 'Two', moves: [st2Move]),
    ];
    final c = Choreography(
      id: 'c1',
      name: 'X',
      songId: 's1',
      styleId: 'st1',
      timeline: {0.0: ChoreographyTimelineRef.encode('st2', 'z1')},
      endTime: 60,
    );
    final ids = ChoreographyTimelineMoves.distinctMoveIdsReferenced(c, styles);
    expect(ids, ['z1']);
  });
}
