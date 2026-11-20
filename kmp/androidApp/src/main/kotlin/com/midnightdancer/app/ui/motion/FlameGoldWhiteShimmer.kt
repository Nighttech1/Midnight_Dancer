package com.midnightdancer.app.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Shared cyclic Flame → Gold → White → Gold → Flame shimmer (C-222 / C-226).
 * Soft Reverse morph (~4.5s, FastOutSlowIn) — no Restart snap.
 */
object FlameGoldWhiteShimmer {
    val Flame = Color(0xFFFF5500)
    val Gold = Color(0xFFFFAE33)
    val BrightGold = Color(0xFFFFD600)

    /** Soft band vocabulary matching Add-element feel + yellow highlight. */
    val Colors: List<Color> = listOf(
        Flame,
        Flame.copy(alpha = 0.90f),
        Gold,
        BrightGold,
        Color.White,
        BrightGold,
        Gold,
        Flame.copy(alpha = 0.90f),
        Flame,
    )

    const val DurationMs: Int = 4_500

    @Composable
    fun rememberShift(label: String = "fgw-shimmer"): State<Float> {
        val transition = rememberInfiniteTransition(label = label)
        return transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(DurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "$label-shift",
        )
    }

    fun linearBrush(
        shift: Float,
        span: Float = 400f,
        vertical: Float = 80f,
    ): Brush {
        val startX = shift * span - span / 2f
        val endX = shift * span + span / 2f
        return Brush.linearGradient(
            colors = Colors,
            start = Offset(startX, 0f),
            end = Offset(endX, vertical),
        )
    }

    fun horizontalFillBrush(shift: Float, travel: Float = 400f): Brush =
        Brush.horizontalGradient(
            colors = Colors,
            startX = -travel / 2f + travel * shift,
            endX = travel / 2f + travel * shift,
        )
}
