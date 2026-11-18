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
| «стиль не меняет элементы» / «элементы не фильтруются» | Elements: `styleId` on card + `visibleElementCards` by selected style; style change moves card |
| «блик на добавить» / «shimmer CTA» | Add button left→right light shimmer (not chase stroke) |
| «иконка тега» / «ценник» / price-tag PNG | `MdTagIcon` Flame `#FF5500` diagonal tag+hole; Music filter→tag |
| «музыка не играет» / «трек прыгает вверх» | Media3 audio; selected stays in list; Flame glow + scrub thumb |
| «сохранить хорео» / «копия хорео» / «playhead треков» | Pro Editor Save embeds; copy naming; vertical Flame playhead both lanes |
| «закрепи первые две строки» / sticky header | Elements/Music/Vault: sticky title+toolbar rows; LazyGrid/Column stickyHeader |
| «редактор на весь экран» / «9×16» | ElementEditorSheet fullscreen Dialog; video portrait 9:16; style/tag/Cancel/Save = Music TrackEditDialog |
| «флажок открывает окно» | PinFlag must stopPropagation — no card onOpen |
| «вибрация кнопок» / «haptic toolbar» | CatalogToolbar / tag / sort / dock Selection → NoOp or remove perform; keep only CTA MediumImpact |
| «прогресс изучения» / mastery sync | Editor mastery slider ↔ ElementCard.masteryPercent on cards |
| «плей в редакторе хорео» | Play advances playhead + Media3 audio + preview current element video |
| «мм.сс.мс» | Timecode MM.SS.ms (2 digits ms); speed scales music clip width |
| «сохранить → выход» | SequenceEditor Save pops to Vault hub |
| «шаги не сворачиваются» / «интервал между элементами» | Storyline accordion toggle fix both modes; label full; voice = SearchablePicker dropdown; remove voice check CTA |
| «прозрачный плей» / play glyph | All element cards: same clear glass Play (no Flame animated ring variants) |
| «Title Case добавить/поиск» | strings: «Добавить элемент», «Поиск элементов…» |
| «один шрифт» / эталон | Everywhere `MdFonts.Display` (Noto Serif); ban Monospace/default Material face except Live clock Understrukk if product requires |
| «шестерёнка везде» | Shared `MdSettingsGear` top-end on Elements/Music/Vault/Trainer hub/Exchange — not Live session |
| «загрузить трек → файл → edit» | SAF OpenDocument audio → draft track + TrackEditDialog; style+tag optional |
| «стоп оставляет scrub» | Stop keeps selected + progress; scrub while paused does not auto-play |
| «овал без тега» | Hide tag chip when tags empty |
| «удалить хорео confirm» | AlertDialog before DeleteItem |
| «создать хорео стиль+тег» | Create dialog = TrackEditDialog pickers pattern |
| «масштаб таймлайна» | CapCut 5th tool Zoom 10–100% discrete; clip widths × zoom |
| «тег вместо элементы» / «тумблер голоса» | Trainer: label «Тег»; voice = 3-way ModeToggle (Руслан/Ирина/Камила) |
| «новое splash video» / «loading на заставке» | Replace `splash_boot.mp4`; **no** Loading/Skip chrome during play; on END → Elements tab |
| «Отмена/Сохранить 1.5×» | Shared dialog action text style ~21sp / weight Bold everywhere (Elements/Music/Vault/Exchange) |
| «жёлтый избранный» / gold pin | PinFlag tint white/Flame only — never Gold CueOverlay special |
| «два элемента выделяются» | Stable LazyGrid keys; selection state by id only; no shared pressed modifier |
| «время под треком мм:сс» | Music scrub row: left position / right duration while selected |
| «таймлайн прыгает к playhead» | Pro Editor: do NOT auto-scroll timeline while user dragging; only optional follow when playing if not user-scrolling |
| «добавить музыку blocked» | CapCut/+ music enabled even when isPlaying |
| «live cue снизу вверх» / «finish» / «scrub круг» | LiveFlow: evaporating cue list scrolls up to Flame line; Finish works; ring scrub seeks freestyle playlist; leave tab stops session; play nearer center; side vols have scrub thumbs |
| «убери + с кнопок» | CTA labels without leading «+» — «Загрузить трек», «Создать хореографию» |
| «splash со 2 сек / Skip / не обрезай» | ExoPlayer seek ~2000ms; Skip visible after ready; RESIZE_MODE_ZOOM→FIT or letterbox so titles not clipped |
| «Отмена/Сохранить меньше» | Dialog CTA text back to ~14–16sp (not 21sp mega) — shared style |
| «звук заглушка splash / mock треки» | Never play `R.raw.splash_boot` as music; Media3 only real track Uri; purge seed/mock tracks; position clock = real media duration |
| «превью элемента первый кадр» | Element card Coil/VideoFrame thumbnail from video Uri — not dashed placeholder only |
| «Все теги везде» | allFiltersLabel = «Все теги» on Elements/Music/Vault/Trainer (not «Все уровни») |
| «чекбокс и название» | Choreo element picker: Spacer ≥12.dp between checkbox and title/desc |
| «верт ползунок = громкость музыки» | Pro Editor vertical volume scrub → Media3 player.volume |
| «active cue оранжевый контур» | Live cue at playhead: Flame border stroke around chip |
| «один кружок громкости» | Side vols: single thumb at current level (not dual end caps) |
| «Завершить → хаб тренировки» | Finish pops Session → Trainer hub Storyline |
| «обмен Cancel сверху» | Export section pickers: Cancel top-end like ChoreoPickers |
| «загрузить .mdance» | SAF OpenDocument mime/filter `.mdance` only; ImportParams: «Создать новый» standalone dark button; success snackbar «пакет … успешно импортирован!» |
| «.dance» / «не mdance» | Product 2026-10-16c: extension **`.dance`** (keep decrypt of legacy `.mdance` on import) |
| «пропустить резко» / «splash лагает» | Skip fade-in α0→1; defer Skip until video steady (no jank on first frame) |
| «Учесть продолжительность» | Checkbox default ON; interval **always** editable (fallback for no-video); OFF = interval only |
| «live накладываются / голос молчит» | **P0** Live: one cue active; cueStepSec timing; TTS speak current; no overlap stack |
| «вибрация мои-добавленные» | Sets hub tab chips: no haptic |
| «тап вне окна убил сет» | Create set CTA → immediate draft save «новый сет»/«новый сет N» before dialog |
| «CTA чёрный текст» | Create/Load set, Create choreo, Load track: white label + Add-element shimmer |
| «создать элемент» | Elements CTA label «Создать элемент» |
| «playhead не двигает A/V» / «play с начала» | Scrub seeks music+video even while playing; Play resumes from playhead not 0 |
| «блоки не меняются местами» | Timeline drag-reorder animated swap |
| «элементы крутятся заново» | After last element clip: empty preview (no loop) while music continues |
| «элемент/музыка стёрлись» / «пропали при смене раздела» | **P0 catalog persist:** Elements/Music Save → `SaveAppData.upsertMove/upsertSong` + `LoadLibrary.observe`; never in-memory-only catalogs; ChildPages DESTROY recreates empty unless Room hydrates; see `knowledge/area_midnight_dancer/catalog-persist-tab-survive.md` |
| «создал элемент → кинуло в Музыку» | Disable ChildPages swipe while element editor Dialog open; no accidental pager settle to Music (tab index 1) |
| «пунктир на карточке элемента» | Remove `MotionVectorOverlay` dashed chase from element cover — no animated dash paths on cards |
| «элементы не найдены» | Hide empty-state copy (`elements_empty`); empty grid = blank, not caption |
| «имя музыки редактировать» | TrackEditDialog: editable title `BasicTextField` / `OutlinedTextField`, Save persists title |
| «длительность 00:00 до плей» | Resolve duration on pick/open via MediaMetadataRetriever (or ExoPlayer prepare without play) → `UpdateTrackDuration` before first Play |
| «музыка играет в других разделах / свёрнуто» | Library audio owner above tab UI; keep playing across tabs + Activity pause; **stop** on Trainer Live start / Pro Editor Play; MediaSession / FGS if needed for background |
| «время трека высоко» | Scrub times flush under progress bar; cut ~2× time-label font height from track block |
| «имя хорео выделить» | Choreo rename field: select-all on focus (`TextFieldValue` full selection) |
| «убери синхронизация и дебаг» | Settings: remove Sync block + DEBUG EdgeStress seed card from SettingsScreen |
| «музыка с начала при смене раздела» | LibraryAudioController: do **not** re-setMediaItem/prepare/seek(0) when returning to Music if same uri still playing — only attach ticker; preserve ExoPlayer position |
| «превью грузятся каждый раз» | Element card thumbs: warm Coil/VideoFrame disk+memory cache at app start (stream); VideoFrameThumb must hit cache by uri — no re-decode on every Elements tab enter |
| «двойной клик не выделяет имя» | Shared `MdSelectAllTextField` / focus → TextFieldValue full selection; apply to **all** editable fields (Elements editor, Music edit, Choreo rename, Settings rename, Exchange names) |
| «в пикере хорео мокапы» | ChoreoCatalog must read real Room/LoadLibrary moves+songs (not MOCK_ lists); style-scoped |
| «тренировка текст с большой» | Storyline/Trainer hub labels Title Case («Интервал», «Тег», «Голос»…) |
| «бар громкости точка не там» | Live side volume: thumb Y = (1−vol)×trackHeight — same mapping as circular scrub fraction |
| «двойной клик не выделяет / только при открытии» | Select-all must work on **every** focus and **double-tap** while editing — not only `selectAllOnOpen`; avoid gesture steal; re-select on double-tap even if already focused |
| «%% ОСВОЕНО → Прогресс» | Element card mastery label: `Прогресс - N%` (not `N% ОСВОЕНО`) |
| «тайминг видео на карточке» | Element card `durationLabel` from real MediaMetadataRetriever / stored duration — not hardcoded `00:30` |
| «play хорео без звука/видео» | Pro Editor play: Media3 music at songPlaybackSpeed + preview current element video under playhead |
| «тег в окне имени хорео» | ChoreoNameStyleDialog always shows SearchablePicker «Тег» (like create flow) before/with style |
| «тренировка мокапы» | Storyline + Live: real Room elements/tracks (LoadLibrary), not MockStyles/MockTracks; Play = Media3 + TTS cues |
| «карусель тупые края» | Live cue list: fade edges (brush alpha); active cue Flame center; past cues evaporate toward top |
| «ожидание записи / заполнения на карточке» | Element card: hide awaiting/waiting caption when mastery=0 / no progress — only show Прогресс or nothing; no «ОЖИДАНИЕ…» |
| «прогресс изучения как Стиль» | ElementEditorSheet: mastery label same Flame Soft style as «Стиль»; drop «(необязательно)» suffix |
| «тап по видео карточке» | KineticElementCard: open detail/editor on tap in video cover area — not only Play button |
| «стоп на видео в редакторе» | Element editor PlayerView: no stop control; tap video while playing → pause + center Play overlay |
| «без тега в пикере» | Element/Music/Choreo tag SearchablePicker: **no** «Без тега» / «Нет тега» option — blank = no tag |
| «музыка scrub на старт» | LibraryAudioController: seek while playing must stick; do **not** re-prepare/seek(0) on ticker or remount |
| «playhead хорео не двигает A/V» | Pro Editor: vertical playhead Seek syncs **both** element ExoPlayer + music ExoPlayer to that timeline ms (one clock) |
| «чёрный экран между клипами» | Pro Editor preview: gapless clip handoff — prepare next before end; no black interstitial |
| «скорость трека в редакторе» | Pro Editor SetSongPlaybackSpeed must apply to Media3 setPlaybackSpeed + timeline widths |
| «масштаб 10% ломает таймлайн» | Timeline zoom 0.1: clips stay visible; horizontal scroll on timeline lanes — not whole screen/page |
| «тег Все в тренировке» | Storyline tag picker includes «Все» (all elements of style) |
| «куи серый текст» | Live CueCard labels: white (active brighter); never grey-on-black unreadability |
| «фристайл один элемент цикл» | Live: 1 filtered element → repeat every interval; 2+ → random pick each slot; white cue text |
| «круг перемотки не ищет» | Live ring scrub must Seek music ExoPlayer position + playheadFraction; not ignore seek while playing |
| «клиент жми кнопки» / «проверял сценарии» | **HARD GATE:** Client must open `scenario-checklist.md`, tap every control (play/pause/scrub/add/picker/back/Finish), write `scenario-report.md` PASS rows + PNGs — without that «согласовано» is forbidden; strengthen scenario-qa + client.mdc |
| «сплющивается клип» / «музыка не меняется в Live» | C-274 READY-swap no alpha; C-275 video-only picker; C-276 Live musicPlaylist from choreo snapshot |
| «обратная прокрутка зависло видео» / «ползунок возвращается» | C-277 was insufficient if Client skipped 2-clip QA; **C-279** soft handoff: swap only after READY+first frame, never timeout-swap; knowledge `choreo-reverse-scrub-soft.md` |
| «клиент жми кнопки scrub» | Client MUST reverse-scrub choreo timeline + Live ring in scenario-report before «согласовано» |
| «снова зависло при обратной» / «Клиент вернул тот же баг» | **HARD:** scenario FAIL if empty draft; require ≥2 real element videos ×3 reverse passes; reopen C-279 if freeze |
| «озвучивается уже начавшийся» / «scrub голос» | C-280: scrub settle speaks **next** cue (`sec > t`) only — not in-progress; `scrub-tts-next-cue.md` |
| «райв вместо видео» / «лого из круга» / «убери Skip» | *(legacy)* Was Canvas/Rive assemble; **2026-10-24 product:** muted `splash_boot.mp4` (trim to mid) + **no Skip** + horizontal slide to Elements |
| «видео заставка» / «обрежь до середины» / «пролистывание влево» | Replace Rive/LogoAssemble with trimmed MP4; Root `stackAnimation(slide)` Splash→Main after Exo detach; same soft feel as ChildPages tab swipe; Client shoot mid-video + swipe frame |
| «скинул видео» / «отсмотри видео» / «баги с экрана» | Client **video-review**: extract frames → tickets C-*; before «согласовано» Client must **record_screen** own repro of EACH bug and attach `visual/*.mp4` PASS — stills alone FAIL |
| «анимация без лого» / «закончись логотипом» | Storyboard 1s panels for user OK **before** Rive; end-state = app launcher logo (`ic_launcher.png` ribbons+orb) + MIDNIGHT DANCER text; parts in `memory/design/logo-parts/` |
| «сет как на макете» / «хранилище сета» / «Выбрано» | Stitch Edit-Set: content cards with section icons + storage bar  N МБ/300; Create title «Создать сет» same body as Edit |
| «поделиться пустым сетом» | Share empty → bottom warn «Сначала загрузите элементы в сет»; block share |
| «непонятен переход t2→t3» / «шрифт как на лого» / «DANCER оранжевый блик» | Splash storyboard: явный morph линии→ленты (t2.5 bridge); wordmark = `MdFonts.Display` / Noto Serif (как title app); MIDNIGHT+DANCER сначала белые → финал: солнечный блик красит **DANCER** white→Flame `#FF5500` |
| «смотрите видео хореографий» / «жал бы кнопки» / «все пользовательские сценарии» | **HARD 2026-10-24:** Client перед согласовано — полный `scenario-checklist` + матрица кнопок + **Play ≥5 с** хорео (Editor + Vault preview); без `scenario-report` PASS слово запрещено; patch client.mdc + native-device-qa + scenario-qa |
| «ломается на стыках» / «reverse scrub» / «плавная склейка CapCut» / «поехали A и B» | План §CH: волна **A+B** (`W-*-choreo-smooth-AB`, C-290 prepare-ahead+bitmap hold, C-291 scrub strip); Client gate = **CHOREO SMOOTH A+B** multi-element + record; не закрывать без ≥2 video |
| «увеличилось / встало / потом старт» / «тот же баг на стыке» / «ложный согласовано AB» | **SEAM FAIL P0:** отозвать AB PASS; не ещё dual-Exo патч; волна **C** CompositionPlayer (Media3 1.9+); Client must watch seam mp4 ×2 — «нет чёрного» ≠ PASS |
| «волна D» / «стресс» / «5+ клипов» / «добить план CH» | Волна **D**: Android stress ≥5 clips + polish; iOS AVComposition **после** Android Client; усилить Client **CHOREO STRESS D** (кнопки+видео+стыки); edge CH-HEAVY по запросу. **С 2026-10-25 D заморожена, пока не закрыта волна E** |
| «старт не с начала» / «второй стык» / «назад видео не работает» / «в самое начало» / «клиент все сценарии» | **ENGINE E (P0, 2026-10-25):** отозвать ложный Play-PASS. Не dual-Exo. Волна `W-2026-10-25-choreo-engine-e`: один clock, `seekTo(index, offset)`, кадр scrub до первого кадра, без молотка play() 250 мс. Client gate **ENGINE E** в `scenario-qa.md` целиком (≥3 видео). План §CH. Без E — запрещено «согласовано» и «качай, плеер готов» |
| «сворачивание → снова intro» / «minimize splash» | C-282: splash **cold start only**; Home→reopen keeps screen/tab |
| «чёрные блоки ещё» / «смотри стиль и трек» | Client **pixel-diff** Storyline 01/04 vs 02/03 SearchablePicker: FAIL if Mode/Voice = solid filled slabs; ModeChip must use picker field chrome (border+label), never OLED fill block; shoot 01+02+04 expanded |
| «плохо видит клиент» | Strengthen scenario-qa **Storyline chrome pixel-diff**; Client must open Style/Track as reference in same session before PASS on 01/04 |
| «вибрация при выборе элементов» хорео | ChoreoCatalogPicker: NoOpHaptic on row select / Выбрать |
| «музыка после scrub лагает навсегда» | Pro Editor: log+fix music ExoPlayer re-prepare/seek storm; scrub must not leave player in buffering thrash |
| «двойной тап едет таймлайн» | Double-tap SeekTo **without** ScrollState animateScroll / bringIntoView pan of lanes |
| «треугольники линия по периметру» | Live Δ: remove stroke outline; hatch fills whole triangle |
| «подсветка ≠ голос» | Live cue border thicker ×2; TTS speak at same frame as cue active |
| «тренировка стоп при смене раздела» | Tab leave / onStop must NOT pop/kill Live; Finish only |
| «сам скачет по разделам» | **P0** ChildPages / dock: disable scroll animation + ignore spurious select; no auto page flip |
| «параметры импорта» Наш стиль / Стиль сета | ImportParams: Стиль сета above Наш стиль; dropdown = SearchablePicker full width; «Создать новый стиль из сета» above search; drop Create button; dialog height ×1.5 |
| «клавиатура при открытии элемента» | ElementEditor NameField: **no** auto-focus / no IME on open; `selectAllOnOpen=false`; focus only on user tap |
| «избранное открывает элемент» | PinFlag must consume click (`clickable`/`pointerInput`); never call onOpen; stay on Elements list |
| «Все теги везде» (2026-10-16) | Tag dropdowns Elements/Music/Vault/Trainer/pickers/Exchange: **«Все теги»** (not «Все») |
| «хорео без Save» / draft | Leaving Pro Editor without Save still persists empty/draft choreo in Vault list |
| «лаги при подгрузке в хорео» | Pro Editor pickers: do not prepare all ExoPlayers; recycle one preview; pause off-screen |
| «разделить пополам» | SplitSelected at **playhead** inside clip (not mid duration) |
| «скорость меняет длину» | Element + music clip timeline width ∝ duration/speed |
| «резкий таймер Live» | Smooth cyclic color morph on Live clock (no hard snap between gradient ends) |
| «плашки касаются треугольников» | Live cue chips width ≤ animation track; gap from neon triangles |
| «пакет → сет / 300 мб» | `package-export-limits.md`: only 300 MB; rename пакет→сет in UI |
| «Вес сета / Выбрано N / Мои сеты» | Weight label + red>300; picker CTA «Выбрано N»; hub «Мои сеты» Vault-like CRUD + orange selection rim |
| «белый прямоугольник на видео / ripple» | Element card cover/footer/play: `clickable(indication=null)` — no Material ripple highlight before open or on Stop |
| «блик прогресса / таймер как Добавить» | MasteryBar + Live clock: cyclic Flame↔Gold↔White shimmer (Reverse, soft); same vocabulary as Add-element light band + yellow |
| «хорео лаги play A/V» | Pro Editor: one timeline clock; prepare+play music+video together at clip speeds; no re-prepare each frame; decoder reuse |
| «разделить имя · b» | Split keeps **same** label on both halves (no « · b») |
| «не делится музыка» | SplitSelected also splits **selected music** clip at playhead |
| «Выбор элементов и интервала» Title Case→Sentence | Storyline accordion titles: first capital only («Выбор элементов и интервала») |
| «применить существующий тайминг» | Storyline checkbox → per-element duration as interval; footnote about missing video → default interval |
| «треугольники как таймер + ×1.5» | Live neon Δ: same color morph + scale pulse 1↔1.5; inset from edges |
| «волна стопится на TTS» | MusicPulseVisualizer keep animating during cue speak (no pause/duck freeze) |
| «Обмен → Сеты» | Tab title «Сеты»; hub toggle Мои/Добавленные; remove Share/Load/Rollback CTAs; per-card Share; Create/Load CTA; system share sheet |
| «белый прямоугольник на видео / ripple» | Element card cover+footer `clickable(indication=null)`; no Material ripple on open/stop |
| «блик прогресса / таймер как Добавить» | MasteryBar + Live clock: cyclic Flame↔Gold↔White shimmer (Add-button style + yellow), Reverse soft morph |
| «хорео всё ещё лагает play» | Pro Editor: one clock; prefetch next clip; no re-prepare per frame; Media3 play() both; drop heavy Coil during play |
| «разделить имена · b» | Split keeps **same** label as source on both halves |
| «не делится музыка» | SplitSelected also splits **selected music** at playhead (same rules as elements) |
| «Выбор элементов и интервала» sentence case | Storyline step titles: first capital only («Выбор элементов и интервала») |
| «применить существующий тайминг» | Storyline checkbox → per-element intervals from duration; footnote if no video → use interval |
| «треугольники Live scale+цвет» | Neon Δ same color morph as clock + scale pulse ×1.5; inset from edges |
| «волна стопается на TTS» | MusicPulseVisualizer keeps animating during speak (demo amp / unduck visual) |
| «Обмен → Сеты» | Tab+title «Сеты»; toggle Мои/Добавленные; Create/Load; remove hub Share/Import/Rollback; per-card Share+Edit+Delete; share → Intent chooser |
| «splash падает / система / 10 сек → Элементы» | **P0** SplashBootHost: soft decode, onPlayerError→safe Continue; no SurfaceFlinger thrash (no per-frame TextureView matrix); never crash process; Client cold-start 3× |
| «вибрация загрузить видео» | Elements upload/CTA: NoOpHapticFeedback / remove Selection on «Загрузить» / create |
| «имя элемента выделено при открытии» | ElementEditor NameField: selectAllOnOpen=false; no auto-focus; IME only on user tap |
| «хорео музыка лаг 2 сек / прерывания scrub» | Pro Editor: seek debounce while dragging; no re-prepare per scrub tick; one clock; report root cause in wave report |
| «разделить блок с начала» | EditorClip.`sourceStartMs`; Split second half starts at cut offset; video+music alike |
| «двойной тап → playhead» | Pro Editor: double-tap gap between CapCut toolbar and timeline lanes → SeekTo(x/width) |
| «видео хорео лагает / split с начала» | Same sourceStartMs + Media3 reuse; no black handoff |
| «чёрные блоки шаги 1 и 4» | Storyline ModeToggle/VoiceSection: remove opaque black fill — match steps 2/3 chrome |
| «стоп → плей сам» | Live: session end / track end → isPaused=true (Play glyph), not stuck Stop |
| «голос говорит все при перемотке» | Live TTS: debounce scrub ≥1s settle OR cancel+speak only final cue; never queue all crossed labels |
| «тренировка стоп при смене раздела / свернуть» | Do **not** pop/pause Live on tab leave or ON_STOP; keep session background; Finish only via CTA |
| «экспорт ок снизу» | Sets hub: hide `lastActionLabel` footer (no «Export OK…» under list) |
| «сеты не сохраняются при смене раздела» | **P0** savedSets → Room/SaveAppData like catalog; survive ChildPages DESTROY |
| «карточка сета = хорео» | Sets card layout/height = VaultCard (title+actions / meta+tag) |
| «меню резко мигает экранами» | Dock tab switch: single settle, no competing AnimatedContent+pager flash; disable swipe while settling |
| «клиент жми кнопки» (усиление 2026-10-17) | Before «согласовано»: full `scenario-checklist.md` + `scenario-report.md` PASS on **every** user bullet; tap/scrub/play/split/save/back — screenshots alone = REJECT |
| «заменить видео зависло» | ElementEditor: stop+clear before setMediaItem; remount PlayerView; no duration onDraftChange loop; Client tap Replace → new preview plays |
| «хорео лаг 4–5 сек старт блока / после scrub» | Hypotheses: mid-file seek / buffer / SEEK threshold / no clipping. Prefer Media3 ClippingConfiguration per clip; scrub-end seek threshold 0; wait READY; report which H closed |
| «двойной тап playhead не работает / линия вне экрана» | Double-tap seek zone: CapCut↔timeline OR lanes; map X→fraction of **full** duration (long jump); auto-scroll playhead into view even if was off-screen |
| «чёрные блоки 1 и 4 ещё есть» | Storyline 1/4: **no** outer slab at all — chips only like step 2/3 SearchablePicker fields; Client expand 01+04 and shoot visual/ |
| «клиент не тот экран» | scenario-report must name screen title from UI tree (Тренировка ≠ Сеты); wrong tab screenshot = FAIL |
| «пропал свайп экранов» | Restore ChildPages swipe + soft animation when editors closed; never consume all horizontal nested scroll globally |
| «бесконечно меняет экраны» | P0: dock coalesce + ignore stale onPageSelected; pageStatus keep tabs CREATED (no DESTROY Live/Session) |
| «оранжевый круг превью / таймлайн слаб / double-tap улетает» | Remove Flame circle; don’t steal lane X; seek via scrollState+viewport only |
| «верни чипы режима/голоса» | ModeToggle dual + Voice PickChips — not SearchablePicker for 01/04 |
| «вибрация dropdown» | SearchablePicker → NoOpHapticFeedback |
| «голос раньше подсветки 3с» | Highlight cue then speak same tick |
| «Live → вкладки → один Фристайл» | Session survives tab leave until Finish; pageStatus CREATED |
| «свернули → перезапуск» | Resume ≠ cold splash |
| «двойной тап откатывает назад» | Suppress play ticker after user Seek; set scrub latch so stale SeekTo cannot win |
| «нет плавной склейки клипов» | Dual ExoPlayer crossfade / prepare-ahead — not only alpha dip |
| «мс на таймере при перемотке» | While scrubbing freeze timecode centiseconds |
| «элемент не в picker хорео» | Refresh ChoreoCatalog from LoadLibrary on picker open; style match + fallback |
| «выбрать хорео в тренировке» | Choreography mode step 2: tag + choreo SearchablePickers; SessionFactory from snapshot |
| «элемент сплющивается на смене» | No alpha-scale on PlayerView; wait READY then swap; keep outgoing until cut |
| «элементы без видео в picker» | elementsForStyle filters blank mediaUri |
| «в тренировке не та музыка хорео» | Live playlist = snapshot.musicClips; snapshot frozen at StartDance |

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
