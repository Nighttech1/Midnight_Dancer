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
import com.midnightdancer.core.domain.exchange.PackageExportLimits

private enum class PickSort { NameAsc, NameDesc }

/**
 * Режим выбора в разделе для экспорта (C-118): toolbar + soft scroll.
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
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val haptics = rememberMdHaptics()
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.32f)
    val max =
        when (kind) {
            ExportPickKind.Elements -> PackageExportLimits.MAX_ELEMENTS
            ExportPickKind.Songs -> PackageExportLimits.MAX_SONGS
            ExportPickKind.Choreographies -> PackageExportLimits.MAX_CHOREOGRAPHIES
        }
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
                }
                FlameWhiteLabelButton(
                    label = "Выбрать $count/$max",
                    onClick = onConfirm,
                    fullWidth = false,
                )
            }

            if (kind == ExportPickKind.Elements || kind == ExportPickKind.Songs ||
                kind == ExportPickKind.Choreographies
            ) {
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
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

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
                            text = "В этом стиле пока нечего выбрать.",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(visible, key = { it.id }) { item ->
                    val checked = item.id in selectedIds
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xCC0A0B10))
                            .border(1.dp, flame.copy(alpha = 0.28f), shape)
                            .interactiveSpringPress(
                                pressedScale = 0.98f,
                                hapticEvent = HapticEvent.Selection,
                                haptics = haptics,
                                onClick = { onToggle(item.id) },
                            )
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggle(item.id) },
                            colors = CheckboxDefaults.colors(
                                checkedColor = flame,
                                uncheckedColor = Color.White.copy(alpha = 0.35f),
                                checkmarkColor = Color.Black,
                            ),
                        )
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

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Отмена",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(16.dp)
                    .interactiveSpringPress(
                        hapticEvent = HapticEvent.Selection,
                        haptics = haptics,
                        onClick = onDismiss,
                    ),
            )
        }
    }
}
