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

```bash
# По одному (для english обязательно --dart-define FLAVOR=english):
flutter build apk --flavor lite --release --dart-define=FLAVOR=lite
flutter build apk --flavor standard --release --dart-define=FLAVOR=standard
flutter build apk --flavor full --release --dart-define=FLAVOR=full
flutter build apk --flavor english --release --dart-define=FLAVOR=english
```

**Или скриптом (PowerShell) — собирает все четыре варианта:**

```powershell
.\scripts\build_apks.ps1
```

Готовые APK:

- `android/app/build/outputs/flutter-apk/app-lite-release.apk`
- `android/app/build/outputs/flutter-apk/app-standard-release.apk`
- `android/app/build/outputs/flutter-apk/app-full-release.apk`
- `android/app/build/outputs/flutter-apk/app-english-release.apk`

## Подпись (релиз)

Для публикации в Store используется свой keystore. В `android/app/build.gradle` настроены чтение `key.properties` и `signingConfigs.release`; при наличии файла подпись подставляется автоматически.

---

### Где вписать свои данные для подписи

**Пароли вписываешь только в файл `key.properties`, нигде в коде их не указывай.**

**Файл:** `android/key.properties` (создай его сам в папке `android/`, в репозиторий он не попадает).

В этом файле должны быть **четыре строки** (подставь свои значения):

| Строка | Что вписать | Пример |
|--------|-------------|--------|
| `storePassword=` | Пароль от keystore-файла (тот, что задал при создании `.jks`) | `storePassword=мой_секретный_пароль` |
| `keyPassword=` | Пароль ключа (часто совпадает с storePassword) | `keyPassword=мой_секретный_пароль` |
| `keyAlias=` | Имя ключа (алиас), который задал при создании keystore | `keyAlias=upload` |
| `storeFile=` | Путь к файлу keystore **относительно папки android**. Если файл лежит в корне проекта: `../upload-keystore.jks`; если в папке android: `upload-keystore.jks` | `storeFile=../upload-keystore.jks` |

**Итого в файле `android/key.properties` должно быть что-то вроде:**

```properties
storePassword=твой_пароль
keyPassword=твой_пароль
keyAlias=upload
storeFile=../upload-keystore.jks
```

- Файл `upload-keystore.jks` создаётся один раз командой из RELEASE_PLAN (шаг 0.1). Храни его и пароли в безопасном месте, не коммить в git.
- После создания и заполнения `android/key.properties` сборка релизных APK будет подписываться этим ключом автоматически.

## iOS (локальные уведомления)

Папка `ios/` добавлена для сборки под iPhone. На **Mac** из корня проекта:

```bash
cd ios && pod install && cd ..
flutter build ios --no-codesign
```

В `ios/Runner/AppDelegate.swift` подключены **UserNotifications** и **flutter_local_notifications** (делегат центра уведомлений и callback регистрации плагинов), в корне `ios/` лежит **Podfile**. После `pod install` открывайте **`Runner.xcworkspace`** в Xcode.

### Сборка IPA в Codemagic

В корне репозитория лежит **`codemagic.yaml`** и инструкция **`CODEMAGIC_IOS.md`** (подпись, Bundle ID `com.midnightdancer.midnightDancer`, интеграция App Store Connect). Тексты напоминаний в приложении — **`assets/notifications/dance_reminder_lines.txt`**.
