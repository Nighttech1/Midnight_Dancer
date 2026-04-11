/// Настройки напоминаний «пора танцевать» (хранятся в [AppData.settings]).
class DanceReminderConfig {
  DanceReminderConfig({
    this.enabled = false,
    this.hour = 19,
    this.minute = 0,
    this.mode = DanceReminderMode.daily,
    this.weekday = DateTime.saturday,
  });

  static const String keyEnabled = 'danceReminderEnabled';
  static const String keyHour = 'danceReminderHour';
  static const String keyMinute = 'danceReminderMinute';
  static const String keyMode = 'danceReminderMode';
  static const String keyWeekday = 'danceReminderWeekday';

  final bool enabled;
  final int hour;
  final int minute;
  final DanceReminderMode mode;
  /// Для [DanceReminderMode.weekly]: [DateTime.monday]..[DateTime.sunday].
  final int weekday;

  factory DanceReminderConfig.fromSettings(Map<String, dynamic> s) {
    final modeStr = s[keyMode] as String? ?? 'daily';
    final mode = switch (modeStr) {
      'weekdays' => DanceReminderMode.weekdays,
      'weekly' => DanceReminderMode.weekly,
      _ => DanceReminderMode.daily,
    };
    return DanceReminderConfig(
      enabled: s[keyEnabled] == true,
      hour: (s[keyHour] as num?)?.toInt().clamp(0, 23) ?? 19,
      minute: (s[keyMinute] as num?)?.toInt().clamp(0, 59) ?? 0,
      mode: mode,
      weekday: (s[keyWeekday] as num?)?.toInt().clamp(1, 7) ?? DateTime.saturday,
    );
  }

  Map<String, dynamic> toSettingsEntries() => {
        keyEnabled: enabled,
        keyHour: hour,
        keyMinute: minute,
        keyMode: mode.name,
        keyWeekday: weekday,
      };
}

enum DanceReminderMode {
  /// Каждый день в выбранное время
  daily,

  /// Пн–Пт в выбранное время
  weekdays,

  /// Раз в неделю в выбранный день недели
  weekly,
}
