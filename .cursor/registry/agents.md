# Реестр команды агентов — Midnight Dancer (KMP Premium)

## Базовая команда

| Роль | Правило | Скиллы | MCP / плагины |
|------|---------|--------|----------------|
| Клиент | `client.mdc` | `client-native-device-qa`, `client-review`, `ticket-engine`, `midnight-premium-qa` | **mobilenext-cloud**, mobile-mcp, maestro · `plugins/client` |
| Оркестратор | `orchestrator.mdc` + **`wave-throughput.mdc`** | `wave-throughput`, team-org + реестр | |
| **Тренер** | `trainer.mdc` | **`team-trainer`**, **`cyber-acoustic-motion-kit`**, **`stitch-to-kmp-ui`**, **`ui-block-from-link`**, **`store-update-data-migration`** | playwright* (референсы) · HF MCP (TTS/ML only) · `plugins/trainer` |
| Архитектор | `architect.mdc` | `architect`, `midnight-kmp-core`, **`store-update-data-migration`**, compose/kmp skills, **wave-throughput** | `plugins/architect` |
| KMP Shared | `kmp-shared.mdc` | `midnight-kmp-core`, compose-skill, kmp-ios-skills, **wave-throughput** | |
| Android Compose | `android-compose.mdc` | compose skills · **`cyber-acoustic-motion-kit`** · `stitch-to-kmp-ui` · `ui-block-from-link` | |
| iOS SwiftUI | `ios-swiftui.mdc` | swiftui / swift-kmp | |
| Motion Premium | `motion-premium.mdc` | **`midnight-premium-motion`**, **`cyber-acoustic-motion-kit`**, `stitch-to-kmp-ui` | `plugins/motion` · Rive Android 11.12+ Compose API |
| **Voice TTS** | `voice-tts.mdc` | **`midnight-piper-tts`** | `plugins/voice-tts` |
| Platform Ops | `platform-ops.mdc` | `app-updates-and-notify` | firebase, storepilot |
| **Хакер** | `hacker.mdc` | `midnight-security-hacker`, cybersecurity-skills (mobile/owasp-audit), owasp-mas-ai-skill, cyberaudit-skill | **semgrep** · `plugins/hacker` |
| **Гуард** | `guard.mdc` | `midnight-security-guard`, owasp secure-coding, **agentsec** | **semgrep** · `plugins/guard` |
| Кодер | `coder.mdc` | `coder` | |
| Тестировщик | `tester.mdc` | `tester`, `midnight-premium-qa`, **wave-throughput** | |
| Фиксер | `fixer.mdc` | `fixer`, **wave-throughput** | |
| Деплоер | `deployer.mdc` | `deployer` | storepilot |

## Типовые chains

| Задача | Chain |
|--------|-------|
| **Любой ход** | **Тренер** (грейд skills/MCP) → …далее |
| Каркас KMP | Тренер → Архитектор → KMP Shared → Тестер → Деплоер → Клиент |
| Экран UI | Тренер → Архитектор → KMP Shared → Android→iOS → Motion → Тестер → Фиксер → Деплоер → Клиент |
| **UI по ссылке** | Тренер → Архитектор → Android Compose (+ Motion) → Тестер → Клиент → позже iOS |
| **Архив Stitch** | Тренер → Архитектор → Android+Motion (дорого) → Тестер → Клиент → iOS → Codemagic |
| **Обновление из стора / данные** | Тренер → Архитектор → KMP Shared (+ Ops) → Тестер upgrade fixture → Клиент **не** принимать пустой каталог |
| **Голоса Piper** | Тренер → Архитектор → **Voice TTS** → Тестер → Клиент |
| **IPA** | Тренер → Platform Ops / Деплоер → **Codemagic** → Клиент Mobile Next |
| Пуши / сторы | Тренер → Архитектор → Platform Ops → Тестер → Деплоер → Клиент |
| **Security wave** | Тренер → Архитектор → **Хакер** (S-*) → **Гуард** → Тестер → Деплоер → Клиент |
| **Жалоба / правка** | **Тренер** (gap знаний) → Фиксер / роль владельца → Тестер → Клиент |

## Внешние security skills (GitHub)

| Пакет | Источник |
|-------|----------|
| `cybersecurity-skills` | briiirussell/cybersecurity-skills (mobile-audit, owasp-audit) |
| `owasp-mas-ai-skill` | GodModeAI2025/owasp-mas-ai-skill |
| `cyberaudit-skill` | ArisRoman/cyberaudit-skill |
| `agentsec` | semiotic-ai/agentsec (AST10 — защита наших skills) |

## MCP

| Server | Зачем |
|--------|-------|
| mobilenext-cloud | iPhone cloud |
| mobile-mcp / maestro | Android локально |
| **semgrep** | SAST для Хакера и Гуарда |
| firebase / storepilot | пуши / сторы |
| playwright* | только web |

## Важно про Хакера

Только **свой** код Midnight Dancer. Находки без exploit PoC. Гуард чинит; Хакер не «ломает прод».
