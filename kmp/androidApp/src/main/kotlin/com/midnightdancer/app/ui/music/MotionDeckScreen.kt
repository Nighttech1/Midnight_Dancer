package com.midnightdancer.app.ui.music

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.components.SearchablePicker
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.feature.music.logic.MusicIntent
import com.midnightdancer.feature.music.logic.MusicSortMode
import com.midnightdancer.feature.music.logic.MusicTrack
import com.midnightdancer.feature.music.logic.MusicUiState

private val CardShape = RoundedCornerShape(18.dp)
private val Hairline = Color.White.copy(alpha = 0.12f)
private val DeckSurface = Color(0xCC0A0B10)

/**
 * Stitch `midnight_dancer_motion_deck` — OLED Motion Deck (C-62 / C-71 / C-76).
 */
@Composable
fun MotionDeckScreen(
    state: MusicUiState,
    onIntent: (MusicIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.30f)
    val playing = state.playingTrack
    val visible = state.filteredTracks
    val libraryTracks = remember(visible, playing?.id) {
        visible.filter { it.id != playing?.id }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oled),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(260.dp)
                .graphicsLayer { alpha = 0.22f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AtelierColors.Flame.copy(alpha = 0.40f), Color.Transparent),
                    ),
                ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            flingBehavior = softFling,
        ) {
            item(key = "md-header") {
                com.midnightdancer.app.ui.components.MdPageTitle(
                    text = state.title.ifBlank { "Музыка" },
                )
            }
            item(key = "md-toolbar") {
                CatalogToolbar(
                    filterOptions = state.tagCatalog,
                    selectedFilter = state.selectedLevel.takeIf { it != "Все теги" },
                    onFilterSelect = { tag ->
                        onIntent(MusicIntent.SetLevelFilter(tag ?: "Все теги"))
                    },
                    sortOptions = listOf(
                        stringResource(R.string.catalog_sort_tag) to {
                            onIntent(MusicIntent.SetSortMode(MusicSortMode.ByTag))
                        },
                        stringResource(R.string.catalog_sort_alpha) to {
                            onIntent(MusicIntent.SetSortMode(MusicSortMode.Alphabetical))
                        },
                    ),
                    sortActive = state.sortMode != MusicSortMode.ByTag,
                    searchQuery = state.searchQuery,
                    onSearchChange = { onIntent(MusicIntent.SetSearchQuery(it)) },
                    searchPlaceholder = stringResource(R.string.music_search_placeholder),
                    haptics = haptics,
                    allFiltersLabel = "Все теги",
                )
            }
            item(key = "md-control-deck") {
                val raiseHardDisk = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current
                ControlDeckCard(
                    onUpload = {
                        if (!com.midnightdancer.app.ui.components.guardMediaDiskOrRaise(raiseHardDisk)) {
                            return@ControlDeckCard
                        }
                        haptics.perform(HapticEvent.MediumImpact)
                        onIntent(MusicIntent.BeginUpload)
                    },
                    haptics = haptics,
                )
            }
            if (playing != null && visible.any { it.id == playing.id }) {
                item(key = "md-playing-${playing.id}") {
                    PlayingHeroCard(
                        track = playing,
                        onStop = { onIntent(MusicIntent.StopTrack(playing.id)) },
                        onDelete = { onIntent(MusicIntent.DeleteTrack(playing.id)) },
                        onEdit = { onIntent(MusicIntent.EditTrack(playing.id)) },
                        haptics = haptics,
                    )
                }
            }
            items(
                items = libraryTracks,
                key = { it.id },
            ) { track ->
                LibraryTrackRow(
                    track = track,
                    onPlay = { onIntent(MusicIntent.SelectTrack(track.id)) },
                    onDelete = { onIntent(MusicIntent.DeleteTrack(track.id)) },
                    onEdit = { onIntent(MusicIntent.EditTrack(track.id)) },
                    haptics = haptics,
                )
            }
        }

        state.editingTrack?.let { track ->
            TrackEditDialog(
                track = track,
                styles = state.styleCatalog,
                tagCatalog = state.tagCatalog,
                onDismiss = { onIntent(MusicIntent.CloseEdit) },
                onSave = { style, tags, speed ->
                    onIntent(
                        MusicIntent.SaveTrackEdit(
                            id = track.id,
                            style = style,
                            tags = tags,
                            basePlaybackSpeed = speed,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ControlDeckCard(
    onUpload: () -> Unit,
    haptics: HapticFeedback,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(DeckSurface)
            .border(1.dp, Hairline, CardShape)
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                    ),
                )
                .interactiveSpringPress(
                    pressedScale = 0.96f,
                    hapticEvent = HapticEvent.MediumImpact,
                    haptics = haptics,
                    onClick = onUpload,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
                Text(
                    text = "Загрузить трек",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MdFonts.Display,
                )
            }
        }
    }
}

@Composable
private fun TrackEditDialog(
    track: MusicTrack,
    styles: List<String>,
    tagCatalog: List<String>,
    onDismiss: () -> Unit,
    onSave: (style: String, tags: List<String>, speed: Double) -> Unit,
) {
    var style by remember(track.id) { mutableStateOf(track.style) }
    var selectedTag by remember(track.id) {
        mutableStateOf(track.tags.firstOrNull() ?: track.level)
    }
    var speed by remember(track.id) {
        mutableFloatStateOf(track.basePlaybackSpeed.toFloat().coerceIn(0.4f, 2f))
    }
    var openPicker by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xF0121319),
        title = {
            Text(
                text = stringResource(R.string.music_edit_title),
                color = Color.White,
                fontFamily = MdFonts.Display,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = track.title,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = MdFonts.Display,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                SearchablePicker(
                    label = "Стиль",
                    value = style,
                    options = styles,
                    expanded = openPicker == "style",
                    onExpandedChange = { open ->
                        openPicker = if (open) "style" else null
                    },
                    onSelect = { style = it },
                    panelMaxHeight = 180.dp,
                )
                SearchablePicker(
                    label = "Тег",
                    value = selectedTag,
                    options = tagCatalog,
                    expanded = openPicker == "tag",
                    onExpandedChange = { open ->
                        openPicker = if (open) "tag" else null
                    },
                    onSelect = { selectedTag = it },
                    panelMaxHeight = 180.dp,
                )
                MdFieldLabel(
                    text = "${stringResource(R.string.music_edit_speed)}: " +
                        String.format("%.1f×", speed),
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = DomainValidators.PLAYBACK_SPEED_MIN.toFloat()..
                        DomainValidators.PLAYBACK_SPEED_MAX.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AtelierColors.Flame,
                        activeTrackColor = AtelierColors.Flame,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(style, listOf(selectedTag), speed.toDouble())
                },
            ) {
                Text("Сохранить", color = AtelierColors.Flame)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.6f))
            }
        },
    )
}

@Composable
private fun PlayingHeroCard(
    track: MusicTrack,
    onStop: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    haptics: HapticFeedback,
) {
    // VaultCard parity: title+actions / meta+tag (+ pause + progress for playing).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0E0F16), Color(0xFF101015), Color(0xFF0C0D12)),
                ),
            )
            .border(1.dp, AtelierColors.Flame.copy(alpha = 0.40f), CardShape)
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PulsingPauseButton(onClick = onStop)
                Spacer(modifier = Modifier.width(4.dp))
                com.midnightdancer.app.ui.components.MarqueeText(
                    text = track.title,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Редактировать",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${track.style} · ${track.durationLabel}",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    fontFamily = MdFonts.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TrackTags(tags = track.tags.ifEmpty { listOf(track.level) }.take(1))
            }
            ProgressShimmerBar(
                progress = track.progress.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PulsingPauseButton(onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pause-pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pause-scale",
    )
    val alpha by pulse.animateFloat(
        initialValue = 0.70f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pause-alpha",
    )
    Box(
        modifier = Modifier
            .size(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                ),
            )
            .interactiveSpringPress(
                pressedScale = 0.94f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = rememberMdHaptics(),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Pause,
            contentDescription = "Стоп",
            tint = Color.Black,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ProgressShimmerBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shift by shimmer.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-x",
    )
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF12131A)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * shift
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: MusicTrack,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    haptics: HapticFeedback,
) {
    // C-95: VaultCard parity — title+edit/delete / meta+tag; tap card to play.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(0xCC0A0B10))
            .border(1.dp, Hairline, CardShape)
            .interactiveSpringPress(
                pressedScale = 0.985f,
                hapticEvent = HapticEvent.LightImpact,
                haptics = haptics,
                onClick = onPlay,
            )
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MdFonts.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Редактировать",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${track.style} · ${track.durationLabel}",
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    fontFamily = MdFonts.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TrackTags(tags = track.tags.ifEmpty { listOf(track.level) }.take(1))
            }
        }
    }
}

@Composable
private fun TrackTags(tags: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.take(1).forEach { tag ->
            Text(
                text = tag,
                color = AtelierColors.Soft,
                fontSize = 10.sp,
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(AtelierColors.Flame.copy(alpha = 0.12f))
                    .border(1.dp, AtelierColors.Flame.copy(alpha = 0.30f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}
