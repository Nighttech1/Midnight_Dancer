package com.midnightdancer.app.ui.choreography

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import android.net.Uri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.MarqueeText
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.elements.EditorScrubFrameCache
import com.midnightdancer.app.ui.elements.ElementThumbCache
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.feature.choreography.logic.ChoreoCatalog
import com.midnightdancer.feature.choreography.logic.EditorClip
import com.midnightdancer.feature.choreography.logic.EditorTrackDurations
import com.midnightdancer.feature.choreography.logic.MAX_TIMELINE_ZOOM
import com.midnightdancer.feature.choreography.logic.MIN_TIMELINE_ZOOM
import com.midnightdancer.feature.choreography.logic.SequenceEditorIntent
import com.midnightdancer.feature.choreography.logic.SequenceEditorUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SurfaceLow = Color(0xB31A1B20)
private val SurfaceLowest = Color(0xFF0D0E12)
private val Hairline = Color.White.copy(alpha = 0.10f)

private val LaneHeight = 36.dp
private val LaneGap = 8.dp
private val LanePlusWidth = 36.dp
private val LanePlusGap = 8.dp
private val PlayheadThumb = 16.dp
private val PlayheadThumbTouch = 32.dp
private val MinClipWidth = 18.dp
/**
 * Music under playhead. With Media3 clipping (C-247), [localMs] is offset inside the clipped
 * item (0 = clip start) — not absolute file position.
 */
private data class MusicLocal(
    val index: Int,
    val localMs: Long,
    val uri: String?,
    val sourceStartMs: Long,
    val clipDurationMs: Long,
)

/** Timeline scale at 100% zoom: dp of bar per second of choreography (5.4). */
private val PxPerSecAt100 = 20.dp
private const val PLAY_TICK_MS = 33L
private const val SEEK_EPSILON_MS = 80L
/** User scrub jump while playing — ignore ticker noise below this (C-251: was 220 → less thrash). */
private const val SEEK_WHILE_PLAY_MS = 750L
/**
 * While dragging — throttled seek (C-285: 120ms thrashed decodes → blurry preview).
 * Was 10_000ms (C-247) which froze the new clip on reverse scrub after handoff.
 */
private const val SEEK_WHILE_SCRUB_MS = 280L
/** C-290: start preparing next/prev clip on back player within this window. */
private const val PREPARE_AHEAD_MS = 700L

/** Temporary scrub A/V diagnostics — remove after Client PASS (C-251). */
private fun mdAvLog(msg: String) {
    android.util.Log.d("MdAvDebug", msg)
}

private fun resolveMusicLocal(s: SequenceEditorUiState): MusicLocal? {
    val pair = EditorTrackDurations.clipLocalMs(s.musicTimelineClips, s.playheadMs) ?: return null
    val idx = pair.first
    val clip = s.musicClips.getOrNull(idx) ?: return null
    val localMs = (pair.second * s.songPlaybackSpeed).toLong().coerceAtLeast(0L)
    return MusicLocal(
        index = idx,
        localMs = localMs,
        uri = clip.mediaUri,
        sourceStartMs = clip.sourceStartMs.coerceAtLeast(0L),
        clipDurationMs = clip.durationMs.coerceAtLeast(1L),
    )
}

/** C-247 H1: clip media so ExoPlayer opens at cut — avoids 4–5s mid-file keyframe seek. */
private fun clippedMediaItem(uri: String, sourceStartMs: Long, durationMs: Long): MediaItem {
    val start = sourceStartMs.coerceAtLeast(0L)
    val end = start + durationMs.coerceAtLeast(1L)
    return MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(start)
                .setEndPositionMs(end)
                .build(),
        )
        .build()
}

/** C-271: lock centiseconds to 00 while the user is scrubbing. */
private fun freezeTimecodeCentiseconds(timecode: String): String {
    val parts = timecode.split('.')
    return if (parts.size >= 3) {
        "${parts[0]}.${parts[1]}.00"
    } else {
        timecode
    }
}

private fun mediaBindKey(uri: String?, sourceStartMs: Long, durationMs: Long): String =
    "${uri.orEmpty()}|$sourceStartMs|$durationMs"

/** C-251 H1: music binds by URI only — seek absolute file pos; no re-prepare on clip edge. */
private fun musicUriBindKey(uri: String?): String = uri.orEmpty()

/**
 * Pro Editor — CapCut-style dock tools, lane + buttons, drag reorder (C-85).
 * C-135 / C-136: style-scoped pickers, Save, proportional timeline + scrub playhead.
 */
@Composable
fun ProEditorScreen(
    state: SequenceEditorUiState,
    onIntent: (SequenceEditorIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val playPulse by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0f,
        animationSpec = MdMotion.interactiveUnderdamped,
        label = "play-pulse",
    )
    var showElementPicker by remember { mutableStateOf(false) }
    var showMusicPicker by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showZoom by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }
    var pickerSpeed by remember { mutableFloatStateOf(state.songPlaybackSpeed.toFloat()) }

    LaunchedEffect(state.saveCount) {
        if (state.saveCount > 0) {
            savedFlash = true
            delay(1400)
            savedFlash = false
        }
    }

    // C-223: one clock = playhead ticker; Media3 music/video play()/pause() on toggle only.
    // Prepare once per URI; seek only on scrub (paused) or clip handoff — never every tick.
    val context = LocalContext.current
    val latestState by rememberUpdatedState(state)
    val latestOnIntent by rememberUpdatedState(onIntent)
    var timelineScrubbing by remember { mutableStateOf(false) }
    /** C-269: after user Seek/double-tap, play ticker must not overwrite with stale fraction. */
    var suppressPlayTickUntilMs by remember { mutableLongStateOf(0L) }
    /** C-271: freeze displayed centiseconds while scrubbing. */
    var scrubDisplayTimecode by remember { mutableStateOf<String?>(null) }
    fun noteUserSeek() {
        suppressPlayTickUntilMs = android.os.SystemClock.uptimeMillis() + 450L
    }
    val player = remember {
        ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context)).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            // C-247 H3: closest sync seeks — snappier after scrub / clip handoff.
            setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                if (latestState.isPlaying) latestOnIntent(SequenceEditorIntent.TogglePlay)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    val musicLocal = resolveMusicLocal(state)
    val musicUri = musicLocal?.uri
    // Absolute file position = clip sourceStart + local offset inside clip.
    val musicAbsMs = ((musicLocal?.sourceStartMs ?: 0L) + (musicLocal?.localMs ?: 0L)).coerceAtLeast(0L)
    val musicBindKey = musicUriBindKey(musicLocal?.uri)
    var boundMusicKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(musicBindKey) {
        val local = resolveMusicLocal(latestState)
        val uri = local?.uri
        if (local == null || uri.isNullOrBlank()) {
            player.pause()
            boundMusicKey = null
            mdAvLog("music clear bind")
            return@LaunchedEffect
        }
        if (musicBindKey != boundMusicKey) {
            boundMusicKey = musicBindKey
            // C-251 H1: full-file item — seek absolute; no clip re-prepare on block edge.
            mdAvLog("music prepare uri=$uri")
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(uri)))
            player.prepare()
            val abs = (local.sourceStartMs + local.localMs).coerceAtLeast(0L)
            player.seekTo(abs)
            if (latestState.isPlaying) {
                player.setPlaybackSpeed(latestState.songPlaybackSpeed.toFloat())
                player.play()
            }
        }
    }
    LaunchedEffect(state.songPlaybackSpeed, state.musicVolume) {
        player.volume = state.musicVolume.coerceIn(0f, 1f)
        player.setPlaybackSpeed(state.songPlaybackSpeed.toFloat())
    }
    // C-251 H2: raise play threshold; while scrubbing skip; log seeks.
    LaunchedEffect(musicAbsMs, musicBindKey, state.isPlaying, timelineScrubbing) {
        if (musicUri.isNullOrBlank()) {
            player.pause()
            return@LaunchedEffect
        }
        val target = musicAbsMs
        val threshold = when {
            timelineScrubbing -> SEEK_WHILE_SCRUB_MS
            latestState.isPlaying -> SEEK_WHILE_PLAY_MS
            else -> SEEK_EPSILON_MS
        }
        val drift = kotlin.math.abs(player.currentPosition - target)
        if (drift > threshold) {
            mdAvLog("music seek pos=${player.currentPosition}→$target drift=$drift thr=$threshold scrub=$timelineScrubbing state=${player.playbackState}")
            player.seekTo(target)
            if (latestState.isPlaying && !player.isPlaying) player.play()
        }
    }
    LaunchedEffect(timelineScrubbing) {
        if (timelineScrubbing) return@LaunchedEffect
        val local = resolveMusicLocal(latestState) ?: return@LaunchedEffect
        if (local.uri.isNullOrBlank()) return@LaunchedEffect
        // C-251 H3/H4: EXACT settle after scrub, then resume CLOSEST_SYNC.
        val abs = (local.sourceStartMs + local.localMs).coerceAtLeast(0L)
        mdAvLog("music scrub-end seek abs=$abs")
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        player.seekTo(abs)
        player.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        if (latestState.isPlaying) {
            player.playWhenReady = true
            player.play()
        }
    }
    val latestScrubbing by rememberUpdatedState(timelineScrubbing)
    val latestSuppressUntil by rememberUpdatedState(suppressPlayTickUntilMs)
    LaunchedEffect(state.isPlaying) {
        if (!state.isPlaying) {
            player.pause()
            return@LaunchedEffect
        }
        // C-178: Pro Editor play stops library Music + Live session audio.
        com.midnightdancer.core.domain.audio.LibraryAudioStopBus.requestStop()
        com.midnightdancer.app.audio.LibraryAudioController.forceStop()
        com.midnightdancer.app.audio.TrainerLiveAudioController.forceStop()
        player.volume = latestState.musicVolume.coerceIn(0f, 1f)
        player.setPlaybackSpeed(latestState.songPlaybackSpeed.toFloat())
        val local = resolveMusicLocal(latestState)
        if (local != null && !local.uri.isNullOrBlank()) {
            val key = musicUriBindKey(local.uri)
            if (key != boundMusicKey) {
                boundMusicKey = key
                mdAvLog("music play-prepare uri=${local.uri}")
                player.setMediaItem(
                    androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(local.uri)),
                )
                player.prepare()
            }
            val abs = (local.sourceStartMs + local.localMs).coerceAtLeast(0L)
            player.seekTo(abs)
            player.play()
        }
        var last = System.nanoTime()
        while (true) {
            delay(PLAY_TICK_MS)
            val now = System.nanoTime()
            val dtMs = (now - last) / 1_000_000L
            last = now
            val s = latestState
            if (!s.isPlaying || s.durationMs <= 0L) break
            // C-269: skip tick while user seek / scrub latch is active.
            if (latestScrubbing ||
                android.os.SystemClock.uptimeMillis() < latestSuppressUntil
            ) {
                continue
            }
            val next = s.playheadFraction + dtMs.toFloat() / s.durationMs.toFloat()
            latestOnIntent(SequenceEditorIntent.SeekTo(next.coerceAtMost(1f)))
            if (next >= 1f) break
        }
    }
    // C-315 telemetry: log every change of the timeline total so the Client auto-QA catches an
    // unexpected jump (e.g. 01:06 → 01:42) objectively, without a human. Pure logging, no behavior.
    LaunchedEffect(state.durationMs) {
        android.util.Log.i(
            "MdPlaybackQA",
            "total durationMs=${state.durationMs} elements=${state.elementsDurationMs} " +
                "music=${state.musicDurationMs} playing=${state.isPlaying}",
        )
    }
    // C-231: after last element clip ends → empty preview (music may continue).
    val pastElements = state.elementClips.isNotEmpty() &&
        state.playheadMs >= state.elementsDurationMs
    // C-284: resolve clip by playhead lane (clipLocalMs), not stale playheadElementIndex —
    // index desync left preview URI null → black stage while label still showed on timeline.
    val previewClipIndex = remember(state.playheadMs, state.elementTimelineClips, pastElements) {
        if (pastElements) return@remember -1
        EditorTrackDurations.clipLocalMs(state.elementTimelineClips, state.playheadMs)?.first
            ?: state.playheadElementIndex
    }
    val previewClip = if (pastElements || previewClipIndex < 0) {
        null
    } else {
        state.elementClips.getOrNull(previewClipIndex)
    }
    // C-216: map playhead through effective element lane, then convert to source ms for ExoPlayer.
    // C-247: local ms inside clipped element item (0 at clip start).
    val previewLocalMs = remember(state.playheadMs, state.elementClips, state.elementTimelineClips, pastElements) {
        if (pastElements) return@remember 0L
        val pair = EditorTrackDurations.clipLocalMs(state.elementTimelineClips, state.playheadMs)
            ?: return@remember 0L
        val clip = state.elementClips.getOrNull(pair.first)
        val speed = clip?.speed?.takeIf { it > 0.0 } ?: 1.0
        (pair.second * speed).toLong().coerceAtLeast(0L)
    }
    val previewSourceStartMs = previewClip?.sourceStartMs?.coerceAtLeast(0L) ?: 0L
    val previewClipDurationMs = previewClip?.durationMs?.coerceAtLeast(1L) ?: 1L
    // C-290: neighbor clips + remaining ms for prepare-ahead.
    val previewTimelineLocalMs = remember(state.playheadMs, state.elementTimelineClips, pastElements) {
        if (pastElements) return@remember 0L
        EditorTrackDurations.clipLocalMs(state.elementTimelineClips, state.playheadMs)?.second ?: 0L
    }
    val previewClipEffDurMs = state.elementTimelineClips.getOrNull(previewClipIndex)?.durationMs?.coerceAtLeast(1L) ?: 1L
    val msToClipEnd = (previewClipEffDurMs - previewTimelineLocalMs).coerceAtLeast(0L)
    val msFromClipStart = previewTimelineLocalMs.coerceAtLeast(0L)
    val nextElementClip = state.elementClips.getOrNull(previewClipIndex + 1)?.takeIf {
        !pastElements && previewClipIndex >= 0
    }
    val prevElementClip = state.elementClips.getOrNull(previewClipIndex - 1)?.takeIf {
        !pastElements && previewClipIndex > 0
    }

    val elementBlocks = state.addedElementLabels
    val elementSelected = state.selectedElementIndex >= 0
    val volumeEnabled = !elementSelected
    // C-197: track speed always available (songPlaybackSpeed), not greyed when element selected.
    val speedEnabled = true

    Box(modifier = modifier.fillMaxSize().background(oled)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar always above preview (zIndex) — never covered by video stage.
            ProEditorTopBar(
                title = state.title,
                savedFlash = savedFlash,
                onSave = {
                    onIntent(SequenceEditorIntent.Save)
                    // 4.3.6 Save is synchronous in the component — return to the vault.
                    onBack()
                },
                onRenameRequest = { showRename = true },
                onBack = onBack,
                haptics = haptics,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(2f),
            )

            // C-293: CompositionPlayer preview (no dual-Exo seam). Keep scrub strip inside.
            val previewGlowPulse = rememberInfiniteTransition(label = "preview-glow-host")
            val previewGlow by previewGlowPulse.animateFloat(
                initialValue = 0.25f,
                targetValue = 0.65f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "glow-host",
            )
            ChoreoCompositionPreview(
                isPlaying = state.isPlaying,
                playPulse = playPulse,
                isEmpty = elementBlocks.isEmpty() || pastElements,
                currentElementLabel = if (pastElements) null else state.previewElementLabel,
                elementClips = state.elementClips,
                playheadInElementsMs = if (pastElements) {
                    state.elementsDurationMs
                } else {
                    state.playheadMs.coerceAtMost(state.elementsDurationMs)
                },
                scrubbing = timelineScrubbing,
                glow = previewGlow,
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .zIndex(0f),
            )

            // C-271: while scrubbing show MM.SS.00 (cs frozen).
            val shownTimecode = scrubDisplayTimecode
                ?: if (timelineScrubbing) {
                    freezeTimecodeCentiseconds(state.timecode)
                } else {
                    state.timecode
                }
            TransportConsole(
                timecode = shownTimecode,
                durationTimecode = state.durationTimecode,
                isPlaying = state.isPlaying,
                canUndo = state.canUndo,
                canRedo = state.canRedo,
                onIntent = onIntent,
                haptics = haptics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
            )

            val timelineVisible = remember { TimelineVisibleWindow() }
            DualTimelineTracks(
                // C-216: both lanes use effective durations (duration / speed).
                elementClips = state.elementTimelineClips,
                musicClips = state.musicTimelineClips,
                totalMs = state.durationMs,
                playheadFraction = state.playheadFraction,
                timelineZoom = state.timelineZoom,
                isPlaying = state.isPlaying,
                selectedElementIndex = state.selectedElementIndex,
                selectedMusicIndex = state.selectedMusicIndex,
                onAddElement = { showElementPicker = true },
                onAddMusic = {
                    pickerSpeed = state.songPlaybackSpeed.toFloat()
                    showMusicPicker = true
                },
                onSelectElement = { onIntent(SequenceEditorIntent.SelectElement(it)) },
                onSelectMusic = { onIntent(SequenceEditorIntent.SelectMusic(it)) },
                onReorderElement = { from, to ->
                    onIntent(SequenceEditorIntent.ReorderElements(from, to))
                },
                onReorderMusic = { from, to ->
                    onIntent(SequenceEditorIntent.ReorderMusic(from, to))
                },
                onSeek = {
                    noteUserSeek()
                    onIntent(SequenceEditorIntent.SeekTo(it))
                },
                onScrubbingChanged = { scrubbing ->
                    timelineScrubbing = scrubbing
                    if (scrubbing) {
                        scrubDisplayTimecode = freezeTimecodeCentiseconds(latestState.timecode)
                        noteUserSeek()
                    } else {
                        scrubDisplayTimecode = null
                    }
                },
                visibleWindow = timelineVisible,
                haptics = haptics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 6.dp),
            )

            // C-263: CapCut↔timeline gap — same visible scroll window as lanes (never off-screen jump).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 12.dp)
                    .pointerInput(timelineVisible) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val frac = timelineVisible.fractionAtLocalX(offset.x, w)
                                noteUserSeek()
                                latestOnIntent(SequenceEditorIntent.SeekTo(frac))
                            },
                        )
                    },
            )

            // 3.10 CapCut toolbar sits flush above the dock — Split, Delete, Volume, Speed.
            CapCutEditToolbar(
                onSplit = {
                    haptics.perform(HapticEvent.MediumImpact)
                    onIntent(SequenceEditorIntent.SplitSelected)
                },
                onDelete = {
                    haptics.perform(HapticEvent.MediumImpact)
                    onIntent(SequenceEditorIntent.DeleteSelected)
                },
                onVolume = { if (volumeEnabled) showVolume = true },
                onSpeed = { if (speedEnabled) showSpeed = true },
                onZoom = { showZoom = true },
                volumeEnabled = volumeEnabled,
                speedEnabled = speedEnabled,
                haptics = haptics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 0.dp),
            )
        }

        // C-272: refresh Room catalog whenever Add Element / Music opens.
        LaunchedEffect(showElementPicker, showMusicPicker) {
            if (showElementPicker || showMusicPicker) {
                onIntent(SequenceEditorIntent.RefreshCatalog)
            }
        }
        val elementPickerItems = remember(state.style, state.catalogRevision) {
            ChoreoCatalog.elementsForStyle(state.style)
        }
        val musicPickerItems = remember(state.style, state.catalogRevision) {
            ChoreoCatalog.tracksForStyle(state.style)
        }

        // 3.2 / 3.3 full-screen style-scoped pickers (Exchange picker pattern).
        if (showElementPicker) {
            ChoreoCatalogPickerScreen(
                title = "Элементы",
                styleName = state.styleDisplayName,
                items = elementPickerItems,
                searchPlaceholder = "Поиск техник, связок, ритмов...",
                emptyText = "В этом стиле пока нет элементов.",
                onConfirm = { picked ->
                    showElementPicker = false
                    if (picked.isNotEmpty()) {
                        onIntent(
                            SequenceEditorIntent.AddElementClips(
                                picked.map { ChoreoCatalog.elementClip(it) },
                            ),
                        )
                    }
                },
                onDismiss = { showElementPicker = false },
                modifier = Modifier.zIndex(5f),
            )
        }
        if (showMusicPicker) {
            ChoreoCatalogPickerScreen(
                title = "Музыка",
                styleName = state.styleDisplayName,
                items = musicPickerItems,
                searchPlaceholder = "Поиск треков...",
                emptyText = "В этом стиле пока нет треков.",
                showLevelFilter = false,
                speed = pickerSpeed,
                onSpeedChange = { pickerSpeed = it },
                onConfirm = { picked ->
                    showMusicPicker = false
                    if (picked.isNotEmpty()) {
                        onIntent(
                            SequenceEditorIntent.AddMusicClips(
                                picked.map { ChoreoCatalog.trackClip(it) },
                            ),
                        )
                    }
                    onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(pickerSpeed.toDouble()))
                },
                onDismiss = { showMusicPicker = false },
                modifier = Modifier.zIndex(5f),
            )
        }
    }

    // 3.1 double-tap title → same name + style dialog.
    if (showRename) {
        ChoreoNameStyleDialog(
            title = "Хореография",
            confirmLabel = "Сохранить",
            initialName = state.title,
            initialStyle = state.styleDisplayName,
            styles = state.availableStyles,
            tagCatalog = state.tagCatalog,
            initialTag = state.tag,
            onConfirm = { name, style, tag ->
                showRename = false
                onIntent(SequenceEditorIntent.SetTitleAndStyle(name, style, tag))
            },
            onDismiss = { showRename = false },
        )
    }
    if (showVolume) {
        SliderDialog(
            title = "Громкость трека",
            value = state.musicVolume,
            valueRange = 0f..1f,
            valueLabel = { String.format("%.0f%%", it * 100) },
            onDismiss = { showVolume = false },
            onLiveChange = { onIntent(SequenceEditorIntent.SetMusicVolume(it)) },
            onConfirm = {
                onIntent(SequenceEditorIntent.SetMusicVolume(it))
                showVolume = false
            },
        )
    }
    if (showZoom) {
        // 5.4: discrete 10%..100% timeline scale (lower = compressed, higher = longer + scroll).
        SliderDialog(
            title = "Масштаб",
            value = state.timelineZoom,
            valueRange = MIN_TIMELINE_ZOOM..MAX_TIMELINE_ZOOM,
            steps = 8,
            valueLabel = { String.format("%.0f%%", it * 100) },
            onDismiss = { showZoom = false },
            onConfirm = {
                onIntent(SequenceEditorIntent.SetTimelineZoom(it))
                showZoom = false
            },
        )
    }
    if (showSpeed) {
        // C-216: selected element → per-clip speed (timeline width); else song/music speed.
        val editingElement = state.selectedElementIndex in state.elementClips.indices
        val speedValue = if (editingElement) {
            state.elementClips[state.selectedElementIndex].speed.toFloat()
        } else {
            state.songPlaybackSpeed.toFloat()
        }
        SliderDialog(
            title = stringResource(R.string.choreo_track_speed),
            value = speedValue,
            valueRange = DomainValidators.CHOREO_PLAYBACK_SPEED_MIN.toFloat()..
                DomainValidators.CHOREO_PLAYBACK_SPEED_MAX.toFloat(),
            valueLabel = { String.format("%.1f×", it) },
            onDismiss = { showSpeed = false },
            onLiveChange = {
                if (editingElement) {
                    onIntent(SequenceEditorIntent.SetClipSpeed(it.toDouble()))
                } else {
                    onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(it.toDouble()))
                }
            },
            onConfirm = {
                if (editingElement) {
                    onIntent(SequenceEditorIntent.SetClipSpeed(it.toDouble()))
                } else {
                    onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(it.toDouble()))
                }
                showSpeed = false
            },
        )
    }
}

@Composable
private fun ProEditorTopBar(
    title: String,
    savedFlash: Boolean,
    onSave: () -> Unit,
    onRenameRequest: () -> Unit,
    onBack: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ColorTokens.OledTrueBlack.toComposeColor())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xCC292A2E))
                    .interactiveSpringPress(
                        pressedScale = 0.90f,
                        hapticEvent = HapticEvent.Selection,
                        haptics = haptics,
                        onClick = onBack,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
            // Editor title = choreography name; double-tap → rename / restyle dialog (3.1).
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MdFonts.Display,
                letterSpacing = (-0.3).sp,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onRenameRequest() })
                    },
            )
        }
        // 3.4 Save replaces Undo/Redo in the top-right.
        // 4.3.1 square rounded Save icon button (no text).
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AtelierColors.Flame)
                .interactiveSpringPress(
                    pressedScale = 0.94f,
                    hapticEvent = HapticEvent.MediumImpact,
                    haptics = haptics,
                    onClick = onSave,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (savedFlash) Icons.Filled.Check else Icons.Filled.Save,
                contentDescription = "Сохранить",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun CapCutEditToolbar(
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onVolume: () -> Unit,
    onSpeed: () -> Unit,
    onZoom: () -> Unit,
    volumeEnabled: Boolean,
    speedEnabled: Boolean,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(Color(0xFF12131A))
            .border(1.dp, Hairline, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapCutTool(Icons.Filled.ContentCut, "Разделить", onSplit, haptics)
        CapCutTool(Icons.Filled.Delete, "Удалить", onDelete, haptics)
        CapCutTool(Icons.AutoMirrored.Filled.VolumeUp, "Громкость", onVolume, haptics, enabled = volumeEnabled)
        CapCutTool(Icons.Filled.Speed, "Скорость", onSpeed, haptics, enabled = speedEnabled)
        CapCutTool(Icons.Filled.ZoomIn, "Масштаб", onZoom, haptics)
    }
}

@Composable
private fun CapCutTool(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    haptics: HapticFeedback,
    enabled: Boolean = true,
) {
    val contentAlpha = if (enabled) 1f else 0.35f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .interactiveSpringPress(
                pressedScale = 0.92f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = { if (enabled) onClick() },
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White.copy(alpha = contentAlpha),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            color = Color.White.copy(alpha = 0.75f * contentAlpha),
            fontSize = 10.sp,
            fontFamily = MdFonts.Display,
        )
    }
}

/**
 * Two lanes (elements / music) sharing one time axis: total = max(elements, music) (3.7),
 * clip width ∝ own duration (3.8), one Flame playhead through both lanes + scrub thumb (3.10).
 *
 * 5.4 zoom: timeline width = max(viewport, seconds × [PxPerSecAt100] × [timelineZoom]).
 * Low zoom compresses bars to fit the screen; high zoom makes bars longer and the lanes scroll
 * horizontally (+ buttons stay pinned on the left).
 */
/** C-263: live scroll window of the element/music lanes for double-tap seek. */
private class TimelineVisibleWindow {
    @Volatile var scrollPx: Int = 0
    @Volatile var contentWidthPx: Float = 1f
    @Volatile var viewportPx: Float = 1f

    fun fractionAtLocalX(localX: Float, localWidth: Float): Float {
        val cw = contentWidthPx.coerceAtLeast(1f)
        val vw = viewportPx.coerceAtLeast(1f)
        val lw = localWidth.coerceAtLeast(1f)
        val start = scrollPx.toFloat() / cw
        val span = (vw / cw).coerceIn(0.02f, 1f)
        return (start + (localX / lw) * span).coerceIn(0f, 1f)
    }
}

@Composable
private fun DualTimelineTracks(
    elementClips: List<EditorClip>,
    musicClips: List<EditorClip>,
    totalMs: Long,
    playheadFraction: Float,
    timelineZoom: Float,
    isPlaying: Boolean,
    selectedElementIndex: Int,
    selectedMusicIndex: Int,
    onAddElement: () -> Unit,
    onAddMusic: () -> Unit,
    onSelectElement: (Int) -> Unit,
    onSelectMusic: (Int) -> Unit,
    onReorderElement: (Int, Int) -> Unit,
    onReorderMusic: (Int, Int) -> Unit,
    onSeek: (Float) -> Unit,
    onScrubbingChanged: (Boolean) -> Unit = {},
    visibleWindow: TimelineVisibleWindow? = null,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val lanesHeight = LaneHeight * 2 + LaneGap
    val leftInset = LanePlusWidth + LanePlusGap
    val scrollState = rememberScrollState()
    // C-163: after the user pans the timeline, do not auto-snap back to the playhead
    // until playback stops (keeps Add Music / lane browsing usable while playing).
    var userBrowsingTimeline by remember { mutableStateOf(false) }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) userBrowsingTimeline = true
    }
    LaunchedEffect(isPlaying) {
        if (!isPlaying) userBrowsingTimeline = false
    }

    // C-197: leftover horizontal drag must not flip ChildPages (especially at 10% zoom).
    val blockPagerScroll = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = Offset(available.x, 0f)
        }
    }

    BoxWithConstraints(modifier = modifier.nestedScroll(blockPagerScroll)) {
        // Room at both ends so the scrub thumb is never clipped by the scroll viewport.
        val viewportWidth = (maxWidth - leftInset).coerceAtLeast(1.dp)
        val fitWidth = (viewportWidth - PlayheadThumbTouch).coerceAtLeast(1.dp)
        // C-197: even at 10% zoom keep content wider than viewport so lanes scroll
        // (not the parent pager / whole screen).
        val zoomedWidth = PxPerSecAt100 * (totalMs / 1000f * timelineZoom.coerceAtLeast(0.1f))
        val minScrollWidth = fitWidth * 1.5f
        val contentWidth = maxOf(zoomedWidth, minScrollWidth, fitWidth)
        val contentWidthPx = with(density) { contentWidth.toPx() }
        val viewportPx = with(density) { viewportWidth.toPx() }
        val thumbPadPx = with(density) { (PlayheadThumbTouch / 2).toPx() }
        val fraction = playheadFraction.coerceIn(0f, 1f)
        val playheadX = contentWidth * fraction

        // C-252/C-263: publish visible window; do NOT auto-pan lanes after Seek/double-tap.
        androidx.compose.runtime.SideEffect {
            visibleWindow?.let { win ->
                win.scrollPx = scrollState.value
                win.contentWidthPx = contentWidthPx
                win.viewportPx = viewportPx
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(LanePlusGap)) {
            // Pinned + buttons (do not scroll with the lanes) — always enabled while playing.
            Column(verticalArrangement = Arrangement.spacedBy(LaneGap)) {
                TimelinePlusButton(plusBlack = true, onClick = onAddElement, haptics = haptics)
                TimelinePlusButton(
                    plusBlack = false,
                    onClick = {
                        userBrowsingTimeline = true
                        onAddMusic()
                    },
                    haptics = haptics,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    // C-263: double-tap → seek inside current scroll viewport only (no pan, no off-screen jump).
                    .pointerInput(contentWidthPx, viewportPx, scrollState.value) {
                        detectTapGestures(
                            onDoubleTap = { offset ->
                                val frac = (visibleWindow?.fractionAtLocalX(offset.x, size.width.toFloat())
                                    ?: ((scrollState.value + offset.x) / contentWidthPx))
                                    .coerceIn(0f, 1f)
                                userBrowsingTimeline = true
                                onSeek(frac)
                            },
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = PlayheadThumbTouch / 2)
                        .width(contentWidth),
                ) {
                    Column {
                        Column(verticalArrangement = Arrangement.spacedBy(LaneGap)) {
                            // Elements lane: black + white left, black blocks / white text
                            TimelineLane(
                                clips = elementClips,
                                totalMs = totalMs,
                                contentWidth = contentWidth,
                                selectedIndex = selectedElementIndex,
                                blockBg = Color.Black,
                                blockFg = Color.White,
                                trackBg = Color(0xFF0A0A0C),
                                // C-304: sticky left + ellipsis (no marquee — was sliding to right edge).
                                useMarquee = false,
                                scrollPx = scrollState.value.toFloat(),
                                viewportPx = viewportPx,
                                onSelect = onSelectElement,
                                onReorder = onReorderElement,
                                haptics = haptics,
                            )
                            // Music lane: orange + black icon
                            TimelineLane(
                                clips = musicClips,
                                totalMs = totalMs,
                                contentWidth = contentWidth,
                                selectedIndex = selectedMusicIndex,
                                blockBg = AtelierColors.Flame,
                                blockFg = Color.Black,
                                trackBg = AtelierColors.Flame.copy(alpha = 0.35f),
                                useMarquee = false,
                                scrollPx = scrollState.value.toFloat(),
                                viewportPx = viewportPx,
                                onSelect = onSelectMusic,
                                onReorder = onReorderMusic,
                                haptics = haptics,
                            )
                        }
                        // Room for the scrub thumb under the lanes.
                        Spacer(Modifier.height(PlayheadThumbTouch - 6.dp))
                    }

                    // Vertical Flame playhead across BOTH lanes.
                    Box(
                        modifier = Modifier
                            .offset(x = playheadX - 1.dp)
                            .width(2.dp)
                            .height(lanesHeight + 4.dp)
                            .background(AtelierColors.Flame),
                    )
                    // Bottom thumb circle (scrub).
                    val currentFraction by rememberUpdatedState(fraction)
                    Box(
                        modifier = Modifier
                            .offset(x = playheadX - PlayheadThumbTouch / 2, y = lanesHeight - 2.dp)
                            .size(PlayheadThumbTouch)
                            .pointerInput(contentWidthPx) {
                                var base = 0f
                                var acc = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = {
                                        base = currentFraction
                                        acc = 0f
                                        onScrubbingChanged(true)
                                    },
                                    onDragEnd = { onScrubbingChanged(false) },
                                    onDragCancel = { onScrubbingChanged(false) },
                                    onHorizontalDrag = { change, dx ->
                                        change.consume()
                                        acc += dx
                                        onSeek((base + acc / contentWidthPx).coerceIn(0f, 1f))
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(PlayheadThumb)
                                .clip(CircleShape)
                                .background(AtelierColors.Flame)
                                .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelinePlusButton(
    plusBlack: Boolean,
    onClick: () -> Unit,
    haptics: HapticFeedback,
) {
    // 7.3 left + buttons
    Box(
        modifier = Modifier
            .size(LanePlusWidth)
            .clip(RoundedCornerShape(8.dp))
            .background(if (plusBlack) Color.Black else AtelierColors.Flame)
            .border(
                1.dp,
                if (plusBlack) Color.White.copy(alpha = 0.25f) else Color.Transparent,
                RoundedCornerShape(8.dp),
            )
            .interactiveSpringPress(
                pressedScale = 0.92f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Добавить",
            tint = if (plusBlack) Color.White else Color.Black,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun TimelineLane(
    clips: List<EditorClip>,
    totalMs: Long,
    contentWidth: Dp,
    selectedIndex: Int,
    blockBg: Color,
    blockFg: Color,
    trackBg: Color,
    useMarquee: Boolean,
    /** C-283: horizontal scroll of parent — sticky labels stay in visible clip portion. */
    scrollPx: Float = 0f,
    viewportPx: Float = Float.MAX_VALUE,
    onSelect: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    haptics: HapticFeedback,
) {
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .width(contentWidth)
            .height(LaneHeight)
            .clip(RoundedCornerShape(8.dp))
            .background(trackBg)
            .border(1.dp, Hairline, RoundedCornerShape(8.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Precompute widths so drag-swap uses absolute positions (C-231).
        val widths = remember(clips, totalMs, contentWidth) {
            clips.map { clip ->
                val proportional =
                    if (totalMs > 0L) contentWidth * (clip.durationMs.toFloat() / totalMs.toFloat())
                    else MinClipWidth
                proportional.coerceAtLeast(MinClipWidth)
            }
        }
        val startsPx = remember(widths, density) {
            val out = ArrayList<Float>(widths.size)
            var acc = 0f
            widths.forEach { w ->
                out += acc
                acc += with(density) { w.toPx() }
            }
            out
        }
        clips.forEachIndexed { index, clip ->
            var dragOffset by remember(index, clip.label, clip.durationMs) { mutableFloatStateOf(0f) }
            val blockWidth = widths.getOrElse(index) { MinClipWidth }
            val blockWidthPx = with(density) { blockWidth.toPx() }
            val clipStartPx = startsPx.getOrElse(index) { 0f }
            // C-304: sticky label at left of visible clip∩viewport — keep ≥72px for text.
            val stickyPadPx = com.midnightdancer.feature.choreography.logic.timelineStickyPadPx(
                clipStartPx = clipStartPx,
                blockWidthPx = blockWidthPx,
                scrollPx = scrollPx,
                viewportPx = viewportPx,
            )
            val stickyPad = with(density) { stickyPadPx.toDp() }
            val centersPx = remember(widths) {
                val out = ArrayList<Float>(widths.size)
                var acc = 0f
                widths.forEach { w ->
                    val px = with(density) { w.toPx() }
                    out += acc + px / 2f
                    acc += px
                }
                out
            }
            Box(
                modifier = Modifier
                    .width(blockWidth)
                    .height(LaneHeight - 8.dp)
                    .zIndex(if (dragOffset != 0f) 2f else 0f)
                    .graphicsLayer {
                        translationX = dragOffset
                        shadowElevation = if (dragOffset != 0f) 8f else 0f
                    }
                    .clip(RoundedCornerShape(6.dp))
                    .background(blockBg)
                    .border(
                        width = if (index == selectedIndex) 2.dp else 1.dp,
                        color = if (index == selectedIndex) {
                            AtelierColors.Gold
                        } else {
                            Color.White.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(6.dp),
                    )
                    // C-231: long-press drag reorder without spring-press stealing the gesture.
                    .pointerInput(index, clips.size, centersPx) {
                        detectDragGesturesAfterLongPress(
                            onDragEnd = {
                                val originCenter = centersPx.getOrElse(index) { 0f }
                                val fingerCenter = originCenter + dragOffset
                                val to = centersPx.indices.minByOrNull { i ->
                                    kotlin.math.abs(centersPx[i] - fingerCenter)
                                } ?: index
                                if (to != index) onReorder(index, to)
                                dragOffset = 0f
                            },
                            onDragCancel = { dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.x
                            },
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            haptics.perform(HapticEvent.Selection)
                            onSelect(index)
                        },
                    )
                    .padding(start = 4.dp + stickyPad, end = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (useMarquee) {
                    MarqueeText(
                        text = clip.label,
                        enabled = true,
                        color = blockFg,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = clip.label,
                        color = blockFg,
                        fontSize = 10.sp,
                        fontFamily = MdFonts.Display,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewStage(
    isPlaying: Boolean,
    playPulse: Float,
    isEmpty: Boolean,
    currentElement: String?,
    currentElementUri: String? = null,
    currentElementOffsetMs: Long = 0L,
    currentElementSourceStartMs: Long = 0L,
    currentElementClipDurationMs: Long = 1L,
    currentElementSpeed: Double = 1.0,
    /** C-231: past last element — drop held frame so preview stays empty. */
    clearHeldFrame: Boolean = false,
    scrubbing: Boolean = false,
    /** C-290: remaining / elapsed in current timeline clip for prepare-ahead. */
    msToClipEnd: Long = Long.MAX_VALUE,
    msFromClipStart: Long = 0L,
    nextClipUri: String? = null,
    nextClipSourceStartMs: Long = 0L,
    nextClipDurationMs: Long = 1L,
    prevClipUri: String? = null,
    prevClipSourceStartMs: Long = 0L,
    prevClipDurationMs: Long = 1L,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pulseAnim = rememberInfiniteTransition(label = "preview-pulse")
    val glow by pulseAnim.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    // C-270: dual players — outgoing keeps last frame while incoming prepares (soft splice).
    fun newSilentPlayer(): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext).setMediaSourceFactory(com.midnightdancer.app.audio.ownedMediaSourceFactory(context)).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        }
    val playerA = remember { newSilentPlayer() }
    val playerB = remember { newSilentPlayer() }
    DisposableEffect(playerA, playerB) {
        onDispose {
            playerA.release()
            playerB.release()
        }
    }
    var frontIsA by remember { mutableStateOf(true) }
    var boundVideoKey by remember { mutableStateOf<String?>(null) }
    /** C-277: true while preparing incoming — seek must not corrupt outgoing (old) frame. */
    var videoHandoffActive by remember { mutableStateOf(false) }
    /** C-290: still frame over players until incoming first frame. */
    var holdBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    /** C-291: scrub strip frame while dragging. */
    var scrubBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var preparedAheadKey by remember { mutableStateOf<String?>(null) }
    var lastVideoUri by remember { mutableStateOf<String?>(null) }
    var lastSourceStart by remember { mutableLongStateOf(0L) }
    var lastClipDur by remember { mutableLongStateOf(1L) }
    androidx.compose.runtime.SideEffect {
        if (clearHeldFrame) {
            lastVideoUri = null
        } else if (!currentElementUri.isNullOrBlank()) {
            lastVideoUri = currentElementUri
            lastSourceStart = currentElementSourceStartMs
            lastClipDur = currentElementClipDurationMs
        }
    }
    val displayUri = if (clearHeldFrame) {
        null
    } else {
        currentElementUri?.takeIf { it.isNotBlank() } ?: lastVideoUri
    }
    val displayStart = if (clearHeldFrame) 0L else {
        if (!currentElementUri.isNullOrBlank()) currentElementSourceStartMs else lastSourceStart
    }
    val displayDur = if (clearHeldFrame) 1L else {
        if (!currentElementUri.isNullOrBlank()) currentElementClipDurationMs else lastClipDur
    }
    val videoBindKey = mediaBindKey(displayUri, displayStart, displayDur)
    val latestOffsetMs by rememberUpdatedState(currentElementOffsetMs)
    val latestSpeed by rememberUpdatedState(currentElementSpeed)
    val latestPlaying by rememberUpdatedState(isPlaying)
    val latestScrubbing by rememberUpdatedState(scrubbing)
    val latestBindKey by rememberUpdatedState(videoBindKey)
    val frontPlayer = if (frontIsA) playerA else playerB
    val backPlayer = if (frontIsA) playerB else playerA

    // C-291: warm strip + show nearest bitmap while scrubbing (no Exo seek storm).
    LaunchedEffect(displayUri, displayStart, displayDur) {
        val uri = displayUri
        if (uri.isNullOrBlank()) return@LaunchedEffect
        EditorScrubFrameCache.warmStrip(context, uri, displayStart, displayDur)
    }
    LaunchedEffect(scrubbing, displayUri, displayStart, currentElementOffsetMs) {
        val uri = displayUri
        if (!scrubbing || uri.isNullOrBlank()) {
            scrubBitmap = null
            return@LaunchedEffect
        }
        val bmp = EditorScrubFrameCache.getOrFetch(
            context,
            uri,
            displayStart,
            currentElementOffsetMs,
        ) ?: ElementThumbCache.get(uri) ?: ElementThumbCache.loadOrFetch(context, uri)
        scrubBitmap = bmp?.asImageBitmap()
    }

    // C-290: prepare-ahead next/prev on back player near boundaries.
    LaunchedEffect(
        msToClipEnd,
        msFromClipStart,
        scrubbing,
        nextClipUri,
        prevClipUri,
        videoBindKey,
        frontIsA,
        videoHandoffActive,
    ) {
        if (videoHandoffActive || displayUri.isNullOrBlank()) return@LaunchedEffect
        val back = if (frontIsA) playerB else playerA
        val wantNext = !nextClipUri.isNullOrBlank() &&
            (msToClipEnd <= PREPARE_AHEAD_MS || (scrubbing && msToClipEnd <= PREPARE_AHEAD_MS * 2))
        val wantPrev = !prevClipUri.isNullOrBlank() &&
            scrubbing &&
            msFromClipStart <= PREPARE_AHEAD_MS
        when {
            wantNext -> {
                val key = mediaBindKey(nextClipUri, nextClipSourceStartMs, nextClipDurationMs)
                if (preparedAheadKey == key) return@LaunchedEffect
                mdAvLog("video prepare_ahead next key=$key rem=$msToClipEnd")
                back.playWhenReady = false
                back.setMediaItem(
                    clippedMediaItem(nextClipUri!!, nextClipSourceStartMs, nextClipDurationMs),
                )
                back.prepare()
                back.seekTo(0L)
                preparedAheadKey = key
            }
            wantPrev -> {
                val key = mediaBindKey(prevClipUri, prevClipSourceStartMs, prevClipDurationMs)
                if (preparedAheadKey == key) return@LaunchedEffect
                mdAvLog("video prepare_ahead prev key=$key fromStart=$msFromClipStart")
                val nearEnd = (prevClipDurationMs - 40L).coerceAtLeast(0L)
                back.playWhenReady = false
                back.setMediaItem(
                    clippedMediaItem(prevClipUri!!, prevClipSourceStartMs, prevClipDurationMs),
                )
                back.prepare()
                back.seekTo(nearEnd)
                preparedAheadKey = key
            }
        }
    }

    // C-274/C-277/C-279/C-290: prepare on back; bitmap hold; coalesce latest key.
    LaunchedEffect(videoBindKey) {
        val requestedKey = videoBindKey
        val handoffStart = System.currentTimeMillis()
        val uri = displayUri
        if (uri.isNullOrBlank()) {
            videoHandoffActive = false
            holdBitmap = null
            playerA.pause()
            playerB.pause()
            playerA.clearMediaItems()
            playerB.clearMediaItems()
            boundVideoKey = null
            preparedAheadKey = null
            return@LaunchedEffect
        }
        if (requestedKey == boundVideoKey) return@LaunchedEffect
        val switching = boundVideoKey != null
        val outgoing = if (frontIsA) playerA else playerB
        val incoming = if (switching) {
            if (frontIsA) playerB else playerA
        } else {
            outgoing
        }
        mdAvLog(
            "video prepare key=$requestedKey offset=${latestOffsetMs} dual=$switching " +
                "scrub=$latestScrubbing ahead=${preparedAheadKey == requestedKey}",
        )
        val speed = latestSpeed.toFloat().coerceIn(0.5f, 2f)
        if (switching) {
            videoHandoffActive = true
            // C-290 bitmap hold from scrub cache / cover so stage never goes black.
            val hold = EditorScrubFrameCache.get(uri, displayStart, latestOffsetMs)
                ?: ElementThumbCache.get(uri)
                ?: ElementThumbCache.loadOrFetch(context, uri)
            holdBitmap = hold?.asImageBitmap()
        }
        var firstFrame = false
        val frameListener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame = true
            }
        }
        try {
            if (switching) incoming.addListener(frameListener)
            incoming.setSeekParameters(
                if (latestScrubbing) {
                    androidx.media3.exoplayer.SeekParameters.PREVIOUS_SYNC
                } else {
                    androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC
                },
            )
            val alreadyAhead = preparedAheadKey == requestedKey && switching
            if (!alreadyAhead) {
                incoming.playWhenReady = false
                incoming.setMediaItem(clippedMediaItem(uri, displayStart, displayDur))
                incoming.prepare()
            }
            incoming.setPlaybackSpeed(speed)
            incoming.seekTo(latestOffsetMs.coerceAtLeast(0L))
            if (switching) {
                incoming.playWhenReady = true
                incoming.play()
                val hardDeadline = System.currentTimeMillis() + 12_000L
                while (
                    !(
                        (
                            incoming.playbackState == Player.STATE_READY ||
                                incoming.playbackState == Player.STATE_ENDED
                            ) && firstFrame
                        )
                ) {
                    // C-290 coalesce: newer bindKey cancelled us or superseded — abort swap.
                    if (latestBindKey != requestedKey) {
                        mdAvLog("video coalesce_drop requested=$requestedKey latest=$latestBindKey")
                        return@LaunchedEffect
                    }
                    if (System.currentTimeMillis() >= hardDeadline) {
                        mdAvLog("video handoff deadline firstFrame=$firstFrame state=${incoming.playbackState}")
                        if (latestBindKey != requestedKey) return@LaunchedEffect
                        val ready =
                            incoming.playbackState == Player.STATE_READY ||
                                incoming.playbackState == Player.STATE_ENDED
                        if (ready || firstFrame) {
                            frontIsA = !frontIsA
                            outgoing.pause()
                            if (latestPlaying && !latestScrubbing) {
                                incoming.playWhenReady = true
                                incoming.play()
                            } else {
                                incoming.playWhenReady = false
                                incoming.pause()
                            }
                            boundVideoKey = requestedKey
                            holdBitmap = null
                        } else {
                            outgoing.setMediaItem(clippedMediaItem(uri, displayStart, displayDur))
                            outgoing.prepare()
                            outgoing.seekTo(latestOffsetMs.coerceAtLeast(0L))
                            outgoing.playWhenReady = latestPlaying && !latestScrubbing
                            if (latestPlaying && !latestScrubbing) outgoing.play() else outgoing.pause()
                            incoming.pause()
                            boundVideoKey = requestedKey
                            holdBitmap = null
                        }
                        return@LaunchedEffect
                    }
                    if (!latestScrubbing) {
                        val want = latestOffsetMs.coerceAtLeast(0L)
                        if (kotlin.math.abs(incoming.currentPosition - want) > SEEK_WHILE_SCRUB_MS) {
                            incoming.seekTo(want)
                        }
                    }
                    delay(16)
                }
                if (latestBindKey != requestedKey) {
                    mdAvLog("video coalesce_drop after ready requested=$requestedKey latest=$latestBindKey")
                    return@LaunchedEffect
                }
                if (!latestScrubbing) {
                    incoming.seekTo(latestOffsetMs.coerceAtLeast(0L))
                    delay(32)
                }
                if (latestPlaying && !latestScrubbing) {
                    incoming.playWhenReady = true
                    incoming.play()
                } else {
                    incoming.playWhenReady = false
                    incoming.pause()
                }
                frontIsA = !frontIsA
                outgoing.pause()
                holdBitmap = null
                preparedAheadKey = null
                mdAvLog(
                    "video handoff swap ok firstFrame=$firstFrame " +
                        "handoff_ms=${System.currentTimeMillis() - handoffStart}",
                )
            } else {
                incoming.playWhenReady = latestPlaying && !latestScrubbing
                if (latestPlaying && !latestScrubbing) incoming.play() else incoming.pause()
            }
            boundVideoKey = requestedKey
        } finally {
            if (switching) {
                runCatching { incoming.removeListener(frameListener) }
            }
            // Always clear; a newer LaunchedEffect(videoBindKey) re-asserts if still handing off.
            videoHandoffActive = false
        }
    }
    LaunchedEffect(currentElementSpeed, frontIsA) {
        frontPlayer.setPlaybackSpeed(currentElementSpeed.toFloat().coerceIn(0.5f, 2f))
    }
    LaunchedEffect(isPlaying, frontIsA, videoHandoffActive, scrubbing) {
        if (videoHandoffActive || scrubbing) return@LaunchedEffect
        if (displayUri.isNullOrBlank()) {
            frontPlayer.pause()
            return@LaunchedEffect
        }
        if (isPlaying) {
            frontPlayer.setPlaybackSpeed(latestSpeed.toFloat().coerceIn(0.5f, 2f))
            frontPlayer.seekTo(latestOffsetMs.coerceAtLeast(0L))
            frontPlayer.playWhenReady = true
            frontPlayer.play()
        } else {
            frontPlayer.playWhenReady = false
            frontPlayer.pause()
        }
    }
    // C-277/C-291: during scrub skip Exo seeks (bitmap path); handoff seek only incoming.
    LaunchedEffect(
        currentElementOffsetMs,
        videoBindKey,
        isPlaying,
        scrubbing,
        frontIsA,
        videoHandoffActive,
    ) {
        if (displayUri.isNullOrBlank()) {
            if (!videoHandoffActive) frontPlayer.pause()
            return@LaunchedEffect
        }
        // C-291: scrubbing uses strip bitmaps — do not thrash Exo.
        if (scrubbing && !videoHandoffActive) return@LaunchedEffect
        val target = currentElementOffsetMs.coerceAtLeast(0L)
        val threshold = when {
            scrubbing || videoHandoffActive -> SEEK_WHILE_SCRUB_MS
            latestPlaying -> SEEK_WHILE_PLAY_MS
            else -> SEEK_EPSILON_MS
        }
        if (videoHandoffActive || videoBindKey != boundVideoKey) {
            if (kotlin.math.abs(backPlayer.currentPosition - target) > threshold) {
                backPlayer.seekTo(target)
            }
            return@LaunchedEffect
        }
        if (kotlin.math.abs(frontPlayer.currentPosition - target) > threshold) {
            frontPlayer.seekTo(target)
            if (latestPlaying && !scrubbing && !frontPlayer.isPlaying) frontPlayer.play()
        }
    }
    LaunchedEffect(scrubbing, frontIsA, videoHandoffActive) {
        if (videoHandoffActive) return@LaunchedEffect
        if (displayUri.isNullOrBlank()) return@LaunchedEffect
        if (scrubbing) {
            frontPlayer.pause()
            return@LaunchedEffect
        }
        mdAvLog("video scrub-end seek=${latestOffsetMs} state=${frontPlayer.playbackState}")
        holdBitmap = null
        scrubBitmap = null
        frontPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
        frontPlayer.seekTo(latestOffsetMs.coerceAtLeast(0L))
        frontPlayer.setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
        if (latestPlaying) frontPlayer.play()
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // 3.9 side letterbox bars 2× thinner: widen frame so (parent − frame)/2 halves.
        val legacyWidth = minOf(maxWidth * 0.72f, maxHeight * (9f / 16f))
        val frameWidth = ((maxWidth + legacyWidth) / 2).coerceAtMost(maxWidth)
        val frameHeight = minOf(maxHeight, legacyWidth * (16f / 9f))
        val shape = RoundedCornerShape(14.dp)

        Box(
            modifier = Modifier
                .width(frameWidth)
                .height(frameHeight)
                .clip(shape)
                .background(SurfaceLowest)
                .then(
                    if (isEmpty) {
                        Modifier
                    } else {
                        Modifier.border(
                            width = 1.dp,
                            color = AtelierColors.Flame.copy(alpha = 0.15f + playPulse * glow * 0.45f),
                            shape = shape,
                        )
                    },
                ),
        ) {
            if (isEmpty) {
                // 3.9 empty state: no orange circle / frame chrome.
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Начнем творить!",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MdFonts.Display,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Загрузи твой первый элемент",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 13.sp,
                        fontFamily = MdFonts.Display,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // C-274: dual PlayerViews bound to A/B — zIndex swap only (no alpha squash).
                if (!displayUri.isNullOrBlank()) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                useController = false
                                player = playerA
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { it.player = playerA },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (frontIsA) 1f else 0f),
                    )
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            androidx.media3.ui.PlayerView(ctx).apply {
                                useController = false
                                player = playerB
                                setKeepContentOnPlayerReset(true)
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { it.player = playerB },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (frontIsA) 0f else 1f),
                    )
                    // C-290: hold last still until incoming first frame.
                    holdBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .zIndex(2f),
                        )
                    }
                    // C-291: scrub strip on top while dragging.
                    if (scrubbing) {
                        scrubBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(3f),
                            )
                        }
                    }
                }
                if (!currentElement.isNullOrBlank()) {
                    // C-261: solid name plate (no translucent wash / orange pulse circle).
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .background(Color(0xFF0A0A0C), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = currentElement,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = MdFonts.Display,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                        if (currentElementUri.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "видео · без звука",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 11.sp,
                                fontFamily = MdFonts.Display,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Timecode left; Undo · Skip back · Play · Skip forward · Redo right (3.4 / 3.6). */
@Composable
private fun TransportConsole(
    timecode: String,
    durationTimecode: String,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onIntent: (SequenceEditorIntent) -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 4.3.2 `current / total`, both MM.SS.cs.
        Text(
            text = "$timecode / $durationTimecode",
            color = AtelierColors.Soft,
            fontSize = 14.sp,
            fontFamily = MdFonts.Display,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TransportIconButton(
                icon = Icons.AutoMirrored.Filled.Undo,
                label = "Undo",
                haptics = haptics,
                enabled = canUndo,
                accent = true,
            ) { onIntent(SequenceEditorIntent.Undo) }
            TransportIconButton(Icons.Filled.SkipPrevious, "SeekStart", haptics) {
                onIntent(SequenceEditorIntent.SeekStart)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(AtelierColors.Flame, Color(0xFFFF553C))))
                    .interactiveSpringPress(
                        pressedScale = 0.92f,
                        hapticEvent = HapticEvent.MediumImpact,
                        haptics = haptics,
                        onClick = { onIntent(SequenceEditorIntent.TogglePlay) },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            TransportIconButton(Icons.Filled.SkipNext, "SeekEnd", haptics) {
                onIntent(SequenceEditorIntent.SeekEnd)
            }
            TransportIconButton(
                icon = Icons.AutoMirrored.Filled.Redo,
                label = "Redo",
                haptics = haptics,
                enabled = canRedo,
                accent = true,
            ) { onIntent(SequenceEditorIntent.Redo) }
        }
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    label: String,
    haptics: HapticFeedback,
    enabled: Boolean = true,
    accent: Boolean = false,
    onClick: () -> Unit,
) {
    val tint = when {
        !enabled -> Color.White.copy(alpha = 0.28f)
        accent -> AtelierColors.Flame
        else -> Color.White.copy(alpha = 0.85f)
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                if (accent && enabled) AtelierColors.Flame.copy(alpha = 0.18f) else Color(0xFF1F1F24),
            )
            .interactiveSpringPress(
                pressedScale = 0.90f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = { if (enabled) onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun SliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
    onLiveChange: ((Float) -> Unit)? = null,
    steps: Int = 0,
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12131A),
        title = { Text(title, color = Color.White, fontFamily = MdFonts.Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(valueLabel(draft), color = Color.White, fontFamily = MdFonts.Display)
                Slider(
                    value = draft,
                    onValueChange = {
                        draft = it
                        onLiveChange?.invoke(it)
                    },
                    valueRange = valueRange,
                    steps = steps,
                    colors = SliderDefaults.colors(
                        thumbColor = AtelierColors.Flame,
                        activeTrackColor = AtelierColors.Flame,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text("OK", color = AtelierColors.Flame)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.70f))
            }
        },
    )
}

