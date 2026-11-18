package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.activity.compose.BackHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.core.domain.exchange.StyleRef

/**
 * The menu is part of the layout below its field: IME/window changes cannot flip it above.
 * Bounded inner scrolling keeps the whole style list reachable inside the dialog.
 *
 * @param widthFraction доля ширины родителя (0.5 = половина окна «Поделиться пакетом»).
 */
@Composable
fun FlameStyleDropdownField(
    displayName: String,
    styles: List<StyleRef>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (StyleRef) -> Unit,
    flame: Color,
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    menuMaxHeight: Dp = 320.dp,
) {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = expanded) { onExpandedChange(false) }
    val screenHalf = LocalConfiguration.current.screenWidthDp.dp * 0.5f
    BoxWithConstraints(modifier = modifier.fillMaxWidth(widthFraction.coerceIn(0.2f, 1f))) {
        val fieldWidth = maxWidth.coerceAtMost(screenHalf)
        val fieldShape = RoundedCornerShape(12.dp)
        Column(modifier = Modifier.width(fieldWidth)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(Color(0xFF14151A))
                    .border(1.dp, flame.copy(alpha = 0.4f), fieldShape)
                    .clickable(enabled = styles.isNotEmpty()) {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        onExpandedChange(!expanded)
                    }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = "Раскрыть список",
                    tint = flame,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .width(fieldWidth)
                        .padding(top = 4.dp)
                        .clip(fieldShape)
                        .background(Color(0xFF14151A))
                        .border(1.dp, flame.copy(alpha = 0.4f), fieldShape)
                        .heightIn(max = menuMaxHeight)
                        .verticalScroll(rememberScrollState()),
                ) {
                    styles.forEach { style ->
                        DropdownMenuItem(
                            text = { Text(style.name, color = Color.White, fontSize = 14.sp) },
                            onClick = {
                                onSelect(style)
                                onExpandedChange(false)
                            },
                        )
                    }
                }
            }
        }
    }
}
