# Сборка iOS на Codemagic (Midnight Dancer)

В репозитории уже есть всё для **локальных уведомлений** на iPhone и для **CI**:

| Что | Где |
|-----|-----|
| Тексты уведомлений в бандле | `assets/notifications/dance_reminder_lines.txt` (в `pubspec.yaml` подключён как asset) |
| Swift: плановые уведомления + делегат | `ios/Runner/AppDelegate.swift` (`flutter_local_notifications`, `UserNotifications`) |
| CocoaPods | `ios/Podfile` (на Mac/Codemagic: `pod install`) |
| Сборка IPA в облаке | `codemagic.yaml` |

Обычное обновление приложения из App Store **не удаляет** ваши данные; см. также `DATA_PROTECTION.md`.

---

## 1. Apple Developer и App Store Connect

### Bundle ID по flavor (iOS)

| Сборка | App ID (как в Android `applicationId`) | Xcode-конфигурации | `codemagic.yaml` workflow |
|--------|------------------------------------------|---------------------|---------------------------|
| **Lite** | **`com.midnightdancer.app.lite`** | `Debug-lite` / `Release-lite` / `Profile-lite` | `ios-ipa-lite` |
| Standard / Full / English | Пока **`com.midnightdancer.app`** (общий) | `*-standard`, `*-full`, `*-english` | `ios-ipa-standard` и т.д. |

Для **lite** в Apple Developer нужен App ID **`com.midnightdancer.app.lite`** и **App Store** provisioning profile на него — тот же идентификатор, что у вас в профиле `MidnightDancerLite…`, если вы заводили lite под пакет как на Android.

Когда появятся отдельные App ID для standard / full / english — задайте в `project.pbxproj` у соответствующих `PRODUCT_BUNDLE_IDENTIFIER` (блоки `*-standard`, `*-full`, `*-english`) и в каждом workflow в `ios_signing.bundle_identifier`.

### Общие шаги

1. Зарегистрируйте нужные **Bundle ID** в [Identifiers](https://developer.apple.com/account/resources/identifiers/list).
2. В [App Store Connect](https://appstoreconnect.apple.com/) создайте приложение с **тем же** идентификатором, что собираете (для lite — `com.midnightdancer.app.lite`).

---

## 2. Codemagic: подключение репозитория

1. [Codemagic](https://codemagic.io/) → добавьте приложение из **Git** (GitHub / GitLab / Bitbucket и т.д.).
2. Укажите использование **`codemagic.yaml`** из корня репозитория.

### Схема Xcode (`Runner` / `lite` / `standard` / `full`)

Codemagic при старте проверяет, что выбранная **Xcode scheme** лежит в репозитории:  
`ios/Runner.xcodeproj/xcshareddata/xcschemes/*.xcscheme`.

- **`Runner`** — основная схема Flutter по умолчанию.
- **`lite`**, **`standard`**, **`full`**, **`english`** — те же target `Runner`, отдельные имена схем для UI Codemagic (если схемы нет в репозитории — ошибка *Scheme "…" not found*).

Сборка **lite / standard / full / english** задаётся **`--flavor`** (нативные конфигурации Xcode `Release-lite` и т.д., как в [документации Flutter по flavors](https://docs.flutter.dev/deployment/flavors)) и дублируется **`--dart-define=FLAVOR=...`** для Dart (`lib/core/app_flavor.dart`). В `codemagic.yaml` выберите workflow: **`ios-ipa-lite`**, **`ios-ipa-standard`**, **`ios-ipa-full`**, **`ios-ipa-english`**. Схемы **`english`**, **`lite`**, **`standard`**, **`full`** в репозитории согласованы с этими flavor’ами.

Проект уже пропатчен скриптом `scripts/patch_ios_flavors_pbxproj.py` (повторный запуск не нужен).

---

## 3. Подпись и App Store Connect

1. В Codemagic: **Teams → Integrations → App Store Connect** — создайте интеграцию (API Key из App Store Connect: Users and Access → Keys).
2. В `codemagic.yaml` в блоке `integrations` замените значение `app_store_connect:` на **идентификатор вашей интеграции** (как он отображается в Codemagic), если он **не** называется `codemagic`.

Либо настройте **группу переменных** (сертификаты, профили) и раскомментируйте `groups:` в workflow — см. [документацию Codemagic по iOS code signing](https://docs.codemagic.io/code-signing/ios-code-signing/).

3. Шаг **`xcode-project use-profiles`** в YAML подставляет профили, которые Codemagic получает из интеграции/групп.

---

## 4. Локальные уведомления на iPhone

Дополнительные **entitlements** для push-сервера не нужны: используются **локальные** запланированные уведомления из Dart (`flutter_local_notifications`).

После установки IPA пользователь включает напоминания в **Настройках** приложения; iOS запросит разрешение на уведомления при первом включении.

---

## 5. Локальная проверка на Mac (по желанию)

```bash
flutter pub get
cd ios && pod install && cd ..
flutter build ios --release --no-codesign --dart-define=FLAVOR=full
```

Для установки на устройство нужны свои сертификаты и `flutter build ipa` или Xcode.

---

## 6. Что не коммитится (нормально)

В `.gitignore`: `ios/Pods/`, сгенерированные `Flutter/Generated.xcconfig`, часть ephemeral-файлов. На Codemagic **`flutter pub get`** и **`pod install`** создают их заново перед сборкой.
