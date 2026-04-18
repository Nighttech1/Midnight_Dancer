import 'package:midnight_dancer/data/models/choreography.dart';
import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/services/choreography_timeline_ref.dart';

/// Какие элементы (по id) задействованы в таймлайне — по всем стилям приложения.
class ChoreographyTimelineMoves {
  ChoreographyTimelineMoves._();

  static List<String> distinctMoveIdsReferenced(
    Choreography choreography,
    List<DanceStyle> allStyles,
  ) {
    final ids = <String>{};
    for (final v in choreography.timeline.values) {
      if (v.isEmpty) continue;
      final m = ChoreographyTimelineRef.resolveMove(allStyles, choreography.styleId, v);
      if (m != null) ids.add(m.id);
    }
    return ids.toList();
  }
}
