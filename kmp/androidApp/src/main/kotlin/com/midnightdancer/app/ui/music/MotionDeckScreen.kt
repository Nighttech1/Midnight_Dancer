package com.midnightdancer.app.ui.music

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.ui.media.*
import com.midnightdancer.core.domain.media.OwnedMediaKind
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.midnightdancer.app.R
import com.midnightdancer.app.audio.LibraryAudioController
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.components.MdSettingsGear
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
import com.midnightdancer.core.domain.audio.LibraryAudioStopBus
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.feature.music.logic.MusicIntent
import com.midnightdancer.feature.music.logic.MusicSortMode
import com.midnightdancer.feature.music.logic.MusicTrack
import com.midnightdancer.feature.music.logic.MusicUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val Hairline = Color.White.copy(alpha = 0.12f)
private val DeckSurface = Color(0xCC0A0B10)
private val PlayButtonSize = 36.dp
private val PlayButtonGap = 10.dp

private fun ExoPlayer.knownDurationMs(): Long? =
    duration.takeIf { it != C.TIME_UNSET && it > 0L }

/** C-180: duration before first Play via MediaMetadataRetriever. */
private fun audioDurationMs(context: Context, uri: Uri): Long? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, Uri.parse(com.midnightdancer.core.data.files.OwnedMediaRuntime.resolveReference(uri.toString())))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?.takeIf { it > 0L }
        }
    }.getOrNull()

/** Display name of a picked document (extension stripped) — DocumentsContract column first. */
private fun audioDisplayName(context: Context, uri: Uri): String {
    val raw = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
    val name = raw.orEmpty().trim()
    val noExt = name.substringBeforeLast('.', missingDelimiterValue = name)
    return noExt.ifBlank { name }.ifBlank { "Новый трек" }
}

/**
 * Stitch `midnight_dancer_motion_deck` — OLED Motion Deck (C-62 / C-71 / C-76 / C-132).
 */
@Composable
fun MotionDeckScreen(
    state: MusicUiState,
    onIntent: (MusicIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val copyController = rememberOwnedMediaCopyController()
    val copyState by copyController.state.collectAsStateWithLifecycle()
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.30f)
    val playing = state.playingTrack
    // C-132 / 2.3: selected track keeps its list slot — no hero split.
    val visible = state.filteredTracks

    val livePosition = remember { mutableFloatStateOf(0f) }
    var scrubbing by remember { mutableStateOf(false) }
    val scrubScope = rememberCoroutineScope()
    val latestOnIntent by rememberUpdatedState(onIntent)
    val playingId = playing?.id
    val playingMediaUri = playing?.mediaUri
    val latestPlayingId by rememberUpdatedState(playingId)
    val latestPlayingMediaUri by rememberUpdatedState(playingMediaUri)
    val latestSpeed by rememberUpdatedState(playing?.basePlaybackSpeed ?: 1.0)
    val latestStartProgress by rememberUpdatedState(playing?.progress ?: 0f)
    // C-152: selection survives Stop — glow + scrubber follow selectedId, not isPlaying.
    val selectedId = state.selectedTrackId
    val latestSelectedProgress by rememberUpdatedState(state.selectedTrack?.progress ?: 0f)

    // Selection changed (or cleared): scrubber position follows the selected track's saved progress.
    LaunchedEffect(selectedId) {
        livePosition.floatValue = if (selectedId == null) 0f else latestSelectedProgress.coerceIn(0f, 1f)
    }

    // C-178: Trainer Live / Pro Editor request → force-stop library audio.
    LaunchedEffect(Unit) {
        LibraryAudioStopBus.requests.collect {
            LibraryAudioController.forceStop()
            latestPlayingId?.let { id ->
                latestOnIntent(MusicIntent.SeekTrack(id, livePosition.floatValue))
                latestOnIntent(MusicIntent.StopTrack(id))
            }
        }
    }

    // After ChildPages recreate: re-attach UI playing state to process-scoped player.
    LaunchedEffect(state.tracks.map { it.id }) {
        val id = LibraryAudioController.activeTrackId ?: return@LaunchedEffect
        if (!LibraryAudioController.isActuallyPlaying()) return@LaunchedEffect
        if (state.tracks.none { it.id == id }) return@LaunchedEffect
        if (state.playingTrack?.id == id) return@LaunchedEffect
        latestOnIntent(MusicIntent.SelectTrack(id))
    }

    // Picks an audio file → draft track named after the file → TrackEditDialog.
    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val title = audioDisplayName(context, uri)
            copyController.save(uri.toString(), OwnedMediaKind.Audio) { owned ->
                latestOnIntent(MusicIntent.AddPickedTrack(title = title, mediaUri = owned.reference))
            }
        }
    }

    // After pick: apply duration to the newest draft (editingTrackId).
    LaunchedEffect(state.editingTrackId, state.editingTrack?.mediaUri) {
        val track = state.editingTrack ?: return@LaunchedEffect
        val uri = track.mediaUri?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (track.durationLabel != "00:00") return@LaunchedEffect
        val ms = audioDurationMs(context, Uri.parse(uri)) ?: return@LaunchedEffect
        latestOnIntent(MusicIntent.UpdateTrackDuration(track.id, ms))
    }

    val latestTracksPlaying by rememberUpdatedState(state.playingTrack)
    val latestTracks by rememberUpdatedState(state.tracks)

    // Start / pause process-scoped ExoPlayer + progress ticker (C-178 survives tabs / minimize).
    LaunchedEffect(playingId, playingMediaUri) {
        val id = playingId
        val mediaUri = latestPlayingMediaUri
        if (id == null || mediaUri.isNullOrBlank()) {
            // Recreate after DESTROY: flags cleared but process player still going — don't pause.
            val active = LibraryAudioController.activeTrackId
            if (active != null &&
                LibraryAudioController.isActuallyPlaying() &&
                latestTracksPlaying == null &&
                latestTracks.any { it.id == active }
            ) {
                return@LaunchedEffect
            }
            // Explicit pause/stop from UI.
            LibraryAudioController.pause()
            LibraryAudioController.clearPlayingMark()
            return@LaunchedEffect
        }
        val exo = LibraryAudioController.obtain(context)
        // C-182: same track still playing after tab remount — attach ticker only, no seek(0).
        val continuing = LibraryAudioController.isContinuing(id, mediaUri)
        if (!continuing) {
            LibraryAudioController.markPlaying(id, mediaUri)
            exo.setMediaItem(MediaItem.fromUri(Uri.parse(mediaUri)))
            exo.prepare()
            exo.setPlaybackSpeed(latestSpeed.toFloat())
            val start = latestStartProgress.coerceIn(0f, 1f)
            livePosition.floatValue = start
            exo.playWhenReady = true
            // C-194: wait until duration is known before seeking — never force 0 while probing.
            var waited = 0
            var duration = exo.knownDurationMs()
            while (duration == null && waited < 200) {
                delay(50)
                waited++
                duration = exo.knownDurationMs()
            }
            if (duration != null) {
                latestOnIntent(MusicIntent.UpdateTrackDuration(id, duration))
                if (start > 0.001f) {
                    exo.seekTo((start * duration).toLong().coerceIn(0L, duration - 1L))
                }
            }
        } else {
            LibraryAudioController.markPlaying(id, mediaUri)
            exo.setPlaybackSpeed(latestSpeed.toFloat())
            exo.playWhenReady = true
            val d = exo.knownDurationMs()
            if (d != null && d > 0L) {
                livePosition.floatValue =
                    (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            }
        }
        var sinceSync = 0
        while (true) {
            delay(50)
            val d = exo.knownDurationMs() ?: LibraryAudioController.knownDurationMs()
            if (d != null && !scrubbing) {
                livePosition.floatValue = (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f)
            }
            sinceSync += 50
            if (sinceSync >= 500) {
                sinceSync = 0
                if (!scrubbing) {
                    latestOnIntent(MusicIntent.SeekTrack(id, livePosition.floatValue))
                }
            }
        }
    }
    LaunchedEffect(playing?.basePlaybackSpeed) {
        LibraryAudioController.current()
            ?.setPlaybackSpeed((playing?.basePlaybackSpeed ?: 1.0).toFloat())
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

        // C-142: fixed header (title + upload + toolbar); only the track list scrolls.
        Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                com.midnightdancer.app.ui.components.MdPageTitle(
                    text = state.title.ifBlank { "Музыка" },
                    modifier = Modifier.weight(1f),
                )
                MdSettingsGear(onClick = onOpenSettings, haptics = haptics)
            }
            val raiseHardDisk = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current
            ControlDeckCard(
                onUpload = {
                    if (state.saving || copyState.busy || !com.midnightdancer.app.ui.components.guardMediaDiskOrRaise(raiseHardDisk)) {
                        return@ControlDeckCard
                    }
                    haptics.perform(HapticEvent.MediumImpact)
                    pickAudio.launch(arrayOf("audio/*"))
                },
                haptics = haptics,
            )
            (copyState.error ?: state.operationError)?.let { Text(it, color = Color(0xFFFFB4AB)) }
            if (state.saving) Text("Сохраняем музыку…", color = Color.LightGray)
            CatalogToolbar(
                filterOptions = state.tagCatalog,
                selectedFilter = state.selectedLevel.takeIf {
                    !it.equals("Все", ignoreCase = true) && !it.equals("Все теги", ignoreCase = true)
                },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 0.dp,
                bottom = 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            flingBehavior = softFling,
        ) {
            items(
                items = visible,
                key = { it.id },
            ) { track ->
                val rowPlaying = track.isPlaying
                val rowSelected = track.id == selectedId
                val progress: () -> Float = remember(track.id, rowSelected) {
                    if (rowSelected) {
                        { livePosition.floatValue }
                    } else {
                        { 0f }
                    }
                }
                LibraryTrackRow(
                    track = track.copy(style = state.displayStyle(track.style)),
                    selected = rowSelected,
                    progress = progress,
                    onTogglePlay = {
                        if (rowPlaying) {
                            LibraryAudioController.pause()
                            LibraryAudioController.clearPlayingMark()
                            onIntent(MusicIntent.SeekTrack(track.id, livePosition.floatValue))
                            onIntent(MusicIntent.StopTrack(track.id))
                        } else if (track.mediaUri.isNullOrBlank()) {
                            onIntent(MusicIntent.SelectTrack(track.id))
                            onIntent(MusicIntent.StopTrack(track.id))
                        } else {
                            onIntent(MusicIntent.SelectTrack(track.id))
                        }
                    },
                    onScrub = { fraction ->
                        scrubbing = true
                        val f = fraction.coerceIn(0f, 1f)
                        livePosition.floatValue = f
                        // C-194: seek while dragging so UI + audio stay mid-track (no snap-to-0).
                        val exo = LibraryAudioController.current()
                        val d = exo?.knownDurationMs()
                            ?: LibraryAudioController.knownDurationMs()
                            ?: (parseDurationLabelToSec(track.durationLabel) * 1000L)
                                .takeIf { it > 0L }
                        if (exo != null && d != null && d > 0L) {
                            exo.seekTo((f * d).toLong().coerceIn(0L, d - 1L))
                        }
                    },
                    onScrubEnd = { fraction ->
                        val exo = LibraryAudioController.current()
                        val f = fraction.coerceIn(0f, 1f)
                        livePosition.floatValue = f
                        onIntent(MusicIntent.SeekTrack(track.id, f))
                        // Keep scrubbing=true until seek lands so ticker can't snap to old position.
                        scrubScope.launch {
                            if (exo != null) {
                                var d = exo.knownDurationMs()
                                    ?: LibraryAudioController.knownDurationMs()
                                var waited = 0
                                while (d == null && waited < 80) {
                                    delay(25)
                                    d = exo.knownDurationMs()
                                        ?: LibraryAudioController.knownDurationMs()
                                    waited++
                                }
                                if (d == null) {
                                    d = (parseDurationLabelToSec(track.durationLabel) * 1000L)
                                        .takeIf { it > 0L }
                                }
                                if (d != null && d > 0L) {
                                    val target = (f * d).toLong().coerceIn(0L, d - 1L)
                                    exo.seekTo(target)
                                    // Hold scrub lock until player reports near the target.
                                    var settle = 0
                                    while (
                                        settle < 20 &&
                                        kotlin.math.abs(exo.currentPosition - target) > 120L
                                    ) {
                                        delay(25)
                                        settle++
                                    }
                                }
                            }
                            livePosition.floatValue = f
                            scrubbing = false
                        }
                    },
                    onDelete = { onIntent(MusicIntent.DeleteTrack(track.id)) },
                    onEdit = { onIntent(MusicIntent.EditTrack(track.id)) },
                    haptics = haptics,
                )
            }
        }
        }

        state.editingTrack?.let { track ->
            TrackEditDialog(
                track = track.copy(style = state.displayStyle(track.style)),
                styles = state.styleCatalog,
                tagCatalog = state.tagCatalog,
                saving = state.saving,
                error = state.operationError,
                onDismiss = { onIntent(MusicIntent.CloseEdit) },
                onSave = { title, style, tags, speed ->
                    onIntent(
                        MusicIntent.SaveTrackEdit(
                            id = track.id,
                            title = title,
                            style = style,
                            tags = tags,
                            basePlaybackSpeed = speed,
                        ),
                    )
                },
            )
        }
    }
    OwnedMediaCopyDialog(copyController)
}

@Composable
private fun ControlDeckCard(
    onUpload: () -> Unit,
    haptics: HapticFeedback,
) {
    // C-230: white label + shimmer (parity «Создать элемент»).
    com.midnightdancer.app.ui.components.FlameShimmerCta(
        label = "Загрузить трек",
        haptics = haptics,
        onClick = onUpload,
    )
}

@Composable
private fun TrackEditDialog(
    track: MusicTrack,
    styles: List<String>,
    tagCatalog: List<String>,
    saving: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, style: String, tags: List<String>, speed: Double) -> Unit,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var style by remember(track.id) { mutableStateOf(track.style) }
    var selectedTag by remember(track.id) {
        mutableStateOf(track.tags.firstOrNull { it.isNotBlank() } ?: "")
    }
    var speed by remember(track.id) {
        mutableFloatStateOf(track.basePlaybackSpeed.toFloat().coerceIn(0.4f, 2f))
    }
    var openPicker by remember { mutableStateOf<String?>(null) }
    val fieldShape = RoundedCornerShape(12.dp)
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
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
                error?.let { Text(it, color = Color(0xFFFFB4AB)) }
                if (saving) Text("Сохраняем…", color = Color.LightGray)
                // C-180 / C-184: editable track name with select-all on focus.
                MdFieldLabel(text = "Название")
                com.midnightdancer.app.ui.components.MdSelectAllTextField(
                    value = title,
                    onValueChange = { title = it.take(80) },
                    singleLine = true,
                    maxLength = 80,
                    selectAllOnOpen = true,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontFamily = MdFonts.Display,
                    ),
                    cursorBrush = SolidColor(AtelierColors.Flame),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(fieldShape)
                        .background(Color(0xFF12131A))
                        .border(1.dp, AtelierColors.Flame.copy(alpha = 0.45f), fieldShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    decorationBox = { inner ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    "Имя трека",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 16.sp,
                                    fontFamily = MdFonts.Display,
                                )
                            }
                            inner()
                        }
                    },
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
                        String.format(Locale.US, "%.1f×", speed),
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
                enabled = !saving,
                onClick = {
                    // Style / tag are optional — blank is saved as blank.
                    onSave(
                        title.trim().ifBlank { track.title },
                        style.trim(),
                        listOf(selectedTag).filter { it.isNotBlank() },
                        speed.toDouble(),
                    )
                },
            ) {
                Text(
                    "Сохранить",
                    color = AtelierColors.Flame,
                    fontSize = 15.sp,
                    fontFamily = MdFonts.Display,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(
                    "Отмена",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 15.sp,
                    fontFamily = MdFonts.Display,
                )
            }
        },
    )
}

/**
 * Card surface + hairline; when [active] adds a pulsing Flame perimeter glow (C-132 / 2.5).
 * Pulse is read in the draw phase only — no recomposition per frame.
 */
@Composable
private fun Modifier.trackCardSurface(active: Boolean): Modifier {
    val transition = rememberInfiniteTransition(label = "track-glow")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "track-glow-pulse",
    )
    return this.drawWithContent {
        val corner = CornerRadius(18.dp.toPx())
        if (active) {
            val glow = AtelierColors.Flame
            listOf(
                12f to 0.05f,
                8f to 0.09f,
                4f to 0.16f,
            ).forEach { (width, alpha) ->
                val w = width.dp.toPx()
                drawRoundRect(
                    color = glow.copy(alpha = alpha * pulse),
                    topLeft = Offset(-w / 2f, -w / 2f),
                    size = Size(size.width + w, size.height + w),
                    cornerRadius = CornerRadius(corner.x + w / 2f),
                    style = Stroke(width = w),
                )
            }
        }
        drawRoundRect(color = DeckSurface, cornerRadius = corner)
        drawContent()
        if (active) {
            val stroke = 1.5.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(
                        AtelierColors.Flame.copy(alpha = 0.55f + 0.45f * pulse),
                        AtelierColors.Glow.copy(alpha = 0.45f + 0.45f * pulse),
                        AtelierColors.Gold.copy(alpha = 0.40f + 0.45f * pulse),
                    ),
                ),
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(corner.x - stroke / 2f),
                style = Stroke(width = stroke),
            )
        } else {
            val stroke = 1.dp.toPx()
            drawRoundRect(
                color = Hairline,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                cornerRadius = CornerRadius(corner.x - stroke / 2f),
                style = Stroke(width = stroke),
            )
        }
    }
}

/** Static Play ↔ Stop (no scale / pulse animation — C-132 / 2.4). */
@Composable
private fun TrackPlayButton(
    playing: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PlayButtonSize)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(
                    listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                ),
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Стоп" else "Воспроизвести",
            tint = Color.Black,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Flame draggable scrubber (Live parity). [progress] is read in the draw phase so the
 * strip + thumb move without recomposing the row.
 */
@Composable
private fun TrackScrubber(
    progress: () -> Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var lastFraction by remember { mutableFloatStateOf(0f) }
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.35f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "scrub-thumb",
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val f = (offset.x / size.width).coerceIn(0f, 1f)
                    onScrub(f)
                    onScrubEnd(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        lastFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onScrub(lastFraction)
                    },
                    onDragEnd = {
                        dragging = false
                        onScrubEnd(lastFraction)
                    },
                    onDragCancel = {
                        dragging = false
                        onScrubEnd(lastFraction)
                    },
                ) { change, _ ->
                    change.consume()
                    lastFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onScrub(lastFraction)
                }
            },
    ) {
        val thumbR = 7.dp.toPx()
        val trackH = 6.dp.toPx()
        val cy = size.height / 2f
        val left = thumbR
        val usable = (size.width - thumbR * 2f).coerceAtLeast(1f)
        val p = progress().coerceIn(0f, 1f)
        val x = left + usable * p
        drawRoundRect(
            color = Color(0xFF12131A),
            topLeft = Offset(left, cy - trackH / 2f),
            size = Size(usable, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        if (x > left) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                    startX = left,
                    endX = left + usable,
                ),
                topLeft = Offset(left, cy - trackH / 2f),
                size = Size(x - left, trackH),
                cornerRadius = CornerRadius(trackH / 2f),
            )
        }
        val r = thumbR * thumbScale
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(AtelierColors.Flame.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(x, cy),
                radius = r * 2.6f,
            ),
            radius = r * 2.6f,
            center = Offset(x, cy),
        )
        drawCircle(color = AtelierColors.Flame, radius = r, center = Offset(x, cy))
        drawCircle(color = Color.White.copy(alpha = 0.85f), radius = r * 0.38f, center = Offset(x, cy))
    }
}

@Composable
private fun LibraryTrackRow(
    track: MusicTrack,
    progress: () -> Float,
    onTogglePlay: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    haptics: HapticFeedback,
    selected: Boolean,
) {
    val playing = track.isPlaying
    val speedLabel = String.format(Locale.US, "%.1f×", track.basePlaybackSpeed)
    // C-152: skip blank style — never render an empty « · » lead.
    val metaLine = listOf(track.style, track.durationLabel, speedLabel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .trackCardSurface(active = selected)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrackPlayButton(
                playing = playing,
                onClick = {
                    haptics.perform(HapticEvent.LightImpact)
                    onTogglePlay()
                },
            )
            Spacer(modifier = Modifier.width(PlayButtonGap))
            // Title + meta share one column → meta starts under title text, not under Play.
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                        text = metaLine,
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 12.sp,
                        fontFamily = MdFonts.Display,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // C-152: only a real tag — no fallback to level, no oval when absent.
                    TrackTags(tags = track.tags.filter { it.isNotBlank() }.take(1))
                }
            }
        }
        if (selected) {
            // C-180: times flush under scrub — cut ~2× label height of dead space.
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                TrackScrubber(
                    progress = progress,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                )
                val frac = progress().coerceIn(0f, 1f)
                val totalSec = parseDurationLabelToSec(track.durationLabel)
                val curSec = (frac * totalSec).toInt().coerceAtLeast(0)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = formatMmSs(curSec),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontFamily = MdFonts.Display,
                        lineHeight = 12.sp,
                    )
                    Text(
                        text = formatMmSs(totalSec),
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp,
                        fontFamily = MdFonts.Display,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

/** Parse "mm:ss" / "m:ss" labels; blank or "—" → 0. */
private fun parseDurationLabelToSec(label: String): Int {
    val parts = label.trim().split(':')
    if (parts.size != 2) return 0
    val m = parts[0].toIntOrNull() ?: return 0
    val s = parts[1].toIntOrNull() ?: return 0
    return (m * 60 + s).coerceAtLeast(0)
}

private fun formatMmSs(totalSec: Int): String {
    val t = totalSec.coerceAtLeast(0)
    return "%02d:%02d".format(t / 60, t % 60)
}

@Composable
private fun TrackTags(tags: List<String>) {
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.filter { it.isNotBlank() }.take(1).forEach { tag ->
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
