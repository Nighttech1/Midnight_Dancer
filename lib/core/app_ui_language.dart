import 'package:flutter/material.dart';
import 'package:midnight_dancer/core/app_flavor.dart';

/// Язык интерфейса (хранится в [AppData.settings], ключ [storageKey]).
enum AppUiLanguage {
  ru,
  en,
  es;

  static const String storageKey = 'ui_language';

  static const List<Locale> materialLocales = [
    Locale('ru'),
    Locale('en'),
    Locale('es'),
  ];

  String get code => switch (this) {
        AppUiLanguage.ru => 'ru',
        AppUiLanguage.en => 'en',
        AppUiLanguage.es => 'es',
      };

  Locale get materialLocale => Locale(code);

  /// Подписи для экрана настроек (на языке интерфейса).
  String get settingsSectionTitle => switch (this) {
        AppUiLanguage.ru => 'Язык приложения',
        AppUiLanguage.en => 'App language',
        AppUiLanguage.es => 'Idioma de la app',
      };

  String get settingsOptionRussian => switch (this) {
        AppUiLanguage.ru => 'Русский',
        AppUiLanguage.en => 'Russian',
        AppUiLanguage.es => 'Ruso',
      };

  String get settingsOptionEnglish => switch (this) {
        AppUiLanguage.ru => 'Английский',
        AppUiLanguage.en => 'English',
        AppUiLanguage.es => 'Inglés',
      };

  String get settingsOptionSpanish => switch (this) {
        AppUiLanguage.ru => 'Испанский',
        AppUiLanguage.en => 'Spanish',
        AppUiLanguage.es => 'Español',
      };

  Map<String, dynamic> toSettingsEntry() => {storageKey: code};

  static AppUiLanguage fromCode(String? raw) {
    switch (raw) {
      case 'en':
        return AppUiLanguage.en;
      case 'es':
        return AppUiLanguage.es;
      case 'ru':
      default:
        return AppUiLanguage.ru;
    }
  }

  static AppUiLanguage fromSettings(Map<String, dynamic> settings) {
    final v = settings[storageKey];
    if (v is! String) return fallbackForFlavor();
    return fromCode(v);
  }

  /// Пока нет записи в настройках: как раньше — сборка english давала английский UI.
  static AppUiLanguage fallbackForFlavor() {
    if (AppFlavor.isEnglish) return AppUiLanguage.en;
    return AppUiLanguage.ru;
  }
}
