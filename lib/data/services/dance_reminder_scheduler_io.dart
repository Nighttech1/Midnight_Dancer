import 'dart:io';

import 'package:flutter/foundation.dart' show debugPrint;
import 'package:flutter/services.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_timezone/flutter_timezone.dart';
import 'package:midnight_dancer/data/services/dance_reminder_config.dart';
import 'package:timezone/data/latest_all.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

/// Планирование локальных уведомлений «пора танцевать» (Android / iOS / desktop с поддержкой).
class DanceReminderScheduler {
  DanceReminderScheduler._();

  static final FlutterLocalNotificationsPlugin _plugin = FlutterLocalNotificationsPlugin();
  static const MethodChannel _notificationCacheChannel =
      MethodChannel('com.midnightdancer.app/notification_cache');
  static bool _initialized = false;

  static const int _idDaily = 9001;
  static const int _idWeekdayBase = 9100;
  static const int _idWeekly = 9020;

  static const AndroidNotificationChannel _channel = AndroidNotificationChannel(
    'dance_reminders',
    'Dance reminders',
    description: 'Reminders to open the app and practice',
    importance: Importance.high,
  );

  static Future<void> init() async {
    if (_initialized) return;
    tzdata.initializeTimeZones();
    try {
      final tzInfo = await FlutterTimezone.getLocalTimezone();
      tz.setLocalLocation(tz.getLocation(tzInfo.identifier));
    } catch (e) {
      debugPrint('DanceReminderScheduler: timezone fallback ($e)');
      tz.setLocalLocation(tz.UTC);
    }

    const androidInit = AndroidInitializationSettings('@mipmap/ic_launcher');
    const darwinInit = DarwinInitializationSettings(
      requestAlertPermission: false,
      requestBadgePermission: false,
      requestSoundPermission: false,
    );
    await _plugin.initialize(
      const InitializationSettings(
        android: androidInit,
        iOS: darwinInit,
        macOS: darwinInit,
      ),
    );

    final androidImpl = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    await androidImpl?.createNotificationChannel(_channel);

    _initialized = true;
  }

  static Future<bool> requestPermissionsIfNeeded() async {
    await init();
    var ok = true;
    if (Platform.isAndroid) {
      final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      final granted = await android?.requestNotificationsPermission();
      ok = granted ?? true;
      // Точные будильники: без этого «через минуту» на Android 12+ часто не срабатывает.
      await android?.requestExactAlarmsPermission();
    } else if (Platform.isIOS || Platform.isMacOS) {
      final ios = _plugin.resolvePlatformSpecificImplementation<IOSFlutterLocalNotificationsPlugin>();
      final mac = _plugin.resolvePlatformSpecificImplementation<MacOSFlutterLocalNotificationsPlugin>();
      final r = await ios?.requestPermissions(alert: true, badge: true, sound: true) ??
          await mac?.requestPermissions(alert: true, badge: true, sound: true);
      ok = r ?? false;
    }
    return ok;
  }

  /// Сбрасывает JSON кэша запланированных уведомлений (после смены версии плагина / битого Gson).
  static Future<void> _clearAndroidScheduledNotificationCache() async {
    if (!Platform.isAndroid) return;
    try {
      await _notificationCacheChannel.invokeMethod<void>('clearFlutterLocalNotificationsScheduleCache');
    } catch (e) {
      debugPrint('DanceReminderScheduler: could not clear notification schedule cache ($e)');
    }
  }

  static Future<void> _cancelReminderIds() async {
    Future<void> cancelAll() async {
      await _plugin.cancel(_idDaily);
      await _plugin.cancel(_idWeekly);
      for (var i = DateTime.monday; i <= DateTime.sunday; i++) {
        await _plugin.cancel(_idWeekdayBase + i);
      }
    }

    try {
      await cancelAll();
    } on PlatformException catch (e) {
      final combined = '${e.code} ${e.message}';
      if (Platform.isAndroid &&
          (combined.contains('Missing type parameter') || combined.contains('RuntimeException'))) {
        debugPrint('DanceReminderScheduler: clearing broken schedule cache and retrying cancel');
        await _clearAndroidScheduledNotificationCache();
        await cancelAll();
      } else {
        rethrow;
      }
    }
  }

  static tz.TZDateTime _nextTodayOrTomorrow(int hour, int minute) {
    final loc = tz.local;
    final now = tz.TZDateTime.now(loc);
    var scheduled = tz.TZDateTime(loc, now.year, now.month, now.day, hour, minute);
    if (!scheduled.isAfter(now)) {
      scheduled = scheduled.add(const Duration(days: 1));
    }
    return scheduled;
  }

  static tz.TZDateTime _nextOnWeekday(int hour, int minute, int weekday) {
    var t = _nextTodayOrTomorrow(hour, minute);
    var guard = 0;
    while (t.weekday != weekday && guard < 14) {
      t = t.add(const Duration(days: 1));
      guard++;
    }
    return t;
  }

  /// Android: exactAllowWhileIdle требует разрешения «Точные будильники»; иначе нативный код кидает исключение.
  /// Без разрешения пробуем inexact* — уведомление может прийти с небольшой задержкой, но не молчит полностью.
  static Future<void> _zonedScheduleReminder({
    required int id,
    required String title,
    required String body,
    required tz.TZDateTime scheduledDate,
    required NotificationDetails details,
    required DateTimeComponents? matchDateTimeComponents,
  }) async {
    Future<void> scheduleAndroid(AndroidScheduleMode mode) {
      return _plugin.zonedSchedule(
        id,
        title,
        body,
        scheduledDate,
        details,
        androidScheduleMode: mode,
        uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
        matchDateTimeComponents: matchDateTimeComponents,
      );
    }

    Future<void> scheduleDarwin() {
      return _plugin.zonedSchedule(
        id,
        title,
        body,
        scheduledDate,
        details,
        androidScheduleMode: AndroidScheduleMode.exactAllowWhileIdle,
        uiLocalNotificationDateInterpretation: UILocalNotificationDateInterpretation.absoluteTime,
        matchDateTimeComponents: matchDateTimeComponents,
      );
    }

    if (!Platform.isAndroid) {
      await scheduleDarwin();
      return;
    }

    final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
    final canExact = await android?.canScheduleExactNotifications();
    final modes = <AndroidScheduleMode>[
      if (canExact == true) AndroidScheduleMode.exactAllowWhileIdle,
      AndroidScheduleMode.inexactAllowWhileIdle,
      AndroidScheduleMode.inexact,
    ];

    Object? lastError;
    for (final mode in modes) {
      try {
        await scheduleAndroid(mode);
        return;
      } catch (e, st) {
        debugPrint('DanceReminderScheduler: zonedSchedule id=$id mode=$mode failed: $e');
        debugPrint('$st');
        lastError = e;
      }
    }
    if (lastError != null) {
      throw Exception('DanceReminderScheduler: all Android schedule modes failed: $lastError');
    }
  }

  static Future<void> apply(
    DanceReminderConfig config, {
    required String notificationTitle,
    required String notificationBody,
  }) async {
    await init();
    await _cancelReminderIds();
    if (!config.enabled) return;

    if (Platform.isAndroid) {
      final android = _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>();
      await android?.requestExactAlarmsPermission();
    }

    final androidDetails = AndroidNotificationDetails(
      _channel.id,
      _channel.name,
      channelDescription: _channel.description,
      importance: Importance.high,
      priority: Priority.high,
    );
    const darwinDetails = DarwinNotificationDetails();
    final details = NotificationDetails(android: androidDetails, iOS: darwinDetails, macOS: darwinDetails);

    final title = notificationTitle;
    final body = notificationBody;

    switch (config.mode) {
      case DanceReminderMode.daily:
        await _zonedScheduleReminder(
          id: _idDaily,
          title: title,
          body: body,
          scheduledDate: _nextTodayOrTomorrow(config.hour, config.minute),
          details: details,
          matchDateTimeComponents: DateTimeComponents.time,
        );
        break;
      case DanceReminderMode.weekdays:
        for (var wd = DateTime.monday; wd <= DateTime.friday; wd++) {
          await _zonedScheduleReminder(
            id: _idWeekdayBase + wd,
            title: title,
            body: body,
            scheduledDate: _nextOnWeekday(config.hour, config.minute, wd),
            details: details,
            matchDateTimeComponents: DateTimeComponents.dayOfWeekAndTime,
          );
        }
        break;
      case DanceReminderMode.weekly:
        await _zonedScheduleReminder(
          id: _idWeekly,
          title: title,
          body: body,
          scheduledDate: _nextOnWeekday(config.hour, config.minute, config.weekday),
          details: details,
          matchDateTimeComponents: DateTimeComponents.dayOfWeekAndTime,
        );
        break;
    }
  }
}
