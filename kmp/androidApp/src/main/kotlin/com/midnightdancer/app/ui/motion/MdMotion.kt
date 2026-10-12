package com.midnightdancer.app.ui.motion

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import com.midnightdancer.core.designsystem.SpringToken
import com.midnightdancer.core.designsystem.SpringTokens

/**
 * Compose mapping of C-20 [SpringTokens] for Motion Premium (C-24).
 * Nav / tab content uses critical damping (ζ = 1) — no overshoot.
 */
object MdMotion {
    val navigationCritical: SpringSpec<Float> =
        SpringTokens.NavigationCritical.toComposeSpring()

    /** Buttons / cards — light overshoot; pair with gesture velocity on release (C-30). */
    val interactiveUnderdamped: SpringSpec<Float> =
        SpringTokens.InteractiveUnderdamped.toComposeSpring()

    /** Pager / [AnimatedContent] fade specs share the same physics (C-24 — do not soften). */
    fun <T> navigationCriticalSpec(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = SpringTokens.NavigationCritical.dampingRatio,
            stiffness = SpringTokens.NavigationCritical.stiffness,
        )

    fun <T> interactiveUnderdampedSpec(): FiniteAnimationSpec<T> =
        spring(
            dampingRatio = SpringTokens.InteractiveUnderdamped.dampingRatio,
            stiffness = SpringTokens.InteractiveUnderdamped.stiffness,
        )
}

fun SpringToken.toComposeSpring(): SpringSpec<Float> =
    spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness,
    )
