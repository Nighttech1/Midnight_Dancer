# Чеклист релиза iOS (чтобы не забыть)

## Сейчас (QA / Mobile Next)

- [x] **Вариант A:** Codemagic `ios-ipa-kmp` без магазинной подписи (unsigned IPA).
- [ ] Скачать IPA → `ios-build/artifacts/current/MidnightDancer-stage4.ipa`
- [ ] Короткий прогон Mobile Next в конце UI-волны

## Перед TestFlight / App Store (обязательно вернуться)

- [ ] **Вариант B:** снова включить в Codemagic App Store signing
  - `ios_signing` + `app_store_connect` в `codemagic.yaml`
  - рабочие сертификаты/профили в Codemagic Integrations
  - Bundle ID `com.midnightdancer.app`
- [ ] Вердикт Клиента без P0/P1
- [ ] Явное «да» пользователя на prod / TestFlight
- [ ] Обновить `ios-build/kmp/CODEMAGIC.md` статус: A → B active

См. также Stage 5 в `tasks/ПЛАН_миграции_на_KMP.md`.
