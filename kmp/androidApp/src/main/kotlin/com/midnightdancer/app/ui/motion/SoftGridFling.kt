package com.midnightdancer.app.ui.motion

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.abs

/**
 * Soft deceleration for Lazy grids — lower friction than stock (C-60).
 * Feels closer to premium web momentum scrolling.
 */
@Composable
fun rememberSoftGridFlingBehavior(
    /** Lower = longer coast (site-like momentum). Music/Vault catalogs use default 0.35. */
    frictionMultiplier: Float = 0.35f,
): FlingBehavior {
    val decay: DecayAnimationSpec<Float> = remember(frictionMultiplier) {
        exponentialDecay(frictionMultiplier = frictionMultiplier)
    }
    return remember(decay) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                if (abs(initialVelocity) < 1f) return initialVelocity
                var velocityLeft = initialVelocity
                var lastValue = 0f
                AnimationState(
                    initialValue = 0f,
                    initialVelocity = initialVelocity,
                ).animateDecay(decay) {
                    val delta = value - lastValue
                    val consumed = scrollBy(delta)
                    lastValue = value
                    if (abs(delta - consumed) > 0.5f) {
                        cancelAnimation()
                    }
                    velocityLeft = this.velocity
                }
                return velocityLeft
            }
        }
    }
}
