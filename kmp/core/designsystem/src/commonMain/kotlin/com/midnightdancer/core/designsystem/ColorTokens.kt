package com.midnightdancer.core.designsystem

/**
 * Shared ARGB color tokens (`0xAARRGGBB` as [Long]).
 * Platforms map to Compose `Color` / SwiftUI `Color` — no UI types here.
 *
 * Aesthetic (TZ Stage 3): OLED True Black canvas + neon accents (spectrum-ready).
 */
object ColorTokens {
    /** OLED True Black — scaffold / window background (`#000000`). */
    const val OledTrueBlack: Long = 0xFF000000L

    /** Near-black elevated surface — cards/sheets without muddy slate gray. */
    const val SurfaceElevated: Long = 0xFF0A0A0AL

    /** Hairline / divider on OLED. */
    const val SurfaceBorder: Long = 0xFF1A1A1AL

    /** Primary neon accent (Flutter `AppColors.accent` lineage). */
    const val NeonOrange: Long = 0xFFF97316L

    /** Stronger neon orange used on active move / highlight chrome. */
    const val NeonOrangeHot: Long = 0xFFFF6B35L

    /** Spectrum neon — cyan. */
    const val NeonCyan: Long = 0xFF00E5FFL

    /** Spectrum neon — magenta. */
    const val NeonMagenta: Long = 0xFFFF2D95L

    /** Spectrum neon — lime. */
    const val NeonLime: Long = 0xFFB8FF3CL

    /** Primary content text on OLED. */
    const val TextPrimary: Long = 0xFFFFFFFFL

    /** Secondary / muted label on OLED. */
    const val TextSecondary: Long = 0xFFB0B0B0L

    /** Destructive / error accent. */
    const val Danger: Long = 0xFFFF6B6BL

    /**
     * Stage 1 stub — kept for call-site stability; prefer [OledTrueBlack] as canvas.
     * Not a scaffold background in Stage 3+.
     */
    const val MidnightNavy: Long = 0xFF0B1220L

    /** Stage 1 stub secondary accent — optional metallic highlight. */
    const val AccentGold: Long = 0xFFC9A227L
}
