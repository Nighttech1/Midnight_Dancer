package com.midnightdancer.app.ui.elements

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midnightdancer.app.ui.media.*
import com.midnightdancer.core.domain.media.OwnedMediaKind
import com.midnightdancer.core.data.files.OwnedMediaRuntime
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.LocalRaiseHardDisk
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.components.SearchablePicker
import com.midnightdancer.app.ui.components.guardMediaDiskOrRaise
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.feature.elements.logic.DanceStyleOption
import com.midnightdancer.feature.elements.logic.ElementEditorDraft
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val EditorBackground = Color(0xFF000000)
private val VideoShape = RoundedCornerShape(20.dp)
private val SpeedSteps = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f)

/**
 * Single editor sheet for Elements: Add opens it as an empty draft, card Play opens it for that card.
 * Media3 preview (frame-synced scrub + speed strip) over an OLED plate, Flame spring chrome.
 */
@Composable
fun ElementEditorSheet(
    draft: ElementEditorDraft,
    styles: List<DanceStyleOption>,
    tags: List<String>,
    onDraftChange: (ElementEditorDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    backendSaving: Boolean = false,
    backendSaveError: String? = null,
) {
    val context = LocalContext.current
    val haptics = rememberMdHaptics()
    val currentDraft by rememberUpdatedState(draft)
    val raiseHardDisk = LocalRaiseHardDisk.current
    val copyController = rememberOwnedMediaCopyController()
    val copyState by copyController.state.collectAsStateWithLifecycle()
    var pickGeneration by remember { mutableLongStateOf(0L) }
    var explicitNewPick by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    val busy = backendSaving || copyState.busy
    val latestOnDraftChange by rememberUpdatedState(onDraftChange)
    val latestOnSave by rememberUpdatedState(onSave)

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 1f
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                previewError = "Не удалось открыть видео. Выберите файл повторно."
            }
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    // C-246: replace video must tear down old item before prepare — else ExoPlayer can hang the sheet.
    LaunchedEffect(draft.videoUri, pickGeneration) {
        previewError = null
        positionMs = 0L
        durationMs = 0L
        isPlaying = false
        player.playWhenReady = false
        runCatching {
            player.stop()
            player.clearMediaItems()
        }
        val uriStr = draft.videoUri ?: return@LaunchedEffect
        runCatching {
            val effective = if (explicitNewPick) uriStr else OwnedMediaRuntime.resolveReference(uriStr)
            player.setMediaItem(MediaItem.fromUri(Uri.parse(effective)))
            player.prepare()
            player.seekTo(0L)
            // C-193: do not autoplay — user taps video / Play to start.
            player.playWhenReady = false
        }.onFailure { previewError = ownedMediaMessage(it) }
    }
    LaunchedEffect(player) {
        while (true) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            durationMs = if (d > 0L && d != androidx.media3.common.C.TIME_UNSET) d else 0L
            delay(60)
        }
    }
    // Push duration once per URI — avoid onDraftChange storms that freeze the dialog.
    var lastDurationPushUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(durationMs, draft.videoUri) {
        val uri = draft.videoUri ?: return@LaunchedEffect
        if (durationMs <= 0L) return@LaunchedEffect
        if (uri == lastDurationPushUri && durationMs == currentDraft.durationMs) return@LaunchedEffect
        lastDurationPushUri = uri
        onDraftChange(currentDraft.copy(durationMs = durationMs))
    }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            explicitNewPick = true
            pickGeneration++
            // Reset duration so UI does not keep old length while new file prepares.
            onDraftChange(
                currentDraft.copy(
                    videoUri = uri.toString(),
                    durationMs = 0L,
                ),
            )
        }
    }

    var openPicker by remember { mutableStateOf<String?>(null) }
    val styleNames = remember(styles) { styles.map { it.name } }
    val currentStyleName = styles
        .firstOrNull { it.id.equals(draft.styleId, ignoreCase = true) }
        ?.name
        .orEmpty()
    // C-193: no «без тега» option — empty selection means no tag.
    val tagOptions = remember(tags) { tags }
    val tagPlaceholder = stringResource(R.string.elements_editor_tag_label)

    // Fullscreen editor: OLED plate fills the whole window, content respects system bars.
    Dialog(
        onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(EditorBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Text(
                    text = stringResource(
                        if (draft.cardId == null) {
                            R.string.elements_editor_title_new
                        } else {
                            R.string.elements_editor_title_edit
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = MdFonts.Display,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                NameField(
                    value = draft.title,
                    onValueChange = { onDraftChange(draft.copy(title = it.take(60))) },
                )
                Spacer(Modifier.height(12.dp))

                VideoPreview(
                    player = player,
                    mediaKey = draft.videoUri.orEmpty() + ":" + pickGeneration,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    speed = speed,
                    onTogglePlay = {
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                    },
                    onSeekFraction = { f ->
                        if (durationMs > 0L) player.seekTo((durationMs * f).toLong())
                    },
                    onSpeed = { s ->
                        speed = s
                        player.setPlaybackSpeed(s)
                    },
                )

                Spacer(Modifier.height(10.dp))
                UploadVideoButton(
                    replace = draft.videoUri != null,
                    onClick = {
                        // C-241: no vibration on «Загрузить видео».
                        if (!busy && guardMediaDiskOrRaise(raiseHardDisk)) {
                            pickVideo.launch(arrayOf("video/*"))
                        }
                    },
                )

                MediaReferenceNotice(draft.videoUri, explicitNewPick)
                (copyState.error ?: backendSaveError ?: previewError)?.let { Text(it, color = Color(0xFFFFB4AB)) }
                Spacer(Modifier.height(16.dp))
                SearchablePicker(
                    label = stringResource(R.string.elements_editor_style_label),
                    value = currentStyleName,
                    options = styleNames,
                    expanded = openPicker == "style",
                    onExpandedChange = { open -> openPicker = if (open) "style" else null },
                    onSelect = { name ->
                        styles.firstOrNull { it.name == name }?.let { picked ->
                            onDraftChange(draft.copy(styleId = picked.id))
                        }
                    },
                    panelMaxHeight = 220.dp,
                )

                Spacer(Modifier.height(14.dp))
                SearchablePicker(
                    label = stringResource(R.string.elements_editor_tag_label),
                    value = draft.tag?.takeIf { it.isNotBlank() } ?: tagPlaceholder,
                    options = tagOptions,
                    expanded = openPicker == "tag",
                    onExpandedChange = { open -> openPicker = if (open) "tag" else null },
                    onSelect = { picked ->
                        onDraftChange(draft.copy(tag = picked.takeIf { it.isNotBlank() }))
                    },
                    panelMaxHeight = 220.dp,
                )

                Spacer(Modifier.height(18.dp))
                // C-193: same Soft label style as «Стиль» (SearchablePicker / MdFieldLabel).
                MdFieldLabel(
                    text = "${stringResource(R.string.elements_editor_mastery_label)}: ${draft.masteryPercent}%",
                )
                Slider(
                    value = draft.masteryPercent.toFloat(),
                    onValueChange = { v ->
                        onDraftChange(draft.copy(masteryPercent = v.roundToInt().coerceIn(0, 100)))
                    },
                    valueRange = 0f..100f,
                    colors = SliderDefaults.colors(
                        thumbColor = AtelierColors.Flame,
                        activeTrackColor = AtelierColors.Flame,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                    ),
                )
                Spacer(Modifier.height(8.dp))
            }

            // Bottom actions — same TextButton style as Music track edit dialog.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel, enabled = !busy) {
                    Text(
                        text = stringResource(R.string.elements_editor_cancel),
                        color = Color.White.copy(alpha = 0.6f),
                        // C-161: Cancel/Save text 1.5× (default ~14sp → 21sp).
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
                TextButton(enabled = !busy, onClick = {
                    val captured = currentDraft
                    val reference = captured.videoUri
                    if (reference.isNullOrBlank()) latestOnSave()
                    else copyController.save(reference, OwnedMediaKind.Video, existingReference = !explicitNewPick) { owned ->
                        val duration = ownedMediaDuration(context, owned.reference)
                        latestOnDraftChange(captured.copy(videoUri = owned.reference, durationMs = duration))
                        explicitNewPick = false
                        latestOnSave()
                    }
                }) {
                    Text(
                        text = stringResource(R.string.elements_editor_save),
                        color = AtelierColors.Flame,
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
            }
        }
    }
    OwnedMediaCopyDialog(copyController)
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val hint = stringResource(R.string.elements_editor_name_hint)
    val shape = RoundedCornerShape(14.dp)
    // C-210 / C-241: no auto-focus, no pre-selected name; select-all only on double-tap.
    com.midnightdancer.app.ui.components.MdSelectAllTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        selectAllOnOpen = false,
        selectAllOnFocus = false,
        textStyle = TextStyle(
            color = Color.White,
            fontSize = 15.sp,
            fontFamily = MdFonts.Display,
        ),
        cursorBrush = SolidColor(AtelierColors.Flame),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(shape)
                    .background(Color.White.copy(alpha = 0.04f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = Color.White.copy(alpha = 0.35f),
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun VideoPreview(
    player: ExoPlayer,
    mediaKey: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onTogglePlay: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSpeed: (Float) -> Unit,
) {
    val haptics = rememberMdHaptics()
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val playCd = stringResource(R.string.elements_play)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)
            .clip(VideoShape)
            .background(Color(0xFF05060C))
            .border(1.dp, Color.White.copy(alpha = 0.10f), VideoShape),
    ) {
        // C-246: remount PlayerView when URI changes so replace does not stick on old surface.
        key(mediaKey) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // C-221: no Material ripple flash on video Play/Stop tap.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTogglePlay,
                    ),
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        this.player = player
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShutterBackgroundColor(android.graphics.Color.parseColor("#05060C"))
                        setKeepContentOnPlayerReset(true)
                    }
                },
                update = { view -> if (view.player !== player) view.player = player },
            )
        }

        // Bottom scrim for transport legibility.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(112.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                ),
        )

        // C-193: while playing — no Stop/Pause chrome; tap video pauses and shows Play.
        if (!isPlaying) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .border(1.dp, AtelierColors.Flame.copy(alpha = 0.65f), CircleShape)
                    .interactiveSpringPress(
                        pressedScale = 0.90f,
                        hapticEvent = HapticEvent.MediumImpact,
                        haptics = haptics,
                        onClick = onTogglePlay,
                    )
                    .semantics { contentDescription = playCd },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        // Bottom: scrub progress + speed strip.
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeLabel(positionMs)
                ScrubBar(
                    progress = progress,
                    onSeek = onSeekFraction,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                TimeLabel(durationMs)
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            ) {
                SpeedSteps.forEach { step ->
                    SpeedChip(
                        label = speedLabel(step),
                        selected = step == speed,
                        onClick = { onSpeed(step) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(ms: Long) {
    Text(
        text = formatTime(ms),
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = MdFonts.Display,
            fontSize = 10.sp,
        ),
        color = Color.White.copy(alpha = 0.80f),
    )
}

@Composable
private fun ScrubBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = dragFraction ?: progress
    val thumbScale by animateFloatAsState(
        targetValue = if (dragFraction != null) 1.6f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "thumbScale",
    )
    Box(
        modifier = modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
            val trackH = 4.dp.toPx()
            val y = (size.height - trackH) / 2f
            val radius = CornerRadius(trackH / 2f, trackH / 2f)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.22f),
                topLeft = Offset(0f, y),
                size = Size(size.width, trackH),
                cornerRadius = radius,
            )
            val fillW = size.width * shown
            if (fillW > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(AtelierColors.Ember, AtelierColors.Glow, AtelierColors.Gold),
                        startX = 0f,
                        endX = size.width.coerceAtLeast(1f),
                    ),
                    topLeft = Offset(0f, y),
                    size = Size(fillW, trackH),
                    cornerRadius = radius,
                )
            }
            val thumbR = 6.dp.toPx() * thumbScale
            drawCircle(
                color = Color.White,
                radius = thumbR,
                center = Offset(fillW.coerceIn(thumbR, size.width - thumbR), size.height / 2f),
            )
        }
    }
}

@Composable
private fun SpeedChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = rememberMdHaptics()
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(shape)
            .background(
                if (selected) AtelierColors.Flame else Color.Black.copy(alpha = 0.50f),
            )
            .border(
                1.dp,
                if (selected) AtelierColors.Flame else Color.White.copy(alpha = 0.22f),
                shape,
            )
            .interactiveSpringPress(
                pressedScale = 0.92f,
                hapticEvent = HapticEvent.Selection,
                haptics = haptics,
                onClick = onClick,
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = MdFonts.Display,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun UploadVideoButton(
    replace: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(AtelierColors.Flame.copy(alpha = 0.10f))
            .border(1.dp, AtelierColors.Flame.copy(alpha = 0.55f), shape)
            .interactiveSpringPress(
                pressedScale = 0.97f,
                hapticEvent = HapticEvent.LightImpact,
                // C-241: silent — no vibrate on upload tap.
                haptics = com.midnightdancer.core.designsystem.NoOpHapticFeedback,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.VideoLibrary,
            contentDescription = null,
            tint = AtelierColors.Flame,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(
                if (replace) R.string.elements_editor_video_replace else R.string.elements_editor_upload_video,
            ),
            style = MaterialTheme.typography.labelLarge.copy(
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
            ),
            color = AtelierColors.Flame,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontFamily = MdFonts.Display),
        color = Color.White.copy(alpha = 0.55f),
    )
}

private fun speedLabel(step: Float): String =
    if (step == step.toInt().toFloat()) "${step.toInt()}x" else "${step}x"

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}
