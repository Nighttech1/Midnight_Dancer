# Runbook: iOS KMP Stage 1 IPA → Mobile Next Cloud (C-09)

## Goal

На облачном iPhone виден Hello-shell: надпись из shared Kotlin + кнопка **Hello** увеличивает счётчик. Скрин для Клиента.

Продукт только **full**: `com.midnightdancer.app` (без lite).

## Экономия Mobile Next (20 мин/день)

**Не** `allocate`, пока нет файла в `ios-build/artifacts/current/` (или свежий artifact с GHA) и не готов upload.

Порядок минут-сбережения:

1. Дождаться зелёного GHA / скачать IPA  
2. `mobilenext_create_upload` + PUT файла  
3. **Только сейчас** `allocate`  
4. `install` → launch → tap Hello → 2 скрина → `release`  

Ожидание CI на уже выделенном телефоне = сжигание дневного лимита.

## Почему GitHub Actions (macos-latest)

На Windows **нельзя** собрать Kotlin/Native iOS / IPA. Сборка идёт в workflow:

**`.github/workflows/kmp-ios.yml`**

- Триггеры: `workflow_dispatch` (ручной) и `push` по путям `kmp/**` / сам workflow / скрипт.
- По умолчанию SDK = **`iphoneos`** (arm64 device) — то, что нужно для Mobile Next Cloud.
- Артефакт: **`MidnightDancer-stage1-ipa`** → файл `MidnightDancer-stage1.ipa` (unsigned; Cloud переподписывает).

## Как получить IPA

### A) GitHub Actions (рекомендуется с Windows)

1. GitHub → Actions → **KMP iOS Stage 1 (unsigned IPA)** → **Run workflow**.
2. SDK: оставьте `iphoneos`.
3. Дождитесь зелёного job `ios-unsigned-ipa`.
4. Artifacts → **MidnightDancer-stage1-ipa** → скачайте `MidnightDancer-stage1.ipa`.
5. Положите файл в `ios-build/artifacts/current/MidnightDancer-stage4.ipa` (локально, без коммита секретов).

CLI (после push / на уже закоммиченном workflow):

```bash
gh workflow run kmp-ios.yml -f sdk=iphoneos
gh run watch
gh run download --name MidnightDancer-stage4-ipa --dir ios-build/artifacts/current
```

### B) Локальный Mac

```bash
chmod +x scripts/build-kmp-ios-ipa.sh
./scripts/build-kmp-ios-ipa.sh iphoneos
# → ios-build/artifacts/current/MidnightDancer-stage4.ipa
```

Скрипт: Gradle `:shared:linkDebugFrameworkIosArm64` → sync в `xcode-frameworks/` → XcodeGen → `xcodebuild` с `CODE_SIGNING_ALLOWED=NO` (fallback ad-hoc `-`) → zip `Payload/MidnightDancer.app` → `ios-build/artifacts/current/`.

## Клиент: установка и скрин (Mobile Next Cloud)

MCP-сервер: **`mobilenext-cloud`** (см. `CLIENT_CLOUD_IOS.md` в этой же папке).

1. **Allocate** iPhone  
   `mobilenext_allocate_device` — `platform: ios`

2. **Upload IPA**  
   - `mobilenext_create_upload` с `filename: MidnightDancer-stage1.ipa` и точным `filesize` в байтах  
   - `PUT` содержимого IPA на выданный presigned URL  
   - сохранить `uploadId`

3. **Install** (Cloud сам re-sign unsigned/ad-hoc IPA)  
   `mobilenext_install_app` — `device` + `uploadId`

4. **Launch**  
   `mobilenext_launch_app` — bundle id `com.midnightdancer.app` (если инструмент требует id)

5. **Сценарий**  
   - дождаться экрана с текстом Hello / кнопкой  
   - tap **Hello**  
   - `mobilenext_take_screenshot` / `mobilenext_save_screenshot`  
   - сохранить в `memory/tickets/C-09-ios-live-screenshot/visual/`

6. **Release** устройства  
   `mobilenext_release_device`

## Что внутри сборки

| Шаг | Команда / путь |
|-----|----------------|
| Shared device framework | `:shared:linkDebugFrameworkIosArm64` → `kmp/shared/build/bin/iosArm64/debugFramework/Shared.framework` |
| Xcode project | `kmp/iosApp/project.yml` → `xcodegen generate` |
| Unsigned / ad-hoc app | `xcodebuild … CODE_SIGNING_ALLOWED=NO` (fallback: `CODE_SIGN_IDENTITY=-`) |
| IPA | `ios-build/artifacts/current/MidnightDancer-stage4.ipa` |

SwiftUI: `kmp/iosApp/iosApp/ContentView.swift` — `RootComponent` / SKIE `uiState`.

## Blockers / ограничения

| Тема | Статус |
|------|--------|
| Сборка с Windows | Невозможна — только macos runner / Mac |
| Подпись Apple Developer | Не нужна для Cloud: unsigned OK; скрипт при отказе Xcode повторяет **ad-hoc** (`-`); Mobile Next re-signs оба варианта |
| Simulator IPA | `iphonesimulator` **не** ставить на cloud iPhone — только `iphoneos` |
| Flutter store pipelines | Не трогаем |
| Первый прогон workflow | Нужен commit+push workflow на remote (делает Оркестратор/Deployer по согласованию) |

## Связанные доки

- `ios-build/cloud-qa/CLIENT_CLOUD_IOS.md` — ключ MCP
- `ios-build/kmp/` — как собрать IPA
- `ios-build/README.md` — карта папок
- `docs/KMP_HOW_TO_OPEN.md` — общий обзор KMP
- Android APK: папка `dist/` (не путать с IPA)
