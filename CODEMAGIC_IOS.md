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

1. Зарегистрируйте **Bundle ID**: `com.midnightdancer.midnightDancer` (как в Xcode / `project.pbxproj` и в `codemagic.yaml` → `ios_signing.bundle_identifier`).
2. В [App Store Connect](https://appstoreconnect.apple.com/) создайте приложение с этим идентификатором.

Если позже сделаете **отдельные приложения** под lite/full/english с разными Bundle ID — скопируйте workflow в `codemagic.yaml` и поменяйте `bundle_identifier` и при необходимости target в Xcode.

---

## 2. Codemagic: подключение репозитория

1. [Codemagic](https://codemagic.io/) → добавьте приложение из **Git** (GitHub / GitLab / Bitbucket и т.д.).
2. Укажите использование **`codemagic.yaml`** из корня репозитория.

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
