# Сборка для iPhone — одна «домашняя» папка

Здесь лежит **всё про сборку и проверку iOS**, чтобы не путать с Android и со старым Flutter.

Исходники экранов приложения по-прежнему в `kmp/iosApp/` — это код.  
Эта папка `ios-build/` — **как собрать файл для iPhone (IPA)** и куда его положить.

---

## Быстрая карта (что открывать)

| Папка | Зачем | Когда смотреть |
|-------|--------|----------------|
| **[`kmp/`](kmp/)** | Новая сборка на KMP (Kotlin + SwiftUI) | **Всегда для новых IPA** |
| **[`flutter-legacy/`](flutter-legacy/)** | Старая Flutter-сборка | Только если чините старый Flutter-пайплайн |
| **[`cloud-qa/`](cloud-qa/)** | Облачный iPhone (Mobile Next) | Когда IPA уже готов и нужен короткий прогон |
| **[`artifacts/`](artifacts/)** | Готовые файлы `.ipa` | Сюда кладём скачанный IPA |
| **[`logs/`](logs/)** | Логи Codemagic / отладка | Если сборка упала |
| **[`scripts/`](scripts/)** | Ссылки на скрипты сборки | Mac / CI |

Android-файлы (`.apk`) **не** кладём сюда — они остаются в `dist/`.

---

## Три способа получить IPA (KMP)

1. **Codemagic `ios-ipa-kmp`** — сейчас **вариант A** (unsigned, для Mobile Next); перед релизом **вариант B** (подпись) — см. [`kmp/CODEMAGIC.md`](kmp/CODEMAGIC.md) и [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md)  
2. **GitHub Actions (unsigned)** → [`kmp/GITHUB_ACTIONS.md`](kmp/GITHUB_ACTIONS.md)  
3. **Свой Mac** → [`kmp/MAC_LOCAL.md`](kmp/MAC_LOCAL.md)

С Windows IPA **нельзя** собрать локально — только облако (Codemagic / GitHub Actions) или Mac.

---

## Куда класть готовый файл

```
ios-build/artifacts/current/MidnightDancer-stageN.ipa   ← актуальный
ios-build/artifacts/archive/                            ← старые (stage1 и т.п.)
```

Не переименовывайте старый Stage 1 IPA в Stage 4 — это другая программа.

---

## Что остаётся в корне репозитория (намеренно)

| Файл | Почему в корне |
|------|----------------|
| `codemagic.yaml` | Codemagic ищет конфиг **в корне** |
| `.github/workflows/kmp-ios.yml` | Так устроен GitHub Actions |
| `scripts/build-kmp-ios-ipa.sh` | Скрипт сборки (ссылка есть и здесь) |
| `kmp/iosApp/` | Исходники iOS-приложения |

Подробности — в подпапках. Старт: **[`kmp/README.md`](kmp/README.md)**.
