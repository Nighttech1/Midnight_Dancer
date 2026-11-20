package com.midnightdancer.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback

/**
 * Full-width Flame CTA with white label + left→right light shimmer
 * (parity with «Создать элемент» / C-230).
 */
@Composable
fun FlameShimmerCta(
    label: String,
    haptics: HapticFeedback,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 44.dp,
) {
    val transition = rememberInfiniteTransition(label = "flame-cta-shimmer")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "cta-shimmer-shift",
    )
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(
                        listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(Color(0xFF3A3A3A), Color(0xFF2A2A2A)),
                    )
                },
            )
            .then(
                if (enabled) {
                    Modifier
                        .drawWithContent {
                            drawContent()
                            val band = size.width * 0.55f
                            val x = -band + (size.width + band) * shift.value
                            drawRect(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.White.copy(alpha = 0.55f),
                                        Color.Transparent,
                                    ),
                                    start = Offset(x, 0f),
                                    end = Offset(x + band, size.height * 0.45f),
                                ),
                            )
                        }
                        .interactiveSpringPress(
                            pressedScale = 0.96f,
                            hapticEvent = HapticEvent.MediumImpact,
                            haptics = haptics,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.45f),
            fontWeight = FontWeight.Bold,
            fontFamily = MdFonts.Display,
        )
    }
}
