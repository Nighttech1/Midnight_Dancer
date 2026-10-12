# Source QA checklist (iOS) — до IPA

Клиент на Windows проверяет **исходники** и контракт. Скрины с телефона — после IPA.

| # | Экран | Файл | Что проверить в коде |
|---|-------|------|----------------------|
| 01 | Splash + Пропустить | `SplashShellView.swift` | bootReady → CTA |
| 02 | Dock | `CyberAcousticDockView.swift` | 5 иконок, Flame selected |
| 03 | Элементы | `ElementsAtelierView.swift` | style ◀▶, LazyVGrid |
| 04 | Музыка | `MotionDeckView.swift` | список + pulse |
| 05 | Хореография | `KineticVaultView.swift` | vault + open editor |
| 06 | Тренировка | `TrainerHubView.swift` | steps + Speak + Танцуем |
| 07 | Обмен export | `SyncDeckView.swift` | ▼ half-width + 500 МБ текст |
| 08 | Обмен import | `SyncDeckView.swift` | «В приложении» ▼ |
| 09 | Настройки | `SettingsScreenView.swift` | delete style alert |

IPA артефакт: `ios-build/artifacts/current/MidnightDancer-stage4.ipa`
