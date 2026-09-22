# Как открыть KMP-проект Midnight Dancer (этап 1)

Простыми словами: рядом со старым Flutter-приложением появилась папка **`kmp/`** — это новый «скелет» приложения. В магазинах пока по-прежнему старое Flutter; новый каркас для проверки команды.

## Что нужно на компьютере

- **JDK 17** (или новее LTS)
- **Android Studio** (Ladybug / свежая) с Android SDK
- Для iPhone: **Mac + Xcode 15+** *или* облачный iPhone (Mobile Next Cloud) — на Windows полный iOS-билд не собрать

## Продукт

Только **полная** сборка Midnight Dancer (**full**): три голоса Руслан / Ирина / Камила будут позже.  
Отдельных продуктов lite / standard / english в KMP **нет**.

## Android (Windows / Mac / Linux)

1. Откройте в Android Studio папку `kmp/` (не корневой Flutter).
2. Дождитесь синхронизации Gradle.
3. Выберите конфигурацию `:androidApp` и запустите на эмуляторе **или** из терминала:

```powershell
cd kmp
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :core:navigation:testDebugUnitTest :core:domain:testDebugUnitTest
```

4. APK: `kmp/androidApp/build/outputs/apk/debug/androidApp-debug.apk`
5. Установка на уже запущенный эмулятор (агент сам поднимает устройства скриптом):

```powershell
.\scripts\install-kmp-android-debug.ps1
```

Package id: `com.midnightdancer.app` (full).

## iPhone

### Быстрый путь: unsigned IPA (для облака / C-09)

На **Mac** или через GitHub Actions (`macos-latest`):

```bash
# Mac локально:
chmod +x scripts/build-kmp-ios-ipa.sh
./scripts/build-kmp-ios-ipa.sh iphoneos
# → dist/MidnightDancer-stage1.ipa
```

CI: Actions → **KMP iOS Stage 1 (unsigned IPA)** → артефакт `MidnightDancer-stage1-ipa`.  
Дальше: [`docs/KMP_IOS_CLOUD_RUNBOOK.md`](KMP_IOS_CLOUD_RUNBOOK.md) (upload → Mobile Next → screenshot).

### Ручная отладка в Xcode (симулятор)

1. На Mac: соберите Shared framework:

```bash
cd kmp
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
# для реального устройства / IPA: :shared:linkDebugFrameworkIosArm64
```

2. Сгенерируйте Xcode-проект (один раз):

```bash
cd kmp/iosApp
# нужен xcodegen: brew install xcodegen
xcodegen generate
open MidnightDancerIos.xcodeproj
```

3. Без Mac: Клиент ставит IPA через **Mobile Next Cloud** (`docs/CLIENT_CLOUD_IOS.md`), когда артефакт готов.

Исходники SwiftUI: `kmp/iosApp/iosApp/` (одна кнопка Hello).

## Карта модулей

См. `memory/waves/stage1-module-map.md`.

## Чего пока нет

- Экранов музыки/тренера/бэкапа (этапы 2–3)
- Голосов Piper (Voice TTS позже)
- Выкладки в App Store / Google Play production
