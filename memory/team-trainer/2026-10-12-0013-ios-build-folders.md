# Trainer — 2026-10-12 ios-build folders

## Plan

Пользователь: собрать всё про сборку iOS в отдельные папки без путаницы; контекст «согласовано» W-2026-10-11-b + handoff Mobile Next.

## Gaps

- Доки Codemagic / Mobile Next / runbook размазаны по корню, `docs/`, `dist/` (смесь APK+IPA).
- Flutter `CODEMAGIC_IOS.md` и KMP путь не разделены визуально.

## Patched

- Дом: `ios-build/` (kmp / flutter-legacy / cloud-qa / artifacts / logs / scripts).
- Stubs на старых путях; rules deployer/platform-ops/client/orchestrator + skills trainer/client-native-device-qa.
- Скрипт + GHA → `ios-build/artifacts/current/`.

## Handoff

Архитектор (chain C-123) → Platform Ops (уже сделал структуру) → Tester → Клиент (non-UI).
