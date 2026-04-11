import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/services/dance_reminder_config.dart';

void main() {
  test('DanceReminderConfig fromSettings defaults and roundtrip keys', () {
    final c = DanceReminderConfig.fromSettings({});
    expect(c.enabled, false);
    expect(c.hour, 19);
    expect(c.minute, 0);
    expect(c.mode, DanceReminderMode.daily);
    expect(c.weekday, DateTime.saturday);

    final m = c.toSettingsEntries();
    final c2 = DanceReminderConfig.fromSettings(m);
    expect(c2.enabled, c.enabled);
    expect(c2.hour, c.hour);
    expect(c2.minute, c.minute);
    expect(c2.mode, c.mode);
    expect(c2.weekday, c.weekday);
  });

  test('DanceReminderConfig parses weekdays mode', () {
    final c = DanceReminderConfig.fromSettings({
      DanceReminderConfig.keyEnabled: true,
      DanceReminderConfig.keyMode: 'weekdays',
      DanceReminderConfig.keyHour: 8,
      DanceReminderConfig.keyMinute: 30,
    });
    expect(c.mode, DanceReminderMode.weekdays);
    expect(c.enabled, true);
    expect(c.hour, 8);
    expect(c.minute, 30);
  });
}
