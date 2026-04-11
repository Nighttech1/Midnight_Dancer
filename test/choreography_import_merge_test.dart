import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/models/move.dart';
import 'package:midnight_dancer/data/services/choreography_import_merge.dart';

void main() {
  test('mergeMoves: no collision keeps names and empty rename map', () {
    final existing = [
      Move(id: 'a', name: 'Old', level: 'Beginner', masteryPercent: 0),
    ];
    final incoming = [
      Move(id: 'x', name: 'Spin', level: 'Intermediate', masteryPercent: 50),
    ];
    final (imported, rename) = ChoreographyImportMerge.mergeMoves(
      existing,
      incoming,
      (i) => 'new-$i',
    );
    expect(imported.length, 1);
    expect(imported.single.name, 'Spin');
    expect(rename, isEmpty);
  });

  test('mergeMoves: name collision adds suffix and remaps timeline', () {
    final existing = [
      Move(id: 'a', name: 'Spin', level: 'Beginner', masteryPercent: 0),
    ];
    final incoming = [
      Move(id: 'x', name: 'Spin', level: 'Advanced', masteryPercent: 0),
    ];
    final (imported, rename) = ChoreographyImportMerge.mergeMoves(
      existing,
      incoming,
      (i) => 'new-$i',
    );
    expect(imported.single.name, 'Spin (2)');
    expect(rename['Spin'], 'Spin (2)');

    final timeline = <double, String>{0.0: 'Spin', 1.5: 'Spin'};
    final remapped = ChoreographyImportMerge.remapTimeline(timeline, rename);
    expect(remapped[0.0], 'Spin (2)');
    expect(remapped[1.5], 'Spin (2)');
  });

  test('remapTimeline: unchanged names pass through', () {
    final t = <double, String>{0.0: 'A', 2.0: 'B'};
    final out = ChoreographyImportMerge.remapTimeline(t, {'X': 'Y'});
    expect(out[0.0], 'A');
    expect(out[2.0], 'B');
  });
}
