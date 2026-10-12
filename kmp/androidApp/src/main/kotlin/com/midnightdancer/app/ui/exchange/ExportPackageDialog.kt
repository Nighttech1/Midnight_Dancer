package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleRef

/**
 * Одно окно «Поделиться пакетом»: стиль + выбор Э/М/Х (C-118 / C-120).
 */
@Composable
fun ExportPackageDialog(
    limitsText: String,
    styles: List<StyleRef>,
    styleId: String,
    styleName: String,
    selectedMoves: Int,
    selectedSongs: Int,
    selectedChoreos: Int,
    onStyleSelect: (String) -> Unit,
    onOpenPick: (ExportPickKind) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(18.dp)
    var styleExpanded by remember { mutableStateOf(false) }
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val menuMaxHeight = (screenH * 0.45f).coerceAtLeast(180.dp).coerceAtMost(420.dp)
    val displayName = styleName.ifBlank { "Выберите стиль" }

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
                text = "Поделиться пакетом",
                color = Color.White,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Стиль пакета",
                color = flame,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlameStyleDropdownField(
                displayName = displayName,
                styles = styles,
                expanded = styleExpanded,
                onExpandedChange = { styleExpanded = it },
                onSelect = { onStyleSelect(it.id) },
                flame = flame,
                widthFraction = 0.5f,
                menuMaxHeight = menuMaxHeight,
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = limitsText.ifBlank { PackageExportLimits.HUMAN_LIMITS_RU },
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))

            TextPickLink(
                title = "Элементы",
                count = selectedMoves,
                max = PackageExportLimits.MAX_ELEMENTS,
                flame = flame,
                onClick = { onOpenPick(ExportPickKind.Elements) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextPickLink(
                title = "Музыка",
                count = selectedSongs,
                max = PackageExportLimits.MAX_SONGS,
                flame = flame,
                onClick = { onOpenPick(ExportPickKind.Songs) },
            )
            Spacer(modifier = Modifier.height(4.dp))
            TextPickLink(
                title = "Хореографии",
                count = selectedChoreos,
                max = PackageExportLimits.MAX_CHOREOGRAPHIES,
                flame = flame,
                onClick = { onOpenPick(ExportPickKind.Choreographies) },
            )

            Spacer(modifier = Modifier.height(18.dp))
            FlameWhiteLabelButton(label = "Создать пакет", onClick = onConfirm)
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Отмена", color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

/** Как «Отмена»: TextButton без заливки, но оранжевым текстом. */
@Composable
private fun TextPickLink(
    title: String,
    count: Int,
    max: Int,
    flame: Color,
    onClick: () -> Unit,
) {
    val label = if (count <= 0) title else "$title · $count/$max"
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = flame,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
