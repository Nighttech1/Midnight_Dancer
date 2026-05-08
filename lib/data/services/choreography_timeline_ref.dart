import 'package:midnight_dancer/data/models/dance_style.dart';
import 'package:midnight_dancer/data/models/move.dart';

/// Ссылки на элементы в таймлайне хореографии.
///
/// Новый формат: `styleId::moveId` (стиль и элемент могут не совпадать со стилем хореографии).
/// Старые данные: только имя или id элемента — ищутся сначала в стиле хореографии, затем во всех стилях.
class ChoreographyTimelineRef {
  ChoreographyTimelineRef._();

  static const String sep = '::';

  static String encode(String styleId, String moveId) => '$styleId$sep$moveId';

  static ({String styleId, String moveId})? decode(String value) {
    final i = value.indexOf(sep);
    if (i <= 0 || i + sep.length >= value.length) return null;
    return (styleId: value.substring(0, i), moveId: value.substring(i + sep.length));
  }

  /// Найти элемент по значению из таймлайна.
  static Move? resolveMove(List<DanceStyle> styles, String choreographyStyleId, String value) {
    if (value.isEmpty) return null;
    final dec = decode(value);
    if (dec != null) {
      for (final s in styles) {
        if (s.id != dec.styleId) continue;
        for (final m in s.moves) {
          if (m.id == dec.moveId) return m;
        }
      }
      return null;
    }
    for (final s in styles) {
      if (s.id != choreographyStyleId) continue;
      for (final m in s.moves) {
        if (m.name == value || m.id == value) return m;
      }
    }
    for (final s in styles) {
      for (final m in s.moves) {
        if (m.name == value || m.id == value) return m;
      }
    }
    return null;
  }

  /// После импорта бэкапа: стиль из архива сливают в локальный [archiveStyleIdToTargetId],
  /// а поля таймлайна остаются в формате `архивныйСтиль::элемент` — переписываем префикс стиля.
  static Map<double, String> remapTimelineForArchiveStyleRedirect(
    Map<double, String> timeline,
    Map<String, String> archiveStyleIdToTargetId,
  ) {
    if (archiveStyleIdToTargetId.isEmpty) return Map<double, String>.from(timeline);
    final out = <double, String>{};
    for (final e in timeline.entries) {
      final dec = decode(e.value);
      if (dec != null) {
        final to = archiveStyleIdToTargetId[dec.styleId];
        out[e.key] = to != null ? encode(to, dec.moveId) : e.value;
      } else {
        out[e.key] = e.value;
      }
    }
    return out;
  }

  /// После `remapImportedSubtreeToNewIds`: обновить `styleId::moveId` и голые id элементов в таймлайне.
  static Map<double, String> remapTimelineAfterFullIdRemap({
    required Map<double, String> timeline,
    required Map<String, String> styleOldToNew,
    required Map<String, String> moveOldToNew,
    required String choreographyRemappedStyleId,
    required List<DanceStyle> remappedStyles,
  }) {
    String? styleContainingMove(String moveId) {
      for (final s in remappedStyles) {
        if (s.moves.any((m) => m.id == moveId)) return s.id;
      }
      return null;
    }

    final out = <double, String>{};
    for (final e in timeline.entries) {
      final v = e.value;
      final dec = decode(v);
      if (dec != null) {
        final ns = styleOldToNew[dec.styleId] ?? dec.styleId;
        final nm = moveOldToNew[dec.moveId] ?? dec.moveId;
        out[e.key] = encode(ns, nm);
        continue;
      }
      final nm = moveOldToNew[v];
      if (nm != null) {
        final st = styleContainingMove(nm) ?? choreographyRemappedStyleId;
        out[e.key] = encode(st, nm);
        continue;
      }
      out[e.key] = v;
    }
    return out;
  }

  /// Подпись в списке точек / сегментах.
  static String displayLabel(
    List<DanceStyle> styles,
    String choreographyStyleId,
    String value,
    String Function(String storedName) displayStyleName,
  ) {
    final m = resolveMove(styles, choreographyStyleId, value);
    if (m == null) return value;
    final dec = decode(value);
    if (dec == null) return m.name;
    DanceStyle? st;
    for (final s in styles) {
      if (s.id == dec.styleId) {
        st = s;
        break;
      }
    }
    final sn = st?.name ?? '…';
    return '${displayStyleName(sn)} · ${m.name}';
  }
}
