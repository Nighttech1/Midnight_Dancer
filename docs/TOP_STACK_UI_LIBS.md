# Топовые библиотеки UI / motion под стек Midnight Dancer

Сюда кидайте ссылки и помечайте, **какой UI-элемент** копировать.  
Стек: **KMP + Decompose + Jetpack Compose (Android) + SwiftUI (iOS) + Coil 3 + Media3 + Rive**.  
**Запрещено** тащить в продукт: Tailwind/WebView Stitch HTML, дешёвый Material NavigationBar, Lottie вместо Rive для героя.

---

## Как подсказывать команде

Формат одной строки:

```
ЭКРАН: Elements / Splash / Settings
ЭЛЕМЕНТ: StyleSplitSelector / Skip CTA / Tag scrollbar
ССЫЛКА: https://…
ЧТО ВЗЯТЬ: пружина / форма кнопки / scrollbar / glass
```

---

## Compose / Android (канон)

| Тема | Ссылка | Зачем нам |
|------|--------|-----------|
| Compose Animation | https://developer.android.com/develop/ui/compose/animation | springs, AnimatedContent, shared transitions |
| Compose Animation cookbook | https://developer.android.com/develop/ui/compose/animation/quick-guide | премиум переходы без «дёрганья» |
| Material 3 Expressive (только timing) | https://m3.material.io/styles/motion/overview | словарь длительностей; **цвет = Flame `#FF5500`, не M3 purple** |
| Decompose Compose | https://arkivanov.github.io/Decompose/extensions/compose/ | ChildPages / Children — SoT навигации |
| Media3 ExoPlayer | https://developer.android.com/media/media3/exoplayer | splash + element video, `RESIZE_MODE_ZOOM` |
| Media3 UI PlayerView | https://developer.android.com/media/media3/ui/compose | TextureView + overlay Skip |
| Coil 3 | https://coil-kt.github.io/coil/compose/ | обложки карточек |
| Rive Android 11+ | https://github.com/rive-app/rive-android | дорогой motion / EQ / dancer SM |
| Rive Android docs | https://rive.app/docs/runtimes/android/android | Compose API |
| Accompanist (legacy→Compose) | https://google.github.io/accompanist/ | только если нет native аналога |
| Haze / blur | https://github.com/chrisbanes/haze | frosted glass (API 31+) |
| Compose Material Icons Extended | androidx.compose.material:material-icons-extended | Chevron / Filter / Sort / Settings |

---

## Motion / haptics / scroll (дорого)

| Тема | Ссылка | Зачем нам |
|------|--------|-----------|
| Spring physics Compose | https://developer.android.com/develop/ui/compose/animation/value-based#spring | StyleSplitSelector, Skip press |
| Fling / nested scroll | https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll | мягкое ускорение/торможение сетки |
| Custom scrollbar | https://developer.android.com/develop/ui/compose/touch-input/pointer-input/scroll#scrollbars | теги в Settings — не стоковый scrollbar |
| VibrationEffect | https://developer.android.com/reference/android/os/VibrationEffect | Selection / MediumImpact на CTA |
| AGSL RuntimeShader | https://developer.android.com/develop/ui/views/graphics/agsl | neon rim / flame bloom |

---

## iOS / SwiftUI (после Android accept)

| Тема | Ссылка | Зачем нам |
|------|--------|-----------|
| SwiftUI Animation | https://developer.apple.com/documentation/swiftui/animation | parity springs |
| ultraThinMaterial | https://developer.apple.com/documentation/swiftui/view/ultrathinmaterial | glass dock |
| Rive iOS | https://rive.app/docs/runtimes/ios | `.riv` parity |
| AVPlayer | https://developer.apple.com/documentation/avfoundation/avplayer | splash / element video |

---

## KMP / data (рядом с UI)

| Тема | Ссылка | Зачем нам |
|------|--------|-----------|
| DataStore Preferences | https://developer.android.com/topic/libraries/architecture/datastore | теги/prefs переживают выход |
| Koin | https://insert-koin.io/ | DI экранов |
| SKIE | https://skie.touchlab.co/ | Swift-friendly sealed |

---

## Дизайн-референсы проекта

| Путь | Содержание |
|------|------------|
| `tasks/stitch_midnight_dancer_ui_redesign/` | Stitch PNG + HTML |
| `memory/design/stitch/2026-10-03-ui-redesign/SCREEN_INVENTORY.md` | карта экранов |
| `.cursor/skills/cyber-acoustic-motion-kit/SKILL.md` | рецепты motion 1:1 |
| Flame Amber | `#FF5500` → `#FF1A00`, OLED `#000` / `#05060C` |

---

## Анти-паттерны (Тренер запрещает)

1. Чёрная «полка» под splash вместо full-bleed video + overlay Skip.  
2. Outline-only Skip с оранжевым текстом — нужен **залитый Flame + белый текст**.  
3. Material `NavigationBar` вместо floating glass dock.  
4. Текстовые chip’ы фильтра вместо **квадратных иконок** с оранжевым контуром при active.  
5. Стоковый scrollbar в длинном списке тегов.  
6. WebView / Tailwind из Stitch HTML в runtime.

---

## Ваши подсказки (дописывайте ниже)

<!-- USER_HINTS_START -->

<!-- USER_HINTS_END -->
