import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:midnight_dancer/core/app_strings.dart';
import 'package:midnight_dancer/core/app_ui_language.dart';
import 'package:midnight_dancer/core/theme/app_theme.dart';
import 'package:midnight_dancer/data/services/dance_reminder_config.dart';
import 'package:midnight_dancer/data/services/dance_reminder_scheduler.dart';
import 'package:midnight_dancer/providers/app_data_provider.dart';
import 'package:midnight_dancer/providers/ui_language_provider.dart';

/// Настройки приложения (пока только напоминания о тренировке).
class SettingsScreen extends ConsumerStatefulWidget {
  const SettingsScreen({super.key, required this.initialReminder});

  final DanceReminderConfig initialReminder;

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late bool _enabled;
  late int _hour;
  late int _minute;
  late DanceReminderMode _mode;
  late int _weekday;

  @override
  void initState() {
    super.initState();
    final c = widget.initialReminder;
    _enabled = c.enabled;
    _hour = c.hour;
    _minute = c.minute;
    _mode = c.mode;
    _weekday = c.weekday;
  }

  String _timeLabel() {
    final h = _hour.toString().padLeft(2, '0');
    final m = _minute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  Future<void> _pickTime() async {
    final t = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: _hour, minute: _minute),
      initialEntryMode: TimePickerEntryMode.input,
      builder: (ctx, child) {
        return Theme(
          data: Theme.of(ctx).copyWith(
            colorScheme: const ColorScheme.dark(
              primary: AppColors.accent,
              surface: AppColors.card,
            ),
          ),
          child: child!,
        );
      },
    );
    if (t != null && mounted) {
      setState(() {
        _hour = t.hour;
        _minute = t.minute;
      });
      await _persist();
    }
  }

  DanceReminderConfig _config() => DanceReminderConfig(
        enabled: _enabled,
        hour: _hour,
        minute: _minute,
        mode: _mode,
        weekday: _weekday,
      );

  Future<void> _persist() async {
    final notifier = ref.read(appDataNotifierProvider.notifier);
    await notifier.saveDanceReminderConfig(_config());
  }

  Future<void> _onEnabledChanged(bool v) async {
    if (v) {
      final ok = await DanceReminderScheduler.requestPermissionsIfNeeded();
      if (!ok && mounted) {
        final str = ref.read(appStringsProvider);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(str.danceReminderPermissionDenied)),
        );
        return;
      }
    }
    setState(() => _enabled = v);
    await _persist();
  }

  Future<void> _setMode(DanceReminderMode m) async {
    setState(() => _mode = m);
    await _persist();
  }

  Future<void> _setWeekday(int wd) async {
    setState(() => _weekday = wd);
    await _persist();
  }

  Future<void> _setUiLanguage(AppUiLanguage lang) async {
    await ref.read(appDataNotifierProvider.notifier).saveUiLanguage(lang);
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final str = ref.watch(appStringsProvider);
    final lang = ref.watch(uiLanguageProvider);
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        foregroundColor: Colors.white,
        elevation: 0,
        title: Text(str.settingsTitle),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
        children: [
          Text(
            lang.settingsSectionTitle,
            style: TextStyle(
              color: Colors.white.withOpacity(0.65),
              fontSize: 13,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Column(
                children: [
                  RadioListTile<AppUiLanguage>(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                    title: Text(lang.settingsOptionRussian, style: const TextStyle(color: Colors.white)),
                    value: AppUiLanguage.ru,
                    groupValue: lang,
                    activeColor: AppColors.accent,
                    onChanged: (v) {
                      if (v != null) _setUiLanguage(v);
                    },
                  ),
                  RadioListTile<AppUiLanguage>(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                    title: Text(lang.settingsOptionEnglish, style: const TextStyle(color: Colors.white)),
                    value: AppUiLanguage.en,
                    groupValue: lang,
                    activeColor: AppColors.accent,
                    onChanged: (v) {
                      if (v != null) _setUiLanguage(v);
                    },
                  ),
                  RadioListTile<AppUiLanguage>(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16),
                    title: Text(lang.settingsOptionSpanish, style: const TextStyle(color: Colors.white)),
                    value: AppUiLanguage.es,
                    groupValue: lang,
                    activeColor: AppColors.accent,
                    onChanged: (v) {
                      if (v != null) _setUiLanguage(v);
                    },
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),
          Text(
            str.settingsNotificationsSection,
            style: TextStyle(
              color: Colors.white.withOpacity(0.65),
              fontSize: 13,
              fontWeight: FontWeight.w600,
              letterSpacing: 0.5,
            ),
          ),
          const SizedBox(height: 12),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    str.danceReminderTitle,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 17,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    str.danceReminderSubtitle,
                    style: TextStyle(
                      color: Colors.white.withOpacity(0.72),
                      fontSize: 14,
                      height: 1.35,
                    ),
                  ),
                  const SizedBox(height: 16),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(str.danceReminderEnabled, style: const TextStyle(color: Colors.white)),
                    value: _enabled,
                    activeColor: AppColors.accent,
                    onChanged: _onEnabledChanged,
                  ),
                  if (_enabled) ...[
                    const Divider(color: AppColors.cardBorder),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(str.danceReminderTime, style: const TextStyle(color: Colors.white70)),
                      trailing: Text(
                        _timeLabel(),
                        style: const TextStyle(
                          color: AppColors.accent,
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      onTap: _pickTime,
                    ),
                    const SizedBox(height: 8),
                    Text(str.danceReminderFrequency, style: const TextStyle(color: Colors.white70)),
                    const SizedBox(height: 8),
                    ...DanceReminderMode.values.map((m) {
                      return RadioListTile<DanceReminderMode>(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(
                          switch (m) {
                            DanceReminderMode.daily => str.danceReminderModeDaily,
                            DanceReminderMode.weekdays => str.danceReminderModeWeekdays,
                            DanceReminderMode.weekly => str.danceReminderModeWeekly,
                          },
                          style: const TextStyle(color: Colors.white, fontSize: 15),
                        ),
                        value: m,
                        groupValue: _mode,
                        activeColor: AppColors.accent,
                        onChanged: (v) {
                          if (v != null) _setMode(v);
                        },
                      );
                    }),
                    if (_mode == DanceReminderMode.weekly) ...[
                      const SizedBox(height: 8),
                      Text(str.danceReminderWeekday, style: const TextStyle(color: Colors.white70)),
                      const SizedBox(height: 8),
                      DropdownButtonFormField<int>(
                        value: _weekday,
                        dropdownColor: AppColors.card,
                        decoration: const InputDecoration(
                          filled: true,
                          fillColor: AppColors.card,
                        ),
                        items: [
                          for (var d = DateTime.monday; d <= DateTime.sunday; d++)
                            DropdownMenuItem(
                              value: d,
                              child: Text(str.weekdayLong(d), style: const TextStyle(color: Colors.white)),
                            ),
                        ],
                        onChanged: (v) {
                          if (v != null) _setWeekday(v);
                        },
                      ),
                    ],
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
