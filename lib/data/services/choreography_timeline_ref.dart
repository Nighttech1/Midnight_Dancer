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
