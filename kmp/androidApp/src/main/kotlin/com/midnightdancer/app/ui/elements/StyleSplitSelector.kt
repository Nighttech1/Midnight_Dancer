package com.midnightdancer.app.ui.elements

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.theme.MdFonts

/** Style pill: ← centered name → (C-80). */
@Composable
fun StyleSplitSelector(
    currentStyle: String,
    onPreviousStyle: () -> Unit,
    onNextStyle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF141518),
        border = BorderStroke(0.5.dp, Color(0x26FFFFFF)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(
                onClick = onPreviousStyle,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronLeft,
                    contentDescription = "Предыдущий стиль",
                    tint = Color(0xFFFF5500),
                )
            }

            AnimatedContent(
                targetState = currentStyle,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 40.dp),
                transitionSpec = {
                    (
                        slideInHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { it } + fadeIn()
                        ) togetherWith (
                        slideOutHorizontally(
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        ) { -it } + fadeOut()
                        )
                },
                label = "StyleAnimation",
            ) { styleName ->
                Text(
                    text = styleName,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = MdFonts.Display,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            IconButton(
                onClick = onNextStyle,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = "Следующий стиль",
                    tint = Color(0xFFFF5500),
                )
            }
        }
    }
}
