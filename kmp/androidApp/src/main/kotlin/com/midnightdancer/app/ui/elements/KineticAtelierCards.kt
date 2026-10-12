package com.midnightdancer.app.ui.elements

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import com.midnightdancer.app.ui.theme.MdFonts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.feature.elements.logic.ElementCard
import com.midnightdancer.feature.elements.logic.ElementCardKind

private val FooterShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
private const val KenBurnsMs = 14_000
private const val RadialSpinMs = 6_000
private const val BeatPeriodMs = 468

@Composable
fun KineticElementCard(
    card: ElementCard,
    imageLoader: ImageLoader,
    bpmLabel: String,
    isPlaying: Boolean,
    player: androidx.media3.exoplayer.ExoPlayer,
    onPlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()

    Box(
        modifier = modifier
            .background(AtelierColors.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(22.dp))
            .interactiveSpringPress(
                pressedScale = 0.97f,
                hapticEvent = HapticEvent.LightImpact,
                haptics = haptics,
                onClick = onOpen,
            ),
    ) {
        if (isPlaying) {
            ElementInlineVideo(player = player, modifier = Modifier.fillMaxSize())
        } else {
            when (card.kind) {
                ElementCardKind.VideoCover -> VideoCoverLayer(
                    coverUrl = card.coverUrl,
                    imageLoader = imageLoader,
                    title = card.title,
                )
                ElementCardKind.Waveform -> WaveformLayer()
                ElementCardKind.CueOverlay -> CueOverlayLayer(
                    coverUrl = card.coverUrl,
                    imageLoader = imageLoader,
                    title = card.title,
                    kenBurnsPhase = if (card.id.contains("madrid")) 0.35f else 0f,
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            CardHud(
                tags = card.tags.ifEmpty { listOf(card.levelLabel) },
                kind = card.kind,
                bpmLabel = bpmLabel,
            )
            Spacer(modifier = Modifier.weight(1f))
            CenterPlay(
                kind = card.kind,
                title = card.title,
                isPlaying = isPlaying,
                onPlay = onPlay,
            )
            Spacer(modifier = Modifier.weight(1f))
            CardFooter(card = card)
        }
    }
}

@Composable
private fun VideoCoverLayer(
    coverUrl: String?,
    imageLoader: ImageLoader,
    title: String,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        KenBurnsCover(coverUrl = coverUrl, imageLoader = imageLoader, title = title)
        MotionVectorOverlay(modifier = Modifier.fillMaxSize())
        CoverScrim()
    }
}

@Composable
private fun CueOverlayLayer(
    coverUrl: String?,
    imageLoader: ImageLoader,
    title: String,
    kenBurnsPhase: Float,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        KenBurnsCover(
            coverUrl = coverUrl,
            imageLoader = imageLoader,
            title = title,
            phase = kenBurnsPhase,
        )
        FloatingCueDots()
        CoverScrim()
    }
}

@Composable
private fun WaveformLayer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0E0F16), Color(0xFF06070A), Color.Black),
                ),
            ),
    ) {
        DottedGrid(modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun KenBurnsCover(
    coverUrl: String?,
    imageLoader: ImageLoader,
    title: String,
    phase: Float = 0f,
) {
    val context = LocalContext.current
    val ken = rememberInfiniteTransition(label = "kenBurns")
    val t by ken.animateFloat(
        initialValue = 0f + phase,
        targetValue = 1f + phase,
        animationSpec = infiniteRepeatable(
            animation = tween(KenBurnsMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenT",
    )
    val scale = 1.02f + 0.10f * t.modOne()
    val tx = -0.02f * t.modOne()
    val ty = -0.01f * t.modOne()

    if (coverUrl.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0C0D12)),
        )
        return
    }

    // Decode only card-sized cover — never full 120 MB video frames into RAM (C-122).
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(coverUrl)
            .size(Size(512, 512))
            .crossfade(true)
            .build(),
        contentDescription = title,
        imageLoader = imageLoader,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = size.width * tx
                translationY = size.height * ty
            },
    )
}

private fun Float.modOne(): Float {
    val m = this % 1f
    return if (m < 0f) m + 1f else m
}

@Composable
private fun CoverScrim() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black),
                ),
            ),
    )
}

@Composable
private fun MotionVectorOverlay(modifier: Modifier = Modifier) {
    val flow = rememberInfiniteTransition(label = "vectorFlow")
    val phase by flow.animateFloat(
        initialValue = 0f,
        targetValue = 240f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dashPhase",
    )
    Canvas(modifier = modifier) {
        val sx = size.width / 100f
        val sy = size.height / 135f
        val primary = Path().apply {
            moveTo(15f * sx, 90f * sy)
            cubicTo(25f * sx, 60f * sy, 45f * sx, 40f * sy, 85f * sx, 55f * sy)
            cubicTo(60f * sx, 75f * sy, 40f * sx, 85f * sy, 20f * sx, 110f * sy)
        }
        val secondary = Path().apply {
            moveTo(80f * sx, 40f * sy)
            cubicTo(65f * sx, 65f * sy, 30f * sx, 75f * sy, 50f * sx, 115f * sy)
        }
        drawPath(
            path = primary,
            brush = Brush.linearGradient(
                colors = listOf(AtelierColors.Ember, AtelierColors.Glow, AtelierColors.Soft),
            ),
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(60f, 20f), phase),
            ),
            alpha = 0.85f,
        )
        drawPath(
            path = secondary,
            color = AtelierColors.Gold.copy(alpha = 0.40f),
            style = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
            ),
        )
    }
}

@Composable
private fun DottedGrid(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val step = 14.dp.toPx()
        var x = 2.dp.toPx()
        while (x < size.width) {
            var y = 2.dp.toPx()
            while (y < size.height) {
                drawCircle(
                    color = AtelierColors.Flame.copy(alpha = 0.20f),
                    radius = 0.8.dp.toPx(),
                    center = Offset(x, y),
                )
                y += step
            }
            x += step
        }
    }
}

@Composable
private fun FloatingCueDots() {
    val float = rememberInfiniteTransition(label = "cues")
    val y1 by float.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "c1",
    )
    val y2 by float.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(3100, delayMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "c2",
    )
    val y3 by float.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, delayMillis = 1300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "c3",
    )
    Box(modifier = Modifier.fillMaxSize()) {
        CueDot(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 72.dp, top = 86.dp)
                .offset(y = y1.dp),
            sizeDp = 12,
            fill = AtelierColors.Flame.copy(alpha = 0.70f),
        )
        CueDot(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = 12.dp, y = y2.dp),
            sizeDp = 10,
            fill = AtelierColors.Gold.copy(alpha = 0.50f),
        )
        CueDot(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 92.dp)
                .offset(y = y3.dp),
            sizeDp = 8,
            fill = AtelierColors.Flame.copy(alpha = 0.40f),
            showCore = false,
        )
    }
}

@Composable
private fun CueDot(
    modifier: Modifier,
    sizeDp: Int,
    fill: Color,
    showCore: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = 0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (showCore) {
            Box(
                modifier = Modifier
                    .size((sizeDp / 4).coerceAtLeast(2).dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun CardHud(
    tags: List<String>,
    kind: ElementCardKind,
    bpmLabel: String,
) {
    val beat = rememberInfiniteTransition(label = "hudBeat")
    val pulse by beat.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(tween(BeatPeriodMs), RepeatMode.Reverse),
        label = "hudPulse",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tags.take(1).forEach { tag ->
                val flameTag = tag.contains("Средн", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (flameTag) AtelierColors.Flame.copy(alpha = 0.30f)
                            else Color.Black.copy(alpha = 0.60f),
                        )
                        .border(
                            1.dp,
                            if (flameTag) AtelierColors.Flame.copy(alpha = 0.40f)
                            else Color.White.copy(alpha = 0.10f),
                            RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = tag.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                        color = if (flameTag) AtelierColors.Soft else Color.White.copy(alpha = 0.90f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (kind is ElementCardKind.VideoCover || kind is ElementCardKind.Waveform) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .clip(CircleShape)
                        .background(AtelierColors.Flame),
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.50f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = null,
                    tint = if (kind is ElementCardKind.CueOverlay) {
                        AtelierColors.Gold
                    } else {
                        Color.White.copy(alpha = 0.80f)
                    },
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun CenterPlay(
    kind: ElementCardKind,
    title: String,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    val playCd = stringResource(R.string.elements_play)
    val haptics = rememberMdHaptics()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .interactiveSpringPress(
                pressedScale = 0.92f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onPlay,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            GlassPlayButton(paused = true)
        } else {
            when (kind) {
                ElementCardKind.VideoCover ->
                    RadialPlaySpinner(contentDescription = "$playCd $title")
                ElementCardKind.Waveform -> WaveformVisualizer()
                ElementCardKind.CueOverlay -> GlassPlayButton(paused = false)
            }
        }
    }
}

@Composable
private fun RadialPlaySpinner(contentDescription: String) {
    val spin = rememberInfiniteTransition(label = "radial")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(RadialSpinMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rot",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .size(56.dp)
                .graphicsLayer { rotationZ = rotation },
        ) {
            val stroke = 2.dp.toPx()
            drawCircle(
                color = Color.White.copy(alpha = 0.15f),
                radius = size.minDimension / 2f - stroke,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = AtelierColors.Flame,
                startAngle = -90f,
                sweepAngle = 80f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = AtelierColors.Gold,
                startAngle = 40f,
                sweepAngle = 46f,
                useCenter = false,
                style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(AtelierColors.Ember, AtelierColors.Glow)),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun GlassPlayButton(paused: Boolean = false) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (paused) Icons.Filled.PlayArrow else Icons.Filled.PlayArrow,
            contentDescription = stringResource(R.string.elements_play),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun ElementInlineVideo(
    player: androidx.media3.exoplayer.ExoPlayer,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        modifier = modifier.background(Color(0xFF05060C)),
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                this.player = player
                setShutterBackgroundColor(android.graphics.Color.parseColor("#05060C"))
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { view ->
            if (view.player !== player) {
                view.player = player
            }
        },
    )
}

@Composable
private fun WaveformVisualizer() {
    val motion = rememberInfiniteTransition(label = "waves")
    val scales = List(9) { index ->
        motion.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 450 + index * 40,
                    delayMillis = index * 50,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "w$index",
        )
    }
    val palette = listOf(
        Color.White.copy(alpha = 0.20f),
        AtelierColors.Flame.copy(alpha = 0.50f),
        AtelierColors.Flame,
        AtelierColors.Gold,
        AtelierColors.Flame,
        Color.White.copy(alpha = 0.60f),
        AtelierColors.Flame,
        AtelierColors.Gold.copy(alpha = 0.70f),
        Color.White.copy(alpha = 0.30f),
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.40f))
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            scales.forEachIndexed { index, anim ->
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = (48f * anim.value).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette[index]),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.elements_kinetic_rhythm),
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 1.4.sp,
            ),
            color = Color.White.copy(alpha = 0.50f),
        )
    }
}

@Composable
private fun CardFooter(card: ElementCard) {
    val mastered = card.masteryPercent >= 100
    val waiting = card.masteryPercent <= 0
    val inProgress = card.masteryPercent in 1..99
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FooterShape)
            .background(AtelierColors.CardScrim)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = FooterShape,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (mastered) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = when {
                        mastered -> stringResource(R.string.elements_mastered, card.masteryPercent)
                        inProgress -> stringResource(R.string.elements_in_progress, card.masteryPercent)
                        else -> stringResource(R.string.elements_mastered, card.masteryPercent)
                    },
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = when {
                        mastered -> AtelierColors.Flame
                        inProgress -> AtelierColors.Gold
                        else -> Color.White.copy(alpha = 0.40f)
                    },
                )
            }
            Text(
                text = if (waiting && card.durationLabel == "—") {
                    stringResource(R.string.elements_awaiting_recording)
                } else {
                    card.durationLabel
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                ),
                color = if (waiting && card.durationLabel == "—") {
                    AtelierColors.Gold
                } else {
                    Color.White.copy(alpha = 0.40f)
                },
            )
        }
        Text(
            text = card.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MdFonts.Display,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        MasteryBar(percent = card.masteryPercent)
    }
}

@Composable
private fun MasteryBar(percent: Int) {
    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shift by shimmer.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sh",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        if (percent > 0) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent / 100f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                AtelierColors.Flame,
                                AtelierColors.Gold,
                                AtelierColors.Flame,
                            ),
                            startX = -200f + 400f * shift,
                            endX = 200f + 400f * shift,
                        ),
                    ),
            )
        }
    }
}
