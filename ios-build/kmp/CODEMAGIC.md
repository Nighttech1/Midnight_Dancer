# Codemagic — основной путь IPA для KMP

## Важно про путаницу

| | Старый Flutter | Новый KMP |
|--|----------------|-----------|
| Исходники | `ios/Runner` | `kmp/iosApp` |
| Инструкция | [`../flutter-legacy/CODEMAGIC_IOS.md`](../flutter-legacy/CODEMAGIC_IOS.md) | **этот файл** |
| Конфиг в корне | `codemagic.yaml` Flutter-workflow’ы | KMP: **`ios-ipa-kmp`** |

Файл `codemagic.yaml` **обязан** лежать в **корне** репозитория.

## Bundle ID

Продукт **full**: `com.midnightdancer.app`.

---

## Вариант A — сейчас (unsigned, для Mobile Next)

Workflow: **`ios-ipa-kmp`** («Midnight Dancer KMP iOS IPA»).

- **Без** `ios_signing` и **без** App Store Connect integration.
- Скрипт `scripts/build-kmp-ios-ipa.sh` → unsigned/ad-hoc IPA.
- Mobile Next Cloud **сам переподписывает** при install.
- Артефакт: `ios-build/artifacts/current/MidnightDancer-stage4.ipa`

Так избегаем ошибки *No matching certificate found for every requested profile*.

## Вариант B — перед релизом / TestFlight / App Store (НЕ ЗАБЫТЬ)

Когда UI и логика приняты Клиентом и пользователь дал «да» на сторы:

1. В `codemagic.yaml` у workflow `ios-ipa-kmp` (или отдельный `ios-ipa-kmp-store`) вернуть:
   - `ios_signing: distribution_type: app_store`, `bundle_identifier: com.midnightdancer.app`
   - `integrations: app_store_connect: <имя интеграции в Codemagic>`
   - шаг `xcode-project use-profiles` + сборка с магазинной подписью
2. В Codemagic: Teams → Integrations → App Store Connect — рабочий API Key.
3. В Apple Developer: App ID + профили под `com.midnightdancer.app`.
4. Checklist в плане: [`tasks/ПЛАН_миграции_на_KMP.md`](../../tasks/ПЛАН_миграции_на_KMP.md) → **Stage 5 · Codemagic вариант B**.

До этого момента **не** включать подпись «на всякий случай» — снова упадёт сборка.

---

## Порядок для команды

1. Push `main` с актуальным yaml.  
2. Codemagic → YAML → Start build → **`ios-ipa-kmp`**.  
3. Скачать IPA → `ios-build/artifacts/current/`.  
4. Только потом Mobile Next (allocate → install → скрины → release).

Запасные каналы: [`GITHUB_ACTIONS.md`](GITHUB_ACTIONS.md), [`MAC_LOCAL.md`](MAC_LOCAL.md).

## Что не делать

- Не брать Flutter IPA (`ios-ipa-full` и т.п.) за KMP.  
- Не держать облачный iPhone, пока Codemagic крутится.  
- Не забывать вернуть **вариант B** перед TestFlight/prod.
