import 'dart:math';

import 'package:flutter/services.dart';

/// Тексты для локальных напоминаний — из [assets/notifications/dance_reminder_lines.txt]
/// (копия смысла из `midnight_dancer_notifications.md`, по одной фразе на строку).
class DanceReminderNotificationCopy {
  DanceReminderNotificationCopy._();

  static const String _assetPath = 'assets/notifications/dance_reminder_lines.txt';

  static List<String>? _cache;

  static Future<List<String>> _lines() async {
    if (_cache != null) return _cache!;
    try {
      final raw = await rootBundle.loadString(_assetPath);
      _cache = raw
          .split(RegExp(r'\r?\n'))
          .map((e) => e.trim())
          .where((e) => e.isNotEmpty)
          .toList(growable: false);
    } catch (_) {
      _cache = const [];
    }
    return _cache!;
  }

  /// Случайная фраза для тела уведомления; если ассет недоступен — [fallback].
  static Future<String> randomBody({required String fallback}) async {
    final lines = await _lines();
    if (lines.isEmpty) return fallback;
    return lines[Random().nextInt(lines.length)];
  }
}
