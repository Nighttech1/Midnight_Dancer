package com.midnightdancer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.elements.AtelierColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class VisualizerPlaybackState {
    Active,
    Paused,
    Idle,
}

/**
 * Premium multi-layer audio visualizer (C-100).
 * Demo amplitude when no PCM feed; swap [amplitudeProvider] later for real RMS/FFT.
 *
 * C-101 superseded Live training usage with [MusicPulseVisualizer] (bar EQ).
 * Kept for reuse / fallback; path-wave style, not the Live slot.
 */
@Composable
fun PremiumAudioVisualizer(
    playbackState: VisualizerPlaybackState,
    modifier: Modifier = Modifier,
    accent: Color = AtelierColors.Flame,
    height: Dp = 56.dp,
    intensity: Float = 1f,
    reducedMotion: Boolean = false,
    amplitudeProvider: AmplitudeProvider = rememberDemoAmplitudeProvider(playbackState),
) {
    val targetEnergy by animateFloatAsState(
        targetValue = when (playbackState) {
            VisualizerPlaybackState.Active -> 1f
            VisualizerPlaybackState.Paused -> 0.28f
            VisualizerPlaybackState.Idle -> 0.12f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "viz-energy",
    )
    val smoothed = remember { Animatable(0.2f) }
    var phase by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playbackState, reducedMotion) {
        if (reducedMotion && playbackState != VisualizerPlaybackState.Active) {
            smoothed.animateTo(0.15f, tween(400))
            return@LaunchedEffect
        }
        while (isActive) {
            val raw = amplitudeProvider.sample(phase)
            val attack = if (raw > smoothed.value) 0.42f else 0.12f
            smoothed.snapTo(smoothed.value + (raw - smoothed.value) * attack)
            phase += when (playbackState) {
                VisualizerPlaybackState.Active -> 0.045f
                VisualizerPlaybackState.Paused -> 0.012f
                VisualizerPlaybackState.Idle -> 0.006f
            }
            delay(16)
        }
    }

    val amp = (smoothed.value * targetEnergy * intensity.coerceIn(0.2f, 1.4f)).coerceIn(0.04f, 1f)
    val primaryPath = remember { Path() }
    val secondaryPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val w = size.width
        val h = size.height
        val mid = h / 2f
        val points = if (reducedMotion) 28 else 56

        fun fillWave(path: Path, scale: Float, freq: Float, lag: Float) {
            path.reset()
            for (i in 0..points) {
                val x = w * i / points
                val n = i.toFloat() / points
                val envelope = 0.55f + 0.45f * sin((n * PI * 2).toFloat()).let { abs(it) }
                val y =
                    mid +
                        sin((n * freq + phase + lag) * PI.toFloat() * 2f) *
                        (h * 0.38f * amp * scale * envelope) +
                        sin((n * (freq * 1.7f) - phase * 0.6f + lag) * PI.toFloat()) *
                        (h * 0.12f * amp * scale)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
        }

        fillWave(secondaryPath, scale = 0.55f, freq = 1.6f, lag = 0.35f)
        drawPath(
            path = secondaryPath,
            brush = Brush.horizontalGradient(
                listOf(
                    accent.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.18f),
                    accent.copy(alpha = 0.10f),
                ),
            ),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Soft glow under primary
        fillWave(primaryPath, scale = 1f, freq = 2.1f, lag = 0f)
        drawPath(
            path = primaryPath,
            color = accent.copy(alpha = 0.22f * amp),
            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = primaryPath,
            brush = Brush.horizontalGradient(
                listOf(
                    accent.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.85f),
                    accent,
                    accent.copy(alpha = 0.70f),
                ),
            ),
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )

        // Peak impulses
        if (playbackState == VisualizerPlaybackState.Active && amp > 0.55f) {
            val peaks = 5
            for (p in 0 until peaks) {
                val nx = ((phase * 0.4f + p * 0.19f) % 1f)
                val px = w * nx
                val ph = h * 0.15f + h * 0.35f * amp * (0.5f + 0.5f * sin(phase * 3f + p))
                drawLine(
                    color = Color.White.copy(alpha = 0.35f * amp),
                    start = Offset(px, mid - ph / 2f),
                    end = Offset(px, mid + ph / 2f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Stable
fun interface AmplitudeProvider {
    fun sample(phase: Float): Float
}

@Composable
fun rememberDemoAmplitudeProvider(
    playbackState: VisualizerPlaybackState,
): AmplitudeProvider {
    return remember(playbackState) {
        AmplitudeProvider { phase ->
            when (playbackState) {
                VisualizerPlaybackState.Active -> {
                    val kick = (sin(phase * 2.2f) * 0.5f + 0.5f)
                    val hat = abs(sin(phase * 7.1f))
                    val swell = 0.35f + 0.65f * abs(sin(phase * 0.37f))
                    (0.25f + 0.45f * kick + 0.30f * hat) * swell
                }
                VisualizerPlaybackState.Paused -> 0.18f + 0.08f * abs(sin(phase * 0.8f))
                VisualizerPlaybackState.Idle -> 0.10f + 0.04f * abs(sin(phase * 0.5f))
            }
        }
    }
}
