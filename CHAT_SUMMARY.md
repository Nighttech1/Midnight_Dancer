# Summary for new chat — Midnight Dancer

## Что за проект
**Midnight Dancer** — Flutter-приложение для тренировки танцев (элементы, музыка, хореография, режим тренировки с TTS). Есть четыре flavor: **lite**, **standard**, **full**, **english**.

## Что сделано в этом диалоге: полная английская локализация

Для сборки с **flavor english** весь пользовательский текст переведён на английский.

### Механика
- **Определение языка:** `AppFlavor.isEnglish` (из `String.fromEnvironment('FLAVOR') == 'english'`). Язык задаётся на этапе сборки, не переключается в рантайме.
- **Строки:** все UI-строки вынесены в `lib/core/app_strings.dart`. Getter'ы вида `_en ? 'English' : 'Русский'`. В экранах и виджетах везде используется `AppStrings.*`.
- **Сборка английской версии:**  
  `flutter build apk --flavor english --release --dart-define=FLAVOR=english`  
  или скрипт `.\scripts\build_apks.ps1` (четыре APK, в т.ч. `app-english-release.apk`).
- **Запуск на устройстве (debug):**  
  `flutter run --flavor english --dart-define=FLAVOR=english`

### Что локализовано
- Навигация, хореография (список, создание, копия, переименование, удаление, диалоги).
- Редактор последовательности (точки, время, движение, подсказки).
- Тренировка: заголовок, режимы (Freestyle/Choreography), стиль, музыка, уровень элементов, интервал, диапазон трека, голос (в т.ч. имена Ruslan/Irina/Kamila через `AppStrings.voiceDisplayName(id)`), тест голоса (фраза "One, two, three"), скорость речи, приглушение музыки, «Нет хореографий», суффикс « sec»/« с».
- Экраны: Элементы (форма элемента, стили, поиск, сортировка/фильтры, диалоги стиля и элемента), Музыка (заголовок, фильтры, диалоги трека, «Нет треков»), экран сессии тренировки (громкость музыки/голоса, «Закончить»).
- Виджеты: MoveCard (уровень через `AppStrings.levelLabelFor`), VideoPreview (ошибка загрузки, скорость, «Preview on web»).
- В `AppStrings` добавлены хелперы: `timeSecLabel(n)`, `levelLabelFor(value)`, `voiceDisplayName(id)`, `deleteTrackMessage(name)`, `copiedSnackbar`, `renamedSnackbar`, `saveErrorSnackbar`, `playErrorSnackbar` и др. Для `catch (e)` передаётся `e.toString()`.

### Важные файлы
- `lib/core/app_strings.dart` — все RU/EN строки.
- `lib/core/app_flavor.dart` — `isEnglish`, `isFull`, и т.д.
- `lib/app.dart` — для english задаётся `locale: Locale('en')`.
- Экраны: `choreography_screen.dart`, `trainer_screen.dart`, `trainer_session_screen.dart`, `elements_screen.dart`, `music_screen.dart`, `sequence_editor_screen.dart`.
- Виджеты: `move_card.dart`, `video_preview_io.dart`, `video_preview_stub.dart`.
- TTS: в UI имена голосов показываются через `AppStrings.voiceDisplayName(v.id)`, не через `v.displayName`.

### Замечания
- Линтер: убраны `const` у виджетов с `AppStrings`; в снэкбарах ошибок используется `e.toString()`.
- В `BUILD.md` описаны подпись, flavor и команды сборки.

## Как продолжить
- Новые экраны/строки: добавлять getter в `AppStrings` и использовать его в UI, чтобы в english-сборке не оставалось русского текста.
- Для проверки английской версии на телефоне: `flutter run --flavor english --dart-define=FLAVOR=english` (устройство по USB с отладкой).
