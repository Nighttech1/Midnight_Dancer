# Splash boot video (iOS)

`splash_boot.mp4` — same asset as Android `res/raw/splash_boot.mp4`.

Wire in `SplashShellView` with `AVPlayer` (loop, muted) after Android Client accept of Cyber-Acoustic boot.
Add the file to the Xcode target when building IPA via Codemagic.
