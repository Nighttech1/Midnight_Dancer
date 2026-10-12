# Правила и под-агенты — Midnight Dancer

## Алгоритм

1. Прочитать `.cursor/rules/orchestrator.mdc` или `node scripts/ensure-orchestrator.js`
2. Прочитать `.cursor/rules/team-org.mdc` и `.cursor/registry/agents.md`
3. Определить роли по задаче
4. Прочитать `.mdc` выбранных ролей + их skills
5. Действовать по `chain.md` (Архитектор пишет chain до кода)

## Роли

| Файл | Роль |
|------|------|
| `orchestrator.mdc` | Оркестратор (always) |
| `team-org.mdc` | Организация команды (always) |
| `client.mdc` | Клиент / приёмка (always) |
| `architect.mdc` | Архитектор |
| `kmp-shared.mdc` | Общее ядро KMP |
| `android-compose.mdc` | Android UI |
| `ios-swiftui.mdc` | iOS UI |
| `motion-premium.mdc` | Пружины / haptics / Rive / шейдеры |
| **`voice-tts.mdc`** | **Piper / Sherpa голоса** |
| `platform-ops.mdc` | Обновления / пуши / сторы |
| `hacker.mdc` | Security audit (свой код) |
| `guard.mdc` | Hardening после Хакера |
| `coder.mdc` | Мелкие правки |
| `tester.mdc` | Тесты |
| `fixer.mdc` | Фиксы |
| `deployer.mdc` | Сборки / релиз |
| `livekit-token-in-chat.mdc` | Legacy LiveKit helper (если ещё нужен) |

Цикл: `docs/CLIENT_ORCHESTRATOR_CYCLE.md` (там же — когда звать Voice TTS). Старт: файл `promts`.
