# Артефакты iOS (только `.ipa`)

Сюда кладём файлы для iPhone.  
Android (`.apk`) — в корневой папке `dist/`, **не сюда**.

## Структура

```
artifacts/
  current/     ← один актуальный IPA для установки / Mobile Next
  archive/     ← старые stage / исторические IPA (не путать с current)
```

## Имена

| Файл | Смысл |
|------|--------|
| `MidnightDancer-stage4.ipa` | Актуальный KMP Stage 4 (цель) |
| `MidnightDancer-stage1.ipa` | Старый Hello-shell — только в `archive/` |

**Запрещено:** переименовать stage1 → stage4 и выдавать за новую сборку.

## Откуда появляется файл

- Codemagic → скачать → положить в `current/`  
- GitHub Actions → download artifact → `current/`  
- Mac-скрипт `scripts/build-kmp-ios-ipa.sh` → пишет сразу в `current/`
