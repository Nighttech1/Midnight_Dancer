# Midnight Dancer

Flutter приложение для тренировки танцев с голосовым ассистентом.

## Требования

- Flutter SDK 3.0+
- Dart 3.0+

## Установка

1. Убедись что Flutter в PATH:
   ```bash
   flutter doctor
   ```

2. Установи зависимости:
   ```bash
   flutter pub get
   ```

3. Запуск: в Cursor/VS Code выбери конфигурацию Run (Lite / Standard / Full). Или из терминала:
   ```bash
   flutter run --flavor lite --dart-define=FLAVOR=lite
   flutter run --flavor standard --dart-define=FLAVOR=standard
   flutter run --flavor full --dart-define=FLAVOR=full
   ```

4. Если иконка не показывается: `flutter clean` и `flutter pub get`, затем пересобери.

5. Ошибка assembleStandartDebug: в Run Configuration должно быть `--flavor standard` (с буквой **a**, не standart).

6. Без лагов (profile): `flutter run --profile --flavor lite --dart-define=FLAVOR=lite`

## Структура

- `lib/main.dart` - точка входа
- `lib/app.dart` - корневой виджет
- `lib/core/` - тема, константы, утилиты
- `lib/data/` - модели, сервисы
- `lib/ui/` - экраны и виджеты
- `old_app_midnightdancer/` - референс (оригинальное Capacitor приложение)

## Хранение данных

Данные приложения (метаданные, музыка, видео) сохраняются локально. Папка создаётся по пути `getApplicationDocumentsDirectory()/MidnightDancer/` — это **приватное хранилище приложения**. Через обычный файловый менеджер на телефоне её не видно (нет root-доступа). Проверить можно через Android Studio → Device File Explorer: `data/data/com.midnightdancer.app/app_flutter/MidnightDancer/`.

## Референс

Оригинальное приложение: папка `old_app_midnightdancer/`
