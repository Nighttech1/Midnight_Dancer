# Trainer — 2026-10-12 iOS screens wave

## Plan (user)

«рисуем iOS экраны» → одна волна до готовности Клиента собирать/проверять/тикетить; после волны — приложение только протестировать на реальном iPhone. Тренер усилить команду; сначала план, потом действие. `@promts` cycle.

## Gaps closed

- Нет skill протокола iOS feature parity → добавлен `ios-swiftui-feature-parity` + строки в team-trainer.
- Shells only (placeholders) → chain C-124… на полные экраны + Shared convenience methods.
- Codemagic KMP workflow отсутствует → Platform Ops в chain.

## Honest gate

Windows ≠ IPA. DoD волны: исходники экранов + CI path; device install = Codemagic/GHA → `ios-build/artifacts/current/`.

## Handoff

Архитектор → iOS SwiftUI (+ Shared aliases) → Motion → Platform Ops → Tester → Client.
