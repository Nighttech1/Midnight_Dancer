package com.midnightdancer.app.ui.elements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.CatalogSearchField
import com.midnightdancer.app.ui.components.CatalogSquareIcon
import com.midnightdancer.app.ui.components.MdTagIcon
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.data.cache.buildCoilImageLoader
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.designsystem.NoOpHapticFeedback
import com.midnightdancer.feature.elements.logic.ElementsIntent
import com.midnightdancer.feature.elements.logic.ElementsSortMode
import com.midnightdancer.feature.elements.logic.ElementsUiState
import org.koin.core.context.GlobalContext

private val CardShape = RoundedCornerShape(22.dp)
private const val CardAspect = 3f / 4.25f

/** Tag dropdown: ~6 rows visible, the rest scrolls. */
private val TagRowHeight = 44.dp
private const val TagRowsVisible = 6

@Composable
fun KineticAtelierScreen(
    state: ElementsUiState,
    onIntent: (ElementsIntent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()
    val context = LocalContext.current
    val imageLoader = remember {
        runCatching { GlobalContext.get().get<ImageLoader>() }
            .getOrElse { buildCoilImageLoader(context) }
    }
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val softFling = rememberSoftGridFlingBehavior()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oled),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(280.dp)
                .graphicsLayer { alpha = 0.18f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AtelierColors.Flame.copy(alpha = 0.35f), Color.Transparent),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Sticky header: title + style/add row + filter/search row stay fixed; only the grid scrolls.
            AtelierHeader(
                state = state,
                onIntent = onIntent,
                onOpenSettings = onOpenSettings,
                haptics = haptics,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 0.dp,
                    bottom = 24.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = softFling,
            ) {
                items(
                    items = state.cards,
                    key = { it.id },
                    contentType = { it.kind::class.simpleName },
                ) { card ->
                    KineticElementCard(
                        card = card,
                        imageLoader = imageLoader,
                        bpmLabel = state.selectedStyle.bpmLabel,
                        // Only CenterPlay opens the editor; pin / card body do not.
                        onPlay = { onIntent(ElementsIntent.OpenEditor(card.id)) },
                        onOpen = { onIntent(ElementsIntent.OpenEditor(card.id)) },
                        onTogglePin = { onIntent(ElementsIntent.TogglePin(card.id)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(CardAspect)
                            .clip(CardShape),
                    )
                }
                // C-179: empty grid stays blank — no «Элементы не найдены» caption.
            }
        }

        state.editor?.let { draft ->
            ElementEditorSheet(
                draft = draft,
                backendSaving = state.saving,
                backendSaveError = state.saveError,
                styles = state.styles,
                tags = state.categories,
                onDraftChange = { onIntent(ElementsIntent.UpdateEditorDraft(it)) },
                onSave = { onIntent(ElementsIntent.SaveElement) },
                onCancel = { onIntent(ElementsIntent.CloseEditor) },
            )
        }
    }
}

@Composable
private fun AtelierHeader(
    state: ElementsUiState,
    onIntent: (ElementsIntent) -> Unit,
    onOpenSettings: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.midnightdancer.app.ui.components.MdPageTitle(
                text = state.title,
                modifier = Modifier.weight(1f),
            )
            GearSettingsButton(
                haptics = haptics,
                onClick = onOpenSettings,
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            StyleSplitSelector(
                currentStyle = state.selectedStyle.name,
                onPreviousStyle = { onIntent(ElementsIntent.StylePrevious) },
                onNextStyle = { onIntent(ElementsIntent.StyleNext) },
                modifier = Modifier.weight(1f),
            )
            AddElementButton(
                haptics = haptics,
                // Opens the editor sheet (empty draft); the element is created on Save.
                onClick = { onIntent(ElementsIntent.AddNewElement) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Same row metrics as Music CatalogToolbar: 44.dp square icons + CatalogSearchField.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TagFilterButton(
                tags = state.categories,
                selected = state.filterCategory,
                onSelect = { onIntent(ElementsIntent.SetFilterCategory(it)) },
                haptics = NoOpHapticFeedback,
            )
            SortMenuButton(
                selected = state.sortMode,
                onSelect = { onIntent(ElementsIntent.SetSortMode(it)) },
            )
            CatalogSearchField(
                query = state.searchQuery,
                onQueryChange = { onIntent(ElementsIntent.SetSearchQuery(it)) },
                placeholder = stringResource(R.string.elements_search_placeholder),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun GearSettingsButton(
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    val cd = stringResource(R.string.elements_tune)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .interactiveSpringPress(
                pressedScale = 0.90f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = onClick,
            )
            .semantics { contentDescription = cd },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            tint = AtelierColors.Flame,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Flame pill with a LEFT→RIGHT light shimmer: an infinite Brush translate drawn over the pill
 * (read in the draw phase only — no recomposition per frame).
 */
@Composable
private fun AddElementButton(
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.elements_add_element_pill)
    val transition = rememberInfiniteTransition(label = "add-shimmer")
    val shift = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-shift",
    )
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(pillShape)
            .background(AtelierColors.Flame)
            .drawWithContent {
                drawContent()
                val band = size.width * 0.55f
                val x = -band + (size.width + band) * shift.value
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.55f),
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + band, size.height * 0.45f),
                    ),
                )
            }
            .interactiveSpringPress(
                pressedScale = 0.94f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.2.sp,
            ),
            color = Color.White,
        )
    }
}

/** Tag filter: MdTagIcon button, rounded dropdown directly below, ≈ half screen wide, ~6 rows + scroll. */
@Composable
private fun TagFilterButton(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    haptics: HapticFeedback,
) {
    var open by remember { mutableStateOf(false) }
    val active = selected != null
    val cd = stringResource(R.string.elements_filter_tag_cd)
    val menuWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
    val menuShape = RoundedCornerShape(18.dp)
    val buttonShape = RoundedCornerShape(14.dp)
    val rim = if (active) AtelierColors.Flame else Color.White.copy(alpha = 0.12f)
    val fill = if (active) AtelierColors.Flame.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f)

    Box {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(buttonShape)
                .background(fill)
                .border(1.5.dp, rim, buttonShape)
                .interactiveSpringPress(
                    pressedScale = 0.92f,
                    hapticEvent = HapticEvent.Selection,
                    haptics = NoOpHapticFeedback,
                    onClick = { open = true },
                )
                .semantics { contentDescription = cd },
            contentAlignment = Alignment.Center,
        ) {
            MdTagIcon(
                size = 22.dp,
                tint = if (active) AtelierColors.Flame else Color.White.copy(alpha = 0.85f),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = TagRowHeight * TagRowsVisible + 16.dp),
            shape = menuShape,
            containerColor = Color(0xF2121319),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            TagMenuRow(
                label = stringResource(R.string.elements_filter_all),
                selected = selected == null,
                onClick = {
                    onSelect(null)
                    open = false
                },
                haptics = haptics,
            )
            tags.forEach { name ->
                TagMenuRow(
                    label = name,
                    selected = name.equals(selected, ignoreCase = true),
                    onClick = {
                        onSelect(name)
                        open = false
                    },
                    haptics = haptics,
                )
            }
        }
    }
}

@Composable
private fun TagMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    haptics: HapticFeedback,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TagRowHeight)
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
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MdFonts.Display,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            ),
            color = if (selected) AtelierColors.Soft else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SortMenuButton(
    selected: ElementsSortMode,
    onSelect: (ElementsSortMode) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val active = selected != ElementsSortMode.ByLevel
    val cd = stringResource(R.string.elements_sort_level)
    val menuWidth = (LocalConfiguration.current.screenWidthDp / 2).dp
    Box {
        CatalogSquareIcon(
            icon = Icons.Outlined.Sort,
            contentDescription = cd,
            active = active,
            onClick = { open = true },
        )
        // Same shell as TagFilterButton: half-screen width, max 6 rows, rounded 18dp.
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .width(menuWidth)
                .heightIn(max = TagRowHeight * TagRowsVisible + 16.dp),
            shape = RoundedCornerShape(18.dp),
            containerColor = Color(0xF2121319),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        ) {
            TagMenuRow(
                label = stringResource(R.string.elements_sort_level),
                selected = selected == ElementsSortMode.ByLevel,
                onClick = {
                    onSelect(ElementsSortMode.ByLevel)
                    open = false
                },
                haptics = NoOpHapticFeedback,
            )
            TagMenuRow(
                label = stringResource(R.string.elements_sort_alpha),
                selected = selected == ElementsSortMode.Alphabetical,
                onClick = {
                    onSelect(ElementsSortMode.Alphabetical)
                    open = false
                },
                haptics = NoOpHapticFeedback,
            )
        }
    }
}
