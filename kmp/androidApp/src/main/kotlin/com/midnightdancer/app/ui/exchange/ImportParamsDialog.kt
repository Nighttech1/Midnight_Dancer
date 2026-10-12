package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.domain.exchange.StyleRef

/**
 * «Параметры импорта» — один целевой стиль (C-117 / C-120).
 */
@Composable
fun ImportParamsDialog(
    localStyles: List<StyleRef>,
    packageStyles: List<StyleRef>,
    targetStyleId: String,
    onTargetStyleChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(18.dp)
    val selected = localStyles.firstOrNull { it.id == targetStyleId }
    val selectedName = selected?.name ?: "Выберите стиль"
    var expanded by remember { mutableStateOf(false) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val menuMaxHeight = (screenH * 0.55f).coerceAtLeast(220.dp).coerceAtMost(480.dp)

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
                text = "Параметры импорта",
                color = Color.White,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Укажите, в какой стиль добавить импортируемые элементы",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "В приложении",
                    color = flame,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "Из пакета",
                    color = flame,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlameStyleDropdownField(
                        displayName = selectedName,
                        styles = localStyles,
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        onSelect = { onTargetStyleChange(it.id) },
                        flame = flame,
                        widthFraction = 1f,
                        menuMaxHeight = menuMaxHeight,
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Создать новый",
                        color = flame,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlameBlackLabelButton(
                        label = selectedName,
                        onClick = { /* visual parity — already selected */ },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (packageStyles.isEmpty()) {
                        Text(
                            text = "—",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                        )
                    } else {
                        packageStyles.forEach { ps ->
                            Text(
                                text = ps.name,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            FlameWhiteLabelButton(label = "Импортировать", onClick = onConfirm)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Отмена", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}
