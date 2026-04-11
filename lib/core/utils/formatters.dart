// Formatting utilities - duration, file size

/// Если [current] нет в [valid], возвращает [fallback].
/// Иначе `DropdownButton` в Flutter падает при несовпадении value и items.
String dropdownValueOrFallback(String current, Set<String> valid, String fallback) {
  return valid.contains(current) ? current : fallback;
}

String formatDuration(double seconds) {
  if (seconds.isNaN || seconds < 0) return '0:00';
  final m = (seconds / 60).floor();
  final s = (seconds % 60).round();
  return '$m:${s.toString().padLeft(2, '0')}';
}

/// Для позиции плеера (just_audio / Duration).
String formatDurationFromDuration(Duration d) {
  if (d.inMilliseconds <= 0) return '0:00';
  final totalSec = d.inSeconds;
  final m = totalSec ~/ 60;
  final s = totalSec % 60;
  return '$m:${s.toString().padLeft(2, '0')}';
}

String formatBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(1)} KB';
  final mb = (bytes / (1024 * 1024) * 10).round() / 10;
  return '${mb.toStringAsFixed(1)} MB';
}
