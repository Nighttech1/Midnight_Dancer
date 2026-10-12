---
name: team-trainer
description: >-
  Team Trainer: each turn grades Midnight Dancer agent skills/rules/MCP/plugins
  against the turn plan and user complaints. Use when acting as Тренер or when
  Orchestrator starts a cycle wave.
---

# Team Trainer (Тренер)

## Goal

Максимально подготовленная команда **до** кода: стек KMP + Compose + SwiftUI + Decompose + Codemagic IPA + Android-first UI.

## Turn checklist

1. **Plan** — `tasks/ПЛАН_миграции_на_KMP.md`, текущая `memory/waves/W-*.md`, user query.
2. **Roster** — `.cursor/registry/agents.md` vs needed roles for this turn.
3. **Gaps** — missing skill/rule for: UI-from-link, **Stitch / Cyber-Acoustic redesign**, **expensive motion libs**, **store-update data survival**, Codemagic IPA, Android-first, Piper TTS, Motion, security.
4. **Patch** — only files that close the gap (prefer append to existing skill over new mega-docs).
5. **Log** — `memory/team-trainer/<stamp>.md` with: plan summary, gaps, files touched, next roles.
6. **Handoff** — Orchestrator continues: Architect → chain…

## Redesign / «дорогой motion» protocol

Когда пользователь кидает Stitch / `tasks/midnight_dancer_ui_redesign` / просит красивый UI:

1. Проверить инвентарь `memory/design/stitch/2026-10-03-ui-redesign/SCREEN_INVENTORY.md`.
2. Убедиться, что в registry у Android Compose + Motion есть **`cyber-acoustic-motion-kit`** + `midnight-premium-motion` + `stitch-to-kmp-ui`.
3. Сверить libs с GitHub/Maven (Rive Compose 11.x, Compose animation, Coil 3) — не тащить Tailwind/WebView.
4. HF MCP — только если нужны голоса/модели; **не** для вёрстки экранов.
5. Handoff: Архитектор (Flame tokens ADR) → Android Compose ∥ Motion → Client в конце UI-волны.

## Complaint protocol

| User signal | Trainer action before Fixer |
|-------------|----------------------------|
| «не возвращай / пока не согласовано» | Cycle docs + orchestrator + client: только «согласовано» в чат; замечания крутить в том же ходе |
| «не то UI / некрасиво» / «дешево» | Strengthen `cyber-acoustic-motion-kit` + `docs/TOP_STACK_UI_LIBS.md`; **forbid** black splash shelf, outline Skip, text filter chips, stock scrollbars; require StyleSplitSelector / filled Flame CTAs |
| «где скрины / папка visual» | Enforce: Client final **always** lists `memory/waves/W-…/visual/` + numbered PNG links from splash onward; patch `orchestrator.mdc` + `client.mdc` if missing |
| «показал Elements вместо редактора» / «хочу экраны редактирования» | Strengthen `client-review` + `client-native-device-qa`: nested Pro Editor / edit sheets mandatory in visual/ |
| «уровни → теги» / «3 тега» | **Product 2026-10-08:** `MAX_TAGS_PER_ELEMENT=1` (элемент / трек / хорео — ровно один тег) |
| «нет стрелки стиля справа» | `StyleSplitSelector`: ChevronLeft **и** ChevronRight |
| «заголовки как Элементы» | `MdPageTitle` Title Case («Музыка», «Тренировка»); **same top slot** — no Y-jump between tabs |
| «бегущая строка» | Marquee for playing title + music clip on editor timeline |
| «теги / высота блока» | Music: **single row** title+desc+tag+actions; **no** second meta line |
| «DRAFT/READY слева» | Vault: remove status pill; title flush start |
| «видео закрывает название» | Pro Editor TopBar `zIndex` above preview; no overlap |
| «таймкод без дней» | Editor: `MM.SS.msms` only |
| «свечение шага / танцуем к dock» | Storyline: strong Flame glow when filled; Dance flush to dock |
| «live cue pipeline» | Vertical cue list + pulsing orange line (premium motion); fade edges; vols at bottom |
| «меню без подписей» | CyberAcousticDock icon-only; Flutter-parity icons |
| «точка под иконкой dock» | No under-dot; selected = Flame tint only; icons ~+25% (30.dp) |
| «блок трека как хорео» | Music cards = VaultCard layout (title+actions / meta+tag) |
| «где скрин редактора музыки» | Client must shoot TrackEditDialog / upload dialog in visual/ |
| «Танцуем никуда не уходит» | Dance overlay BottomCenter fixed; LazyColumn pads under CTA |
| «ломаная пульс-линия / Завершить» | Live: zigzag playhead near ring; Finish CTA dock-flush; cues centered no left dot |
| «язык — dropdown» | Settings: premium animated dropdown right; text wrap left; no 3 chips |
| «Тег макс.1 / searchable» | Label «Тег»; SearchablePicker in-dialog scroll + search field; mock catalogs |
| «убери Стиль и тег» | Upload → BeginUpload opens TrackEditDialog (no UploadStyleTagDialog) |
| «эксклюзивные dropdown storyline» | One open at a time; panel grows toward Dance CTA |
| «тег 12 символов» | ElementCategoryConfig.MAX_NAME_LENGTH = 12 |
| «live MM.SS Understrukk / бит-линия» | Center time Understrukk shimmer; play lower; side vols; beat-wave marquee; cue active on line touch |
| «элементы = searchable / интервал слайдер» | Storyline: tag SearchablePicker; interval Slider 2–15с + value right |
| «Танцуем заходит за dock» | Dance/Finish: small bottom gap above CyberAcousticDock; no +18 offset into bar |
| «лейблы как Элементы/Интервал» | Field labels: AtelierColors.Soft · 11sp · SemiBold (MdFieldLabel) |
| «Завершить = Танцуем» | Live Finish same Flame shimmer CTA + placement as Dance |
| «иконки трек/голос» | Side vols: MusicNote left, Person/face right — no ALL-CAPS text |
| «play прозрачный / без затемнения круга» | Play/pause clear bg; clock brush without Black wash |
| «premium audio visualizer» | Procedural Canvas layers ACTIVE/PAUSED/IDLE; demo amp if no PCM; 60fps |
| «music pulse waveform / полоски пульса / image_2 EQ» | **Bars not path-wave:** single Canvas, 40–60 RoundCap bars (w 2–4dp, gap 2–3dp); spring ζ≈0.65; edge taper + center peaks; shimmer gradient `#050505→#FF5500→#FFD600→#FFF→…`; glow blur; Active=BPM sim / Paused=ambient min 2–4dp; replace smooth `PremiumAudioVisualizer` on Live between timer & cues |
| «5 кубов + испарение» | Cue viewport fits 5; lit→past cue evaporates (fade/remove) |
| «обмен / Settings→Настройки» | Exchange MdPageTitle + Share CTAs; Settings title RU; + square rounded; captions Title Case |
| «плавный скролл как сайты» | `rememberSoftGridFlingBehavior(friction≈0.35)` on Music + Vault |
| «редактор undo / SNAP / Safe» | Undo/Redo orange when can; **no SNAP / Safe 9:16**; vol+speed grey if element selected |
| «тренировка storyline/live» | No SPATIAL/LIVE TRAINING chrome; mode inside step 01; CTA below; live circle scrubber |
| «CapCut toolbar в редакторе» | Pro Editor: Split / Volume / Delete / Speed between timeline and dock; no Animation/Effects |
| «скорость трека / в хорео другая» | Dual speed: Song.base 0.4–2; Choreo override 0.5–2 — см. `knowledge/area_midnight_dancer/tags-not-levels-playback-speed.md` |
| «toolbar как в элементах» | Shared CatalogToolbar parity Music + Vault |
| «компактные карточки» | CompactMediaCard: edit on title row; tags bottom-end |
| «подскажи библиотеки / скопируй UI» | Point user+team at `docs/TOP_STACK_UI_LIBS.md` (append USER_HINTS) |
| «сделай по макетам сам» | Point chain at `tasks/midnight_dancer_ui_redesign` + inventory; no wait for re-upload |
| «на iPhone не так» | Remind Android-first; Codemagic IPA path; SwiftUI native (not CMP) |
| «рисуем iOS» / «готово к айфону» / «экраны SwiftUI» | Load **`ios-swiftui-feature-parity`** protocol below; chain: Architect → iOS SwiftUI (+ thin Shared aliases) → Motion → Platform Ops Codemagic KMP → Tester → Client; **no** Mobile Next until IPA in `ios-build/artifacts/current/` |
| «голос не тот» | Voice TTS skill + on-demand Piper notes |
| «медленно / jank» | Motion + wave-throughput + 120fps notes |
| «сборка IPA» / «путаница папок iOS» | Platform Ops + **`ios-build/`** (KMP vs flutter-legacy vs artifacts) + Codemagic |
| «вылетало» / «краштест» / «краевые условия» / «много элементов» / «архив GB» | Strengthen `client-review/edge-stress-qa.md` + Client must author `edge-contracts/*` + `edge-report.md`; crash = P0; loop until green before «согласовано» |
| «play/stop больше» / «кнопка тренировки» | Live ring Play/Pause hit+icon ×1.5; Android Compose + visual check |
| «.mdance» / «шифр архива» / «нельзя вытащить видео» | `midnight-kmp-core/mdance-container.md` — AES-GCM container; Hacker→Guard; no open zip export |
| «места на диске» / «Загрузить серая» / «+20%» | `disk-space-gates.md` C-109 — free vs required; button disabled |
| «баннер освободите место» / «5 Гб» / «порог в настройках» | C-110 LowDiskBanner under MdPageTitle; exclude Trainer; prefs threshold |
| «параметры импорта» / «сопоставление стилей» / «не создавать стили» | `import-package-scope.md` C-114 — таблица слева локальные / справа пакет; без галочек содержимого; всегда весь пакет |
| «лимит пакета» / «5 элементов» / «2 музыки» / «1 хорео» / «вес пакета» / «500 мб» | `package-export-limits.md` — один стиль → 5/2/1 → **жёстко 500 МБ**; элемент-видео ≤120 МБ |
| «треугольник / dropdown половины» | Export/Import style field: `fillMaxWidth(0.5f)` окна + ▼ `KeyboardArrowDown`; menu `width` = поле |
| «удаление стиля» | Settings: AlertDialog до RemoveStyle — удалятся элементы/музыка/хорео стиля |
| «10 гб элементов / не падает» | LazyVerticalGrid + Coil size decode; не грузить все видео в RAM |
| «разные стили в пакете» / «сначала стиль» / «Выбрать X/5» | C-116/C-118: один dialog стиль+выбор; текст-ссылки Flame; picker + toolbar; «Выбрать» белый |
| «укажите в какой стиль» / «Создать новый» / «dropdown до dock» | `import-package-scope.md` — «Создать новый» как заголовок колонки; dropdown к dock |
| «убери память устройства» / «не показывай свободно ГБ» | Не показывать DiskSpacePanel / блок памяти в Settings; gate внутри без UI |

## Stack anchors (do not invent)

- KMP, Decompose, SKIE, Compose, SwiftUI, Room, DataStore, Ktor, Koin, Coil 3
- Motion: springs, Rive hosts, AGSL/Metal, haptics
- TTS: `:core:tts`, Piper on-demand, System fallback
- IPA: **Codemagic** primary (`codemagic.yaml` в корне; доки/артефакты в **`ios-build/`**)
- Product: full-only `com.midnightdancer.app`

## iOS SwiftUI feature-parity protocol (волна «рисуем экраны»)

Skill: `.cursor/skills/ios-swiftui-feature-parity/SKILL.md`

1. **Bridge-only Shared** — Features/* never `@import Shared`; map full `*UiState` → pure Swift snapshots; intents via Kotlin **convenience methods** (Trainer pattern: `selectVoiceByIndex`) not fragile SKIE sealed ctor names.
2. **Must-ship screens this wave:** Splash · Elements (style split + LazyVGrid) · Music deck · Vault hub · Trainer hub+speak · Exchange (500 МБ + ▼ dropdown half-width) · Settings (style delete warn) · glass dock.
3. **Nested Pro Editor / Live session** — functional hub + open path; deep CapCut timeline can residual if hub+export+settings green.
4. **IPA:** Platform Ops adds Codemagic **KMP** workflow → artifact `ios-build/artifacts/current/`; GHA fallback. Client **source QA** on Windows; device QA only after IPA.
5. **DoD for «осталось протестировать на iPhone»:** all tabs show real Shared data + intents; Codemagic/GHA path documented; no placeholder «Tracks · no player yet» shells.

## Output format

```
🏋️ **ТРЕНЕР:**
План хода: …
Gaps: …
Дописал: …
Дальше: 🏗️ Архитектор / …
```
