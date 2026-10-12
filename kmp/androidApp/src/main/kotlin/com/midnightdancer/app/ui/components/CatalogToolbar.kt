package com.midnightdancer.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback

private val SearchShape = RoundedCornerShape(14.dp)

/**
 * Shared filter / sort / search row — Elements · Music · Choreography (C-76 / C-77).
 */
@Composable
fun CatalogToolbar(
    filterOptions: List<String>,
    selectedFilter: String?,
    onFilterSelect: (String?) -> Unit,
    sortOptions: List<Pair<String, () -> Unit>>,
    sortActive: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    searchPlaceholder: String,
    haptics: HapticFeedback = rememberMdHaptics(),
    allFiltersLabel: String = "Все",
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CatalogFilterButton(
            options = filterOptions,
            selected = selectedFilter,
            allLabel = allFiltersLabel,
            onSelect = onFilterSelect,
            haptics = haptics,
        )
        CatalogSortButton(
            options = sortOptions,
            active = sortActive,
            haptics = haptics,
        )
        CatalogSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = searchPlaceholder,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CatalogFilterButton(
    options: List<String>,
    selected: String?,
    allLabel: String,
    onSelect: (String?) -> Unit,
    haptics: HapticFeedback,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        CatalogSquareIcon(
            icon = Icons.Filled.FilterList,
            contentDescription = allLabel,
            active = selected != null,
            onClick = {
                haptics.perform(HapticEvent.Selection)
                open = true
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xF0121319),
        ) {
            DropdownMenuItem(
                text = { Text(allLabel, color = Color.White) },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            options.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name, color = Color.White) },
                    onClick = {
                        onSelect(name)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CatalogSortButton(
    options: List<Pair<String, () -> Unit>>,
    active: Boolean,
    haptics: HapticFeedback,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        CatalogSquareIcon(
            icon = Icons.Outlined.Sort,
            contentDescription = "Сортировка",
            active = active,
            onClick = {
                haptics.perform(HapticEvent.Selection)
                open = true
            },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xF0121319),
        ) {
            options.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, color = Color.White) },
                    onClick = {
                        action()
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
fun CatalogSquareIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val rim = if (active) AtelierColors.Flame else Color.White.copy(alpha = 0.12f)
    val fill = if (active) AtelierColors.Flame.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(1.5.dp, rim, RoundedCornerShape(14.dp))
            .interactiveSpringPress(
                pressedScale = 0.92f,
                hapticEvent = HapticEvent.Selection,
                haptics = rememberMdHaptics(),
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) AtelierColors.Flame else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun CatalogSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(SearchShape)
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), SearchShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = MdFonts.Display,
            ),
            cursorBrush = SolidColor(AtelierColors.Flame),
            decorationBox = { inner ->
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 14.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
                inner()
            },
        )
    }
}
