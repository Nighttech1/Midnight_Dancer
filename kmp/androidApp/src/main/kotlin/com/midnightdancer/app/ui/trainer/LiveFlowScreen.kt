package com.midnightdancer.app.ui.trainer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.components.MusicPulseVisualizer
import com.midnightdancer.app.ui.components.VisualizerPlaybackState
import com.midnightdancer.app.ui.elements.AtelierColors
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
import kotlin.math.min
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
/** Fits ~5 cue cards under the visualizer. */
private val CueViewportHeight = 320.dp
private const val HoldDurationMs = 1000L
private const val CueStepSec = 8f
/** Mock full track length for MM.SS clock (cap display at 99.59). */
private const val TrackTotalSec = 225f
private const val VisibleCueSlots = 5

/**
 * Live session (C-98) — Understrukk clock, side vols, beat-wave, cue on line touch.
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

    // Auto-advance playhead while playing so clock / cues sync (session stub has no audio clock yet).
    LaunchedEffect(state.isPaused) {
        if (state.isPaused) return@LaunchedEffect
        var frac = state.playheadFraction
        while (isActive) {
            delay(50)
            val step = 50f / (TrackTotalSec * 1000f)
            frac = (frac + step).coerceAtMost(1f)
            onIntent(TrainerSessionIntent.Seek(frac))
            if (frac >= 1f) break
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
                onTogglePause = { onIntent(TrainerSessionIntent.TogglePause) },
                onSeek = { onIntent(TrainerSessionIntent.Seek(it)) },
                onTrack = { onIntent(TrainerSessionIntent.SetTrackVol(it)) },
                onVoice = { onIntent(TrainerSessionIntent.SetVoiceVol(it)) },
                haptics = haptics,
            )
            CuePipeline(
                cues = state.cueSchedule,
                playheadFraction = state.playheadFraction,
                isPaused = state.isPaused,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        FinishDanceCta(
            holdProgress = state.holdProgress,
            onTick = { onIntent(TrainerSessionIntent.HoldFinishTick(it)) },
            onReset = { onIntent(TrainerSessionIntent.ResetHold) },
            onComplete = onBack,
            haptics = haptics,
            modifier = Modifier
                .align(Alignment.BottomCenter)
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
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
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
            onTogglePause = onTogglePause,
            onSeek = onSeek,
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
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
                .width(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .pointerInput(value) {
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
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(value.coerceIn(0.04f, 1f))
                    .background(
                        Brush.verticalGradient(
                            listOf(AtelierColors.Gold, AtelierColors.Flame),
                        ),
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

@Composable
private fun PhaseHub(
    playheadFraction: Float,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onSeek: (Float) -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val progress = playheadFraction.coerceIn(0f, 1f)
    val timeLabel = formatTrackClock(progress * TrackTotalSec)
    val shimmer = rememberInfiniteTransition(label = "clock-shimmer")
    val shimmerShift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "clock-shift",
    )

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
                        onSeek(fractionFrom(down.position))
                        drag(down.id) { change ->
                            change.consume()
                            onSeek(fractionFrom(change.position))
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
                brush = Brush.linearGradient(
                    colors = listOf(
                        AtelierColors.Flame,
                        Color.White,
                        AtelierColors.Gold,
                        Color.White,
                        AtelierColors.Flame,
                    ),
                    start = Offset(shimmerShift * 400f - 200f, 0f),
                    end = Offset(shimmerShift * 400f + 200f, 80f),
                ),
            ),
            modifier = Modifier.offset(y = (-18).dp),
        )

        // Play/pause — lower in ring, transparent chrome (×1.5 vs prior 48/30 hit/icon)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 52.dp)
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

@Composable
private fun CuePipeline(
    cues: List<CueAt>,
    playheadFraction: Float,
    isPaused: Boolean,
    modifier: Modifier = Modifier,
) {
    var firstIndex by remember { mutableIntStateOf(0) }
    var evaporating by remember { mutableStateOf<Int?>(null) }

    val totalSec = remember(cues) {
        if (cues.isEmpty()) CueStepSec else (cues.last().sec + CueStepSec).coerceAtLeast(CueStepSec)
    }
    val t = playheadFraction.coerceIn(0f, 1f) * totalSec
    val timeIndex = remember(cues, t) {
        if (cues.isEmpty()) 0 else cues.indexOfLast { it.sec <= t }.coerceAtLeast(0)
    }

    LaunchedEffect(timeIndex, cues.size) {
        if (cues.isEmpty() || evaporating != null) return@LaunchedEffect
        if (timeIndex > firstIndex) {
            evaporating = firstIndex
            delay(400)
            firstIndex = timeIndex
            evaporating = null
        }
    }

    val window = remember(cues, firstIndex) {
        cues.drop(firstIndex).take(VisibleCueSlots).mapIndexed { i, cue ->
            (firstIndex + i) to cue
        }
    }

    val vizState = when {
        isPaused -> VisualizerPlaybackState.Paused
        playheadFraction <= 0f || playheadFraction >= 1f -> VisualizerPlaybackState.Idle
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
            modifier = Modifier.fillMaxWidth(),
            height = 72.dp,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            window.forEach { (globalIdx, cue) ->
                val isEvaporating = evaporating == globalIdx
                val evaporateProgress by animateFloatAsState(
                    targetValue = if (isEvaporating) 0f else 1f,
                    animationSpec = tween(380),
                    label = "cue-evap-$globalIdx",
                )
                // Lights only when this cue is current and sits under the visualizer (top slot).
                val active = !isEvaporating && globalIdx == timeIndex && globalIdx == firstIndex
                CueCard(
                    label = cue.label,
                    active = active,
                    past = globalIdx < timeIndex,
                    modifier = Modifier.graphicsLayer {
                        alpha = evaporateProgress
                        translationY = (1f - evaporateProgress) * -32f
                        scaleX = 0.96f + 0.04f * evaporateProgress
                        scaleY = evaporateProgress.coerceAtLeast(0.01f)
                    },
                )
            }
        }
    }
}

@Composable
private fun CueCard(
    label: String,
    active: Boolean,
    past: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        active -> AtelierColors.Flame.copy(alpha = 0.70f)
        else -> Hairline
    }
    val bg = when {
        active -> AtelierColors.Flame.copy(alpha = 0.16f)
        else -> Color.White.copy(alpha = 0.03f)
    }
    val titleColor = when {
        active -> Color.White
        past -> Color.White.copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.55f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(CueItemHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
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
        )
    }
}

/** Same Flame shimmer CTA as Storyline «Танцуем!» — hold 1s to exit. */
@Composable
private fun FinishDanceCta(
    holdProgress: Float,
    onTick: (Float) -> Unit,
    onReset: () -> Unit,
    onComplete: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var localProgress by remember { mutableFloatStateOf(0f) }
    val displayProgress = if (holdProgress > 0f) holdProgress else localProgress
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var completed by remember { mutableStateOf(false) }
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
            .clip(shape)
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
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    completed = false
                    holdJob?.cancel()
                    holdJob = scope.launch {
                        val start = System.currentTimeMillis()
                        while (isActive) {
                            val elapsed = System.currentTimeMillis() - start
                            val p = min(elapsed.toFloat() / HoldDurationMs, 1f)
                            localProgress = p
                            onTick(p)
                            if (p >= 1f) {
                                completed = true
                                haptics.perform(HapticEvent.Success)
                                onComplete()
                                break
                            }
                            delay(16)
                        }
                    }
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.any { it.pressed }
                    } while (pressed)
                    holdJob?.cancel()
                    holdJob = null
                    if (!completed) {
                        localProgress = 0f
                        onReset()
                    }
                }
            }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Hold fill overlay
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    scaleX = displayProgress.coerceIn(0f, 1f).takeIf { it > 0f } ?: 0.001f
                    alpha = if (displayProgress > 0f) 0.35f else 0f
                }
                .background(Color.Black.copy(alpha = 0.35f)),
        )
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
                text = if (displayProgress > 0f) "Сохранение…" else "Завершить",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
        }
    }
}
