package com.midnightdancer.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * C-31 Rive host slot — branded neon fallback until `.riv` assets ship.
 *
 * State-machine input names (parity with Rive SM when assets drop):
 * `intensity`, `bpm`, `touch_x`, `touch_y`. See ticket `asset-gap.md`.
 *
 * Drop files under `androidApp/src/main/assets/rive/` then swap body to Rive runtime
 * without changing call sites.
 */
enum class MdRiveKind {
    Equalizer,
    Rhythm,
    Dancer,
    ;

    val userLabel: String
        get() = when (this) {
            Equalizer -> "Эквалайзер"
            Rhythm -> "Ритм"
            Dancer -> "Танцор"
        }
}

@Composable
fun MdRiveHostSlot(
    kind: MdRiveKind,
    accent: Long = ColorTokens.NeonCyan,
    intensity: Float = 0.72f,
    bpm: Float = 108f,
    modifier: Modifier = Modifier,
) {
    val accentColor = accent.toComposeColor()
    var touchX by remember { mutableFloatStateOf(0.5f) }
    var touchY by remember { mutableFloatStateOf(0.5f) }
    val pulse = remember { Animatable(intensity.coerceIn(0.15f, 1f)) }

    LaunchedEffect(intensity) {
        pulse.animateTo(
            intensity.coerceIn(0.15f, 1f),
            animationSpec = MdMotion.interactiveUnderdamped,
        )
    }

    NeonCard(
        accent = accent,
        modifier = modifier
            .semantics {
                contentDescription = "Rive ${kind.name.lowercase()} host — branded fallback"
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val w = size.width.coerceAtLeast(1)
                    val h = size.height.coerceAtLeast(1)
                    touchX = (change.position.x / w).coerceIn(0f, 1f)
                    touchY = (change.position.y / h).coerceIn(0f, 1f)
                }
            },
    ) {
        Text(
            text = kind.userLabel,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (kind == MdRiveKind.Dancer) 112.dp else 72.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                MdRiveKind.Equalizer -> NeonEqCanvas(
                    accent = accentColor,
                    intensity = pulse.value,
                    bpm = bpm,
                    barCount = 11,
                    touchX = touchX,
                )
                MdRiveKind.Rhythm -> NeonEqCanvas(
                    accent = accentColor,
                    intensity = pulse.value,
                    bpm = bpm,
                    barCount = 9,
                    touchX = touchX,
                    mirrored = true,
                )
                MdRiveKind.Dancer -> NeonDancerSilhouette(
                    accent = accentColor,
                    intensity = pulse.value,
                    bpm = bpm,
                    touchX = touchX,
                    touchY = touchY,
                )
            }
        }
    }
}

@Composable
private fun NeonEqCanvas(
    accent: Color,
    intensity: Float,
    bpm: Float,
    barCount: Int,
    touchX: Float,
    mirrored: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "riveEq")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (60_000f / bpm.coerceIn(60f, 180f)).toInt().coerceIn(280, 900),
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "eqPhase",
    )
    val cyan = ColorTokens.NeonCyan.toComposeColor()
    val magenta = ColorTokens.NeonMagenta.toComposeColor()

    Canvas(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp)) {
        val gap = 5.dp.toPx()
        val barW = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(3f)
        val maxH = size.height
        for (i in 0 until barCount) {
            val focus = 1f - abs((i + 0.5f) / barCount - touchX) * 1.4f
            val wave = abs(sin(phase + i * 0.55f + if (mirrored) i * 0.2f else 0f))
            val level = (0.18f + wave * 0.82f) * intensity * (0.55f + 0.45f * focus.coerceIn(0.2f, 1f))
            val h = maxH * level
            val x = i * (barW + gap)
            val y = maxH - h
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(accent.copy(alpha = 0.95f), cyan.copy(alpha = 0.55f), magenta.copy(alpha = 0.25f)),
                    startY = y,
                    endY = maxH,
                ),
                topLeft = Offset(x, y),
                size = Size(barW, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }
    }
}

@Composable
private fun NeonDancerSilhouette(
    accent: Color,
    intensity: Float,
    bpm: Float,
    touchX: Float,
    touchY: Float,
) {
    val transition = rememberInfiniteTransition(label = "riveDancer")
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = (60_000f / bpm.coerceIn(60f, 180f)).toInt().coerceIn(320, 1000),
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sway",
    )
    val lime = ColorTokens.NeonLime.toComposeColor()
    val cyan = ColorTokens.NeonCyan.toComposeColor()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val cx = size.width * (0.42f + touchX * 0.16f)
        val cy = size.height * (0.55f + (touchY - 0.5f) * 0.12f)
        val amp = 10f * intensity * sway
        val stroke = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
        val glow = accent.copy(alpha = 0.35f + 0.4f * intensity)

        // Soft neon bloom behind figure
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow, Color.Transparent),
                center = Offset(cx, cy),
                radius = size.minDimension * 0.55f,
            ),
            radius = size.minDimension * 0.45f,
            center = Offset(cx, cy),
        )

        val head = Offset(cx + amp * 0.35f, cy - 38.dp.toPx())
        val neck = Offset(cx + amp * 0.2f, cy - 26.dp.toPx())
        val hip = Offset(cx - amp * 0.15f, cy + 8.dp.toPx())
        val lShoulder = Offset(cx - 16.dp.toPx() + amp, cy - 22.dp.toPx())
        val rShoulder = Offset(cx + 16.dp.toPx() + amp * 0.6f, cy - 20.dp.toPx())
        val lHand = Offset(lShoulder.x - 10.dp.toPx() - amp * 1.2f, lShoulder.y + 22.dp.toPx())
        val rHand = Offset(rShoulder.x + 14.dp.toPx() + amp, rShoulder.y - 8.dp.toPx() * intensity)
        val lFoot = Offset(hip.x - 12.dp.toPx() - amp * 0.4f, hip.y + 36.dp.toPx())
        val rFoot = Offset(hip.x + 14.dp.toPx() + amp * 0.5f, hip.y + 34.dp.toPx())

        val body = Path().apply {
            moveTo(head.x, head.y)
            lineTo(neck.x, neck.y)
            lineTo(hip.x, hip.y)
            moveTo(lShoulder.x, lShoulder.y)
            lineTo(rShoulder.x, rShoulder.y)
            moveTo(lShoulder.x, lShoulder.y)
            lineTo(lHand.x, lHand.y)
            moveTo(rShoulder.x, rShoulder.y)
            lineTo(rHand.x, rHand.y)
            moveTo(hip.x, hip.y)
            lineTo(lFoot.x, lFoot.y)
            moveTo(hip.x, hip.y)
            lineTo(rFoot.x, rFoot.y)
        }
        drawPath(body, color = accent.copy(alpha = 0.95f), style = stroke)
        drawCircle(color = lime.copy(alpha = 0.9f), radius = 6.dp.toPx(), center = head)
        drawCircle(color = cyan.copy(alpha = 0.55f), radius = 10.dp.toPx(), center = head, style = Stroke(width = 1.5.dp.toPx()))
    }
}
