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
- [x] Создать стиль "Сальса"
- [x] Добавить движение, загрузить видео — file picker открывается
- [x] Видео-превью в карточке
- [x] Удаление движения работает
- [x] Фильтр, поиск работают
- [x] Перезапуск — данные на месте

✅ Шаг выполнен.

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
- [x] Выбрать видео из Downloads — сохраняется правильное видео
- [x] Видео воспроизводится в карточке (tap, hover)
- [x] Перезапуск приложения — приложение открывается, видео на месте
- [x] Удалить файл из Downloads — карточка остаётся, превью "Нет превью"
- [x] Logcat: нет JNI ExceptionCheck, нет FATAL

✅ Шаг выполнен.

---

## Шаг 6.1: Корректировка предупреждений сборки (Java 8, Gradle/Android)

**Промт:**
```
Устрани предупреждения сборки при flutter run (assembleLiteDebug):

1) В корневом android/build.gradle принудительно задай для всех подпроектов (subprojects) Java 17: в блоке subprojects { subproject -> subproject.afterEvaluate { ... } } для модулей с плагином com.android.library или com.android.application выставляй android.compileOptions (sourceCompatibility и targetCompatibility = JavaVersion.VERSION_17) и для KotlinCompile — kotlinOptions.jvmTarget = "17". Цель — убрать предупреждения "source value 8 is obsolete" / "target value 8 is obsolete" из плагинов (например audio_session). Не меняй android/app/build.gradle — там уже Java 17.

2) Предупреждения KGP (Kotlin Gradle Plugin) и "SDK XML version 4" носят информационный характер; не меняй android/settings.gradle и версии KGP без необходимости — они решаются обновлением стека/инструментов при необходимости.
```

**Мой чеклист:**
- [x] `flutter run --flavor lite --dart-define=FLAVOR=lite` — сборка без ошибок
- [x] В логе сборки нет (или заметно меньше) предупреждений «source value 8 is obsolete» / «target value 8 is obsolete»
- [x] Приложение запускается на устройстве/эмуляторе как раньше

✅ Шаг выполнен.

---

## Шаг 7: Экран Music

**Промт:**
```
Реализуй экран Music: загрузка аудио (file_picker, mp3/m4a/wav), список треков с названием, стилем, уровнем, длительностью, размером. Проигрывание через just_audio. Кнопки Play/Pause, редактирование метаданных, удаление. Фильтры по стилю и уровню. Сохранение файлов через StorageService.
```

**Мой чеклист (Checkpoint 5):**
- [x] Загрузить MP3 с телефона
- [x] Play — музыка играет, Pause — останавливается
- [x] Редактирование метаданных
- [x] Удаление трека
- [x] Перезапуск — треки сохранены

✅ Шаг выполнен.

---

## Шаг 8: Экран Choreography

**Промт:**
```
Реализуй экран Choreography: список хореографий, кнопка "Создать". При создании — выбор стиля и музыки. SequenceEditor: timeline по горизонтали (длительность трека), zoom 25–400%, точки с названиями движений. Добавление точки (время + движение), редактирование, удаление. Обрезка диапазона музыки (start/end в секундах). Drag точек для перемещения. Копирование хореографии.
```

**Мой чеклист (Checkpoint 6):**
- [x] Создать хореографию (стиль + музыка)
- [x] Timeline открывается
- [x] Добавить точку на 5 сек, привязать движение
- [x] Zoom работает
- [x] Сохранить — хореография в списке
- [x] Копировать хореографию
- [x] Удалить хореографию

✅ Шаг выполнен.

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

- [x] Модель загружается (прогресс-бар)
- [x] "Тест голоса" → "Раз, два, три"
- [x] Скорость меняет голос (тональность убрана)
- [ ] Режим полёта — голос работает офлайн (проверить вручную)

**Standard/Full:**
- [x] Выбор голоса в экране Trainer (Руслан/Ирина, + Камила для Full)

✅ Шаг выполнен (TtsService, Sherpa-ONNX + fallback flutter_tts, прогресс загрузки, тест в Trainer).

---

## Шаг 10: Экран Trainer

✅ **Шаг выполнен.** Реализованы режимы Фристайл и Хореография, настройки (стиль, музыка, голос, ducking, интервал 0.5–30 сек, уровень, диапазон трека), обратный отсчёт 3-2-1, отдельный экран сессии с карточками и громкостями, предзагрузка музыки, остановка при выходе.

**Промт:**
```
Реализуй экран Trainer: два режима (Случайный / Хореография). Настройки: стиль, музыка, голос (из TtsService.availableVoices), ducking (приглушение музыки при речи), интервал (2–30 сек для случайного), уровень (Все/Начинающий/Средний/Профи), диапазон трека. Кнопка "Начать" → обратный отсчёт 3-2-1 → тренировка. Случайный: каждые N сек голос называет случайное движение. Хореография: голос по timeline. Регуляторы громкости музыки и голоса. Кнопка "Стоп". Прогресс-бар для режима хореографии.
```

**Мой чеклист (Checkpoint 8):**

**Случайный режим (Фристайл):**
- [x] Стиль, музыка, интервал (шаг 0.5 сек)
- [x] "Танцуем!" → 3-2-1 → музыка + голос сразу после "1"
- [x] Регуляторы громкости (на экране сессии)
- [x] Ducking при речи
- [x] "Закончить" / назад — останавливает всё

**Режим хореографии:**
- [x] Выбрать хореографию → "Танцуем!"
- [x] Голос по timeline
- [x] Прогресс по треку (карточка + громкости)
- [x] Конец трека — возврат в раздел тренировки

---

## Шаг 11: Интеграционный тест

✅ **Шаг выполнен.** Приложение проверено на полноту, экраны связаны, данные сохраняются. Fallback Hive в браузере работает.

**Промт:**
```
Проверь приложение на полноту: все экраны связаны, данные сохраняются, нет утечек. Добавь недостающие обработки ошибок и логи для отладки. Убедись что fallback Hive работает в браузере.
```

**Мой чеклист (Checkpoint 9):**

**Полный сценарий на телефоне:**
- [x] Очистить данные приложения / полный сценарий
- [x] Создать стиль, движения, треки, хореография
- [x] Тренировка (хореография/фристайл), окончание, возврат
- [x] Данные сохраняются после перезапуска

**Браузер:** `flutter run -d chrome`
- [x] Fallback Hive: данные сохраняются после F5

---

## Шаг 12: Сборка APK

✅ **Шаг выполнен.** Обновлён default versionName в build.gradle (1.0.0), добавлен скрипт `scripts/build_apks.ps1` и инструкция `BUILD.md`.

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

- [x] versionCode/versionName из pubspec.yaml (1.0.0+1), default в build.gradle
- [x] Скрипт `scripts/build_apks.ps1`, инструкция в BUILD.md
- [x] APK в android/app/build/outputs/flutter-apk/ (после сборки)
- [x] Размеры после оптимизации: full ~293 МБ, standard ~230 МБ, lite ~175 МБ
- [x] Установить на другой телефон — всё работает

**Шаг 12 (продолжение): Уменьшение размера сборок**

Чеклист по уменьшению размеров APK (один универсальный APK на flavor, все устройства поддерживаются):

- [x] **Lite:** в сборке только голос Руслана в 1 экземпляре — в `$voiceBlockLite` только `assets/voices/lite/ruslan/`.
- [x] **Standard:** в сборке только голоса Руслана и Ирины по 1 экземпляру — в `$voiceBlockStandard` только `standard/ruslan/` и `standard/irina/`.
- [x] **Full:** в `$voiceBlockFull` только три папки: `full/ruslan/`, `full/irina/`, `full/kamila/` (убрать lite и standard из full).
- [x] Подстановка блока по строкам (Replace-VoiceBlock) вместо regex; скрипт передаёт `--dart-define=FLAVOR=$f`.
- [x] Пересобрать три flavor (`.\scripts\build_apks.ps1`), подстановка срабатывает.
- [x] Замерить размеры: full ~293 МБ, standard ~230 МБ, lite ~175 МБ (вместо 460 у full).

**Промт для ассистента (уменьшение размера APK):**
```
По плану уменьшения размера APK в scripts/build_apks.ps1: (1) Lite — только Руслан в 1 экземпляре: в $voiceBlockLite только assets/voices/lite/ruslan/. (2) Standard — только Руслан и Ирина по 1 экземпляру: в $voiceBlockStandard только standard/ruslan/ и standard/irina/. (3) Full — только full: в $voiceBlockFull только full/ruslan/, full/irina/, full/kamila/, убери lite и standard. (4) В regex $voiceBlockPattern разреши оба символа тире в «по умолчанию — full» ([—\-] или аналог). Не добавляй --split-per-abi — один универсальный APK на flavor для всех устройств. После правок пересобери и замерь размеры.
```

---

## Шаг 13: Публикация приложения (Google Play, Firebase, App Store)

Три направления ведём параллельно: Google Play, Firebase (раздача друзьям), App Store.

---

### 13.1 Выгрузка в Google Play

**Цель:** приложение в каталоге Google Play, пользователи могут установить из магазина.

**Чеклист:**
- [ ] Аккаунт разработчика Google Play (разовый платёж ~$25)
- [ ] Создать приложение в [Google Play Console](https://play.google.com/console): название, краткое/полное описание, скриншоты, контент-рейтинг, политика конфиденциальности (если нужна)
- [ ] Собрать релизные AAB или APK: `flutter build appbundle --flavor full --release --dart-define=FLAVOR=full` (AAB предпочтительнее для Play) или использовать готовые APK из `.\scripts\build_apks.ps1`
- [ ] В Console: Production (или тестовый трек) → создать релиз → загрузить AAB/APK
- [ ] Заполнить все обязательные поля магазина и отправить на проверку
- [ ] После модерации приложение появится в Google Play

**Промт (при необходимости):**
```
Подготовь к публикации в Google Play: проверь versionCode/versionName в android/app/build.gradle, добавь в BUILD.md команду сборки AAB для full flavor и краткую инструкцию по загрузке в Play Console.
```

---

### 13.2 Firebase App Distribution (раздача друзьям без App Store)

**Цель:** раздавать сборки друзьям по ссылке (Android APK и при желании iOS), без публикации в App Store.

**Чеклист:**
- [ ] Создать проект в [Firebase Console](https://console.firebase.google.com)
- [ ] Добавить приложение Android (package name из build.gradle, например `com.midnightdancer.app`)
- [ ] При желании добавить приложение iOS (Bundle ID) для раздачи IPA друзьям с iPhone
- [ ] Включить **App Distribution** в меню проекта
- [ ] Загрузить билды:
  - **Android:** в App Distribution → Releases → загрузить APK (например, app-full-release.apk из `android/app/build/outputs/flutter-apk/`)
  - **iOS:** загрузить IPA (собранный в Codemagic или на Mac)
- [ ] Добавить тестировщиков по email (друзья) — они получат ссылку на установку
- [ ] Друзья открывают ссылку на телефоне и устанавливают приложение (без магазинов)

**Промт (при необходимости):**
```
Добавь в проект поддержку Firebase: firebase_core в pubspec, android/app/google-services.json (placeholder или из Console). Краткая инструкция в README или BUILD.md: как загрузить APK в Firebase App Distribution и пригласить тестеров.
```

---

### 13.3 Выгрузка в App Store

**Цель:** приложение в каталоге App Store (параллельно с Google Play и Firebase).

**Чеклист:**
- [ ] Аккаунт Apple Developer Program ($99/год), дождаться активации
- [ ] Добавить платформу iOS в проект (папки `ios/` нет): на Mac выполнить `flutter create . --platforms=ios` или собрать первый раз в Codemagic
- [ ] Собрать подписанный IPA (Codemagic или Mac с Xcode): `flutter build ipa --flavor full --release --dart-define=FLAVOR=full`
- [ ] В [App Store Connect](https://appstoreconnect.apple.com) создать приложение (iOS), указать Bundle ID, заполнить метаданные, скриншоты, рейтинг
- [ ] Загрузить IPA в App Store Connect (Transporter на Mac или из Codemagic)
- [ ] Выбрать билд в версии приложения и отправить на ревью
- [ ] После одобрения Apple приложение появится в App Store

**Промт (при необходимости):**
```
Напиши пошаговый чеклист публикации в App Store для Flutter-приложения: Apple Developer, добавление ios/, сборка IPA (Codemagic или Xcode), загрузка через Transporter, метаданные и ревью.
```

---

## Шаг 14: Деплой на хостинг (опционально)

Страница со ссылками на APK для прямого скачивания — по желанию, после магазинов.

**Промт:**
```
Создай простую HTML-страницу index.html для скачивания APK: заголовок Midnight Dancer, три ссылки на midnight-dancer-lite.apk, midnight-dancer-standard.apk, midnight-dancer-full.apk с описанием (Lite, Standard, Full и размеры). Минимальный стиль.
```

**Чеклист:**
- [ ] Загрузить 3 APK и index.html на сервер
- [ ] Открыть URL в браузере — страница со ссылками
- [ ] Скачать APK на телефон — установка успешна

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
