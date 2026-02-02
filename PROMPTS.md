# Промты для разработки Midnight Dancer (Flutter)

Этот файл содержит промты для каждого шага разработки. Копируй нужный блок и отправляй AI. После каждого шага — проверь по чеклисту (Android Studio + браузер где указано).

**План по видео:** хранение по ссылке без дублирования — см. `.cursor/plans/reference-only_video_storage*.plan.md` и Шаг 6а.

---

## Шаг 0: Подготовка

**Промт:**
```
Создай Flutter-проект "Midnight Dancer" в текущей папке. Структура по плану в .cursor/plans. Добавь pubspec.yaml с зависимостями: flutter_riverpod, path_provider, hive_flutter, just_audio, video_player, file_picker, json_serializable, freezed, go_router. Референс: папка old/ содержит оригинальное Capacitor-приложение.
```

**Мой чеклист (Checkpoint 1):**
- [x] В терминале: `flutter run` — приложение запускается
- [x] В Android Studio: Run на подключённый телефон — экран (тёмный фон)
- [x] Logcat: нет красных ошибок

---

## Шаг 1: Flavors + Splash + Оптимизация

**Промт:**
```
Настрой 3 Flutter flavors (lite/standard/full), добавь splash screen с иконкой приложения при загрузке, уменьши лаги. AppFlavor.dart, product flavors в build.gradle, иконка в assets.
```

**Мой чеклист:**
- [x] `flutter run --flavor lite` — запускается
- [x] `flutter run --flavor standard` — запускается  
- [x] `flutter run --flavor full` — запускается

---

## Шаг 2: Модели данных

**Промт:**
```
Создай модели данных для Midnight Dancer с freezed и json_serializable:
- DanceStyle (id, name, moves: List<Move>)
- Move (id, name, level: Beginner/Intermediate/Advanced, description?, videoUri?) — videoUri: content:// или путь к файлу, без копирования в папку приложения
- Song (id, title, danceStyle, level, fileName, duration, sizeBytes)
- Choreography (id, name, songId, styleId, timeline: Map<double,String>, startTime, endTime)
- AppData (danceStyles, songs, choreographies, settings) — корневая модель для metadata.json
Добавь fromJson/toJson для сериализации в JSON. В fromJson для Move: обратная совместимость с videoFileName и videoRef.
```

**Мой чеклист:**
- [x] Модели компилируются
- [x] Приложение запускается как на шаге 1 (Cursor: всё ок, Android Studio: пустой экран без иконки)

---

## Шаг 3: Тема

**Промт:**
```
Настрой тёмную тему для Midnight Dancer: фон #0f172a (slate-900), акцент #f97316 (orange-500). Используй ThemeData в MaterialApp. Rounded corners 24–40px. Шрифт Inter. Добавь AppColors и AppTheme в lib/core/theme/.
```

**Мой чеклист:**
- [x] Приложение с тёмным фоном
- [x] Оранжевые акценты видны

---

## Шаг 4: Навигация

**Промт:**
```
Реализуй адаптивную навигацию: на mobile — BottomNavigationBar внизу с 4 вкладками (Элементы, Музыка, Хореография, Тренировка). На tablet/desktop — NavigationRail слева. Используй MediaQuery для определения ширины. 4 пустых экрана-заглушки. Иконки: список, музыка, танцор, молния.
```

**Мой чеклист (Checkpoint 2):**
- [x] Телефон: нижняя навигация, 4 иконки, переключение по тапу
- [x] Тёмный фон, оранжевый акцент на активной вкладке
- [x] Браузер: `flutter run -d chrome` — навигация работает

**На следующую итерацию:** в браузерной версии показывать иконку приложения в левом верхнем углу.

---

## Шаг 5: StorageService (JSON + Fallback)

**Промт:**
```
Реализуй StorageService: 
1) Основной режим: path_provider + JSON файл metadata.json в папке MidnightDancer (getApplicationDocumentsDirectory). Структура: metadata.json, music/. Папку videos/ не создавать — видео хранятся по ссылке (см. Шаг 6а).
2) Fallback: если kIsWeb или нет доступа к файлам — Hive (IndexedDB). Определение режима: useFilesystem = !kIsWeb && (Platform.isAndroid || Platform.isIOS).
Методы: loadAppData(), saveAppData(AppData), saveMediaFile(id, bytes, type), loadMediaFile(id, type), deleteMediaFile(id) — для music. Для видео — только metadata (videoUri в Move).
Логи: "StorageService: using filesystem mode" или "StorageService: using Hive fallback".
```

**Мой чеклист (Checkpoint 3):**
- [x] Logcat: "StorageService: using filesystem mode"
- [x] Папка MidnightDancer создаётся при сохранении

---

## Шаг 6: Экран Elements

**Промт:**
```
Реализуй экран Elements по плану и референсу в old/index.html: выпадающий список стилей, кнопка "Новый стиль", список карточек движений. CRUD: добавление/редактирование/удаление стилей и движений. Для движения: название, уровень (Начинающий/Средний/Профи), описание, видео (file_picker). VideoPreview с контролем скорости 0.5x–1.5x. Фильтр по уровню, поиск по названию, сортировка. Карточка MoveCard с видео-превью (автовоспроизведение при наведении/hover).
```

**Мой чеклист (Checkpoint 4):**
- [ ] Создать стиль "Сальса"
- [ ] Добавить движение, загрузить видео — file picker открывается
- [ ] Видео-превью в карточке
- [ ] Удаление движения работает
- [ ] Фильтр, поиск работают
- [ ] Перезапуск — данные на месте

---

## Шаг 6а: Видео по ссылке (без дублирования)

**Промт:**
```
Переработай загрузку видео по плану reference-only: не копировать файлы в папку приложения, хранить только ссылку (URI или путь). Референс: .cursor/plans/reference-only_video_storage.

1) Move: заменить videoFileName на videoUri (String?). fromJson — обратная совместимость с videoFileName, videoRef. videoUri хранит content:// или /storage/... путь.

2) MainActivity.kt: добавить метод takeUriPermission(uri) в MethodChannel. Обернуть все вызовы в try/catch, гарантировать result.success/error во всех ветках (устранение JNI-краша). Убрать copyToCache или оставить только для preview при необходимости.

3) AppDataNotifier: addMove/updateMove — не вызывать saveMediaFileFromPath/saveMediaFile для видео. Сохранять только videoUri в Move. deleteStyle/deleteMove — не вызывать deleteMediaFile для видео. Добавить clearVideoForMove(styleId, moveId) для обновления move при недоступности файла.

4) _pickVideo: withData: false на мобильных. Сохранять file.path, при content:// вызывать takeUriPermission. Передавать URI/путь в onSave как videoPath. Не вызывать copyPickedFileToCache.

5) MoveCard: убрать loadVideo и video_temp. Передавать move.videoUri напрямую в MoveCardVideo. При отсутствии videoUri — "Нет превью".

6) MoveCardVideo: если videoUri начинается с content:// — VideoPlayerController.contentUri(Uri.parse(...)); иначе VideoPlayerController.file(File(...)). При ошибке инициализации — callback clearVideoForMove, карточку оставить.

7) main.dart: обернуть StorageService.instance.init() в try/catch.
```

**Мой чеклист (Checkpoint 4а):**
- [ ] Выбрать видео из Downloads — сохраняется правильное видео
- [ ] Видео воспроизводится в карточке (tap, hover)
- [ ] Перезапуск приложения — приложение открывается, видео на месте
- [ ] Удалить файл из Downloads — карточка остаётся, превью "Нет превью"
- [ ] Logcat: нет JNI ExceptionCheck, нет FATAL

---

## Шаг 7: Экран Music

**Промт:**
```
Реализуй экран Music: загрузка аудио (file_picker, mp3/m4a/wav), список треков с названием, стилем, уровнем, длительностью, размером. Проигрывание через just_audio. Кнопки Play/Pause, редактирование метаданных, удаление. Фильтры по стилю и уровню. Сохранение файлов через StorageService.
```

**Мой чеклист (Checkpoint 5):**
- [ ] Загрузить MP3 с телефона
- [ ] Play — музыка играет, Pause — останавливается
- [ ] Редактирование метаданных
- [ ] Удаление трека
- [ ] Перезапуск — треки сохранены

---

## Шаг 8: Экран Choreography

**Промт:**
```
Реализуй экран Choreography: список хореографий, кнопка "Создать". При создании — выбор стиля и музыки. SequenceEditor: timeline по горизонтали (длительность трека), zoom 25–400%, точки с названиями движений. Добавление точки (время + движение), редактирование, удаление. Обрезка диапазона музыки (start/end в секундах). Drag точек для перемещения. Копирование хореографии.
```

**Мой чеклист (Checkpoint 6):**
- [ ] Создать хореографию (стиль + музыка)
- [ ] Timeline открывается
- [ ] Добавить точку на 5 сек, привязать движение
- [ ] Zoom работает
- [ ] Сохранить — хореография в списке
- [ ] Копировать хореографию
- [ ] Удалить хореографию

---

## Шаг 9: TTS (Sherpa-ONNX)

**Промт:**
```
Интегрируй Sherpa-ONNX для офлайн TTS. Создай TtsService с поддержкой 3 голосов: ruslan, irina, kamila. Методы: initVoice(Voice), speak(text, speed, pitch), stop(). availableVoices зависит от flavor (lite: ruslan, standard: ruslan+irina, full: все). Модели в assets/voices/{flavor}/{voice}/. Прогресс-бар при загрузке модели. Если sherpa_onnx недоступен — fallback на flutter_tts (системный).
```

**Мой чеклист (Checkpoint 7):**

**Каждый flavor:**
```bash
flutter run --flavor lite
flutter run --flavor standard  
flutter run --flavor full
```

- [ ] Модель загружается (прогресс-бар)
- [ ] "Тест голоса" → "Раз, два, три"
- [ ] Скорость/тональность меняют голос
- [ ] Режим полёта — голос работает офлайн

**Standard/Full:**
- [ ] Выбор голоса в настройках (Руслан/Ира, + Камила для Full)

---

## Шаг 10: Экран Trainer

**Промт:**
```
Реализуй экран Trainer: два режима (Случайный / Хореография). Настройки: стиль, музыка, голос (из TtsService.availableVoices), ducking (приглушение музыки при речи), интервал (2–30 сек для случайного), уровень (Все/Начинающий/Средний/Профи), диапазон трека. Кнопка "Начать" → обратный отсчёт 3-2-1 → тренировка. Случайный: каждые N сек голос называет случайное движение. Хореография: голос по timeline. Регуляторы громкости музыки и голоса. Кнопка "Стоп". Прогресс-бар для режима хореографии.
```

**Мой чеклист (Checkpoint 8):**

**Случайный режим:**
- [ ] Стиль, музыка, интервал 5 сек
- [ ] "Начать" → 3-2-1 → музыка + голос каждые 5 сек
- [ ] Регуляторы громкости
- [ ] Ducking при речи
- [ ] "Стоп" останавливает всё

**Режим хореографии:**
- [ ] Выбрать хореографию → "Начать"
- [ ] Голос по timeline
- [ ] Прогресс-бар
- [ ] Конец трека — остановка

---

## Шаг 11: Интеграционный тест

**Промт:**
```
Проверь приложение на полноту: все экраны связаны, данные сохраняются, нет утечек. Добавь недостающие обработки ошибок и логи для отладки. Убедись что fallback Hive работает в браузере.
```

**Мой чеклист (Checkpoint 9):**

**Полный сценарий на телефоне:**
- [ ] Очистить данные приложения
- [ ] Создать стиль "Бачата"
- [ ] Добавить 5 движений с видео (из Downloads, без дублирования)
- [ ] Загрузить 2 трека
- [ ] Создать хореографию с 10 точками
- [ ] Запустить тренировку в режиме хореографии
- [ ] Дождаться окончания
- [ ] Перезапуск телефона → всё на месте

**Браузер:** `flutter run -d chrome`
- [ ] Fallback Hive: добавить стиль/трек → F5 → данные сохранились

---

## Шаг 12: Сборка APK

**Промт:**
```
Подготовь к сборке: обнови android/app/build.gradle (versionCode, versionName), убедись что все 3 flavor-а собираются. Создай скрипт или инструкцию для сборки трёх APK.
```

**Мой чеклист (Checkpoint 10):**

```bash
flutter build apk --flavor lite --release
flutter build apk --flavor standard --release
flutter build apk --flavor full --release
```

- [ ] APK в build/app/outputs/flutter-apk/
- [ ] Lite ~20 MB, Standard ~35 MB, Full ~50 MB
- [ ] Установить на другой телефон — всё работает

---

## Шаг 13: Деплой на хостинг

**Промт:**
```
Создай простую HTML-страницу index.html для скачивания APK: заголовок Midnight Dancer, три ссылки на midnight-dancer-lite.apk, midnight-dancer-standard.apk, midnight-dancer-full.apk с описанием (Lite 20MB, Standard 35MB, Full 50MB). Минимальный стиль.
```

**Мой чеклист:**
- [ ] Загрузить 3 APK и index.html на сервер
- [ ] Открыть URL в браузере — страница со ссылками
- [ ] Скачать APK на телефон — установка успешна

---

## Шаг 14: Firebase (опционально, для iOS)

**Промт:**
```
Настрой Firebase App Distribution для Midnight Dancer. Добавь firebase_core и firebase_app_check в pubspec. Создай android/app/google-services.json и ios/Runner/GoogleService-Info.plist (placeholder). Инструкция по загрузке APK в Firebase Console.
```

---

## Быстрые команды для проверки

| Действие | Команда |
|----------|---------|
| Запуск на телефоне | `flutter run` или `flutter run --flavor lite` |
| Запуск в браузере | `flutter run -d chrome` |
| Список устройств | `flutter devices` |
| Логи | `flutter logs` или Logcat в Android Studio |
| Сборка APK | `flutter build apk --flavor lite --release` |
| Проверка кода | `flutter analyze` |

---

## Структура референса

Оригинальное приложение: папка `old/`
- `old/index.html` — основной код (React, логика, UI)
- `old/tts-worker.js` — TTS через Sherpa-ONNX WASM
- `old/voices/` — модели голосов
