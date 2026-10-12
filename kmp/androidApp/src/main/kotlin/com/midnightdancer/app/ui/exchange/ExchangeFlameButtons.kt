package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent

private val ButtonShape = RoundedCornerShape(14.dp)

/** Оранжевая кнопка, чёрный текст — «Выбрать» / «Выбрано X/N». */
@Composable
fun FlameBlackLabelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val haptics = rememberMdHaptics()
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .background(flame)
            .interactiveSpringPress(
                pressedScale = 0.96f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

/** Оранжевая кнопка, белый текст — «Создать пакет» / «Импортировать» / «Выбрать X/N». */
@Composable
fun FlameWhiteLabelButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullWidth: Boolean = true,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val haptics = rememberMdHaptics()
    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = 48.dp)
            .clip(ButtonShape)
            .background(flame)
            .interactiveSpringPress(
                pressedScale = 0.96f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}
