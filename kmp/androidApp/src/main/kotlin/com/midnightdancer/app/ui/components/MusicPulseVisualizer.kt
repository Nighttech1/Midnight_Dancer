package com.midnightdancer.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Bar-EQ music pulse visualizer (C-101).
 * Single Canvas — never one Box/View per bar. Heights spring toward BPM targets;
 * shimmer gradient slides horizontally; soft neon glow via a wider translucent pass.
 */
@Composable
fun MusicPulseVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    playbackState: VisualizerPlaybackState? = null,
    height: Dp = 72.dp,
    barCount: Int = 50,
    bpm: Float = 128f,
) {
    val n = barCount.coerceIn(40, 60)
    val state = playbackState ?: if (isPlaying) {
        VisualizerPlaybackState.Active
    } else {
        VisualizerPlaybackState.Idle
    }

    val targets = remember(n) { FloatArray(n) { 0.08f } }
    val heights = remember(n) { FloatArray(n) { 0.08f } }
    val velocities = remember(n) { FloatArray(n) }
    var frameTick by remember { mutableIntStateOf(0) }
    val colorPhaseHolder = remember { floatArrayOf(0f) }
    val timeHolder = remember { floatArrayOf(0f) }
    val noise = remember(n) { FloatArray(n) { Random.nextFloat() } }

    // Shared ~16ms clock: update targets + spring-step heights + shimmer phase.
    LaunchedEffect(state, bpm, n) {
        val stiffness = Spring.StiffnessMediumLow // ~200
        val dampingRatio = 0.65f
        val omega = sqrt(stiffness)
        val damping = 2f * dampingRatio * omega
        val dt = 1f / 60f
        val beatPeriodSec = 60f / bpm.coerceAtLeast(60f)

        while (isActive) {
            timeHolder[0] += dt
            colorPhaseHolder[0] = (colorPhaseHolder[0] + dt / 4f) % 1f
            val t = timeHolder[0]
            val beat = (t / beatPeriodSec) * (2f * PI.toFloat())

            for (i in 0 until n) {
                val edge = 1f - (2f * i / n - 1f).let { it * it }
                targets[i] = when (state) {
                    VisualizerPlaybackState.Active -> {
                        val wave = 0.55f + 0.45f * sin(beat + i * 0.22f + noise[i] * PI.toFloat())
                        val hat = 0.35f + 0.65f * abs(sin(beat * 2.1f + i * 0.41f))
                        val kick = 0.4f + 0.6f * (0.5f + 0.5f * sin(beat))
                        (0.18f + 0.72f * wave * hat * kick * edge).coerceIn(0.06f, 1f)
                    }
                    VisualizerPlaybackState.Paused -> {
                        val jitter = 0.02f * sin(t * 1.4f + i * 0.7f + noise[i])
                        (0.06f + 0.04f * edge + jitter).coerceIn(0.04f, 0.14f)
                    }
                    VisualizerPlaybackState.Idle -> {
                        val jitter = 0.015f * sin(t * 0.9f + i * 0.55f)
                        (0.05f + 0.03f * edge + jitter).coerceIn(0.035f, 0.12f)
                    }
                }

                // Underdamped spring toward target (ζ=0.65, MediumLow stiffness).
                val x = heights[i]
                val v = velocities[i]
                val force = -stiffness * (x - targets[i]) - damping * v
                val newV = v + force * dt
                velocities[i] = newV
                heights[i] = (x + newV * dt).coerceIn(0f, 1.15f)
            }
            frameTick++
            delay(16)
        }
    }

    // Read tick in composition so Canvas redraws each ~16ms clock step.
    val tick = frameTick
    val colorPhase = colorPhaseHolder[0]

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        // Keep tick referenced so the compiler does not drop the composition read.
        if (tick < 0) return@Canvas

        val w = size.width
        val h = size.height
        val mid = h / 2f
        val minBarPx = 3.dp.toPx() // ~2–4.dp idle floor
        val maxBarPx = h * 0.92f
        val barW = 3.dp.toPx().coerceIn(2.dp.toPx(), 4.dp.toPx())
        val gap = 2.5.dp.toPx().coerceIn(2.dp.toPx(), 3.dp.toPx())
        val totalSpan = n * barW + (n - 1) * gap
        val startX = (w - totalSpan) / 2f + barW / 2f

        val shift = colorPhase * w
        val shimmerColors = listOf(
            Color(0xFF050505),
            Color(0xFFFF5500),
            Color(0xFFFFD600),
            Color(0xFFFFFFFF),
            Color(0xFFFFD600),
            Color(0xFFFF5500),
            Color(0xFF050505),
        )
        val brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(-shift, 0f),
            end = Offset(w - shift, 0f),
            tileMode = TileMode.Mirror,
        )

        // Pass 1 — soft neon glow (wider translucent strokes under bars).
        for (i in 0 until n) {
            val edge = 1f - (2f * i / n - 1f).let { it * it }
            val barH = (minBarPx + heights[i] * (maxBarPx - minBarPx)).coerceAtLeast(minBarPx)
            val x = startX + i * (barW + gap)
            val half = barH / 2f
            val glowAlpha = (0.12f + 0.28f * edge * heights[i].coerceIn(0f, 1f))
            val glowColor = when {
                edge > 0.75f -> Color.White.copy(alpha = glowAlpha * 0.85f)
                edge > 0.45f -> Color(0xFFFF5500).copy(alpha = glowAlpha)
                else -> Color(0xFFFFD600).copy(alpha = glowAlpha * 0.55f)
            }
            drawLine(
                color = glowColor,
                start = Offset(x, mid - half),
                end = Offset(x, mid + half),
                strokeWidth = barW * 2.8f,
                cap = StrokeCap.Round,
            )
        }

        // Pass 2 — crisp shimmer bars.
        for (i in 0 until n) {
            val barH = (minBarPx + heights[i] * (maxBarPx - minBarPx)).coerceAtLeast(minBarPx)
            val x = startX + i * (barW + gap)
            val half = barH / 2f
            drawLine(
                brush = brush,
                start = Offset(x, mid - half),
                end = Offset(x, mid + half),
                strokeWidth = barW,
                cap = StrokeCap.Round,
            )
        }
    }
}
