package com.midnightdancer.app.ui.choreography

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.zIndex
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.MarqueeText
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.core.domain.validation.DomainValidators
import com.midnightdancer.feature.choreography.logic.SequenceEditorIntent
import com.midnightdancer.feature.choreography.logic.SequenceEditorUiState

private val SurfaceLow = Color(0xB31A1B20)
private val SurfaceLowest = Color(0xFF0D0E12)
private val Hairline = Color.White.copy(alpha = 0.10f)

private val StubElements = listOf(
    "Волна к себе",
    "Sensual Head Roll",
    "Мадридский шаг",
    "Body Roll вниз",
    "Базовый футворк",
)
private val StubTracks = listOf(
    "Charles Luis - Ocasión",
    "Romeo Santos - Propuesta",
    "Marc Anthony - Vivir Mi Vida",
    "Jensen - Carita",
)

/**
 * Pro Editor — CapCut-style dock tools, lane + buttons, drag reorder (C-85).
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

    val elementBlocks = state.addedElementLabels
    val musicBlocks = state.addedMusicLabels
    val elementSelected = state.selectedElementIndex >= 0
    val volumeEnabled = !elementSelected
    val speedEnabled = !elementSelected

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(oled)
            .padding(bottom = 4.dp),
    ) {
        // Title bar always above preview (zIndex) — never covered by video stage.
        ProEditorTopBar(
            title = state.title,
            canUndo = state.canUndo,
            canRedo = state.canRedo,
            onUndo = { onIntent(SequenceEditorIntent.Undo) },
            onRedo = { onIntent(SequenceEditorIntent.Redo) },
            onBack = onBack,
            haptics = haptics,
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(2f),
        )

        PreviewStage(
            isPlaying = state.isPlaying,
            playPulse = playPulse,
            currentElement = state.previewElementLabel
                ?: elementBlocks.getOrNull(state.selectedElementIndex.coerceAtLeast(0)),
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .heightIn(max = 320.dp)
                .padding(horizontal = 28.dp, vertical = 6.dp)
                .zIndex(0f),
        )

        TransportConsole(
            timecode = state.timecode,
            durationLabel = state.durationLabel,
            isPlaying = state.isPlaying,
            onIntent = onIntent,
            haptics = haptics,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
        )

        DualTimelineTracks(
            elementBlocks = elementBlocks,
            musicBlocks = musicBlocks,
            selectedElementIndex = state.selectedElementIndex,
            selectedMusicIndex = state.selectedMusicIndex,
            onAddElement = { showElementPicker = true },
            onAddMusic = { showMusicPicker = true },
            onSelectElement = { onIntent(SequenceEditorIntent.SelectElement(it)) },
            onSelectMusic = { onIntent(SequenceEditorIntent.SelectMusic(it)) },
            onReorderElement = { from, to ->
                onIntent(SequenceEditorIntent.ReorderElements(from, to))
            },
            onReorderMusic = { from, to ->
                onIntent(SequenceEditorIntent.ReorderMusic(from, to))
            },
            haptics = haptics,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 6.dp),
        )

        // 7.5 CapCut toolbar between timeline and dock — Split, Delete, Volume, Speed
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
            volumeEnabled = volumeEnabled,
            speedEnabled = speedEnabled,
            haptics = haptics,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }

    if (showElementPicker) {
        MultiSelectDialog(
            title = "Добавить элементы (без звука)",
            options = StubElements,
            onDismiss = { showElementPicker = false },
            onConfirm = { selected ->
                onIntent(SequenceEditorIntent.AddElements(selected))
                showElementPicker = false
            },
        )
    }
    if (showMusicPicker) {
        MusicAttachDialog(
            options = StubTracks,
            currentSpeed = state.songPlaybackSpeed,
            onDismiss = { showMusicPicker = false },
            onConfirm = { selected, speed ->
                onIntent(SequenceEditorIntent.AddMusicTracks(selected))
                onIntent(SequenceEditorIntent.SetSongPlaybackSpeed(speed))
                showMusicPicker = false
            },
        )
    }
    if (showVolume) {
        SliderDialog(
            title = "Громкость трека",
            value = state.musicVolume,
            valueRange = 0f..1f,
            valueLabel = { String.format("%.0f%%", it * 100) },
            onDismiss = { showVolume = false },
            onConfirm = {
                onIntent(SequenceEditorIntent.SetMusicVolume(it))
                showVolume = false
            },
        )
    }
    if (showSpeed) {
        SliderDialog(
            title = stringResource(R.string.choreo_track_speed),
            value = state.clipSpeed.toFloat(),
            valueRange = DomainValidators.CHOREO_PLAYBACK_SPEED_MIN.toFloat()..
                DomainValidators.CHOREO_PLAYBACK_SPEED_MAX.toFloat(),
            valueLabel = { String.format("%.1f×", it) },
            onDismiss = { showSpeed = false },
            onConfirm = {
                onIntent(SequenceEditorIntent.SetClipSpeed(it.toDouble()))
                showSpeed = false
            },
        )
    }
}

@Composable
private fun ProEditorTopBar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
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
            // Editor title = choreography name (same family as MdPageTitle, slightly tighter for long names).
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = MdFonts.Display,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            HistoryAction(
                icon = Icons.AutoMirrored.Filled.Undo,
                enabled = canUndo,
                haptics = haptics,
                onClick = onUndo,
            )
            HistoryAction(
                icon = Icons.AutoMirrored.Filled.Redo,
                enabled = canRedo,
                haptics = haptics,
                onClick = onRedo,
            )
        }
    }
}

@Composable
private fun HistoryAction(
    icon: ImageVector,
    enabled: Boolean,
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(
                if (enabled) AtelierColors.Flame.copy(alpha = 0.18f) else Color.Transparent,
            )
            .interactiveSpringPress(
                pressedScale = 0.88f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = { if (enabled) onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) AtelierColors.Flame else Color.White.copy(alpha = 0.28f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CapCutEditToolbar(
    onSplit: () -> Unit,
    onDelete: () -> Unit,
    onVolume: () -> Unit,
    onSpeed: () -> Unit,
    volumeEnabled: Boolean,
    speedEnabled: Boolean,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF12131A))
            .border(1.dp, Hairline, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CapCutTool(Icons.Filled.ContentCut, "Разделить", onSplit, haptics)
        CapCutTool(Icons.Filled.Delete, "Удалить", onDelete, haptics)
        CapCutTool(Icons.AutoMirrored.Filled.VolumeUp, "Громкость", onVolume, haptics, enabled = volumeEnabled)
        CapCutTool(Icons.Filled.Speed, "Скорость", onSpeed, haptics, enabled = speedEnabled)
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

@Composable
private fun DualTimelineTracks(
    elementBlocks: List<String>,
    musicBlocks: List<String>,
    selectedElementIndex: Int,
    selectedMusicIndex: Int,
    onAddElement: () -> Unit,
    onAddMusic: () -> Unit,
    onSelectElement: (Int) -> Unit,
    onSelectMusic: (Int) -> Unit,
    onReorderElement: (Int, Int) -> Unit,
    onReorderMusic: (Int, Int) -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Elements lane: black + white left, black blocks / white text
        TimelineLane(
            plusBlack = true,
            onPlus = onAddElement,
            blocks = elementBlocks,
            selectedIndex = selectedElementIndex,
            blockBg = Color.Black,
            blockFg = Color.White,
            trackBg = Color(0xFF0A0A0C),
            useMarquee = false,
            onSelect = onSelectElement,
            onReorder = onReorderElement,
            haptics = haptics,
        )
        // Music lane: orange + black icon — full track names with marquee
        TimelineLane(
            plusBlack = false,
            onPlus = onAddMusic,
            blocks = musicBlocks,
            selectedIndex = selectedMusicIndex,
            blockBg = AtelierColors.Flame,
            blockFg = Color.Black,
            trackBg = AtelierColors.Flame.copy(alpha = 0.35f),
            useMarquee = true,
            onSelect = onSelectMusic,
            onReorder = onReorderMusic,
            haptics = haptics,
        )
    }
}

@Composable
private fun TimelineLane(
    plusBlack: Boolean,
    onPlus: () -> Unit,
    blocks: List<String>,
    selectedIndex: Int,
    blockBg: Color,
    blockFg: Color,
    trackBg: Color,
    useMarquee: Boolean,
    onSelect: (Int) -> Unit,
    onReorder: (Int, Int) -> Unit,
    haptics: HapticFeedback,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 7.3 left + buttons
        Box(
            modifier = Modifier
                .size(36.dp)
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
                    onClick = onPlus,
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

        Row(
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(trackBg)
                .border(1.dp, Hairline, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            blocks.forEachIndexed { index, label ->
                var dragOffset by remember(index, label) { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .then(
                            if (useMarquee) Modifier.widthIn(min = 96.dp, max = 168.dp)
                            else Modifier,
                        )
                        .graphicsLayer { translationX = dragOffset }
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
                        .pointerInput(index, blocks.size) {
                            detectDragGesturesAfterLongPress(
                                onDragEnd = {
                                    val step = 120f
                                    val delta = (dragOffset / step).toInt()
                                    val to = (index + delta).coerceIn(0, blocks.lastIndex)
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
                        .interactiveSpringPress(
                            pressedScale = 0.96f,
                            hapticEvent = HapticEvent.Selection,
                            haptics = haptics,
                            onClick = { onSelect(index) },
                        )
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (useMarquee) {
                        MarqueeText(
                            text = label,
                            enabled = true,
                            color = blockFg,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = label.take(14),
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
}

@Composable
private fun PreviewStage(
    isPlaying: Boolean,
    playPulse: Float,
    currentElement: String?,
    modifier: Modifier = Modifier,
) {
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

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceLowest)
                .border(
                    width = 1.dp,
                    color = AtelierColors.Flame.copy(alpha = 0.15f + playPulse * glow * 0.45f),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            // Silent element preview label (no audio) — no Safe 9:16 badge / corner chrome.
            if (!currentElement.isNullOrBlank()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = currentElement,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MdFonts.Display,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "видео · без звука",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 11.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
            }

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .size(40.dp)
                        .graphicsLayer { alpha = 0.35f + glow * 0.40f }
                        .background(AtelierColors.Flame.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TransportConsole(
    timecode: String,
    durationLabel: String,
    isPlaying: Boolean,
    onIntent: (SequenceEditorIntent) -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceLow)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "TIMECODE",
                color = Color.White.copy(alpha = 0.40f),
                fontSize = 9.sp,
                fontFamily = MdFonts.Display,
                letterSpacing = 1.2.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = timecode,
                    color = AtelierColors.Soft,
                    fontSize = 14.sp,
                    fontFamily = MdFonts.Display,
                    fontWeight = FontWeight.Bold,
                )
                if (durationLabel.isNotBlank() && durationLabel != "00:00") {
                    Text(
                        text = " / $durationLabel",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.sp,
                        fontFamily = MdFonts.Display,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TransportIconButton(Icons.Filled.FastRewind, "-1", haptics) {
                onIntent(SequenceEditorIntent.NudgeFrameBack)
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
            TransportIconButton(Icons.Filled.FastForward, "+1", haptics) {
                onIntent(SequenceEditorIntent.NudgeFrameForward)
            }
        }
        // Spacer keeps transport controls centered after SNAP removal
        Spacer(Modifier.width(48.dp))
    }
}

@Composable
private fun TransportIconButton(
    icon: ImageVector,
    label: String,
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(0xFF1F1F24))
            .interactiveSpringPress(
                pressedScale = 0.90f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun MultiSelectDialog(
    title: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12131A),
        title = {
            Text(title, color = Color.White, fontFamily = MdFonts.Display, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Checkbox(
                            checked = option in selected.value,
                            onCheckedChange = { checked ->
                                selected.value = if (checked) {
                                    selected.value + option
                                } else {
                                    selected.value - option
                                }
                            },
                        )
                        Text(option, color = Color.White, fontFamily = MdFonts.Display)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.value.toList()) }) {
                Text("Добавить", color = AtelierColors.Flame)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.70f))
            }
        },
    )
}

@Composable
private fun MusicAttachDialog(
    options: List<String>,
    currentSpeed: Double,
    onDismiss: () -> Unit,
    onConfirm: (List<String>, Double) -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<String>()) }
    var speed by remember {
        mutableFloatStateOf(
            currentSpeed.toFloat().coerceIn(
                DomainValidators.CHOREO_PLAYBACK_SPEED_MIN.toFloat(),
                DomainValidators.CHOREO_PLAYBACK_SPEED_MAX.toFloat(),
            ),
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12131A),
        title = {
            Text("Добавить музыку", color = Color.White, fontFamily = MdFonts.Display, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                options.forEach { option ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(
                            checked = option in selected.value,
                            onCheckedChange = { checked ->
                                selected.value = if (checked) selected.value + option else selected.value - option
                            },
                        )
                        Text(option, color = Color.White, fontFamily = MdFonts.Display)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "${stringResource(R.string.choreo_track_speed)}: ${String.format("%.1f×", speed)}",
                    color = Color.White,
                    fontFamily = MdFonts.Display,
                    fontSize = 13.sp,
                )
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    valueRange = DomainValidators.CHOREO_PLAYBACK_SPEED_MIN.toFloat()..
                        DomainValidators.CHOREO_PLAYBACK_SPEED_MAX.toFloat(),
                    colors = SliderDefaults.colors(
                        thumbColor = AtelierColors.Flame,
                        activeTrackColor = AtelierColors.Flame,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.value.toList(), speed.toDouble()) }) {
                Text("Добавить", color = AtelierColors.Flame)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = Color.White.copy(alpha = 0.70f))
            }
        },
    )
}

@Composable
private fun SliderDialog(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: (Float) -> String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var draft by remember { mutableFloatStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF12131A),
        title = { Text(title, color = Color.White, fontFamily = MdFonts.Display, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(valueLabel(draft), color = Color.White, fontFamily = MdFonts.Display)
                Slider(
                    value = draft,
                    onValueChange = { draft = it },
                    valueRange = valueRange,
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
