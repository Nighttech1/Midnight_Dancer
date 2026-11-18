package com.midnightdancer.app.ui.choreography

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.components.SearchablePicker
import com.midnightdancer.app.ui.exchange.FlameWhiteLabelButton
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.feature.choreography.logic.ChoreoCatalogEntry
import com.midnightdancer.feature.choreography.logic.EditorTrackDurations

private const val NO_TAG_LABEL = "Без тега"

/**
 * Small dialog: choreography name + style (create flow from the vault, rename / restyle
 * from Pro Editor title double-tap) — C-135 · 3.1.
 */
@Composable
fun ChoreoNameStyleDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialStyle: String,
    styles: List<String>,
    onConfirm: (name: String, style: String, tag: String) -> Unit,
    onDismiss: () -> Unit,
    /** Tag catalog for the «Тег» picker (C-191 — always shown, before style). */
    tagCatalog: List<String> = emptyList(),
    initialTag: String = "",
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(18.dp)
    var nameField by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialName,
                selection = TextRange(0, initialName.length),
            ),
        )
    }
    var style by remember { mutableStateOf(initialStyle.ifBlank { styles.firstOrNull().orEmpty() }) }
    var tag by remember { mutableStateOf(initialTag) }
    var openPicker by remember { mutableStateOf<String?>(null) }
    val effectiveTags = tagCatalog.ifEmpty {
        com.midnightdancer.core.domain.settings.ElementCategoryConfig.DEFAULT_NAMES
    }
    // C-193: no «Без тега» row — blank value means no tag.
    val tagOptions = remember(effectiveTags) { effectiveTags }
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF0A0B10))
                .border(1.dp, flame.copy(alpha = 0.45f), shape)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Text(
                text = title,
                color = Color.White,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(Modifier.height(14.dp))
            Text("Название", color = flame, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            val fieldShape = RoundedCornerShape(12.dp)
            // C-181: select-all name on open so rename replaces the whole title.
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                nameField = nameField.copy(
                    selection = TextRange(0, nameField.text.length),
                )
            }
            BasicTextField(
                value = nameField,
                onValueChange = { next ->
                    nameField = next.copy(text = next.text.take(60))
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 16.sp,
                    fontFamily = MdFonts.Display,
                ),
                cursorBrush = SolidColor(flame),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .clip(fieldShape)
                    .background(Color(0xFF12131A))
                    .border(1.dp, flame.copy(alpha = 0.45f), fieldShape)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                decorationBox = { inner ->
                    Box {
                        if (nameField.text.isEmpty()) {
                            Text(
                                "Название хореографии",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 16.sp,
                                fontFamily = MdFonts.Display,
                            )
                        }
                        inner()
                    }
                },
            )
            // C-191: tag picker before style (same as create / other dialogs).
            Spacer(Modifier.height(14.dp))
            SearchablePicker(
                label = "Тег",
                value = tag.ifBlank { "Тег" },
                options = tagOptions,
                expanded = openPicker == "tag",
                onExpandedChange = { open -> openPicker = if (open) "tag" else null },
                onSelect = { tag = it },
                panelMaxHeight = 180.dp,
            )
            Spacer(Modifier.height(14.dp))
            SearchablePicker(
                label = "Стиль",
                value = style,
                options = styles,
                expanded = openPicker == "style",
                onExpandedChange = { open -> openPicker = if (open) "style" else null },
                onSelect = { style = it },
                panelMaxHeight = 180.dp,
            )
            Spacer(Modifier.height(18.dp))
            // C-163: Cancel / Save (confirm) — same 1.5× text as Elements/Music editors.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        "Отмена",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
                TextButton(
                    onClick = {
                        onConfirm(
                            nameField.text.trim().ifEmpty { "Новая хореография" },
                            style,
                            tag,
                        )
                    },
                ) {
                    Text(
                        confirmLabel,
                        color = flame,
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

private enum class PickerSort { NameAsc, NameDesc }

/**
 * Full-screen multi-select picker of style-scoped catalog entries (elements / tracks).
 * Same pattern as Exchange `ExportSectionPickerScreen`, but top button is plain «Выбрать»
 * (no X/5 limit) — C-135 · 3.2 / 3.3.
 */
@Composable
fun ChoreoCatalogPickerScreen(
    title: String,
    styleName: String,
    items: List<ChoreoCatalogEntry>,
    searchPlaceholder: String,
    emptyText: String,
    onConfirm: (List<ChoreoCatalogEntry>) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showLevelFilter: Boolean = true,
    speed: Float? = null,
    onSpeedChange: ((Float) -> Unit)? = null,
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    // C-250: selecting elements/music into choreography must not vibrate.
    val haptics = com.midnightdancer.core.designsystem.NoOpHapticFeedback
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.32f)
    var selected by remember { mutableStateOf(setOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var filterTag by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PickerSort.NameAsc) }

    BackHandler(onBack = onDismiss)

    val filterOptions = remember(items, showLevelFilter) {
        if (!showLevelFilter) emptyList()
        else items.map { it.subtitle }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = remember(items, searchQuery, filterTag, sort) {
        var list = items
        if (!filterTag.isNullOrBlank()) {
            list = list.filter { it.subtitle.equals(filterTag, ignoreCase = true) }
        }
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true)
            }
        }
        when (sort) {
            PickerSort.NameAsc -> list.sortedBy { it.title.lowercase() }
            PickerSort.NameDesc -> list.sortedByDescending { it.title.lowercase() }
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
                        // 5.3: style name 2× larger (12sp → ~28sp Display Bold).
                        Text(
                            text = styleName,
                            color = flame,
                            fontSize = 28.sp,
                            fontFamily = MdFonts.Display,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // 5.3: «Отмена» top-right, right next to «Выбрать».
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "Отмена",
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = MdFonts.Display,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .interactiveSpringPress(
                                hapticEvent = HapticEvent.Selection,
                                haptics = haptics,
                                onClick = onDismiss,
                            )
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                    FlameWhiteLabelButton(
                        label = "Выбрать",
                        onClick = { onConfirm(items.filter { it.id in selected }) },
                        fullWidth = false,
                    )
                }
            }

            CatalogToolbar(
                filterOptions = filterOptions,
                selectedFilter = filterTag,
                onFilterSelect = { filterTag = it },
                sortOptions = listOf(
                    "А → Я" to { sort = PickerSort.NameAsc },
                    "Я → А" to { sort = PickerSort.NameDesc },
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                flingBehavior = softFling,
            ) {
                if (visible.isEmpty()) {
                    item {
                        Text(
                            text = emptyText,
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(visible, key = { it.id }) { entry ->
                    val checked = entry.id in selected
                    // Single toggle path — Checkbox must not also fire Row click (double-toggle).
                    val toggle = {
                        selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                    }
                    val shape = RoundedCornerShape(14.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(Color(0xCC0A0B10))
                            .border(1.dp, flame.copy(alpha = if (checked) 0.65f else 0.28f), shape)
                            .interactiveSpringPress(
                                pressedScale = 0.98f,
                                hapticEvent = HapticEvent.Selection,
                                haptics = haptics,
                                onClick = toggle,
                            )
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(
                                checkedColor = flame,
                                uncheckedColor = Color.White.copy(alpha = 0.35f),
                                checkmarkColor = Color.Black,
                            ),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.title, color = Color.White, fontSize = 15.sp, fontFamily = MdFonts.Display)
                            if (entry.subtitle.isNotBlank() && showLevelFilter) {
                                Text(
                                    entry.subtitle,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp,
                                    fontFamily = MdFonts.Display,
                                )
                            }
                        }
                        Text(
                            text = EditorTrackDurations.formatMmSs((entry.durationMs / 1000L).toInt()),
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 12.sp,
                            fontFamily = MdFonts.Display,
                        )
                    }
                }
            }

            if (speed != null && onSpeedChange != null) {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        "${stringResource(R.string.choreo_track_speed)}: ${String.format("%.1f×", speed)}",
                        color = Color.White,
                        fontFamily = MdFonts.Display,
                        fontSize = 13.sp,
                    )
                    Slider(
                        value = speed,
                        onValueChange = onSpeedChange,
                        valueRange = DomainValidators.CHOREO_PLAYBACK_SPEED_MIN.toFloat()..
                            DomainValidators.CHOREO_PLAYBACK_SPEED_MAX.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = flame,
                            activeTrackColor = flame,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
