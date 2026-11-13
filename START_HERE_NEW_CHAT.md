# START HERE — передача Midnight Dancer новому чату

Контекст от предыдущего агента. Состояние после работы 12–13 ноября 2026. Владелец переносит общение в другой аккаунт GPT и просит продолжить проект с этой точки. Это памятка о решениях и доказательствах, а не разрешение на публикацию приложения или реальные списания.

## 1. Важнейшее: GitHub и проверенный локальный код различаются

**Реализация проверенной сборки пока не закоммичена и не отправлена в GitHub. Публикация этой памятки переносит контекст, но не переносит автоматически рабочий код.**

- Основной репозиторий: https://github.com/Nighttech1/Midnight_Dancer — underscore.
- Реальный рабочий проект Windows: `D:\Applications\Midnight Dancer app\Midnight_Dancer`.
- Актуальный стек: `kmp/` — Kotlin Multiplatform, Android Compose, iOS SwiftUI. GitHub README ещё описывает Flutter и не отражает всю текущую разработку.
- Папка чата в OneDrive — указатель/заметки, не исходники APK; соседние резервные копии тоже не рабочая база.
- На старте передачи локальная ветка main, локальный HEAD и удалённый main до документального коммита: `b3a748f7d26fe677206dbe1cb0f8c47f6a3bf4ac`.
- На старте подготовки было 412 записей git status, включая прежние чужие изменения и untracked-файлы. Не всё это сделано в данном чате. Массовый commit/push этого дерева не выполнялся.
- Локальные AGENTS.md и отчёты текущей волны на момент передачи не были tracked-файлами Git. Ссылки на них ниже — карта локального проекта, не обещание их наличия в remote.

Если работаешь на том же компьютере, прочитай реальный локальный проект. Если доступен только GitHub, один clone **не даёт проверенную реализацию**. Сначала сопоставь версии и организуй отдельный безопасный перенос локальных изменений с владельцем. Не объявляй новые функции отсутствующими или реализованными только по старому remote.

Перед правками проверь cwd, remote, branch и git status. Сохраняй незакоммиченную работу. Не выполняй reset/clean и разрушительное «выравнивание» репозитория. Документальный коммит памятки может быть новее локального HEAD; сначала сопоставь состояния, не затирай код.

## 2. Продукт и запрос владельца

Midnight Dancer — приложение танцевальных тренировок: элементы/видео, музыка, постановки в редакторе, обычные тренировки, голосовые подсказки, сохранение и передача сетов. Долгосрочная цель — коммерческий Android-выпуск в RuStore, затем другие подходящие площадки.

В чате просили: изучить правильный проект и продуктовый SPEC; сначала составить план; проверить текущие функции, особенно сеты; реализовать Free/Pro и отработку участка; тестировать на USB Android; показывать реальную работу команды; дать APK и подробный честный отчёт.

Общение на русском, иногда с ошибками диктовки. Пиши понятно, без лишнего жаргона. Владелец предпочитает автономное выполнение уже разрешённой работы и не любит повторных подтверждений. Уточняй необходимое, продолжая независимую работу. Ответ «готово» на просьбу разблокировать телефон означал готовность телефона, не согласование релиза.

## 3. Принятые продуктовые решения

Владелец явно подтвердил для этой волны:

- Free: одна новая собственная редактируемая постановка и обычная тренировка.
- Pro: безлимит постановок и отработка участка.
- Существующие постановки, редактирование и прежние возможности/оплаченные права не урезать.
- Цены и реальные списания пока не включать.

Legacy-защита сохраняет прежние возможности до проверки старых оплат, но не доказывает активную подписку. Gate создания/копирования работает до добавления Draft. Демо не расходуют первый слот; сохранённый пустой vault не заменяется демо.

Debug имеет явную надпись «Тестовый Pro · не подписка». Он не сохраняется как покупка, сбрасывается после перезапуска процесса и ограждён BuildConfig.DEBUG. Покупка/restore честно сообщают, что ещё не подключены. Grant/покупка сами не запускают тренировку.

## 4. SPEC и источник задания

SPEC лежит в другом репозитории — с дефисом:

https://github.com/Nighttech1/Midnight-Dancer/blob/main/team/runs/MD-202611-REHEARSAL-001/SPEC.md

Закреплённый SHA: `c0d4fc9d6f11f4e12345cd1d2619a9bce18f1f7c`. Рядом читался ENGINEERING.md. Предыдущий локальный план: `memory/waves/W-2026-11-11-rehearsal-pro/architect-spec.md`. Текущая волна: `memory/waves/W-2026-11-12-product-rehearsal/`.

Отработка: A включается, B исключается; минимум1с, N1–99 общих проходов, P0–120с отдыха, C0–10с подготовки. Подготовка один раз, отдых N−1 раз. Музыка/голос останавливаются на B. Музыкальные границы основаны на позиции плеера, таймеры — на монотонных часах. Пауза всех фаз, явное продолжение после прерывания/фона, отмена устаревших callbacks. Настройки по постановке/revision, подтверждение границ после смены материалов. Музыка текущего режима —1×. Не обещать gapless или акустическую/Bluetooth-задержку по таймеру Media3.

## 5. Как устроен настоящий цикл команды

Читать локальный AGENTS.md, `.cursor/rules/README.md`, роли/skills по задаче и актуальную волну. Не читать все внешние библиотеки подряд. AGENTS требует настоящие субагенты для инженерной работы; короткая документальная передача может выполняться главным агентом.

Цикл: план/контракт → назначение роли и путей → код → независимое ревью → конкретный дефект владельцу пути → guard/тест → сборка → установка точного APK → независимый Client GUI → отчёт/остатки.

- Один писатель на путь. Контракты согласовать до интеграции.
- Ревьюер/Клиент не принимает собственный продуктовый код.
- Не выдумывать агентов, переписку или чужие запуски. В новом аккаунте старые экземпляры агентов могут отсутствовать: назначь новые реальные роли и запиши их в chain.
- Если multi-agent tools недоступны, сказать об этом и работать честно одним исполнителем.
- Root запускал Gradle; телефон всегда у одного исполнителя. Передача ADB/UI только explicit handoff, никакого параллельного управления.
- Короткие фактические обновления примерно раз в минуту: назначение, находка/риск, исправление, доказательство повтора. AGENTS заменяет старые требования «в чат только после согласовано».
- Вести chain.md/status.md/agent-events.md; тикет закрывать с исполняемым guard, guards.md и строкой regression registry.
- PASS требует выполненной проверки. Название screenshot, снимок до завершения или наличие APK сами по себе ничего не доказывают.
- Package/bundle ID сохранять. Не clear/uninstall пользовательское приложение ради теста. Не обходить PIN и не просить код.
- Не публиковать приложение, не включать платежи и не отправлять файлы людям без соответствующей авторизации. Chooser в тестах открывали и отменяли.

Реальные участники прошлой волны:

| Исполнитель | Фактический вклад |
|---|---|
| /root | Оркестратор, этап Тренера, Android UI/audio/persistence/DI, Free/legacy/debug access, интеграция, сборки и автоматические проверки |
| /root/rehearsal_architect | Shared-контракт/контроллер/план/тесты, scrub Surface race; консультация/review crypto и доказательств |
| /root/subscription_platform_ops | Сеты, SAF, media/editor/legacy bridge, quota/rollback, контейнер, metadata-editor shared logic/guards |
| /root/protocol_reviewer | Независимое ревью и реальный Client на телефоне, без написания продуктового кода |

Не придумывать оплаченные часы/токены/стоимость: учитывались задачи и доказательства, точного финансового учёта сессии нет.

## 6. Что реализовано и исправлено

### Отработка и доступ

Shared RehearsalModels/PlanBuilder/Controller: validator, fingerprint/revision, source mapping/cues, реальные часы, cancellation/generation, pause/resume/stop, bounded readiness, access до аудио и один раз на текущую сессию. Добавлено27 shared unit-сценариев.

Android: собственный ExoPlayer, exact seek/clip mapping, проверка длительности материалов, громкость, lifecycle/focus/noisy pause, экран настройки/прогресса, persistence по постановке, Pro offer без auto-start. Голос — существующий System TTS; Piper/Sherpa-модель не добавлялась. Владелец сообщил, что голос слышен и Stop работает; это человеческое наблюдение, не acoustic metric.

### Сеты и перенос

Persistent AtomicFile metadata вместо process-only карточек; unreadable/newer schema не перезаписывается пустым списком. У каждой Imported-карточки собственный retained archive вместо глобального последнего файла. SAF читается через ContentResolver; explicit source не подменяется чужим canonical-файлом.

Точные editor snapshots/trims/speed/volume/media, legacy bridge, переносимые ссылки; только новые записи импорта привязываются к проверенным локальным файлам. ZIP paths/types/count/size/free space/SHA, media conflicts, quota/idempotency, rollback/cancellation. При неудачном recovery медиа сохраняются. Старый успешный экспорт переживает новую неудачу. Исправлены Windows absolute-path routing, Sensual/sensual picker и видимость ошибок формирования/импорта архива.

### Большие архивы

Набор176.1МиБ укладывался в300МиБ, но Android AEAD накапливал payload: header-only temp, большой heap, отказ. На телефоне измерено: default AndroidOpenSSL на4МиБ update выдаёт0bytes, всё приfinal.

MdanceContainer переведён на bundled `org.bouncycastle:bcprov-jdk15to18:1.87`, lightweight GCMBlockCipher, fixed64КиБ buffers, без global provider registration. Сохранены MDANCE1/v1, прежний ключ, IV12, tag128, отсутствие AAD. До проверки tag plaintext только в private temp, публикация atomic, предыдущий verified output сохраняется. Guards: JCA↔BC, corruption/truncate/append/wrong-key. Не писать свои криптопримитивы и не добавлять AAD вv1 без изменения протокола.

Лимит300МиБ не уменьшали. Старый получатель большого файла тоже нуждается в обновлении: wire compatibility не исправляет память его старого reader.

### Редактор полученного архива и scrub

У opaque Imported-карточки IDs пусты: прежний generic editor показывал пустой состав; rename-only не сохранялся, добавлениеC отправляло C-only вместо ожидаемого A+B+C.

C08: Imported с пустыми тремя списками IDs получает редактор имени/стиля карточки. Payload/path/bytes/origin/ID сохраняются, ZIP не запускается, остаётся «Добавленные». Content controls скрыты и guarded в component. Unknown/blank/historical style при rename не меняется; явная смена style ID обновляет только производный tag, custom tags сохраняются. Mine и старые Imported с непустыми IDs не мигрировались. Добавлено7 guards.

Отдельно исправлен первый scrub request до Surface attach, native cleanup/dispatcher lifecycle. Старый warmFrames0 воспроизведён, assertions не ослабляли.

## 7. Проверенный артефакт и сила доказательств

APK локальный; GitHub Release/APK в этой сессии не публиковался:

- `dist/MidnightDancer-0.5.0-rehearsal-archive-card-fix-debug.apk`.
- SHA-256 `AA52B85D35F400E26E3E18B584FA542DBEE21759BC44A4FF3E20454030895CAE`.
- 99 992 016 байт; versionName0.5.0-rehearsal-preview, versionCode3; packagecom.midnightdancer.app.
- Установлен поверх без очистки на Redmi23117RA68G/Android13.
- Локальный source-manifest-final.sha256:375 KMP-файлов,375/375 совпадений, независимый proof-check.

`final-wave-build-tests.log`: BUILD SUCCESSFUL3m46s. Актуальные XML:

| Модуль | Tests | Failures/errors |
|---|---:|---:|
| trainer | 38 | 0 |
| choreography | 44 | 0 |
| exchange | 20 | 0 |
| core/data | 85 | 0 |
| Всего | 187 | 0 |

Это не187 свежих исполнений: trainer/choreography/data UP-TO-DATE, exchange исполнялся заново. Ранее полный170-тестовый прогон — отдельная история.

На AA52 один `final-device-tests.log`:13/13 PASS,76.919s, музыка/focus/video/scrub/persistence/bridge/crypto. Реальные21проход/20переходов: starts21×0ms, endmax20ms, assertions≤150ms. Это Media3 position, не звук/Bluetooth.

Native184МиБ exact hash roundtrip:31.524s, sampled Java heap growth786432bytes — не полный RSS/PSS и не каждый transient peak. Standalone192МиБ exact roundtrip под java -Xmx64m:13.913s на неизменённом crypto коде.

Три локальных Client-отчёта:

1. client-saf-final-report.md:503989, small6.2MB roundtrip/error/no partial mutation/restart; большой экспорт тогдаFAIL.
2. client-container-final-report.md:3F91, actual176.1МиБ export→chooser без отправки→Download→системный SAF import→media hashes→restart; oldsmall после bigglobal передал собственный архив. Debug grantOFF в подготовке позволил закончить сессию, следующийStart gated; rapidstop/short rehearsal.
3. client-archive-card-final-report.md:AA52, rename/styleBachata→Imported→restart, retained/sharedSHA неизменны, normalMine controls/Cancel, short35..38/N2/P1/C1 Completed2/2 иRepeatStopReady.

Между3F91 иAA52 изменены только4 metadata/UI файла и новый тест. MdanceContainer/exporter/DI/BC/audioadapter по хэшам не менялись. Не подменять «bigGUI на3F91» утверждением «повторён наAA52»: наAA52 свежие native crypto и отдельный свой GUI scope.

Пять исходных постановок сохранились побайтно. QA-карточки/настройки менялись для проверки; originals c1..c5 и исходные медиа не редактировались.

## 8. Карта локальной документации и кода

От корня рабочего проекта (наличие в GitHub отдельно проверить):

- AGENTS.md; .cursor/rules/README.md; .cursor/registry/agents.md; .cursor/skills/.
- memory/waves/W-2026-11-12-product-rehearsal/FINAL_REPORT.md — полный результат.
- В той же волне RESUME.md/status.md/chain.md/agent-events.md/scenario-report.md/AC-COVERAGE-2026-11-13.md, три Client-отчёта, final-artifact.json и source manifest.
- memory/tickets/C-20261112-01..05, C-20261113-06..08; C-save/C-share/C-saf/C-vault; .cursor/skills/client-review/regression-guards.md.
- memory/tickets/C-20261113-07/crypto-plan.md — primary sources и wire contract.
- feature/trainer/logic: RehearsalModels/PlanBuilder/Controller и tests.
- androidApp: RehearsalAndroidAdapters/RehearsalScreen/AndroidRehearsalSettingsRepository, ProductAccessRuntime/ProOfferDialog/DI.
- feature/exchange/logic: ExchangeComponent/Models/SavedSetsPersister/ImportedArchiveMetadataTest.
- core/data: AndroidDebugExchangeZipUseCase/AndroidExchangeArchiveFiles/MdanceContainer/whitelist/DI/tests.
- androidApp: AndroidSavedSetsPersister/AndroidChoreographyExchangeBridge/ScrubVideoDecoder/KineticSyncDeckScreen/ExportPackageDialog/MySetsScreen/device tests.

Сокращённые пути уточняй через rg --files. Не считай локальные отчёты опубликованными только по названию.

## 9. Телефон, сборки и сохранность

ADB прежнего компьютера: `D:\Applications\Android\Sdk\platform-tools\adb.exe`. Serial получить через `adb devices -l`, устройство не угадывать. Владелец разрешал USB debug/install и сам подтверждал PIN/отпечаток.

На Xiaomi streaming install давал INSTALL_FAILED_USER_RESTRICTED даже при включённой USB-установке. Рабочий вариант: adb install --no-streaming -r APK; test APK — --no-streaming -r -t. Package ID сохранять, данные не удалять.

Windows Gradle сталкивался с JAR/Kotlin-daemon locks. Из kmp запускали последовательно:

```powershell
.\gradlew.bat :feature:trainer:logic:testDebugUnitTest :feature:choreography:logic:testDebugUnitTest :feature:exchange:logic:testDebugUnitTest :core:data:testDebugUnitTest :androidApp:assembleDebug :androidApp:assembleDebugAndroidTest --no-daemon --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.vfs.watch=false'
```

Не запускать два Gradle одновременно; сначала исключить инфраструктурный lock, не убивать чужие Java процессы. GRADLE_USER_HOME может быть вне проекта, проверь окружение.

Instrumentation runner: com.midnightdancer.app.test/androidx.test.runner.AndroidJUnitRunner. Устанавливать именно выдаваемый APK и соответствующий test APK. Classes: ChoreoPlaybackSmokeTest, ChoreoScrubStressTest, audio.RehearsalPlaybackDeviceTest, exchange.ExchangePersistenceDeviceTest, exchange.MdanceContainerDeviceTest.

Локальный device_qa.py читает accessibility tree/управляет ADB, содержит старый serial — перепроверить. IME/анимации/offscreen bounds учитывать; отказ helper нажать невидимую кнопку не баг приложения.

Baseline APK/data backup и private media/XML/logs/visual/device-private остаются локально и исключены из Git. В публичную памятку не переносить содержимое телефона, ключи, APK и приватные архивы.

Последнее состояние телефона — историческое: Sets→Added, playbackнет, c1A35/B38/N2/P1/C1voice-on, testgrantfalse послеrestart. QA MineMD-QA-12, ImportedMD-QA-12-big, ImportedMD-QA-12-renamed/Bachata и тестовые архивы оставлены. Подключение/разблокировку в новом чате проверять заново. Напоминание13ноября19:00 выполнено и удалено.

## 10. Остатки — это ещё не коммерческий релиз

- Реальные purchase/restore, merchant/SKU/prices/backend validation/notifications/receipt migration не подключены. Release signing/store sandbox не проверены. Требования RuStore/других площадок и SDK перепроверить по актуальным официальным источникам при коммерческой интеграции; прежнее исследование не равно доступу к кабинету.
- Fresh-install/cross-device не выполнены; same-install GUI, hashes и isolated adapter tests не равны независимой установке.
- Не вся ручная AC01–12/старая CH-матрица закрыта: composite music trim/seam, длинные/близкие реальные фразы, call/noisy, акустика/Bluetooth, некоторые input/revision cases.
- Lock/resume прерван secure keyguard и аннулирован updates/instrumentation: PENDING, неPASS. Home/background/native focus отдельно проверены. Новый lock-test проводить с владельцем рядом, код не вводить.
- Один ранний15s audio stall503989 не объяснён последующими PASS. Test не wiring interruption callback, UI wiring есть — гипотеза, не доказанная причина. При повторении собрать playerstate/suppression/pass/counts доcleanup; assertions/timeouts не ослаблять.
- Persist внутри StateFlow.update допускает повторную metadata write при CAS retry(P2), archive corruption не доказана. Один write не выносить без общего сериализатора.
- Durable journal media/Room/vault при kill посередине commit нет. API-only choreographyOnly ghost-merge имеет media-remap ограничения; GUIAll/missingmedia fail-closed.
- Лимит300МиБ сохранён. Полнаяc1оценена302.3МиБ и отклоняется; успешный176.1МиБ набор не доказывает перенос всейc1.

## 11. Первые шаги нового агента

1. Прочитай памятку, найди реальный локальный проект, проверь remote/local/status. Назови рабочую папку и сохрани dirtytree.
2. Прочитай локальные FINAL_REPORT/status/RESUME/AGENTS. Если есть только GitHub — честно обозначь, что проверенный локальный код ещё не перенесён, и реши его перенос отдельно.
3. Подтверди владельцу этап: проверочная сборка технически проверена, магазинный выпуск ещё не готов. Не повторяй сделанные C06/C07/C08 без новых изменений/симптомов.
4. Уточняй только реально недостающий следующий приоритет. Для коммерции нужны параметры провайдера/аккаунта/подписи/цен; сначала подготовить конкретный результат для просмотра. Публикация требует отдельного явного согласования.
5. Продолжай нужные волны из memory/waves по приоритету владельца. Не утверждай, что старые авторизации, живые агенты, локальные артефакты или новые запуски автоматически перенеслись в другой аккаунт.

Короткий стартовый запрос: «Прочитай START_HERE_NEW_CHAT.md в Nighttech1/Midnight_Dancer. Это передача предыдущего чата. Сначала проверь доступность актуального локального проекта и незакоммиченных изменений, затем продолжи с сохранённой точки. Не перепутай старый GitHub main с проверенной сборкой AA52».
