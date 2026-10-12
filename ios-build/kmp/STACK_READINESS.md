# Готовность стека под iOS (обновлено 2026-10-12)

## Экраны SwiftUI (волна W-2026-10-12-ios-feature-screens)

| Экран | Статус |
|-------|--------|
| Splash + Пропустить | готово |
| Dock (glass) | готово |
| Элементы (LazyVGrid + style ◀▶) | готово |
| Музыка + pulse | готово |
| Хореография vault | готово (editor через Shared stack) |
| Тренировка hub + Speak + Танцуем | готово |
| Обмен 500 МБ + ▼ | готово |
| Настройки + warn удаления стиля | готово |

## Сборка IPA

| Канал | Где |
|-------|-----|
| Codemagic | workflow `ios-ipa-kmp` |
| GHA | `.github/workflows/kmp-ios.yml` |
| Артефакт | `ios-build/artifacts/current/MidnightDancer-stage4.ipa` |

Осталось для живого iPhone: получить IPA → Mobile Next (короткий прогон) или TestFlight.
