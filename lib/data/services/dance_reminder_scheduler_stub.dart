import 'package:midnight_dancer/data/services/dance_reminder_config.dart';

/// Web: локальные уведомления не планируем.
class DanceReminderScheduler {
  DanceReminderScheduler._();

  static Future<void> init() async {}

  static Future<void> apply(
    DanceReminderConfig config, {
    required String notificationTitle,
    required String notificationBody,
  }) async {}

  static Future<bool> requestPermissionsIfNeeded() async => true;
}
