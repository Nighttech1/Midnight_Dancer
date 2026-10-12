# Boot / splash video

| Platform | Path |
|----------|------|
| **Android (runtime)** | `kmp/androidApp/src/main/res/raw/splash_boot.mp4` |
| iOS (asset staged) | `kmp/iosApp/iosApp/Resources/video/splash_boot.mp4` |
| Design archive copy | `tasks/midnight_dancer_ui_redesign/assets/splash_boot.mp4` |

Source file from user: `icon video Midnight Dancer.mp4`.

Android plays it muted + looping on Splash via Media3 (`SplashBootVideo`). Tap → enter Main.
iOS: file staged; wire `AVPlayer` in `SplashShellView` after Android accept.

Do **not** put README files under `res/raw/` (breaks resource merge).
