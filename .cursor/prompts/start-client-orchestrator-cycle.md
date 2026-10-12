# Старт цикла Клиент ↔ Оркестратор (Midnight Dancer)

```text
Прочитай docs/CLIENT_ORCHESTRATOR_CYCLE.md и запусти цикл Клиент↔Оркестратор до «согласовано».

Сначала физически прочитай:
- .cursor/rules/team-org.mdc
- .cursor/rules/orchestrator.mdc
- .cursor/registry/agents.md
- .cursor/rules/trainer.mdc (+ .cursor/skills/team-trainer/SKILL.md)
- .cursor/rules/client.mdc
- .cursor/rules/architect.mdc
- .cursor/rules/kmp-shared.mdc
- .cursor/rules/android-compose.mdc
- .cursor/rules/ios-swiftui.mdc
- .cursor/rules/motion-premium.mdc
- .cursor/rules/voice-tts.mdc
- .cursor/rules/platform-ops.mdc
- .cursor/rules/hacker.mdc
- .cursor/rules/guard.mdc
- .cursor/rules/tester.mdc
- .cursor/rules/fixer.mdc
- .cursor/rules/deployer.mdc
- knowledge/area_midnight_dancer/_index.md
- tasks/ТЗ на переработку приложения с Flutter на премиальный стек KMP.md

Библиотека:
- skills: .cursor/skills/ и .agents/skills/ (в т.ч. midnight-piper-tts, team-trainer, ui-block-from-link)
- MCP: mobilenext-cloud, mobile-mcp, maestro, semgrep, firebase, storepilot, playwright* …
- plugins: plugins/ (в т.ч. plugins/trainer)
- knowledge: knowledge/engineering/ и knowledge/area_midnight_dancer/
- IPA: ios-build/ (карта) + ios-build/kmp/CODEMAGIC.md + корневой codemagic.yaml → артефакт ios-build/artifacts/current/

Цикл:
1. Клиент: цель / приёмка / тикеты C-*
2. Оркестратор: волна без лимита «3 тикета»
3. **Тренер**: план хода → грейд skills/rules/MCP → memory/team-trainer/ лог
4. Архитектор: architect-spec + chain.md до кода
5. Специалисты по chain (KMP Shared / Android Compose / iOS SwiftUI / Motion Premium / Voice TTS / Platform Ops / Хакер→Гуард)
6. Тестировщик + guards
7. Фиксер до зелёного (после жалобы — снова Тренер на gap)
8. Деплоер: APK local; IPA = Codemagic
9. Клиент снова; пока не «согласовано»

Voice TTS обязателен в chain, если тикет про Piper/Sherpa/голоса Trainer/latency TTS.
UI-ссылка пользователя → ui-block-from-link (Android-first).
```
