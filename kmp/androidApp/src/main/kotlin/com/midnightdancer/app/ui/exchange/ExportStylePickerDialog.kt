package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.domain.exchange.StyleRef

/**
 * Шаг 1 экспорта — выбор единственного стиля пакета (C-116).
 */
@Composable
fun ExportStylePickerDialog(
    styles: List<StyleRef>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(18.dp)
    val haptics = rememberMdHaptics()
    val softFling = rememberSoftGridFlingBehavior()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF0A0B10))
                .border(1.dp, flame.copy(alpha = 0.45f), shape)
                .padding(18.dp),
        ) {
            Text(
                text = "Стиль пакета",
                color = Color.White,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "В одном пакете можно собрать содержимое только одного стиля.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            Spacer(modifier = Modifier.height(14.dp))
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(scroll, flingBehavior = softFling),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (styles.isEmpty()) {
                    Text(
                        text = "Нет стилей в каталоге. Добавьте стиль в настройках.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp,
                    )
                } else {
                    styles.forEach { style ->
                        val rowShape = RoundedCornerShape(12.dp)
                        Text(
                            text = style.name,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(rowShape)
                                .background(Color(0xFF14151A))
                                .border(1.dp, flame.copy(alpha = 0.3f), rowShape)
                                .interactiveSpringPress(
                                    pressedScale = 0.97f,
                                    hapticEvent = HapticEvent.Selection,
                                    haptics = haptics,
                                    onClick = { onSelect(style.id) },
                                )
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
