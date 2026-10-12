# KMP → IPA (актуальный путь)

Это **новая** сборка: общая логика на Kotlin (`kmp/shared`, `kmp/core`), экраны на SwiftUI (`kmp/iosApp`).

Не путать с [`../flutter-legacy/`](../flutter-legacy/) — там старое приложение на Flutter.

## Документы

| Файл | Содержание |
|------|------------|
| [CODEMAGIC.md](CODEMAGIC.md) | Основной путь: облачная Mac-сборка с подписью |
| [GITHUB_ACTIONS.md](GITHUB_ACTIONS.md) | Запасной unsigned IPA для Mobile Next |
| [MAC_LOCAL.md](MAC_LOCAL.md) | Сборка на своём Mac |
| [STACK_READINESS.md](STACK_READINESS.md) | Что уже общее, что дорисовать в SwiftUI |

## Исходники (не переносить сюда)

- Приложение: `kmp/iosApp/`
- Общий код: `kmp/shared/`, `kmp/core/`

## Готовый файл

После любой успешной сборки положите IPA в:

`ios-build/artifacts/current/MidnightDancer-stageN.ipa`
