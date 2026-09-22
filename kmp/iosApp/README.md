# Midnight Dancer iOS (KMP Stage 1)

## Prefer CI / script

Unsigned IPA for Mobile Next Cloud:

```bash
# From repo root on a Mac (or via GitHub Actions macos-latest):
./scripts/build-kmp-ios-ipa.sh iphoneos
# → dist/MidnightDancer-stage1.ipa
```

Workflow: `.github/workflows/kmp-ios.yml`  
Runbook: `docs/KMP_IOS_CLOUD_RUNBOOK.md`

## Manual (Xcode)

```bash
cd kmp
./gradlew :shared:linkDebugFrameworkIosArm64   # device
# or: :shared:linkDebugFrameworkIosSimulatorArm64

cd iosApp
brew install xcodegen   # once
xcodegen generate
open MidnightDancerIos.xcodeproj
```

`FRAMEWORK_SEARCH_PATHS` points at `../shared/build/bin/iosArm64/debugFramework` (and simulator/release variants).  
Shared is a **static** Kotlin/Native framework (`baseName = Shared`) — linked via `-framework Shared`, not embedded as a separate dynamic binary.
