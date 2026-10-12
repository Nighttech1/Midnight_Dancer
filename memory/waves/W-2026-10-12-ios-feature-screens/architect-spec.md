# Architect — W-2026-10-12-ios-feature-screens

## Goal

iOS SwiftUI tabs + Settings + Exchange dialogs wired to Shared so Client can review sources and, after IPA, test on real iPhone.

## Plan (порядок)

1. **Тренер** — skill `ios-swiftui-feature-parity` ✅  
2. **Shared** — convenience methods на `*Component` (паттерн Trainer TTS)  
3. **Bridge** — полные Swift snapshots + actions  
4. **SwiftUI** — Elements / Music / Vault / Trainer / Exchange / Settings / Dock / Splash  
5. **Motion** — dock springs + haptics  
6. **Platform Ops** — Codemagic workflow `ios-ipa-kmp` → `ios-build/artifacts/current/`  
7. **Tester** — guards source; GHA/Codemagic path  
8. **Клиент** — source QA; device после IPA  

## Tickets

| ID | Scope |
|----|--------|
| C-124 | Bridge snapshots + Shared aliases |
| C-125 | Elements + Music screens |
| C-126 | Vault + Trainer hub |
| C-127 | Exchange + Settings (500MB, delete warn) |
| C-128 | Dock + Splash continue |
| C-129 | Codemagic KMP IPA workflow |

## Paths

- Shared: `kmp/feature/*/logic/**` (aliases only)  
- iOS: `kmp/iosApp/**`  
- CI: `codemagic.yaml`, `ios-build/**`  

## Acceptance (Клиент)

Все 5 вкладок + Настройки показывают данные Shared и реагируют на жесты.  
Exchange: ▼ + текст 500 МБ. Settings: warn удаления стиля.  
IPA path готов; install на телефон — следующий шаг после артефакта.
