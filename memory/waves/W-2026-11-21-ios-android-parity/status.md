# Status

The Android reference is the accepted 0.5.0 APK captured locally on Android Emulator. iOS candidates run in Codemagic on a fresh iPhone 17 Pro simulator with iOS 26.5. Installations and artifacts here are internal QA.

| Revision | Evidence | Result |
|---|---|---|
| f245938 | [Codemagic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01abbe8f52ca275129bde1) | First UI test passed (intro, five tabs, Settings). The recording exposed a white system launch frame. |
| 68c8b43 | [Codemagic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01aff98f52ca275129bf61) | Native build failed because Okio ZIP support is unavailable on iOS. |
| e436b9f | [Codemagic diagnostic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01b2058f52ca275129c030) | One UI test passed, zero failures, 12 named screenshots. Real recording shows the black system launch and original Android intro. Element and choreography editors opened. Diagnostic branch excludes the broken ZIP changes. |
| bc45ec6 | [Codemagic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01b4f58f52ca275129c15a) | Kotlin completed through framework linkage with native ZIP. Swift failed to resolve the internal archive protocol. |
| 26b802f | [Codemagic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01b6c28f52ca275129c1fa) | The app compiled through Kotlin and Swift. The separate CryptoSwift XCTest bundle lacked the Shared framework search path. Added the Swift-facing archive protocol, catalog filters/sort, styled music editor, existing-library creation protection and a Photos import plus restart scenario. |
| e7f7234 | [Codemagic simulator](https://codemagic.io/app/69da4fab6ca45d8302fb5731/build/6b01b8fa8f52ca275129c2bd) | Added the framework search path for the crypto unit target, archive preparation and a native Share sheet. Run started. |
| 1a725b2 | [GitHub parity branch](https://github.com/Nighttech1/Midnight_Dancer/tree/beta/ios-android-parity) | Added relocatable iOS archive/media references, an import transaction behind an uninstalled writer interface, and Kotlin/Native test invocation. |

Host JVM runs report 133 passing cases across Settings, Choreography, Trainer, Music and Exchange. The data module's previously generated Gradle tests are up to date. These runs do not execute iOS playback or TestFlight upgrade.

The signed physical-device upgrade and the actual set import transaction remain release gates. No external TestFlight distribution was made in this wave.
