package com.midnightdancer.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * Custom motion scrollbar for Settings tags list (C-61).
 * Flame gradient thumb + spring tracking — not the stock Compose scrollbar.
 */
@Composable
fun PremiumTagScrollHost(
    modifier: Modifier = Modifier,
    flame: Color = Color(0xFFFF5500),
    content: @Composable (scrollModifier: Modifier) -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbFraction = remember { Animatable(0f) }
    val thumbSizeFraction = remember { Animatable(0.22f) }
    val glow = remember { Animatable(0.4f) }
    val maxScroll = scrollState.maxValue
    val canScroll = maxScroll > 0

    LaunchedEffect(scrollState) {
        snapshotFlow {
            val max = scrollState.maxValue.coerceAtLeast(1)
            val value = scrollState.value
            val viewportGuess = 1f
            // Approximate thumb size from overflow ratio.
            val sizeFrac = (viewportGuess / (viewportGuess + max / density.density))
                .coerceIn(0.12f, 0.42f)
            val travel = (1f - sizeFrac).coerceAtLeast(0.001f)
            val frac = (value.toFloat() / max.toFloat()).coerceIn(0f, 1f) * travel
            Triple(frac, sizeFrac, scrollState.isScrollInProgress)
        }
            .distinctUntilChanged()
            .collect { (frac, sizeFrac, scrolling) ->
                thumbFraction.animateTo(
                    frac,
                    spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
                )
                thumbSizeFraction.animateTo(
                    sizeFrac,
                    spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessLow),
                )
                glow.animateTo(
                    if (scrolling) 0.9f else 0.42f,
                    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
                )
            }
    }

    BoxWithConstraints(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (canScroll) 14.dp else 0.dp),
        ) {
            content(Modifier.verticalScroll(scrollState))
        }
        if (canScroll) {
            val trackHeightPx = with(density) { maxHeight.toPx() }
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(10.dp)
                    .padding(vertical = 8.dp)
                    .pointerInput(scrollState, trackHeightPx, maxScroll) {
                        detectVerticalDragGestures { change, _ ->
                            change.consume()
                            val y = change.position.y.coerceIn(0f, trackHeightPx)
                            val ratio = (y / trackHeightPx).coerceIn(0f, 1f)
                            val target = (ratio * maxScroll).toInt()
                            scope.launch {
                                scrollState.scrollTo(target)
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.06f),
                    topLeft = Offset(w * 0.35f, 0f),
                    size = Size(w * 0.3f, h),
                    cornerRadius = CornerRadius(w, w),
                )
                val thumbH = max(h * thumbSizeFraction.value, 28f)
                val thumbTop = (h - thumbH) * (thumbFraction.value /
                    (1f - thumbSizeFraction.value).coerceAtLeast(0.001f))
                    .coerceIn(0f, 1f)
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            flame.copy(alpha = 0.55f + glow.value * 0.3f),
                            flame.copy(alpha = 0.98f),
                            flame.copy(alpha = 0.55f + glow.value * 0.2f),
                        ),
                    ),
                    topLeft = Offset(0f, thumbTop),
                    size = Size(w, thumbH),
                    cornerRadius = CornerRadius(w, w),
                )
            }
        }
    }
}
