# Скрипты сборки iOS

Живой скрипт **не дублируем** (чтобы не разъехались версии) — он в корневом `scripts/`:

| Скрипт | Назначение |
|--------|------------|
| `scripts/build-kmp-ios-ipa.sh` | KMP unsigned IPA → `ios-build/artifacts/current/` |
| `scripts/patch_ios_flavors_pbxproj.py` | **Flutter legacy** flavors (не для KMP) |

Как запускать KMP: [`../kmp/MAC_LOCAL.md`](../kmp/MAC_LOCAL.md).
