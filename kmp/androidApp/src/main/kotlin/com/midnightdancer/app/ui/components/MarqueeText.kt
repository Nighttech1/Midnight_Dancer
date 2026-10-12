package com.midnightdancer.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.theme.MdFonts
import kotlin.math.max

/**
 * Bidirectional marquee for playing track title (C-82).
 * Text slides left↔right between edges when [enabled] and text overflows.
 */
@Composable
fun MarqueeText(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    var containerW by remember { mutableFloatStateOf(0f) }
    var textW by remember { mutableFloatStateOf(0f) }
    val travel = max(0f, textW - containerW + 8f)
    val animate = enabled && travel > 4f

    val infinite = rememberInfiniteTransition(label = "marquee")
    val shift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "marquee-x",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerW = it.width.toFloat() },
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MdFonts.Display,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .onSizeChanged { textW = it.width.toFloat() }
                .graphicsLayer {
                    translationX = if (animate) -(shift * travel) else 0f
                },
        )
    }
}
