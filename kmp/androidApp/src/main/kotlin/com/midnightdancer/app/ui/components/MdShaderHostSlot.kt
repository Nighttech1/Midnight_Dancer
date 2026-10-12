package com.midnightdancer.app.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader as AndroidShader

private val GlassShape = RoundedCornerShape(16.dp)

/**
 * C-32 — Frosted glass + procedural neon gradient host.
 *
 * - API 33+: AGSL RuntimeShader for animated neon wash + optional blur frost.
 * - API 31–32: Compose [Modifier.blur] frosted panel + Brush gradient.
 * - API 29–30: opaque glass-tint gradient (no crash; no RenderEffect).
 *
 * Prewarm runs on first composition so the first visible frame avoids shader compile jank.
 */
@Composable
fun MdShaderHostSlot(
    accent: Long = ColorTokens.NeonCyan,
    modifier: Modifier = Modifier,
    heightDp: Int = 72,
    intensity: Float = 0.75f,
) {
    val accentColor = accent.toComposeColor()
    val cyan = ColorTokens.NeonCyan.toComposeColor()
    val magenta = ColorTokens.NeonMagenta.toComposeColor()
    val lime = ColorTokens.NeonLime.toComposeColor()
    val navy = ColorTokens.MidnightNavy.toComposeColor()
    val sdk = Build.VERSION.SDK_INT

    var prewarmed by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Force first compile / pipeline touch before the slot is treated as warm.
    LaunchedEffect(sdk) {
        if (sdk >= 33) {
            runCatching {
                val warm = RuntimeShader(NEON_GRADIENT_AGSL)
                warm.setFloatUniform("resolution", 64f, 64f)
                warm.setFloatUniform("time", 0.01f)
                warm.setFloatUniform("intensity", intensity.coerceIn(0.2f, 1f))
                warm.setColorUniform("accent", accentColor.toArgbPacked())
                warm.setColorUniform("cyan", cyan.toArgbPacked())
                warm.setColorUniform("magenta", magenta.toArgbPacked())
                warm.setColorUniform("navy", navy.toArgbPacked())
                // Touch GPU by creating a blur effect once (cheap PSO warm).
                AndroidRenderEffect.createBlurEffect(8f, 8f, AndroidShader.TileMode.CLAMP)
            }
        } else if (sdk >= 31) {
            runCatching {
                AndroidRenderEffect.createBlurEffect(12f, 12f, AndroidShader.TileMode.CLAMP)
            }
        }
        // One frame after attach — lets Compose schedule the real draw path.
        view.post { prewarmed = true }
        if (!prewarmed) {
            kotlinx.coroutines.delay(16)
            prewarmed = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .clip(GlassShape)
            .border(1.dp, accentColor.copy(alpha = 0.55f), GlassShape)
            .semantics {
                contentDescription = "Премиум стекло и неоновый градиент"
            },
    ) {
        when {
            sdk >= 33 -> AgslNeonFrostLayer(
                accent = accentColor,
                cyan = cyan,
                magenta = magenta,
                navy = navy,
                intensity = intensity,
                prewarmed = prewarmed,
            )
            sdk >= 31 -> BlurFrostGradientLayer(
                accent = accentColor,
                cyan = cyan,
                magenta = magenta,
                lime = lime,
                navy = navy,
                intensity = intensity,
            )
            else -> ComposeGradientFallbackLayer(
                accent = accentColor,
                cyan = cyan,
                magenta = magenta,
                lime = lime,
                navy = navy,
                intensity = intensity,
            )
        }

        // Sharp glass veil (never blurred with the wash).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.28f),
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun AgslNeonFrostLayer(
    accent: Color,
    cyan: Color,
    magenta: Color,
    navy: Color,
    intensity: Float,
    prewarmed: Boolean,
) {
    val shader = remember { RuntimeShader(NEON_GRADIENT_AGSL) }
    val transition = rememberInfiniteTransition(label = "agslTime")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "agslT",
    )
    val safeIntensity = intensity.coerceIn(0.2f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // Soft frost pass after AGSL wash (API 33+).
                if (prewarmed) {
                    renderEffect = AndroidRenderEffect
                        .createBlurEffect(10f, 10f, AndroidShader.TileMode.CLAMP)
                        .asComposeRenderEffect()
                }
            }
            .drawWithCache {
                shader.setFloatUniform("resolution", size.width, size.height)
                shader.setFloatUniform("time", time)
                shader.setFloatUniform("intensity", safeIntensity)
                shader.setColorUniform("accent", accent.toArgbPacked())
                shader.setColorUniform("cyan", cyan.toArgbPacked())
                shader.setColorUniform("magenta", magenta.toArgbPacked())
                shader.setColorUniform("navy", navy.toArgbPacked())
                val brush = ShaderBrush(shader)
                onDrawBehind {
                    drawRect(brush = brush)
                }
            },
    )
}

@Composable
private fun BlurFrostGradientLayer(
    accent: Color,
    cyan: Color,
    magenta: Color,
    lime: Color,
    navy: Color,
    intensity: Float,
) {
    val transition = rememberInfiniteTransition(label = "blurGrad")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "blurPhase",
    )
    val amp = intensity.coerceIn(0.2f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .blur(18.dp)
            .background(
                proceduralBrush(accent, cyan, magenta, lime, navy, phase, amp),
            ),
    )
}

@Composable
private fun ComposeGradientFallbackLayer(
    accent: Color,
    cyan: Color,
    magenta: Color,
    lime: Color,
    navy: Color,
    intensity: Float,
) {
    val transition = rememberInfiniteTransition(label = "fallbackGrad")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fallbackPhase",
    )
    val amp = intensity.coerceIn(0.2f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                proceduralBrush(accent, cyan, magenta, lime, navy, phase, amp),
            )
            // Fake frost without blur — milky OLED veil.
            .background(Color.White.copy(alpha = 0.07f + 0.05f * amp)),
    )
}

private fun proceduralBrush(
    accent: Color,
    cyan: Color,
    magenta: Color,
    lime: Color,
    navy: Color,
    phase: Float,
    amp: Float,
): Brush {
    val ox = 0.5f + 0.35f * cos(phase.toDouble()).toFloat() * amp
    val oy = 0.45f + 0.30f * sin((phase * 0.85f).toDouble()).toFloat() * amp
    return Brush.linearGradient(
        colors = listOf(
            navy.copy(alpha = 0.95f),
            accent.copy(alpha = 0.55f + 0.25f * amp),
            cyan.copy(alpha = 0.45f),
            magenta.copy(alpha = 0.40f),
            lime.copy(alpha = 0.22f * amp),
            navy.copy(alpha = 0.9f),
        ),
        start = Offset.Zero,
        end = Offset(900f * ox, 420f * oy),
    )
}

/** Pack Compose [Color] into AGSL `setColorUniform` ARGB int. */
private fun Color.toArgbPacked(): Int {
    val a = (alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
    val r = (red * 255f + 0.5f).toInt().coerceIn(0, 255)
    val g = (green * 255f + 0.5f).toInt().coerceIn(0, 255)
    val b = (blue * 255f + 0.5f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

/**
 * Procedural neon wash — OLED navy base with orange/cyan/magenta accents (C-20).
 * Soft luminance veil approximates frosted glass when paired with blur.
 */
private const val NEON_GRADIENT_AGSL = """
uniform float2 resolution;
uniform float time;
uniform float intensity;
layout(color) uniform half4 accent;
layout(color) uniform half4 cyan;
layout(color) uniform half4 magenta;
layout(color) uniform half4 navy;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float t = time * 0.085;
    float wave = 0.5 + 0.5 * sin((uv.x + uv.y) * 7.5 + t * 1.7);
    float wave2 = sin(uv.x * 11.0 - t * 1.2) * cos(uv.y * 8.0 + t * 0.9);
    half4 base = mix(navy, accent, clamp(uv.y * 0.55 + wave * 0.28, 0.0, 1.0));
    half4 neon = mix(cyan, magenta, clamp(wave + wave2 * 0.2, 0.0, 1.0));
    half4 color = mix(base, neon, 0.28 + 0.32 * intensity);
    float frost = smoothstep(0.05, 0.95, uv.y) * (0.14 + 0.12 * intensity);
    color = mix(color, half4(0.88, 0.92, 1.0, 1.0), frost);
    color.a = 0.94;
    return color;
}
"""
