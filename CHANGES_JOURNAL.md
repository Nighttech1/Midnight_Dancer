# Журнал изменений (Change log)

Файл ведётся ассистентом: здесь фиксируется **что именно было изменено** (файлы и суть правок) и **какие ошибки/баги/недочёты** на каком этапе вылезли или были исправлены. По нему можно понять, что откатить или восстановить, если что-то пошло не так.

---

## Ошибки / баги / недочёты (фронтенд и сборки)

Ниже — что на каком этапе проявилось и что уже исправлено. Статусы: **обнаружена** → **исправлена** (или «не воспроизводится» / «окружение»).

| Этап | Дата       | Описание | Статус | Файлы / действие |
|------|------------|----------|--------|------------------|
| Lite (debug) | до 08.02.2026 | «No materialLocalizations found» на экранах Музыка, Хореография, Тренировка; падение при «+ новый стиль» в Элементах | **Исправлена** 08.02.2026 | Шаг 1: `pubspec.yaml`, `lib/app.dart` — подключены `flutter_localizations` и `localizationsDelegates` |
| Standard (debug) | до 08.02.2026 | `copyFlutterAssetsStandardDebug` → IOException при копировании голосов (ru_RU-irina-medium.onnx) | **Исправлена** 08.02.2026 | Окружение: `gradlew --stop`, `flutter clean`, `flutter pub get` (без правок кода) |
| Standard (debug) | 08.02.2026 | В терминале: VM service socket (Operation not permitted), FlutterJNI detached | **Не баг** | Не влияет на работу приложения; отрабатывать в коде не требуется |
| Full (release) | при необходимости | «execution history cache already locked» (Gradle) | План есть (шаг 3) | Окружение: `gradlew --stop`, при повторе — удалить `android\.gradle\8.7\executionHistory`, затем `flutter clean` |
| Full (debug) | 08.02.2026 | Сборка открылась и работает; размер APK **428 МБ** — из‑за дублирования голосов (см. ниже) | **Работает** | Отметить в плане шаг 3/4; уменьшение размера — через правку `pubspec.yaml` или только release через скрипт |
| English (debug) | 08.02.2026 23-00| Проверка после Lite/Standard/Full | **Запускается хорошо** | Интерфейс на английском, основные экраны работают. |
| Релизные сборки (Lite, Standard, Full, English) | 09.02.2026 0:02 МСК | Проверка перед RuStore | **Все работают** | Скрипт `build_apks.ps1`; установка и проверка всех четырёх APK. Начата загрузка в RuStore. |

**Дубликаты голосов и размер сборки (428 МБ в debug):**

В `pubspec.yaml` в `flutter.assets` перечислены **все** папки голосов сразу: lite, standard, full. В бандл попадают одни и те же файлы по несколько раз:

| Файл / голос | Где дублируется | Итог |
|--------------|------------------|------|
| Ruslan (`ru_RU-ruslan-medium.onnx` + `tokens.txt`) | `lite/ruslan/`, `standard/ruslan/`, `full/ruslan/` | **3 копии** |
| Irina (`ru_RU-irina-medium.onnx` + `tokens.txt`) | `standard/irina/`, `full/irina/` | **2 копии** |
| Kamila | только `full/kamila/` | 1 копия |
| espeak-ng-data | один раз | без дубликатов |

Поэтому debug-сборка (особенно full) весит ~428 МБ. **Release-сборки** дубликатов не имеют: скрипт `.\scripts\build_apks.ps1` перед каждой сборкой подставляет в `pubspec.yaml` только голоса нужного flavor и собирает APK — в каждый release-APK попадают только свои голоса.

**Когда снова запускать сборку после «удаления лишнего»:**

1. **Если делаешь только release (Lite/Standard/Full/English):** ничего в `pubspec.yaml` не трогай. Запускай сборку так: из корня `Midnight_Dancer` выполнить `.\scripts\build_apks.ps1`. Скрипт сам подставит нужные голоса и вернёт `pubspec` обратно; дубликатов в APK не будет. После скрипта отдельно перезапускать сборку не нужно — он уже собирает все четыре APK.

2. **Если хочешь уменьшить размер именно debug Full:** в `pubspec.yaml` в блоке `flutter.assets` оставить только голоса full и espeak (удалить или закомментировать строки с `assets/voices/lite/...` и `assets/voices/standard/...`). Затем выполнить:
   - `flutter clean`
   - `flutter pub get`
   - снова запустить сборку: `flutter run --flavor full --dart-define=FLAVOR=full` (или нужный тебе flavor).
   Тогда в debug попадёт только один набор голосов (full + espeak), размер уменьшится. **Важно:** debug для flavor **lite** и **standard** тогда перестанут иметь свои голоса в бандле (пока не вернёшь строки в pubspec). Если нужны все flavor в debug — лучше оставить как есть (428 МБ) и для «лёгких» сборок использовать только release через скрипт.

**Запланировано к исправлению (этап 5, после стабилизации сборок):**

- **Локализация (EN):** хардкод «Ошибка: $e», «Удалить элемент?» / «Вы собираетесь удалить…» в `elements_screen.dart`; «Нет данных трека» в `music_screen.dart`; « с» в `trainer_screen.dart` — заменить на `AppStrings.*`.
- **Линт:** неиспользуемый метод `_buildMovesGrid` в `elements_screen.dart` — удалить.
- **Опционально:** устаревший `activeColor` у Slider/Switch в `trainer_screen.dart`, `trainer_session_screen.dart` — заменить на `activeTrackColor`/`thumbColor` при обновлении SDK.

При появлении новых багов или после исправления пунктов из этапа 5 — добавлять строки в таблицу выше и помечать статус.

---

## Формат записей изменений кода

- **Дата** (и при необходимости время/контекст)
- **Файлы** — список изменённых файлов
- **Что сделано** — краткое описание
- **Как откатить** — при необходимости (например, удалить блок, вернуть строку)

---

## 2026-02-08 — Шаг 1 плана: MaterialLocalizations (Lite)

**Файлы:**
- `pubspec.yaml`
- `lib/app.dart`

**Что сделано:**
1. **pubspec.yaml** — в `dependencies` добавлена зависимость:
   ```yaml
   flutter_localizations:
     sdk: flutter
   ```
   (сразу после `flutter: sdk: flutter`.)

2. **lib/app.dart** — добавлено:
   - импорт: `import 'package:flutter_localizations/flutter_localizations.dart';`
   - в `MaterialApp` добавлено свойство `localizationsDelegates`:
     ```dart
     localizationsDelegates: const [
       GlobalMaterialLocalizations.delegate,
       GlobalWidgetsLocalizations.delegate,
       GlobalCupertinoLocalizations.delegate,
     ],
     ```
   Свойства `locale`, `supportedLocales` не трогались.

**Зачем:** убрать «No materialLocalizations found» и падение при «+ новый стиль» в Lite.

**Как откатить:**  
- В `pubspec.yaml` удалить блок `flutter_localizations` (строки с `flutter_localizations:` и `sdk: flutter`).  
- В `lib/app.dart` удалить импорт `flutter_localizations` и блок `localizationsDelegates` (и запятую после `theme`, если она останется лишней). Затем выполнить `flutter pub get`.

---

## 2026-02-08 — Правки отображения перед релизом (Элементы, Хореография, Тренировка)

**Файлы:**
- `lib/core/theme/app_theme.dart`
- `lib/ui/screens/choreography/choreography_screen.dart`
- `lib/core/app_strings.dart`
- `lib/ui/screens/trainer/trainer_screen.dart`

**Что сделано:**

1. **Элементы — закруглённые углы у всех полей в окне «Добавить элемент»**  
   В теме не было `outlinedButtonTheme`, поэтому кнопки «Выбрать видео» и «Отмена» (OutlinedButton) отображались с дефолтными прямоугольными углами. В `app_theme.dart` добавлен `outlinedButtonTheme` с `shape: RoundedRectangleBorder(borderRadius: AppRadius.radiusMd)` и `side: BorderSide(color: AppColors.cardBorder)`. Теперь все OutlinedButton в приложении (в т.ч. в форме элемента) с закруглёнными углами, как остальные поля.

2. **Хореография — кнопка «Создать» в базовом стиле приложения**  
   В диалогах «Новая хореография» и переименования использовался `FilledButton` (другой стиль). Заменён на `ElevatedButton` в обоих местах (кнопки «Создать» и «Сохранить» в диалоге переименования), чтобы использовался `elevatedButtonTheme` (accent, скругление) как в остальном приложении.

3. **Тренировка (фристайл) — поле «Конец» без текста в скобках**  
   В настройках диапазона трека подпись поля была «Конец (0=всё)» / «End (0=all)». В `app_strings.dart` добавлен `trackEndLabel` («Конец» / «End»). В `trainer_screen.dart` у поля «Конец» задано `labelText: AppStrings.trackEndLabel`, `hintText: ''` — на экране отображается только «Конец» (без скобок и подсказки).

**Как откатить:**
- **app_theme.dart:** удалить весь блок `outlinedButtonTheme: OutlinedButtonThemeData(...)` (и запятую после `elevatedButtonTheme`).
- **choreography_screen.dart:** в двух местах заменить `ElevatedButton` обратно на `FilledButton` (диалог создания хореографии и диалог переименования).
- **app_strings.dart:** удалить строку `static String get trackEndLabel => ...`.
- **trainer_screen.dart:** в декорации поля «Конец» вернуть `labelText: AppStrings.trackEndHint`, `hintText: AppStrings.trackEndHint`.

---

*Далее новые правки добавляются ниже этой секции.*
