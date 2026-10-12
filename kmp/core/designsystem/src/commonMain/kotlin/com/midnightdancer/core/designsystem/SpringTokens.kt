package com.midnightdancer.core.designsystem

/**
 * Cross-platform spring pair (TZ + midnight-premium-motion).
 *
 * - iOS: `Animation.spring(response:dampingFraction:)`
 * - Android Compose: `spring(dampingRatio, stiffness)`
 *
 * Motion Premium (C-24 / Stage 4) consumes these; nav uses critical damping (no overshoot).
 */
data class SpringToken(
    val id: String,
    /** Damping ratio ζ — `1.0` = critical (no bounce). */
    val dampingRatio: Float,
    /** iOS `response` — approximate settle time in seconds. */
    val responseSeconds: Float,
    /** Android Compose `stiffness` companion to [responseSeconds]. */
    val stiffness: Float,
)

object SpringTokens {
    /**
     * Navigation / sheets / tab content — critical damping (ζ ≈ 1), no overshoot.
     */
    val NavigationCritical =
        SpringToken(
            id = "navigation_critical",
            dampingRatio = 1.0f,
            responseSeconds = 0.35f,
            stiffness = 1400f,
        )

    /**
     * Buttons / cards — light underdamped overshoot (Stage 4 polish).
     */
    val InteractiveUnderdamped =
        SpringToken(
            id = "interactive_underdamped",
            dampingRatio = 0.72f,
            responseSeconds = 0.28f,
            stiffness = 900f,
        )

    val All: List<SpringToken> = listOf(NavigationCritical, InteractiveUnderdamped)
}
