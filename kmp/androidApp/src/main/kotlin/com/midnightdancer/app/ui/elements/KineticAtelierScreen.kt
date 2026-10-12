package com.midnightdancer.app.ui.elements

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.midnightdancer.app.ui.theme.MdFonts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil3.ImageLoader
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.data.cache.buildCoilImageLoader
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.elements.logic.ElementsIntent
import com.midnightdancer.feature.elements.logic.ElementsSortMode
import com.midnightdancer.feature.elements.logic.ElementsUiState
import org.koin.core.context.GlobalContext


private val CardShape = RoundedCornerShape(22.dp)
private val SearchShape = RoundedCornerShape(16.dp)
private const val CardAspect = 3f / 4.25f

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

    val catalogPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
        }
    }
    DisposableEffect(Unit) {
        onDispose { catalogPlayer.release() }
    }
    LaunchedEffect(state.playingCardId) {
        val id = state.playingCardId
        if (id == null) {
            catalogPlayer.pause()
            catalogPlayer.clearMediaItems()
        } else {
            val uri = Uri.parse("android.resource://${context.packageName}/${R.raw.splash_boot}")
            catalogPlayer.setMediaItem(MediaItem.fromUri(uri))
            catalogPlayer.prepare()
            catalogPlayer.playWhenReady = true
        }
    }

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

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            flingBehavior = softFling,
        ) {
            item(
                key = "atelier-header",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "header",
            ) {
                AtelierHeader(
                    state = state,
                    onIntent = onIntent,
                    onOpenSettings = onOpenSettings,
                    haptics = haptics,
                )
            }
            items(
                items = state.cards,
                key = { it.id },
                contentType = { it.kind::class.simpleName },
            ) { card ->
                KineticElementCard(
                    card = card,
                    imageLoader = imageLoader,
                    bpmLabel = state.selectedStyle.bpmLabel,
                    isPlaying = state.playingCardId == card.id,
                    player = catalogPlayer,
                    onPlay = { onIntent(ElementsIntent.PlayCard(card.id)) },
                    onOpen = { onIntent(ElementsIntent.OpenDetail(card.id)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(CardAspect)
                        .clip(CardShape),
                )
            }
            if (state.cards.isEmpty()) {
                item(
                    key = "atelier-empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "empty",
                ) {
                    Text(
                        text = stringResource(R.string.elements_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ColorTokens.TextSecondary.toComposeColor(),
                        modifier = Modifier.padding(vertical = 32.dp),
                    )
                }
            }
        }

        state.detailCard?.let { detail ->
            ElementDetailSheet(
                card = detail,
                onDismiss = { onIntent(ElementsIntent.CloseDetail) },
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
                onPreviousStyle = {
                    haptics.perform(HapticEvent.Selection)
                    onIntent(ElementsIntent.StylePrevious)
                },
                onNextStyle = {
                    haptics.perform(HapticEvent.Selection)
                    onIntent(ElementsIntent.StyleNext)
                },
                modifier = Modifier.weight(1f),
            )
            val raiseHardDisk = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current
            AddElementButton(
                haptics = haptics,
                onClick = {
                    if (com.midnightdancer.app.ui.components.guardMediaDiskOrRaise(raiseHardDisk)) {
                        onIntent(ElementsIntent.AddNewElement)
                    }
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterMenuButton(
                tags = state.categories,
                selected = state.filterCategory,
                onSelect = { onIntent(ElementsIntent.SetFilterCategory(it)) },
                haptics = haptics,
            )
            SortMenuButton(
                selected = state.sortMode,
                onSelect = { onIntent(ElementsIntent.SetSortMode(it)) },
                haptics = haptics,
            )
            GlassSearchField(
                query = state.searchQuery,
                onQueryChange = { onIntent(ElementsIntent.SetSearchQuery(it)) },
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

@Composable
private fun AddElementButton(
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.elements_add_element_pill)
    val chase = rememberInfiniteTransition(label = "add-chase")
    val angle by chase.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "chase-angle",
    )
    val pillShape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(48.dp)
            .clip(pillShape)
            .background(AtelierColors.Flame)
            .drawWithContent {
                drawContent()
                val stroke = 2.5.dp.toPx()
                // Perimeter chase-light: SweepGradient rotated around oval (C-69).
                rotate(degrees = angle) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            0.0f to Color.Transparent,
                            0.08f to Color.White.copy(alpha = 0.95f),
                            0.22f to Color.Transparent,
                            0.55f to Color.White.copy(alpha = 0.20f),
                            1.0f to Color.Transparent,
                        ),
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                        style = Stroke(width = stroke),
                    )
                }
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

@Composable
private fun FilterMenuButton(
    tags: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    haptics: HapticFeedback,
) {
    var open by remember { mutableStateOf(false) }
    val active = selected != null
    val cd = stringResource(R.string.elements_filter_all)
    Box {
        SquareToolbarIcon(
            icon = Icons.Filled.FilterList,
            contentDescription = cd,
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
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.elements_filter_all), color = Color.White)
                },
                onClick = {
                    onSelect(null)
                    open = false
                },
            )
            tags.forEach { name ->
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
private fun SortMenuButton(
    selected: ElementsSortMode,
    onSelect: (ElementsSortMode) -> Unit,
    haptics: HapticFeedback,
) {
    var open by remember { mutableStateOf(false) }
    val active = selected != ElementsSortMode.ByLevel
    val cd = stringResource(R.string.elements_sort_level)
    Box {
        SquareToolbarIcon(
            icon = Icons.Outlined.Sort,
            contentDescription = cd,
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
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.elements_sort_level), color = Color.White)
                },
                onClick = {
                    onSelect(ElementsSortMode.ByLevel)
                    open = false
                },
            )
            DropdownMenuItem(
                text = {
                    Text(stringResource(R.string.elements_sort_alpha), color = Color.White)
                },
                onClick = {
                    onSelect(ElementsSortMode.Alphabetical)
                    open = false
                },
            )
        }
    }
}

@Composable
private fun SquareToolbarIcon(
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
private fun GlassSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val placeholder = stringResource(R.string.elements_search_placeholder)
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
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                inner()
            },
        )
    }
}
