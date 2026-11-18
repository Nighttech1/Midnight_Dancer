package com.midnightdancer.app.ui.trainer

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.components.MusicPulseVisualizer
import com.midnightdancer.app.ui.components.VisualizerPlaybackState
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.FlameGoldWhiteShimmer
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.trainer.logic.CueAt
import com.midnightdancer.feature.trainer.logic.TrainerSessionIntent
import com.midnightdancer.feature.trainer.logic.TrainerSessionUiState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val Hairline = Color.White.copy(alpha = 0.12f)
/** Gap above CyberAcousticDock — match Storyline Dance CTA (C-100). */
private val FinishCtaClearance = 12.dp
private val FinishReserve = 92.dp
private val CueItemHeight = 48.dp
private val CueItemGap = 6.dp
/** Fits ~5 cue cards under the visualizer. */
private val CueViewportHeight = 320.dp
/**
 * C-202: active cue sits on the **middle** list row (sketch: triangles point at center).
 * Slot 0 = above mid, 1 = mid (active gate), 2+ = below.
 */
private const val ActiveCueSlot = 1f
private const val CueStepSec = 8f
private val VolumeThumbOuter = 14.dp
private val VolumeThumbInner = 8.dp
private val NeonTriangleSize = 18.dp
/** C-226: inset so scale×1.5 triangles do not clip at screen edges. */
private val NeonTriangleEdgeInset = 14.dp

/**
 * Live session (C-98 / C-164 / C-192) — Understrukk clock, side vols, beat-wave, cue pipeline.
 *
 * ANDROID COMPOSE / MOTION — C-164: volume end-thumbs, cue scroll↑, tap Finish,
 * scrub-safe playhead, play button closer to ring center.
 * C-192: real Media3 track + TTS cues + soft fade carousel edges.
 */
@Composable
fun LiveFlowScreen(
    state: TrainerSessionUiState,
    onIntent: (TrainerSessionIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    var userScrubbing by remember { mutableStateOf(false) }
    /** C-278: last user seek target — block ticker until Exo catches up (no ring snap-back). */
    var scrubLatchFrac by remember { mutableFloatStateOf(-1f) }
    var scrubSettleJob by remember { mutableStateOf<Job?>(null) }
    val scrubScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val trackTotalSec = remember(state.trackDurationMs, state.cueSchedule) {
        com.midnightdancer.feature.trainer.logic.TrainerReady.sessionTotalSec(state)
            .coerceAtLeast(1f)
    }

    // C-178: Live training takes the audio — stop library Music deck.
    LaunchedEffect(Unit) {
        com.midnightdancer.core.domain.audio.LibraryAudioStopBus.requestStop()
        com.midnightdancer.app.audio.LibraryAudioController.forceStop()
    }

    // C-256: process-scoped player — do NOT release on tab leave / Compose dispose.
    val musicPlayer = remember(context) {
        com.midnightdancer.app.audio.TrainerLiveAudioController.obtain(context)
    }
    val playlistUris = remember(state.musicPlaylist, state.trackMediaUri) {
        state.musicPlaylist.map { it.uri }.filter { it.isNotBlank() }
            .ifEmpty { listOfNotNull(state.trackMediaUri?.takeIf { it.isNotBlank() }) }
    }
    val playlistDurations = remember(state.musicPlaylist, state.trackDurationMs) {
        if (state.musicPlaylist.isNotEmpty()) {
            state.musicPlaylist.map { it.durationMs.coerceAtLeast(1_000L) }
        } else {
            listOf(state.trackDurationMs.coerceAtLeast(1_000L))
        }
    }
    // C-276: bind full choreography music playlist (or single freestyle URI).
    LaunchedEffect(playlistUris) {
        val exo = if (playlistUris.size > 1) {
            com.midnightdancer.app.audio.TrainerLiveAudioController.ensurePlaylist(context, playlistUris)
        } else {
            com.midnightdancer.app.audio.TrainerLiveAudioController.ensureUri(
                context,
                playlistUris.firstOrNull(),
            )
        }
        exo.volume = state.trackVol.coerceIn(0f, 1f)
        if (playlistUris.isNotEmpty() && !state.isPaused) exo.play()
    }
    LaunchedEffect(state.isPaused, state.trackVol) {
        musicPlayer.volume = state.trackVol.coerceIn(0f, 1f)
        if (state.isPaused) musicPlayer.pause() else if (musicPlayer.mediaItemCount > 0) {
            musicPlayer.play()
        }
    }
    // C-226: if the platform still stalls the player during TTS, nudge play — keep BPM motion alive.
    LaunchedEffect(state.isPaused) {
        if (state.isPaused) return@LaunchedEffect
        while (isActive) {
            delay(250)
            if (!state.isPaused &&
                musicPlayer.mediaItemCount > 0 &&
                !musicPlayer.isPlaying &&
                musicPlayer.playbackState == androidx.media3.common.Player.STATE_READY
            ) {
                musicPlayer.play()
            }
        }
    }

    // C-199: playhead follows real ExoPlayer position (ring scrub seeks the player).
    // C-276: absolute position across playlist windows.
    // C-226: if position stalls while session runs, advance by wall clock so cues/wave keep moving.
    LaunchedEffect(state.isPaused, userScrubbing, trackTotalSec, playlistUris) {
        if (state.isPaused || userScrubbing) return@LaunchedEffect
        var lastPos = -1L
        while (isActive) {
            delay(50)
            val totalMs = (trackTotalSec * 1000f).toLong().coerceAtLeast(1L)
            val mediaDur = com.midnightdancer.app.audio.TrainerLiveAudioController
                .absoluteDurationMs(musicPlayer, totalMs)
            val frac = if (musicPlayer.mediaItemCount > 0 && mediaDur > 0L) {
                val pos = com.midnightdancer.app.audio.TrainerLiveAudioController
                    .absolutePositionMs(musicPlayer)
                if (!musicPlayer.isPlaying && pos == lastPos) {
                    (state.playheadFraction + 50f / totalMs).coerceAtMost(1f)
                } else {
                    lastPos = pos
                    (pos.toFloat() / mediaDur.toFloat()).coerceIn(0f, 1f)
                }
            } else {
                (state.playheadFraction + 50f / totalMs).coerceAtMost(1f)
            }
            onIntent(TrainerSessionIntent.Seek(frac, fromUserScrub = false))
            // C-243: track end → Stop becomes Play (paused) automatically.
            if (frac >= 1f) {
                if (!state.isPaused) {
                    onIntent(TrainerSessionIntent.TogglePause)
                }
                break
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oled),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = FinishReserve),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MdPageTitle(text = state.title)
            PhaseHubWithSideVols(
                playheadFraction = state.playheadFraction,
                isPaused = state.isPaused,
                trackVol = state.trackVol,
                voiceVol = state.voiceVol,
                trackTotalSec = trackTotalSec,
                onTogglePause = { onIntent(TrainerSessionIntent.TogglePause) },
                onSeek = { frac ->
                    val f = frac.coerceIn(0f, 1f)
                    scrubLatchFrac = f
                    // C-199/C-276/C-278: ring scrub across playlist absolute timeline.
                    val mediaDur = com.midnightdancer.app.audio.TrainerLiveAudioController
                        .absoluteDurationMs(musicPlayer, state.trackDurationMs.coerceAtLeast(1L))
                    val targetMs = (f * mediaDur).toLong().coerceIn(0L, mediaDur - 1L)
                    com.midnightdancer.app.audio.TrainerLiveAudioController.seekAbsolute(
                        musicPlayer,
                        targetMs,
                        playlistDurations,
                    )
                    onIntent(TrainerSessionIntent.Seek(f, fromUserScrub = true))
                },
                onScrubStart = {
                    scrubSettleJob?.cancel()
                    userScrubbing = true
                },
                onScrubEnd = {
                    // C-278: keep ticker paused until Exo position ≈ latch (reverse scrub snap fix).
                    scrubSettleJob?.cancel()
                    scrubSettleJob = scrubScope.launch {
                        val target = scrubLatchFrac
                        if (target < 0f) {
                            userScrubbing = false
                            return@launch
                        }
                        var attempt = 0
                        while (isActive && attempt < 24) {
                            val mediaDur = com.midnightdancer.app.audio.TrainerLiveAudioController
                                .absoluteDurationMs(
                                    musicPlayer,
                                    state.trackDurationMs.coerceAtLeast(1L),
                                )
                            val pos = com.midnightdancer.app.audio.TrainerLiveAudioController
                                .absolutePositionMs(musicPlayer)
                            val exoFrac = (pos.toFloat() / mediaDur.toFloat()).coerceIn(0f, 1f)
                            if (kotlin.math.abs(exoFrac - target) <= 0.025f) break
                            // Re-assert seek if player lagged behind reverse drag.
                            if (attempt == 4 || attempt == 12) {
                                val targetMs = (target * mediaDur).toLong()
                                    .coerceIn(0L, mediaDur - 1L)
                                com.midnightdancer.app.audio.TrainerLiveAudioController.seekAbsolute(
                                    musicPlayer,
                                    targetMs,
                                    playlistDurations,
                                )
                            }
                            delay(40)
                            attempt++
                        }
                        userScrubbing = false
                        scrubLatchFrac = -1f
                    }
                },
                onTrack = { onIntent(TrainerSessionIntent.SetTrackVol(it)) },
                onVoice = { onIntent(TrainerSessionIntent.SetVoiceVol(it)) },
                haptics = haptics,
            )
            CuePipeline(
                cues = state.cueSchedule,
                playheadFraction = state.playheadFraction,
                // C-267: same clock as Seek/TTS — prevents voice 3s ahead of highlight.
                sessionTotalSec = trackTotalSec,
                isPaused = state.isPaused,
                bpm = state.bpm.toFloat(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FinishDanceCta(
            onComplete = {
                if (!state.isPaused) {
                    onIntent(TrainerSessionIntent.TogglePause)
                }
                // C-256: only Finish tears down session audio.
                com.midnightdancer.app.audio.TrainerLiveAudioController.forceStop()
                onBack()
            },
            haptics = haptics,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(10f)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = FinishCtaClearance),
        )
    }
}

@Composable
private fun PhaseHubWithSideVols(
    playheadFraction: Float,
    isPaused: Boolean,
    trackVol: Float,
    voiceVol: Float,
    trackTotalSec: Float,
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    onTrack: (Float) -> Unit,
    onVoice: (Float) -> Unit,
    haptics: HapticFeedback,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SideVolumeBar(
            icon = Icons.Filled.MusicNote,
            contentDescription = "Громкость трека",
            value = trackVol,
            onChange = onTrack,
            modifier = Modifier.width(44.dp).fillMaxHeight(),
        )
        PhaseHub(
            playheadFraction = playheadFraction,
            isPaused = isPaused,
            trackTotalSec = trackTotalSec,
            onTogglePause = onTogglePause,
            onSeek = onSeek,
            onScrubStart = onScrubStart,
            onScrubEnd = onScrubEnd,
            haptics = haptics,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        SideVolumeBar(
            icon = Icons.Filled.Person,
            contentDescription = "Громкость голоса",
            value = voiceVol,
            onChange = onVoice,
            modifier = Modifier.width(44.dp).fillMaxHeight(),
        )
    }
}

@Composable
private fun SideVolumeBar(
    icon: ImageVector,
    contentDescription: String,
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pct = (value * 100).toInt()
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Hairline, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = AtelierColors.Soft,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "$pct%",
            color = AtelierColors.Flame,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = MdFonts.Display,
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .width(VolumeThumbOuter)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun fromY(y: Float): Float {
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            return (1f - (y / h)).coerceIn(0f, 1f)
                        }
                        onChange(fromY(down.position.y))
                        drag(down.id) { change ->
                            change.consume()
                            onChange(fromY(change.position.y))
                        }
                    }
                },
        ) {
            val v = value.coerceIn(0f, 1f)
            // C-187: top = 100%, bottom = 0% — same mapping as circular scrub fraction.
            val thumbTop =
                ((maxHeight - VolumeThumbOuter) * (1f - v)).coerceAtLeast(0.dp)
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(10.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.10f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(10.dp)
                        .fillMaxHeight(v.coerceIn(0.04f, 1f))
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.verticalGradient(
                                listOf(AtelierColors.Gold, AtelierColors.Flame),
                            ),
                        ),
                )
                VolumeEndThumb(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = thumbTop),
                )
            }
        }
    }
}

@Composable
private fun VolumeEndThumb(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(VolumeThumbOuter)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(VolumeThumbInner)
                .background(AtelierColors.Flame, CircleShape),
        )
    }
}

@Composable
private fun PhaseHub(
    playheadFraction: Float,
    isPaused: Boolean,
    trackTotalSec: Float,
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
    onScrubStart: () -> Unit,
    onScrubEnd: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val progress = playheadFraction.coerceIn(0f, 1f)
    val timeLabel = formatTrackClock(progress * trackTotalSec.coerceAtLeast(1f))
    // C-222: shared Flame→Gold→White vocabulary (Reverse, ~4.5s) + yellow highlight.
    val shimmerShift by FlameGoldWhiteShimmer.rememberShift(label = "clock-shimmer")

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(176.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun fractionFrom(pos: Offset): Float {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val dx = pos.x - cx
                            val dy = pos.y - cy
                            var deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                            deg = (deg + 90.0 + 360.0) % 360.0
                            return (deg / 360.0).toFloat()
                        }
                        onScrubStart()
                        try {
                            onSeek(fractionFrom(down.position))
                            drag(down.id) { change ->
                                change.consume()
                                onSeek(fractionFrom(change.position))
                            }
                        } finally {
                            onScrubEnd()
                        }
                    }
                },
        ) {
            val stroke = 4.dp.toPx()
            val radius = size.minDimension / 2f - stroke
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = radius,
                style = Stroke(width = stroke),
            )
            val sweep = 360f * progress
            drawArc(
                color = AtelierColors.Flame,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            )
            val angleRad = Math.toRadians((-90.0 + sweep).toDouble())
            val kx = center.x + (radius * cos(angleRad)).toFloat()
            val ky = center.y + (radius * sin(angleRad)).toFloat()
            drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(kx, ky))
            drawCircle(color = AtelierColors.Flame, radius = 4.dp.toPx(), center = Offset(kx, ky))
        }

        // Center: Understrukk MM.SS shimmer (no grey inner disk)
        Text(
            text = timeLabel,
            fontFamily = MdFonts.Understrukk,
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(
                brush = FlameGoldWhiteShimmer.linearBrush(shimmerShift),
            ),
            modifier = Modifier.offset(y = (-18).dp),
        )

        // Play/pause — nearer ring center (C-164 §4.6: was 52.dp → ~24.dp)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 24.dp)
                .size(72.dp)
                .interactiveSpringPress(
                    hapticEvent = HapticEvent.Selection,
                    haptics = haptics,
                    onClick = onTogglePause,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isPaused) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = AtelierColors.Flame,
                    modifier = Modifier.size(45.dp),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.5.dp)) {
                    Box(
                        modifier = Modifier
                            .size(width = 7.5.dp, height = 27.dp)
                            .background(Color.White, RoundedCornerShape(1.5.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 7.5.dp, height = 27.dp)
                            .background(Color.White, RoundedCornerShape(1.5.dp)),
                    )
                }
            }
        }
    }
}

/** Cap at 99.59 — minutes.seconds with two digits each. */
internal fun formatTrackClock(elapsedSec: Float): String {
    val total = elapsedSec.toInt().coerceIn(0, 99 * 60 + 59)
    val m = (total / 60).coerceAtMost(99)
    val s = total % 60
    return "%02d.%02d".format(m, s)
}

/**
 * C-164 / C-202 — cues scroll bottom→up; **active gate = middle row** between neon triangles.
 * Glow lasts the full cue interval (stepSec). Past cues evaporate upward past the gate.
 */
@Composable
private fun CuePipeline(
    cues: List<CueAt>,
    playheadFraction: Float,
    sessionTotalSec: Float,
    isPaused: Boolean,
    bpm: Float = 128f,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stridePx = with(density) { (CueItemHeight + CueItemGap).toPx() }
    val cardHeightPx = with(density) { CueItemHeight.toPx() }
    val midGateTopPx = ActiveCueSlot * stridePx

    val stepSec = remember(cues) {
        com.midnightdancer.feature.trainer.logic.TrainerReady.cueStepSec(cues)
    }
    val totalSec = sessionTotalSec.coerceAtLeast(1f)
    val t = playheadFraction.coerceIn(0f, 1f) * totalSec
    // Active while playhead is inside [cue.sec, next.sec) — full spoken duration.
    val timeIndex = remember(cues, t) {
        if (cues.isEmpty()) 0 else cues.indexOfLast { it.sec <= t }.coerceAtLeast(0)
    }
    // C-229: variable element durations broke uniform stepSec → cues overlapped.
    // Slot by index + intra-cue progress (one visual row per cue).
    val gateProgress = remember(cues, timeIndex, t, stepSec) {
        if (cues.isEmpty()) 0f
        else {
            val cur = cues[timeIndex].sec
            val next = cues.getOrNull(timeIndex + 1)?.sec ?: (cur + stepSec)
            val span = (next - cur).coerceAtLeast(0.05f)
            ((t - cur) / span).coerceIn(0f, 1f)
        }
    }

    // C-226: keep BPM/ambient bars Active while the session runs — TTS duck must not freeze wave.
    // Idle only at true session ends; never drop to Idle mid-cue just because audio ducked.
    val vizState = when {
        isPaused -> VisualizerPlaybackState.Paused
        playheadFraction >= 1f -> VisualizerPlaybackState.Idle
        else -> VisualizerPlaybackState.Active
    }

    Column(
        modifier = modifier
            .height(CueViewportHeight)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MusicPulseVisualizer(
            isPlaying = vizState == VisualizerPlaybackState.Active,
            playbackState = vizState,
            bpm = bpm.coerceAtLeast(60f),
            modifier = Modifier.fillMaxWidth(),
            height = 72.dp,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 2.dp)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val fadePx = size.height * 0.18f
                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                (fadePx / size.height).coerceIn(0f, 0.4f) to Color.Black,
                                (1f - fadePx / size.height).coerceIn(0.6f, 1f) to Color.Black,
                                1f to Color.Transparent,
                            ),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            cues.forEachIndexed { globalIdx, cue ->
                // slotOffset 0 = "now" → parked on mid gate (ActiveCueSlot).
                val slotOffset = (globalIdx - timeIndex).toFloat() - gateProgress
                val translationY = (slotOffset + ActiveCueSlot) * stridePx
                // Past the mid gate upward → evaporate (toward visualizer).
                val evaporate = (-slotOffset).coerceIn(0f, 1.2f)
                val edgeFade = when {
                    translationY < 0f -> (1f + translationY / (cardHeightPx * 1.2f)).coerceIn(0f, 1f)
                    translationY > stridePx * 4.2f ->
                        (1f - (translationY - stridePx * 4.2f) / (cardHeightPx * 1.4f)).coerceIn(0f, 1f)
                    else -> 1f
                }
                val alpha = when {
                    evaporate <= 0f -> edgeFade
                    evaporate >= 1f -> 0f
                    else -> (1f - evaporate) * edgeFade
                }
                val scale = when {
                    evaporate <= 0f -> 1f
                    evaporate >= 1f -> 0.88f
                    else -> 1f - 0.12f * evaporate
                }
                if (translationY < -cardHeightPx * 1.5f || translationY > stridePx * 6f) {
                    return@forEachIndexed
                }
                if (alpha <= 0.01f) return@forEachIndexed

                // Lit for the whole cue duration while it is the current spoken element.
                val active = globalIdx == timeIndex && evaporate < 0.95f
                // C-218: keep chips inside animation track — clear of neon side triangles.
                CueCard(
                    label = cue.label,
                    active = active,
                    past = globalIdx < timeIndex,
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            this.alpha = alpha
                            this.translationY = translationY
                            scaleX = scale
                            scaleY = scale
                            if (evaporate > 0f) {
                                this.translationY = translationY - evaporate * 28f
                            }
                        },
                )
            }

            // C-202: neon triangles mark the mid gate (sketch).
            NeonGateTriangles(
                gateTopY = with(density) { midGateTopPx.toDp() },
                cardHeight = CueItemHeight,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Inward neon triangles L/R — C-226: clock color morph + scale pulse 1.0↔1.5. */
@Composable
private fun NeonGateTriangles(
    gateTopY: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "neon-gate")
    val glow by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neon-glow",
    )
    // Soft breathe 1.0↔1.5 (infinite Reverse) — feels springy without hard snap.
    val scalePulse by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "neon-scale",
    )
    // C-222/C-226: same Flame→Gold→White shift as Live clock.
    val colorShift by FlameGoldWhiteShimmer.rememberShift(label = "neon-color")
    val centerY = gateTopY + cardHeight / 2

    Box(modifier = modifier) {
        NeonTriangle(
            pointRight = true,
            glow = glow,
            colorShift = colorShift,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = NeonTriangleEdgeInset,
                    y = centerY - NeonTriangleSize / 2,
                )
                .size(NeonTriangleSize)
                .graphicsLayer {
                    scaleX = scalePulse
                    scaleY = scalePulse
                },
        )
        NeonTriangle(
            pointRight = false,
            glow = glow,
            colorShift = colorShift,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = -NeonTriangleEdgeInset,
                    y = centerY - NeonTriangleSize / 2,
                )
                .size(NeonTriangleSize)
                .graphicsLayer {
                    scaleX = scalePulse
                    scaleY = scalePulse
                },
        )
    }
}

@Composable
private fun NeonTriangle(
    pointRight: Boolean,
    glow: Float,
    colorShift: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (pointRight) {
                moveTo(0f, 0f)
                lineTo(w, h / 2f)
                lineTo(0f, h)
            } else {
                moveTo(w, 0f)
                lineTo(0f, h / 2f)
                lineTo(w, h)
            }
            close()
        }
        val morph = FlameGoldWhiteShimmer.linearBrush(
            shift = colorShift,
            span = w * 2.2f,
            vertical = h,
        )
        // C-254: full-triangle hatch only — no perimeter stroke outline.
        drawPath(
            path = path,
            brush = morph,
            alpha = (0.55f + 0.45f * glow).coerceIn(0f, 1f),
        )
        drawPath(
            path = path,
            brush = Brush.linearGradient(
                colors = FlameGoldWhiteShimmer.Colors.map { c ->
                    c.copy(alpha = (0.40f + 0.50f * glow).coerceIn(0f, 1f))
                },
                start = Offset(w * colorShift, 0f),
                end = Offset(w * (colorShift + 0.85f), h),
            ),
        )
    }
}

@Composable
private fun CueCard(
    label: String,
    active: Boolean,
    past: Boolean,
    modifier: Modifier = Modifier,
) {
    val neon = rememberInfiniteTransition(label = "cue-neon")
    val rim by neon.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cue-rim",
    )
    val borderColor = when {
        active -> AtelierColors.Flame.copy(alpha = 0.55f + 0.40f * rim)
        else -> Hairline
    }
    val bg = when {
        active -> AtelierColors.Flame.copy(alpha = 0.12f + 0.10f * rim)
        else -> Color.White.copy(alpha = 0.03f)
    }
    val titleColor = Color.White
    @Suppress("UNUSED_VARIABLE")
    val unusedPast = past

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CueItemHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                // C-255: active cue border 2× thicker (2→4.dp).
                width = if (active) 4.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .then(
                if (active) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(
                                    AtelierColors.Flame.copy(alpha = 0.0f),
                                    AtelierColors.Glow.copy(alpha = 0.55f * rim),
                                    Color.White.copy(alpha = 0.35f * rim),
                                    AtelierColors.Flame.copy(alpha = 0.0f),
                                ),
                            ),
                            style = Stroke(width = 4.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = titleColor,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            letterSpacing = 1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = MdFonts.Display,
        )
    }
}

/**
 * C-164 §4.3 — tap «Завершить» ends session (hold was unreliable / dock stole touches).
 * zIndex kept high so sibling content does not intercept.
 */
@Composable
private fun FinishDanceCta(
    onComplete: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberInfiniteTransition(label = "finish-shimmer")
    val shineX by shimmer.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "finish-shine-x",
    )
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .zIndex(10f)
            .clip(shape)
            .clickable(onClick = onComplete)
            .background(
                Brush.horizontalGradient(
                    listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                ),
            )
            .drawWithContent {
                drawContent()
                val w = size.width
                val band = w * 0.28f
                val x = shineX * (w + band) - band
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        start = Offset(x, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            }
            .interactiveSpringPress(
                hapticEvent = HapticEvent.Success,
                haptics = haptics,
                onClick = onComplete,
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(Color.White, RoundedCornerShape(2.dp)),
            )
            Text(
                text = "Завершить",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                fontFamily = MdFonts.Display,
            )
        }
    }
}
