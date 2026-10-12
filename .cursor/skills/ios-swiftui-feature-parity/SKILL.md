---
name: ios-swiftui-feature-parity
description: >-
  Port Android-accepted Midnight Dancer tabs to SwiftUI via SKIE bridge.
  Use when drawing iOS screens, iOS feature parity, or preparing IPA for device QA.
---

# iOS SwiftUI feature parity

## Rules

1. Android Compose is the **feel** reference — not pixel clone.
2. `Bridge/` only imports `Shared`. `Features/` gets Swift snapshots + closures.
3. Prefer Kotlin **convenience methods** on `*Component` for Swift (same as Trainer TTS).
4. Flame Amber `#FF5500`, OLED black, glass dock, MdPageTitle Title Case RU.
5. Exchange: style field `~50%` width + ▼; menu same width; `PackageExportLimits` 500 МБ text.
6. Settings: confirm before `RemoveStyle` — elements/music/choreo of that style go away.
7. Elements: LazyVGrid + small covers; never load all videos into RAM.
8. IPA home: `ios-build/artifacts/current/`. No Mobile Next without IPA.

## File map (target)

| Feature | SwiftUI |
|---------|---------|
| Chrome | `CyberAcousticDockView`, `MainShellView` |
| Elements | `ElementsAtelierView` |
| Music | `MotionDeckView` + `MusicPulseVisualizer` |
| Vault | `KineticVaultView` |
| Trainer | `TrainerHubView` + `MdSpeakZoneView` |
| Exchange | `SyncDeckView` + export/import sheets |
| Settings | `SettingsScreenView` |
| Splash | `SplashShellView` + continue |

## Bridge checklist

- [ ] Full `*UiState` fields mapped (not only `placeholderItems`)
- [ ] Intent closures wired
- [ ] Settings overlay intents
- [ ] Exchange dialog flags from state
---

## Roster

iOS SwiftUI · Motion Premium (dock springs) · Platform Ops (Codemagic KMP) · Tester · Client
