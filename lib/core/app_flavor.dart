/// Определяет текущий flavor приложения.
/// Задаётся через --dart-define=FLAVOR=lite|standard|full|english|playverify при запуске.
class AppFlavor {
  AppFlavor._();

  static const String _flavor = String.fromEnvironment(
    'FLAVOR',
    defaultValue: 'lite',
  );

  static bool get isLite => _flavor == 'lite';
  static bool get isStandard => _flavor == 'standard';
  static bool get isFull => _flavor == 'full';
  /// Английская сборка: интерфейс на английском, два голоса (Kamila, Ruslan).
  static bool get isEnglish => _flavor == 'english';
  /// Только Android: тот же [applicationId], что у full, но APK только arm64-v8a (для проверки в Play Console).
  static bool get isPlayverify => _flavor == 'playverify';

  /// Три голоса Piper: только [isFull]. Вариант playverify пакует один голос для маленького APK.
  static bool get hasFullVoiceSet => isFull;

  static String get name => _flavor;

  /// Папка под ассеты Piper: playverify использует те же файлы, что [full].
  static String get voiceAssetsFlavor =>
      isEnglish ? 'full' : (isPlayverify ? 'full' : name);
}
