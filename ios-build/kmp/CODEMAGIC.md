# Codemagic — основной путь IPA для KMP

## Важно про путаницу

| | Старый Flutter | Новый KMP |
|--|----------------|-----------|
| Исходники | `ios/Runner` | `kmp/iosApp` |
| Инструкция | [`../flutter-legacy/CODEMAGIC_IOS.md`](../flutter-legacy/CODEMAGIC_IOS.md) | **этот файл** |
| Конфиг в корне | `codemagic.yaml` сейчас содержит **Flutter**-workflow’ы (`ios-ipa-full` и т.д.) | Нужен отдельный workflow под KMP (см. ниже) |

Файл `codemagic.yaml` **обязан** лежать в **корне** репозитория — Codemagic так настроен.  
Новые KMP-шаги добавляют **рядом** с Flutter-workflow’ами, не удаляя старые без решения продукта.

## Bundle ID

Продукт **full**: `com.midnightdancer.app` (как в Play / App Store сейчас).

## Порядок для команды

1. Экраны / логика в `kmp/iosApp` + shared готовы.  
2. В Codemagic запускается workflow **KMP** (когда добавлен в `codemagic.yaml`).  
3. Скачанный IPA кладётся в `ios-build/artifacts/current/MidnightDancer-stageN.ipa`.  
4. Только потом — Mobile Next (см. [`../cloud-qa/`](../cloud-qa/)).

## Workflow в корневом `codemagic.yaml`

Имя: **`ios-ipa-kmp`** («Midnight Dancer KMP iOS IPA»).

1. Запускает `scripts/build-kmp-ios-ipa.sh iphoneos`  
2. Кладёт IPA в `ios-build/artifacts/current/MidnightDancer-stage4.ipa`  
3. Артефакт Codemagic: `ios-build/artifacts/current/*.ipa`

Запасные каналы:

- GitHub Actions: [`GITHUB_ACTIONS.md`](GITHUB_ACTIONS.md)  
- Mac: [`MAC_LOCAL.md`](MAC_LOCAL.md)

Подпись App Store Connect — по схеме Apple; unsigned IPA подходит для Mobile Next (облако переподписывает). **Цель** — `kmp/iosApp`, не Flutter `Runner`.

## Что не делать

- Не брать Flutter IPA из `build/ios/ipa/` и не выдавать его за KMP Stage N.  
- Не держать облачный iPhone, пока Codemagic ещё крутится.
