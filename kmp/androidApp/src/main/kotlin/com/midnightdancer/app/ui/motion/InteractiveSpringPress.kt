package com.midnightdancer.app.ui.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalViewConfiguration
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.designsystem.NoOpHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Underdamped press scale ([MdMotion.interactiveUnderdamped]) with gesture velocity on release.
 * Click fires on clean release; haptic fires **after** spring settle ≈ zero velocity (C-30).
 */
fun Modifier.interactiveSpringPress(
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    hapticEvent: HapticEvent = HapticEvent.LightImpact,
    haptics: HapticFeedback = NoOpHapticFeedback,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled || onClick == null) {
        return@composed this
    }
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val touchSlop = LocalViewConfiguration.current.touchSlop

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(pressedScale, hapticEvent, onClick, haptics) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                var travel = Offset.Zero

                var pressJob: Job? = scope.launch {
                    scale.animateTo(
                        targetValue = pressedScale,
                        animationSpec = MdMotion.interactiveUnderdamped,
                    )
                }

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    tracker.addPosition(change.uptimeMillis, change.position)
                    if (change.pressed) {
                        travel += change.positionChange()
                    }
                    if (change.changedToUp()) {
                        break
                    }
                }

                pressJob?.cancel()
                pressJob = null

                val velocity = tracker.calculateVelocity()
                val speed = sqrt(velocity.x * velocity.x + velocity.y * velocity.y)
                val scaleVelocity = (speed / 3500f).coerceIn(0f, 2.5f)
                val cleanTap = hypot(travel.x, travel.y) <= touchSlop * 2.5f

                if (cleanTap) {
                    onClick()
                }

                scope.launch {
                    scale.animateTo(
                        targetValue = 1f,
                        animationSpec = MdMotion.interactiveUnderdamped,
                        initialVelocity = scaleVelocity,
                    )
                    // animateTo completes at rest ≈ spring zero-velocity.
                    if (cleanTap) {
                        haptics.perform(hapticEvent)
                    }
                }
            }
        }
}
