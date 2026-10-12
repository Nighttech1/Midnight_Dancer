# GitHub Actions — unsigned IPA (KMP)

Нужен, когда с Windows нужен файл для **облачного iPhone** (Mobile Next сам переподпишет).

## Workflow

Файл (остаётся в стандартном месте GitHub):

`.github/workflows/kmp-ios.yml`

Имя артефакта: `MidnightDancer-stage4-ipa` → файл `MidnightDancer-stage4.ipa`.

## Как запустить

1. GitHub → Actions → **KMP iOS Stage 4 (unsigned IPA)** → Run workflow.  
2. SDK: **`iphoneos`** (не simulator — simulator на облачный телефон не ставится).  
3. Дождаться зелёного job.  
4. Artifacts → скачать IPA.  
5. Положить в:

```
ios-build/artifacts/current/MidnightDancer-stage4.ipa
```

CLI (если настроен `gh`):

```bash
gh workflow run kmp-ios.yml -f sdk=iphoneos
gh run watch
gh run download --name MidnightDancer-stage4-ipa --dir ios-build/artifacts/current
```

## Скрипт внутри job

`scripts/build-kmp-ios-ipa.sh` → пишет IPA в `ios-build/artifacts/current/`.

## Дальше

Только после файла на диске → [`../cloud-qa/MOBILE_NEXT_RULES.md`](../cloud-qa/MOBILE_NEXT_RULES.md).
