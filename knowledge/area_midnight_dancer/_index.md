# Midnight Dancer — домен команды

## Продукт

Мобильное музыкально-визуальное приложение. Реинжиниринг с Flutter на **Kotlin Multiplatform + Jetpack Compose + SwiftUI + SKIE**.

## Цели UX

- Ощущение «нативного флагмана», 120 FPS.
- Пружинная кинематика + точные haptics.
- Rive + аппаратные шейдеры (AGSL / Metal).
- Стабильные обновления приложения и ОС, in-app update, push-уведомления.

## Источник правды

Полное ТЗ: `tasks/ТЗ на переработку приложения с Flutter на премиальный стек KMP.md`.  
Пошаговый трекер с чекбоксами: `tasks/ПЛАН_миграции_на_KMP.md`.

## Голоса (Piper / Sherpa)

**Продукт (с 2026-09-22):** только сборка **full** — три голоса Руслан / Ирина / Камила.  
ADR: `memory/decisions/D-2026-09-22-full-only-product.md`.  
lite / standard / english как отдельные KMP-продукты **не ведём**.

Сейчас во Flutter ещё лежит старый flavor-код: `lib/data/services/tts_*.dart`, модели в `assets/voices/` (в т.ч. lite/standard папки — legacy).  
Улучшения — роль **Voice TTS**. При миграции на KMP — порт full-набора.

## Security

Волны: Хакер (находки) → Гуард (hardening). Только свой продукт.
