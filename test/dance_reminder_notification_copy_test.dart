import 'package:flutter_test/flutter_test.dart';
import 'package:midnight_dancer/data/services/dance_reminder_notification_copy.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('randomBody returns a line from asset or fallback', () async {
    final s = await DanceReminderNotificationCopy.randomBody(fallback: 'fallback_body');
    expect(s.isNotEmpty, true);
    expect(s == 'fallback_body', isFalse);
  });
}
