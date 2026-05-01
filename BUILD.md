# Сборка APK — Midnight Dancer

## Версия

- **versionCode** и **versionName** задаются в `pubspec.yaml` (поле `version: 1.0.0+1` → name=1.0.0, code=1) и при `flutter build` подставляются в `android/app/build.gradle`.
- Перед новой публикацией в Store увеличьте в `pubspec.yaml`: например `1.0.0+1` → `1.0.1+2` (versionName+versionCode).

## Варианты (flavor)

| Flavor   | Описание                    | Ориентировочный размер APK |
|----------|-----------------------------|-----------------------------|
| lite     | Один голос                  | ~20 MB                      |
| standard | Два голоса                  | ~35 MB                      |
| full     | Три голоса                  | ~50 MB                      |
| english  | Интерфейс на английском, два голоса (Kamila, Ruslan) | ~35 MB |
| playverify | Только Android: **один** голос Piper (Руслан), тот же `applicationId`, что у full; сборка с `--split-per-abi` — для проверки владения пакетом под лимит **160 МБ** (берите arm64-сплит) | зависит от сборки |

В **`pubspec.yaml`** в APK попадают **только** Piper-папки выбранного flavor (между `# BEGIN_PIPER_VOICE_ASSETS` и `# END_PIPER_VOICE_ASSETS`). В репозитории по умолчанию записан **full** (три голоса). Перед сборкой другого варианта выполни:

```powershell
.\scripts\set_pubspec_voice_assets.ps1 -Flavor standard   # или lite | full | english | playverify
flutter pub get
```

**Сборка release APK `playverify`** (один голос Руслан, `com.midnightdancer.app`; для лимита Play Console **160 MB** загружайте **arm64** из split):

```powershell
.\scripts\set_pubspec_voice_assets.ps1 -Flavor playverify
flutter pub get
flutter build apk --flavor playverify --release --dart-define=FLAVOR=playverify --split-per-abi
```

Файлы в `build/app/outputs/flutter-apk/`:

- **`app-arm64-v8a-playverify-release.apk`** — для большинства телефонов и для проверки в консоли;
- `app-armeabi-v7a-playverify-release.apk`, `app-x86_64-playverify-release.apk` — другие ABI.

Проверка в тестах, что в playverify в UI попадёт только Руслан:  
`flutter test test/playverify_tts_config_test.dart --dart-define=FLAVOR=playverify`

Один «толстый» APK без split снова тянет все ABI (~300 MB). **iOS** этого flavor нет — только Android.

Скрипт **`scripts\build_apks.ps1`** сам подставляет голоса для каждого flavor и в конце восстанавливает `pubspec.yaml` из резервной копии. **`scripts\run_android_debug.ps1`** перед запуском тоже выставляет Piper-ассеты под выбранный `-Flavor`.

## Android SDK (Gradle и телефон)

Путь к SDK задаётся в **`android/local.properties`** (файл локальный, в git не коммитится):

```properties
sdk.dir=D\:\\Applications\\Android\\Sdk
```

Корень SDK — папка, где лежат `platform-tools`, `build-tools`, `platforms` (у тебя это `D:\Applications\Android\Sdk` внутри `D:\Applications\Android`).

Для **`flutter run` / `flutter doctor`** в PowerShell задай переменную окружения (можно в профиле PowerShell, чтобы не вводить каждый раз):

```powershell
$env:ANDROID_HOME = "D:\Applications\Android\Sdk"
```

Скрипт **`scripts/run_android_debug.ps1`** выставляет `ANDROID_HOME` и запускает debug на подключённом устройстве.

Один раз на ПК: **`.\scripts\setup_android_home_profile.ps1`** — записывает `ANDROID_HOME` в переменные среды пользователя Windows и добавляет строку в профиль PowerShell (новые окна терминала уже с готовым путём).

Телефон не виден в `adb devices`? Запусти на **своём** ПК с подключённым USB: **`.\scripts\adb_find_device.ps1`** — по шагам проверяет путь к SDK, перезапуск ADB, `reconnect`, статусы, PnP и `flutter devices`.

## Запуск в debug (в т.ч. английская версия)

**Обязательно запускай из папки проекта** `Midnight_Dancer` (там, где лежит `pubspec.yaml`):

```bash
cd Midnight_Dancer
flutter run --flavor english --dart-define=FLAVOR=english
```

Для других вариантов: замени `english` на `lite`, `standard` или `full`. В Cursor/VS Code можно выбрать конфигурацию «Midnight Dancer (English)» в списке запуска.

## Сборка всех четырёх APK

**Из корня проекта** (папка `Midnight_Dancer`):

```powershell
# Перед сборкой подставьте Piper-голоса этого flavor (см. блок выше), затем:
flutter build apk --flavor lite --release --dart-define=FLAVOR=lite
flutter build apk --flavor standard --release --dart-define=FLAVOR=standard
flutter build apk --flavor full --release --dart-define=FLAVOR=full
flutter build apk --flavor english --release --dart-define=FLAVOR=english
```

**Или скриптом (PowerShell) — собирает все четыре варианта:**

```powershell
.\scripts\build_apks.ps1
```

Готовые APK (путь от корня проекта):

- `build/app/outputs/flutter-apk/app-lite-release.apk`
- `build/app/outputs/flutter-apk/app-standard-release.apk`
- `build/app/outputs/flutter-apk/app-full-release.apk`
- `build/app/outputs/flutter-apk/app-english-release.apk`

## Подпись (релиз)

Для RuStore / Google Play используется **релизный keystore**. В `android/app/build.gradle` подключены `signingConfigs.release`: пароли можно задать **либо** в `android/key.properties`, **либо** через переменные окружения (удобно для CI и Codemagic).

По умолчанию ожидается файл **`Ключи/upload-keystore.jks`** в корне проекта (папка `Ключи/` в `.gitignore`). Путь по умолчанию от папки `android/`: `storeFile=../Ключи/upload-keystore.jks`. Шаблон без паролей: **`android/key.properties.example`** → скопировать в `android/key.properties`.

### Переменные окружения (имеют приоритет над строками в `key.properties`)

| Переменная | Назначение |
|------------|------------|
| `MIDNIGHT_UPLOAD_STORE_PASSWORD` | Пароль хранилища ключей (.jks) |
| `MIDNIGHT_UPLOAD_KEY_PASSWORD` | Пароль ключа (часто совпадает с store) |
| `MIDNIGHT_UPLOAD_KEY_ALIAS` | Алиас ключа (если не задан — используется `upload`) |
| `MIDNIGHT_UPLOAD_KEYSTORE` | Полный путь к `.jks`, если не используете путь по умолчанию |

Пример в PowerShell перед `flutter build apk`:

```powershell
$env:MIDNIGHT_UPLOAD_STORE_PASSWORD = 'ВАШ_ПАРОЛЬ'
$env:MIDNIGHT_UPLOAD_KEY_PASSWORD = 'ВАШ_ПАРОЛЬ'
flutter build apk --flavor full --release --dart-define=FLAVOR=full
```

Релизная подпись включается только если файл keystore **существует** и заданы оба пароля (файл или env).

---

### Где вписать свои данные для подписи (файл)

**Пароли храни только в `android/key.properties` или в секретах CI, не в коде.**

**Файл:** `android/key.properties` (создай в папке `android/`, в git не коммитится).

| Строка | Что вписать | Пример |
|--------|-------------|--------|
| `storePassword=` | Пароль от `.jks` | см. выше |
| `keyPassword=` | Пароль ключа | часто как у store |
| `keyAlias=` | Алиас при создании keystore | `upload` |
| `storeFile=` | Путь к `.jks` **относительно папки `android/`** | `storeFile=../Ключи/upload-keystore.jks` |

```properties
storePassword=твой_пароль
keyPassword=твой_пароль
keyAlias=upload
storeFile=../Ключи/upload-keystore.jks
```

- Файл keystore и пароли не коммить; после заполнения релизные APK подписываются автоматически при сборке.

## iOS (локальные уведомления)

Папка `ios/` добавлена для сборки под iPhone. На **Mac** из корня проекта:

```bash
cd ios && pod install && cd ..
flutter build ios --no-codesign
```

В `ios/Runner/AppDelegate.swift` подключены **UserNotifications** и **flutter_local_notifications** (делегат центра уведомлений и callback регистрации плагинов), в корне `ios/` лежит **Podfile**. После `pod install` открывайте **`Runner.xcworkspace`** в Xcode.

### Сборка IPA в Codemagic

В корне репозитория лежит **`codemagic.yaml`** и инструкция **`CODEMAGIC_IOS.md`** (подпись, Bundle ID `com.midnightdancer.app`, интеграция App Store Connect). Тексты напоминаний в приложении — **`assets/notifications/dance_reminder_lines.txt`**.
