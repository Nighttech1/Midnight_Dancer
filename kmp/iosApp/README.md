# Midnight Dancer iOS (KMP) — feature screens

SwiftUI tabs bind to shared `RootComponent` / feature components via SKIE + Decompose.  
**No Compose Multiplatform.** Bridge-only Shared import (`RootBridge`, helpers).

## Screens (W-2026-10-12)

| Tab | SwiftUI |
|-----|---------|
| Splash | `SplashShellView` + Continue when bootReady |
| Elements | `ElementsAtelierView` — style split, LazyVGrid |
| Music | `MotionDeckView` + `MusicPulseVisualizer` |
| Choreography | `KineticVaultView` → open editor |
| Trainer | `TrainerHubView` + speak + Dance |
| Exchange | `SyncDeckView` — 500 МБ, ▼ half-width |
| Settings | `SettingsScreenView` — style delete warn |
| Dock | `CyberAcousticDockView` |

## Build IPA

См. **`ios-build/`** (карта в `ios-build/README.md`):

- Codemagic workflow **`ios-ipa-kmp`**
- GHA `.github/workflows/kmp-ios.yml`
- Mac: `./scripts/build-kmp-ios-ipa.sh iphoneos` → `ios-build/artifacts/current/`

Windows: исходники только; Xcode/link iOS framework — на Mac/CI.

## Compile on Mac

```bash
cd kmp && ./gradlew :shared:linkDebugFrameworkIosArm64
cd iosApp && xcodegen generate && open MidnightDancerIos.xcodeproj
```
