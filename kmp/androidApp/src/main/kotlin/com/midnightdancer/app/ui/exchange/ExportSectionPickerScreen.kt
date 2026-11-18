package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.domain.exchange.CatalogPickItem
import com.midnightdancer.core.domain.exchange.ExportPickKind

private enum class PickSort { NameAsc, NameDesc }

/**
 * Режим выбора раздела для экспорта / редактирования сета (C-219 / C-220).
 */
@Composable
fun ExportSectionPickerScreen(
    kind: ExportPickKind,
    styleName: String,
    items: List<CatalogPickItem>,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Edit mode: Flame 2.dp rim on selected rows. */
    highlightSelected: Boolean = false,
    /** Live set weight while picking (edit mode). */
    weightMb: Float? = null,
    overweight: Boolean = false,
    readOnly: Boolean = false,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val haptics = rememberMdHaptics()
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.32f)
    val title =
        when (kind) {
            ExportPickKind.Elements -> "Элементы"
            ExportPickKind.Songs -> "Музыка"
            ExportPickKind.Choreographies -> "Хореография"
        }
    val searchPlaceholder =
        when (kind) {
            ExportPickKind.Elements -> "Поиск техник, связок, ритмов..."
            ExportPickKind.Songs -> "Поиск треков..."
            ExportPickKind.Choreographies -> "Поиск хореографий..."
        }
    val count = selectedIds.size

    var searchQuery by remember { mutableStateOf("") }
    var filterTag by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PickSort.NameAsc) }

    val filterOptions =
        remember(items) {
            items.map { it.subtitle }.filter { it.isNotBlank() }.distinct().sorted()
        }
    val visible =
        remember(items, searchQuery, filterTag, sort) {
            var list = items
            if (!filterTag.isNullOrBlank()) {
                list = list.filter { it.subtitle.equals(filterTag, ignoreCase = true) }
            }
            val q = searchQuery.trim()
            if (q.isNotEmpty()) {
                list =
                    list.filter {
                        it.title.contains(q, ignoreCase = true) ||
                            it.subtitle.contains(q, ignoreCase = true)
                    }
            }
            when (sort) {
                PickSort.NameAsc -> list.sortedBy { it.title.lowercase() }
                PickSort.NameDesc -> list.sortedByDescending { it.title.lowercase() }
            }
        }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ColorTokens.OledTrueBlack.toComposeColor()),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    MdPageTitle(text = title)
                    if (styleName.isNotBlank()) {
                        Text(
                            text = styleName,
                            color = flame,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (weightMb != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Вес сета: ${formatPickerWeight(weightMb)} МБ",
                            color = if (overweight) Color.Red else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (readOnly) "Назад" else "Отмена",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .interactiveSpringPress(
                                hapticEvent = HapticEvent.Selection,
                                haptics = haptics,
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    if (!readOnly) FlameWhiteLabelButton(
                        label = "Выбрано $count",
                        onClick = onConfirm,
                        fullWidth = false,
                    )
                }
            }

            CatalogToolbar(
                filterOptions = filterOptions,
                selectedFilter = filterTag,
                onFilterSelect = { filterTag = it },
                sortOptions =
                    listOf(
                        "А → Я" to { sort = PickSort.NameAsc },
                        "Я → А" to { sort = PickSort.NameDesc },
                    ),
                sortActive = true,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                searchPlaceholder = searchPlaceholder,
                haptics = haptics,
                allFiltersLabel = "Все теги",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                flingBehavior = softFling,
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = when {
                                searchQuery.isNotBlank() || !filterTag.isNullOrBlank() -> "Ничего не найдено."
                                readOnly -> "В этом разделе архива нет материалов."
                                else -> "В этом стиле пока нечего выбрать."
                            },
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(visible, key = { it.id }) { item ->
                    val checked = item.id in selectedIds
                    val shape = RoundedCornerShape(14.dp)
                    val borderWidth =
                        if (highlightSelected && checked) 2.dp else 1.dp
                    val borderColor =
                        if (highlightSelected && checked) {
                            flame
                        } else {
                            flame.copy(alpha = 0.28f)
                        }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xCC0A0B10))
                            .border(borderWidth, borderColor, shape)
                            .then(if (readOnly) Modifier else Modifier.interactiveSpringPress(
                                pressedScale = 0.98f,
                                hapticEvent = HapticEvent.Selection,
                                haptics = haptics,
                                onClick = { onToggle(item.id) },
                            ))
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!readOnly) Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(item.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = flame,
                                uncheckedColor = Color.White.copy(alpha = 0.35f),
                                checkmarkColor = Color.Black,
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = item.title, color = Color.White, fontSize = 15.sp)
                            if (item.subtitle.isNotBlank()) {
                                Text(
                                    text = item.subtitle,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatPickerWeight(weightMb: Float): String {
    val tenths = ((weightMb * 10f).toInt().coerceAtLeast(0)) / 10f
    return if (tenths == tenths.toInt().toFloat()) {
        tenths.toInt().toString() + ".0"
    } else {
        tenths.toString()
    }
}
