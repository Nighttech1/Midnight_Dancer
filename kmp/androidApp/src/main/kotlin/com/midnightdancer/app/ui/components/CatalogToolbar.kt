package com.midnightdancer.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.designsystem.NoOpHapticFeedback

private val SearchShape = RoundedCornerShape(14.dp)
private val MenuRowHeight = 44.dp
private const val MenuRowsVisible = 6
private val MenuMaxHeight = MenuRowHeight * MenuRowsVisible + 16.dp
private val MenuShape = RoundedCornerShape(18.dp)

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
    allFiltersLabel: String = "Все теги",
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
    // C-132 / 2.2: half-screen-wide menu, 6 rows visible then scroll.
    val menuWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
    Box {
        CatalogSquareIcon(
            contentDescription = allLabel,
            active = selected != null,
            onClick = { open = true },
        ) {
            MdTagIcon(size = 22.dp, tint = AtelierColors.Flame)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Color(0xF2121319),
            shape = MenuShape,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = MenuMaxHeight),
        ) {
            CatalogMenuRow(
                label = allLabel,
                selected = selected == null,
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            options.forEach { name ->
                CatalogMenuRow(
                    label = name,
                    selected = name.equals(selected, ignoreCase = true),
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
    val menuWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
    Box {
        CatalogSquareIcon(
            icon = Icons.Outlined.Sort,
            contentDescription = "Сортировка",
            active = active,
            onClick = { open = true },
        )
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = MenuMaxHeight),
            shape = MenuShape,
            containerColor = Color(0xF2121319),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            options.forEach { (label, action) ->
                CatalogMenuRow(
                    label = label,
                    selected = false,
                    onClick = {
                        action()
                        open = false
                    },
                )
            }
        }
    }
}

/** Same row shell as Elements `TagMenuRow` (C-152). */
@Composable
private fun CatalogMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MenuRowHeight)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) AtelierColors.Flame.copy(alpha = 0.16f) else Color.Transparent)
            .interactiveSpringPress(
                pressedScale = 0.97f,
                hapticEvent = HapticEvent.Selection,
                haptics = NoOpHapticFeedback,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            color = if (selected) AtelierColors.Soft else Color.White,
            fontFamily = MdFonts.Display,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun CatalogSquareIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    CatalogSquareIcon(
        contentDescription = contentDescription,
        active = active,
        onClick = onClick,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (active) AtelierColors.Flame else Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Square toolbar button with a custom glyph slot (e.g. [MdTagIcon]). */
@Composable
fun CatalogSquareIcon(
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
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
                // Toolbar taps are silent (C-142).
                hapticEvent = HapticEvent.Selection,
                haptics = NoOpHapticFeedback,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
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
