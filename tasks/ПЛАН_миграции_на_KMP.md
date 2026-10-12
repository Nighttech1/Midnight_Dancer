# План миграции Midnight Dancer: Flutter → KMP

**Зачем этот файл:** пошаговый чеклист, по которому команда двигается от текущего Flutter-приложения к целевому стеку из ТЗ.

**Источники правды**

- ТЗ: [`ТЗ на переработку приложения с Flutter на премиальный стек KMP.md`](./ТЗ%20на%20переработку%20приложения%20с%20Flutter%20на%20премиальный%20стек%20KMP.md)
- Сжатая архитектура: `.cursor/skills/midnight-kmp-core/SKILL.md`
- Домен: `knowledge/area_midnight_dancer/_index.md`
- Цикл команды: `docs/CLIENT_ORCHESTRATOR_CYCLE.md`

**Целевой стек (коротко)**

| Что | Решение |
|-----|---------|
| Общая логика | Kotlin Multiplatform (Kotlin 2.0+, coroutines, StateFlow) |
| Навигация / экраны-логика | Decompose (Child Stack / Slot / Pages) |
| Мост в iOS | Touchlab SKIE |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI (**не** Compose Multiplatform / Skia) |
| Сеть | Ktor |
| База | Room KMP |
| Настройки | DataStore |
| DI | Koin (или kotlin-inject) |
| Картинки | Coil 3 |
| Анимации / премиум | пружины + Rive + шейдеры AGSL/Metal + haptics |

**Как пользоваться планом**

1. Идём сверху вниз: не начинаем этап N+1, пока не закрыты обязательные пункты этапа N (помечены ★).
2. Каждый крупный пункт = волна Оркестратора: Архитектор → исполнители по `chain.md` → Тестировщик → Фиксер → Деплоер → **Клиент** (с скринами).
3. Отмечаем `[x]` только после зелёной приёмки Клиента (для UI) или зелёных тестов (для инфраструктуры).
4. Flutter-приложение **не выкидываем**, пока этап 5 не подтвердит паритет функций.

### UI: сначала Android, потом iPhone (обязательный порядок)

На **этапах 3–4** (и дальше для новых экранов) команда работает так:

1. **Дизайн и motion на Android (Jetpack Compose)** — OLED `#000000`, neon-токены C-20, пружины, haptics, AGSL/шейдеры, Rive-хосты. Клиент смотрит **локальный AVD** + скрины; правки делаем, пока на Android всё выглядит и двигается «дорого».
2. **Проверка на Android** — `:androidApp:assembleDebug`, сценарий «5 вкладок + Settings + Speak», без dev-меток на экране.
3. **Перенос на iPhone (SwiftUI + SKIE)** — **после** того как Android принят с замечаниями или без. Не pixel-match: **нативные** SwiftUI (`.ultraThinMaterial`, CoreHaptics, Metal-хосты), тот же Decompose-граф и те же Intent/UiState из KMP. Цель — **такое же ощущение** плавности (120 FPS, без jank), не копия Compose 1:1.
4. **IPA + Mobile Next** — только когда Stage 4 APK на Android зелёный и исходники iOS синхронизированы; allocate iPhone **один раз** в конце UI-волны.

### IPA: сборка через Codemagic (обязательный путь)

На Windows локально **нет** Xcode — IPA **не** собираем на машине разработчика и **не** ждём ручной Mac «ради галочки».

| Что | Решение |
|-----|---------|
| **Основной канал IPA** | **[Codemagic](https://codemagic.io/)** — облачная Mac-сборка |
| Конфиг | корень репо: `codemagic.yaml` |
| Инструкция | [`ios-build/kmp/CODEMAGIC.md`](../ios-build/kmp/CODEMAGIC.md) · карта [`ios-build/README.md`](../ios-build/README.md) |
| Кто зовёт | **Деплоер / Platform Ops** в chain (C-35 / дальше Stage 5) |
| Артефакт | скачать IPA из Codemagic → `ios-build/artifacts/current/MidnightDancer-stageN.ipa` (APK по-прежнему в `dist/`) |
| **Сейчас (QA)** | **Вариант A:** workflow `ios-ipa-kmp` **без** App Store signing (unsigned → Mobile Next re-sign) |
| **Перед релизом** | **Вариант B:** вернуть signing — Stage 5 §5.5 + `ios-build/RELEASE_CHECKLIST.md` |
| Клиент | **Mobile Next allocate только после** готового Stage N IPA из Codemagic |

**Правила:**

1. После зелёного Android APK волны → push/commit исходников iOS → **запуск workflow Codemagic** (full / KMP-workflow, когда будет отдельный для `:iosApp`).
2. Пока `codemagic.yaml` ещё ориентирован на **Flutter flavors** — для KMP Stage 4+ Деплоер **дописывает/добавляет** workflow под `kmp/iosApp` + `shared.xcframework` (SKIE), не подменяет Stage 4 IPA старым Flutter/Stage 1 артефактом.
3. Fallback (редко): локальный Mac `./scripts/build-kmp-ios-ipa.sh` — только если Codemagic недоступен; GHA `kmp-ios.yml` — запасной, не основной.
4. Prod TestFlight / App Store через Codemagic — **только** после вердикта Клиента без P0/P1 и явного «да» пользователя.

**Запрещено:** параллельно «ломать» один и тот же экран на Android и iOS в одном тикете (overlapping paths). Порядок в chain: **C-28 Android → приёмка → C-29 iOS** (или C-28∥C-29 только если paths disjoint и Android уже эталон).

### Stitch → дорогой UI (когда пользователь кидает архив)

1. Класть ZIP в `memory/design/stitch/<дата>/`.
2. Skill `.cursor/skills/stitch-to-kmp-ui/SKILL.md`: инвентарь экранов → Android Compose + **Motion Premium** → Client → iOS → Codemagic.
3. HTML/Tailwind из Stitch **не** в WebView; только референс.
4. Параллельно **не** ломать data-survival (package id / `MidnightDancer/`).

**Статус плана:** трекер живой · обновлено: 2026-10-04 · Stage 4 Android done · **Stitch UI: готовность GO** (`W-2026-10-04-cyber-acoustic-ui`) — код после «согласовано»; IPA → Codemagic

---

## Легенда ролей

| Код | Роль | Когда зовём |
|-----|------|-------------|
| **TRN** | **Тренер** | **каждый ход первым**: грейд skills/rules/MCP; после жалобы — до Фиксера |
| ARCH | Архитектор | до кода: spec + chain |
| KMP | KMP Shared | commonMain, Decompose, Ktor, Room, DataStore |
| AND | Android Compose | `:feature:*:ui-compose` |
| IOS | iOS SwiftUI | `iosApp` + SKIE |
| MOT | Motion Premium | пружины, Rive, шейдеры, 120 FPS |
| TTS | Voice TTS | Piper / Sherpa / голоса в Trainer |
| OPS | Platform Ops | CI, подписи, сторы, пуши, updates |
| HACK / GUARD | Хакер → Гуард | security-волна |
| TEST / FIX / DEP | Тестировщик / Фиксер / Деплоер | каждый этап |
| CLIENT | Клиент | финальный вердикт + скрины |

---

## Карта экранов Flutter → feature-модули KMP

| Сейчас (Flutter) | Целевой feature | Примечание |
|------------------|-----------------|------------|
| Splash | `:feature:splash` | холодный старт, consent |
| Home + MainScaffold | `:feature:home` + `:core:navigation` | нижняя навигация |
| Music | `:feature:music` | плейбек, библиотека |
| Elements / Moves | `:feature:elements` | карточки движений + видео |
| Choreography + Sequence editor | `:feature:choreography` | таймлайн, share zip |
| Trainer + session | `:feature:trainer` | ★ голоса TTS |
| Exchange / backup | `:feature:exchange` | secure zip, full backup |
| Settings | `:feature:settings` | язык, legal, reminders |
| Flavor **full only** (3 голоса) | одна продуктовая сборка | lite/standard/english сняты с KMP-контура (ADR full-only) |

---

## Этап 0. Подготовка и правила игры ★

*Цель: все понимают границы и не ломают прод раньше времени.*

- [x] ★ Прочитать ТЗ целиком (команда: ARCH + Оркестратор)
- [x] ★ Зафиксировать решение: **KMP Core + Compose + SwiftUI** (не CMP на iOS)
- [x] ★ Зафиксировать минимальные ОС: iOS 16+, Android API 29+
- [x] ★ Инвентаризация Flutter: экраны, сервисы, модели, ассеты, flavors (`lib/`, `assets/`)
- [x] ★ Список «нельзя потерять»: backup/import, secure zip, TTS голоса, reminders, legal consent, видео/музыка локально
- [x] Репозиторий: ветка `kmp/` или монорепо-папка `kmp/` рядом с Flutter (ARCH решает в spec)
- [x] Правила: UI не в `commonMain`; навигация только через Decompose; публичные Flow/sealed/suspend — SKIE-friendly
- [x] Метрики приёмки из ТЗ выписаны в `memory/waves/` (120 FPS, cold start, frozen frames, RAM)
- [x] CLIENT: согласовать, что Flutter остаётся «боевым» до этапа 5

Артефакты: `memory/waves/W-2026-09-22-stage0.md` — **этап 0 принят Клиентом (2026-09-22)**.

**Выход этапа 0:** короткий `architect-spec` волны 0 + этот план принят как трекер. ✅

---

## Этап 1. Инфраструктура KMP и каркас (недели 1–3) ★

*Цель: пустые приложения Android/iOS собираются из общего Kotlin-ядра.*

### 1.1 Gradle / модули

- [x] ★ Корневой multi-module Gradle + Version Catalog
- [x] ★ Kotlin 2.0+ (K2), Android Gradle Plugin, Xcode-интеграция
- [x] ★ Модули-заготовки:
  - [x] `:core:domain`
  - [x] `:core:data`
  - [x] `:core:navigation`
  - [x] `:core:designsystem`
  - [x] `:androidApp`
  - [x] `:shared` / export `shared.xcframework` (SKIE; link framework на Mac)
  - [x] `:iosApp` (SwiftUI shell + project.yml; живой IPA — Mac/cloud)
- [x] ★ Touchlab SKIE подключён на `:shared` (hello StateFlow; AsyncSequence на Mac)
- [x] ★ Decompose + Essenty: корневой `RootComponent` + один dummy Child
- [x] Koin (или kotlin-inject) bootstrap в common + platform
- [x] `commonTest` smoke: «модуль собирается»

### 1.2 CI / OPS

- [x] ★ CI: сборка Android debug APK (`.github/workflows/kmp-android.yml`)
- [ ] ★ CI: сборка iOS framework / Simulator (или cloud) — runbook есть, Mac job TBD
- [x] Скрипты деплоя рядом с `scripts/ensure-client-devices.ps1` (`install-kmp-android-debug.ps1`)
- [x] Документ «как открыть KMP проект» для команды (`docs/KMP_HOW_TO_OPEN.md`)

### 1.3 Приёмка этапа 1

- [x] TEST: unit smoke + сборки зелёные
- [x] DEP: APK на эмулятор Android
- [x] ★ CLIENT: на Android и iPhone виден пустой shell с одной кнопкой из shared-стейта (скрины) — Android ✅; iPhone ✅ (C-09, 2026-09-23)

**Критерий ТЗ:** каркас `:shared.xcframework` + `:androidApp`, CI собирает обе платформы.

---

## Этап 2. Слой данных и домен (недели 4–6) ★

*Цель: вся бизнес-логика и хранилище живут в Kotlin, без UI.*

**Волна Оркестратора:** [`memory/waves/W-2026-09-23-stage2.md`](../memory/waves/W-2026-09-23-stage2.md) + [`stage2-architect-spec.md`](../memory/waves/stage2-architect-spec.md) · **принят Клиентом с замечаниями 2026-09-28** (C-10…C-18; carry: C-16 extract residuals, iOS Coil → закрыт в C-23).

### 2.1 Domain

- [x] ★ Сущности 1:1 с Flutter-моделями: Song, Move, Choreography, DanceStyle, AppData, …
- [x] Валидации / инварианты (чистый Kotlin, без Android/iOS SDK)
- [x] Use-case интерфейсы (загрузка библиотеки, merge import, timeline refs)

### 2.2 Data — сеть и настройки

- [x] ★ Ktor Client: Android OkHttp/CIO + iOS Darwin
- [x] HTTP/2 (и задел под WebSockets телеметрии — по необходимости продукта)
- [x] ★ DataStore Preferences: язык UI, legal consent, reminder config, flavor flags
- [x] Миграция значений с текущих Flutter prefs / Hive / файлов — **план совместимости** (ARCH)

### 2.3 Data — локальное хранилище

- [x] ★ Room KMP: схемы таблиц под каталог танцев / хореографий / метаданные
- [x] Реактивные `Flow<List<T>>` для списков
- [x] Файловое хранилище медиа (expect/actual или platform ops): путь аналог `MidnightDancer/` documents
- [x] Кэш Coil 3: лимит диска + LRU (как в ТЗ)
- [x] Secure zip / full backup: порт логики из `lib/data/services/secure_zip/*` и `full_backup_*` → Kotlin (KMP + platform)
- [x] Choreography package / import merge → Kotlin

### 2.4 Тесты данных

- [x] ★ commonTest: репозитории, merge import, whitelist zip, timeline
- [x] Идентичность тестовых фикстур со старым приложением (golden JSON / zip samples)
- [x] HACK→GUARD: аудит secure zip / path traversal / zip slip (без exploit PoC)

### 2.5 Приёмка этапа 2

- [x] TEST: покрытие бизнес-логики модульными тестами (цель ТЗ: 100% критичных сценариев)
- [x] ★ CLIENT: не UI-волна — достаточно отчёта Тестировщика + demo CLI/instrumented, что данные читаются одинаково

**Критерий ТЗ:** данные и use-case в Kotlin; ответы/кэш совместимы со старым приложением. ✅ (замечания carry → later extract / Stage 4+)

---

## Этап 3. Навигация и базовый UI (недели 7–10) ★

*Цель: пользователь проходит основные сценарии на Compose и SwiftUI поверх Decompose.*

**Волна:** [`memory/waves/W-2026-09-28-stage3.md`](../memory/waves/W-2026-09-28-stage3.md) · closeout [`stage3-closeout.md`](../memory/waves/stage3-closeout.md) · **Android ПРИНЯТО С ЗАМЕЧАНИЯМИ 2026-09-28** (C-19…C-23, C-25…C-27); **C-24 in flight → absorb C-30**; **IPA Stage 3 in flight** (`ipa-blocker.md`); iPhone delta → IPA then C-27 **или** defer → Stage 4 C-36.

### 3.1 Navigation (KMP)

- [x] ★ Дерево Decompose: Root → tabs (Elements / Music / Choreography / Trainer / Exchange + Settings stack)
- [x] Child Stack для вложенных экранов (sequence editor, trainer session) — stubs
- [x] Child Slot для диалогов (backup options, mapping, legal) — stubs
- [x] StateKeeper / InstanceKeeper: поворот экрана и process death не теряют черновик (best-effort + guards)

### 3.2 Design system

- [x] ★ Токены: OLED True Black `#000000`, неон-акценты, типографика
- [x] Интерфейсы haptic (реализации — этап 4 / C-30)
- [x] Параметры пружин (константы) в `:core:designsystem`
- [x] AND + IOS: базовые shell-компоненты (кнопка, список, app bar) — stubs; премиум-замена → Stage 4

### 3.3 Экраны (логика → UI параллельно)

Порядок миграции (рекомендуемый, можно уточнять ARCH):

1. [x] ★ Splash + legal consent — logic + Compose + SwiftUI (stubs)  
2. [x] ★ Home / shell навигации (Main tabs)  
3. [x] Settings (язык, reminders stubs)  
4. [x] Music (список shell; плейбек platform — follow-up)  
5. [x] Elements / move cards shell (+ Coil placeholder; video — follow-up)  
6. [x] Choreography list shell (+ sequence editor stub)  
7. [x] Trainer list (без полного TTS — заглушка speak → Stage 4 C-33)  
8. [x] Exchange: export/import UI stubs  
9. [x] Product = **full only** (3 голоса Ruslan/Irina/Kamila); без lite/standard/english в KMP  

Для **каждого** экрана:

- [x] `:feature:<name>:logic` — Intent / UiState / Decompose component  
- [x] `:feature:<name>:ui-compose` / androidApp UI — Android shells  
- [x] SwiftUI view в `iosApp` через SKIE (sources; live IPA → Stage 4 C-35)  
- [x] TEST + CLIENT скрины Android; iPhone device QA deferred → Stage 4 C-36  

### 3.4 Медиа platform

- [ ] Audio engine Android + iOS (замена just_audio и т.п.) — follow-up / Stage 5
- [ ] Video preview / move card video (ExoPlayer / AVPlayer) — follow-up
- [ ] Share / Downloads / SAF / document picker — OPS + platform actuals — follow-up

### 3.5 Приёмка этапа 3

- [x] ★ Навигация работает на Android; back stack корректен (iPhone sources ready, device QA deferred)
- [x] ★ Состояние восстанавливается после recreate (выборочно / best-effort)
- [x] CLIENT: сценарий «открыл → полистал все вкладки → Settings» без краша — **Android ✅**; iPhone → Stage 4

**Критерий ТЗ:** полноценная навигация + сохранение/восстановление стейта. ✅ Android shells; iPhone device proof → Stage 4.

---

## Этап 4. Премиум-графика, Rive, 120 FPS, голоса (недели 11–13)

*Цель: ощущение «дорогого» флагмана + рабочие голоса в Trainer.*

**Волна (открыта мандатом 2026-09-29):** [`memory/waves/W-2026-09-29-stage4.md`](../memory/waves/W-2026-09-29-stage4.md) + [`stage4-architect-spec.md`](../memory/waves/stage4-architect-spec.md) · тикеты **C-28…C-36** · **старт кода после Stage 3 stop** ([`stage3-closeout.md`](../memory/waves/stage3-closeout.md)) · максимум красоты OLED/neon + Voice TTS Ruslan/Irina/Kamila · конец: **APK+IPA** + Client virtual QA обеих платформ.

### 4.1 Motion Premium

- [x] ★ Пружины навигации: критическое демпфирование — C-24 + C-30
- [x] ★ Пружины кнопок/карточек: недодемпфированная упругость + импульс жеста — C-30
- [x] ★ Animated UI shells (OLED+neon) — C-28 Android; C-29 iOS sources (device → после IPA)
- [x] Rive hosts: neon fallback EQ/rhythm/dancer — C-31 (real `.riv` → Stage 5 / asset drop)
- [x] Шейдеры Frosted Glass + gradient + prewarm — C-32 (Metal PSO residual on Mac)
- [x] Haptics sync — C-30
- [x] Профилирование sample — C-34 (AVD best-effort; Systrace → Stage 5)
- [x] **Polish R5:** dev-метки C-31/C-32 сняты с экрана (2026-09-29)

### 4.2 Voice TTS ★

- [x] ARCH + API — C-33 (`:core:tts`, on-demand ONNX policy)
- [x] Speak в Trainer wired; ducking; System TTS fallback
- [ ] ★ Piper ONNX на устройстве (скачать ~205 МБ) — **residual Stage 5 / ops**; три timbre на Client listen
- [x] TEST + CLIENT Android — C-34 / C-36

### 4.3 Приёмка этапа 4

- [x] ★ CLIENT Android: premium shells + Speak — C-36 **ПРИНЯТО С ЗАМЕЧАНИЯМИ**; polish APK **0.4.1-stage4-polish**
- [ ] ★ CLIENT iPhone — **blocked** until `dist/MidnightDancer-stage4.ipa` из **Codemagic**
- [x] DEP APK — `dist/MidnightDancer-stage4-debug.apk`
- [ ] DEP IPA — **через Codemagic** (`codemagic.yaml` + `CODEMAGIC_IOS.md`; при необходимости KMP-workflow для `kmp/iosApp`)

**Критерий ТЗ:** 120 FPS при интерактиве; нет пропусков кадров на карточках/переключениях.  
**OUT этапа:** prod store publish без «да» пользователя; Flutter teardown = Stage 5.

---

## Этап 5. Профилирование, аудит, демонтаж Flutter, релиз (недели 14–16)

*Цель: метрики ТЗ, безопасность, магазины — без Flutter в проде.*

### 5.1 Перф и стабильность

- [ ] Systrace / Perfetto / Instruments по критичным сценариям
- [ ] Android Baseline Profiles
- [ ] Cold start / warm start p95 в пределах ТЗ
- [ ] Peak RAM в пределах ТЗ (базовый + Rive/аудио)
- [ ] Slow / frozen frames / ANR / hangs — пороги ТЗ
- [ ] Crashlytics / MetricKit подключены (OPS)

### 5.2 Security

- [ ] HACK: MASVS-волна (хранилище, zip, intents, ATS/network)
- [ ] GUARD: hardening + Semgrep / agentsec по skills
- [ ] Нет секретов в репо; secure zip guards актуальны

### 5.3 Паритет продукта + сохранность данных (P0)

Уже в Play / App Store: Flutter **`com.midnightdancer.app`**. Skill: `.cursor/skills/store-update-data-migration/SKILL.md`.

- [ ] ★ Чеклист функций Flutter ↔ KMP закрыт на 100% must-have
- [ ] ★ **То же applicationId / Bundle ID** — обновление, не «новое пустое приложение»
- [ ] ★ Upgrade fixture: `MidnightDancer/{music,videos,metadata.json}` + prefs → после KMP **элементы, музыка, хореографии на месте**
- [ ] ★ CLIENT: **НЕ ПРИНЯТО** internal/prod, если каталог/медиа пустые после update
- [ ] Миграция: C-11/C-13/C-14 guards актуальны; идемпотентный migrator
- [ ] Push / dance reminders
- [ ] In-app / soft-force update (Platform Ops)
- [ ] Legal / privacy / store listings

### 5.4 Демонтаж Flutter

- [ ] ★ Клиент принял KMP без P0/P1
- [ ] Пользователь **явно** согласовал снятие Flutter с прод-трека
- [ ] Удаление Flutter SDK / Dart из релизного контура (архив ветки ok)
- [ ] README / BUILD.md переписаны под KMP

### 5.5 Codemagic iOS: вернуть подпись (вариант B) — НЕ ЗАБЫТЬ

Сейчас для QA / Mobile Next включён **вариант A** (unsigned `ios-ipa-kmp`, без сертификатов).  
Перед TestFlight / App Store **обязательно** вернуться к **варианту B**:

- [ ] ★ Вернуть в `codemagic.yaml` у KMP-workflow: `ios_signing` (app_store) + `integrations.app_store_connect` + `xcode-project use-profiles`
- [ ] ★ Codemagic Teams → Integrations → App Store Connect: рабочий API Key / сертификаты
- [ ] ★ Apple Developer: Bundle ID `com.midnightdancer.app` + matching profiles
- [ ] Проверить IPA подписан (не unsigned) → TestFlight
- [ ] Обновить статус в `ios-build/kmp/CODEMAGIC.md` и закрыть чеклист `ios-build/RELEASE_CHECKLIST.md`

Подробности: `ios-build/kmp/CODEMAGIC.md` (A vs B), `ios-build/RELEASE_CHECKLIST.md`.

### 5.6 Релиз

- [ ] DEP: Play Internal + TestFlight
- [ ] ★ CLIENT: приёмка internal/TF со скринами
- [ ] Prod (Play / App Store) — **только** после вердикта Клиента без P0/P1 **и** явного «да» пользователя

**Критерий ТЗ:** все инженерные метрики; публикация в сторы.

---

## Сквозные правила на всём пути

- [ ] Каждая UI-волна: Android local AVD **и** iPhone (Mobile Next Cloud) + скрины в чат и `memory/tickets/.../visual/`
- [ ] Закрытие `C-*` только с `guards.md`
- [x] Волны Оркестратора **без** лимита «3 тикета» (снято 2026-09-22)
- [ ] Голоса / Piper → всегда роль **Voice TTS** в chain
- [ ] Не параллелить overlapping paths из одного chain
- [ ] Локальный API (если появится в KMP) — обновлять при сборках автоматически

---

## Быстрый трекер прогресса этапов

| Этап | Название | Статус |
|------|----------|--------|
| 0 | Подготовка | ☑ закрыт 2026-09-22 |
| 1 | Каркас KMP | ☑ закрыт 2026-09-23 (C-09) |
| 2 | Данные / домен | ☑ принят с замечаниями 2026-09-28 — W-2026-09-23-stage2 |
| 3 | Навигация + UI | ☑ Android принят 2026-09-28; formal STOP 2026-09-29 (IPA→C-36) |
| 4 | Premium + TTS | ☑ Android закрыт с замечаниями (0.4.1 polish); iPhone/IPA + Piper ONNX → residual |
| 5 | Аудит + релиз | ☐ не начат |

*Когда этап закрыт Клиентом — поставьте `☑ закрыт` и дату.*

---

## Что делать следующей сессией

1. **Пользователь:** личный тест **`dist/MidnightDancer-stage4-debug.apk`** (версия `0.4.1-stage4-polish`, без dev-меток на экране).
2. **Деплоер / Platform Ops:** IPA через Codemagic workflow **`ios-ipa-kmp` (вариант A, unsigned)** → `ios-build/artifacts/current/MidnightDancer-stage4.ipa` → Client Mobile Next.
3. **Stage 5** по команде: аудит, Piper ONNX, real `.riv`; **перед TestFlight/prod — Codemagic вариант B (подпись)** §5.5; prod stores только после «да».

Готов продолжить по команде пользователя.
