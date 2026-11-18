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
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.components.SearchablePicker
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.domain.exchange.StyleRef

private const val CREATE_FROM_SET = "Создать новый стиль из сета"

/**
 * «Параметры импорта» — C-258: Стиль сета above Наш стиль; SearchablePicker full width;
 * create-from-set option above search; dialog height ×1.5; no standalone Create button.
 */
@Composable
fun ImportParamsDialog(
    localStyles: List<StyleRef>,
    packageStyles: List<StyleRef>,
    targetStyleId: String,
    onTargetStyleChange: (String) -> Unit,
    onCreateNewStyle: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(18.dp)
    val selected = localStyles.firstOrNull { it.id == targetStyleId }
    val selectedName = selected?.name ?: "Выберите стиль"
    var expanded by remember { mutableStateOf(false) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    // C-258: ~1.5× taller dialog content area (even top/bottom padding + body).
    val dialogPadV = 27.dp
    val packageNames = packageStyles.map { it.name }.ifEmpty { listOf("—") }
    val pickerOptions = remember(localStyles) { localStyles.map { it.name } }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = (screenH * 0.42f).coerceAtMost(520.dp))
                .clip(shape)
                .background(Color(0xFF0A0B10))
                .border(1.dp, flame.copy(alpha = 0.45f), shape)
                .padding(horizontal = 18.dp, vertical = dialogPadV),
        ) {
            Text(
                text = "Параметры импорта",
                color = Color.White,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Укажите, в какой стиль добавить импортируемые элементы",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
            Spacer(modifier = Modifier.height(18.dp))

            // C-258: Стиль сета above Наш стиль (not side-by-side).
            MdFieldLabel(text = "Стиль сета")
            Spacer(modifier = Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 120.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE6121319))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                packageNames.forEach { name ->
                    Text(text = name, color = Color.White, fontSize = 14.sp, fontFamily = MdFonts.Display)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SearchablePicker(
                label = "Наш стиль",
                value = selectedName,
                options = pickerOptions,
                expanded = expanded,
                onExpandedChange = { expanded = it },
                onSelect = { name ->
                    if (name == CREATE_FROM_SET) {
                        onCreateNewStyle()
                    } else {
                        localStyles.firstOrNull { it.name == name }?.let { onTargetStyleChange(it.id) }
                    }
                    expanded = false
                },
                panelMaxHeight = 260.dp,
                pinnedAboveSearch = CREATE_FROM_SET,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(22.dp))
            FlameWhiteLabelButton(label = "Импортировать", onClick = onConfirm)
            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text(
                    "Отмена",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 15.sp,
                )
            }
        }
    }
}
