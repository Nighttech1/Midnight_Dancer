# Локальная сборка IPA на Mac (KMP)

Нужны: macOS, Xcode, JDK 17+, [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`).

## Команда

Из корня репозитория:

```bash
chmod +x scripts/build-kmp-ios-ipa.sh
./scripts/build-kmp-ios-ipa.sh iphoneos
```

Результат:

```
ios-build/artifacts/current/MidnightDancer-stage4.ipa
```

Другое имя:

```bash
IPA_NAME=MidnightDancer-custom.ipa ./scripts/build-kmp-ios-ipa.sh iphoneos
```

Симулятор (не для Mobile Next Cloud):

```bash
./scripts/build-kmp-ios-ipa.sh iphonesimulator
```

## Кратко что делает скрипт

1. Gradle: линкует `Shared.framework` под iOS  
2. Кладёт framework в `kmp/iosApp`  
3. XcodeGen → `xcodebuild` без магазинной подписи  
4. Упаковывает `.app` в `.ipa`

## Ссылка на скрипт из этой папки

См. [`../scripts/README.md`](../scripts/README.md).
