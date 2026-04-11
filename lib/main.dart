// Midnight Dancer — Nighttech / Alik
import 'package:audio_session/audio_session.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:midnight_dancer/data/services/dance_reminder_scheduler.dart';
import 'package:midnight_dancer/data/services/storage_service.dart';
import 'app.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      systemNavigationBarColor: Color(0xFF0f172a),
    ),
  );
  try {
    await StorageService.instance.init();
  } catch (e, st) {
    debugPrint('StorageService init error: $e');
    debugPrintStack(stackTrace: st);
  }
  try {
    await DanceReminderScheduler.init();
  } catch (e, st) {
    debugPrint('DanceReminderScheduler init: $e');
    debugPrintStack(stackTrace: st);
  }
  try {
    final session = await AudioSession.instance;
    await session.configure(const AudioSessionConfiguration.music());
  } catch (e, st) {
    debugPrint('AudioSession configure: $e');
    debugPrintStack(stackTrace: st);
  }
  runApp(const MidnightDancerApp());
}
