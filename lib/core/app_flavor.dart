/// Определяет текущий flavor приложения.
/// Задаётся через --dart-define=FLAVOR=lite|standard|full|english при запуске.
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

  static String get name => _flavor;
}
