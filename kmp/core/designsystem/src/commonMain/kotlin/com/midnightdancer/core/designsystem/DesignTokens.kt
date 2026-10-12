package com.midnightdancer.core.designsystem

/**
 * Facade / module marker for `:core:designsystem` (C-20).
 *
 * Token families:
 * - [ColorTokens] — OLED True Black + neon accents
 * - [TypographyTokens] — type scale names/sizes
 * - [SpringTokens] — nav critical + interactive underdamped
 * - [HapticFeedback] / [NoOpHapticFeedback] — Stage 4 impl hook
 * - [ShellChromeTokens] / [ShellChromeSlot] — app bar / tab bar dims
 *
 * Exported to apps via `:shared` (`api(project(":core:designsystem"))`).
 */
object DesignTokens {
    const val MODULE: String = "core-designsystem"
    const val STAGE: String = "stage3-c20"
}
