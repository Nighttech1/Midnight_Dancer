package com.midnightdancer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.motion.MdMotion
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.ShellChromeTokens
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(20.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

/** Strip "(stub)" / "(stub — …)" noise from Stage 3 labels for premium chrome. */
fun String.asPremiumLabel(): String =
    replace(Regex("""\s*\(stub(?:\s*[—\-].*?)?\)""", RegexOption.IGNORE_CASE), "")
        .trim()
        .ifEmpty { this }

val NeonAccentCycle: List<Long> = listOf(
    ColorTokens.NeonOrange,
    ColorTokens.NeonCyan,
    ColorTokens.NeonMagenta,
    ColorTokens.NeonLime,
)

@Composable
fun AccentHairline(
    accent: Long,
    modifier: Modifier = Modifier,
) {
    val accentColor = accent.toComposeColor()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        accentColor.copy(alpha = 0.85f),
                        accentColor,
                        accentColor.copy(alpha = 0.85f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}

@Composable
fun NeonPulseBars(
    accent: Long,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "neonEq")
    val accentColor = accent.toComposeColor()
    val heights = List(5) { index ->
        transition.animateFloat(
            initialValue = 0.32f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 480 + index * 110,
                    easing = FastOutSlowInEasing,
                    delayMillis = index * 40,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "barH$index",
        )
    }
    Row(
        modifier = modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        heights.forEach { anim ->
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height((28f * anim.value).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor.copy(alpha = 0.5f + 0.4f * anim.value)),
            )
        }
    }
}

@Composable
fun ShellEnter(
    modifier: Modifier = Modifier,
    fillMax: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(280, easing = FastOutSlowInEasing)) +
            slideInVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 16 },
            ),
        modifier = modifier,
    ) {
        Column(
            modifier = if (fillMax) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
fun NeonCard(
    accent: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val accentColor = accent.toComposeColor()
    val haptics = rememberMdHaptics()
    val base = Modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(ColorTokens.SurfaceElevated.toComposeColor())
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = 0.55f),
                    ColorTokens.SurfaceBorder.toComposeColor(),
                    accentColor.copy(alpha = 0.25f),
                ),
            ),
            shape = CardShape,
        )
        .interactiveSpringPress(
            enabled = onClick != null,
            pressedScale = 0.97f,
            hapticEvent = HapticEvent.LightImpact,
            haptics = haptics,
            onClick = onClick,
        )
        .padding(horizontal = 16.dp, vertical = 14.dp)

    Column(modifier = modifier.then(base), content = content)
}

@Composable
fun StaggeredNeonList(
    items: List<String>,
    emptyLabel: String,
    accent: Long = ColorTokens.NeonOrange,
    modifier: Modifier = Modifier,
    leadingMeta: ((index: Int, label: String) -> String)? = null,
    onItemClick: ((index: Int, label: String) -> Unit)? = null,
) {
    if (items.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyLabel,
                style = MaterialTheme.typography.bodyLarge,
                color = ColorTokens.TextSecondary.toComposeColor(),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { index, label -> "$index-$label" },
            contentType = { _, _ -> "neon_row" },
        ) { index, raw ->
            val label = raw.asPremiumLabel()
            val rowAccent = NeonAccentCycle[index % NeonAccentCycle.size]
            StaggeredItem(index = index) {
                NeonCard(
                    accent = rowAccent,
                    onClick = onItemClick?.let { handler -> { handler(index, label) } },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(rowAccent.toComposeColor()),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.titleMedium,
                                color = ColorTokens.TextPrimary.toComposeColor(),
                                fontWeight = FontWeight.SemiBold,
                            )
                            val meta = leadingMeta?.invoke(index, label)
                            if (meta != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = meta,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ColorTokens.TextSecondary.toComposeColor(),
                                )
                            }
                        }
                        Text(
                            text = "›",
                            style = MaterialTheme.typography.titleLarge,
                            color = accent.toComposeColor().copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun StaggeredItem(
    index: Int,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(12f) }
    LaunchedEffect(index) {
        delay(index * 45L)
        alpha.animateTo(1f, MdMotion.interactiveUnderdamped)
        offsetY.animateTo(0f, MdMotion.interactiveUnderdamped)
    }
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            translationY = offsetY.value
        },
    ) {
        content()
    }
}

@Composable
fun NeonActionButton(
    label: String,
    accent: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    /** Solid orange pill + white label (premium CTA — Settings / Skip parity). */
    solidFill: Boolean = false,
    contentDescription: String? = null,
) {
    val accentColor = accent.toComposeColor()
    val haptics = rememberMdHaptics()
    val bg = when {
        solidFill -> accentColor
        filled -> accentColor.copy(alpha = 0.18f)
        else -> Color.Transparent
    }
    val borderColor = when {
        solidFill -> accentColor
        else -> accentColor.copy(alpha = if (filled) 0.7f else 0.45f)
    }
    val labelColor = if (solidFill) Color.White else accentColor
    Box(
        modifier = modifier
            .heightInMinTouch()
            .clip(ButtonShape)
            .background(bg)
            .border(1.dp, borderColor, ButtonShape)
            .interactiveSpringPress(
                pressedScale = 0.94f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            )
            .semantics {
                this.contentDescription = contentDescription ?: label
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun Modifier.heightInMinTouch(): Modifier =
    heightIn(min = ShellChromeTokens.MinTouchTargetDp.dp)

/**
 * Trainer speak zone — Voice TTS (C-33) owns speak Intent; chrome may be polished by Motion.
 * [selectedVoiceIndex]: 0 Руслан / 1 Ирина / 2 Камила ([TrainerVoice] ordinal).
 */
@Composable
fun TrainerSpeakZone(
    speakLabel: String,
    speakCount: Int,
    selectedVoiceIndex: Int,
    onVoiceSelected: (Int) -> Unit,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val voices = listOf(
        Triple("Руслан", ColorTokens.NeonOrange, "Ruslan"),
        Triple("Ирина", ColorTokens.NeonCyan, "Irina"),
        Triple("Камила", ColorTokens.NeonMagenta, "Kamila"),
    )
    val safeIndex = selectedVoiceIndex.coerceIn(0, voices.lastIndex)
    val accent = voices[safeIndex].second

    NeonCard(accent = accent, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Голос тренера",
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorTokens.TextPrimary.toComposeColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (speakCount > 0) {
                        speakLabel.ifBlank { "Озвучка · запросов: $speakCount" }
                    } else {
                        "Руслан / Ирина / Камила · Piper или System TTS"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTokens.TextSecondary.toComposeColor(),
                )
            }
            NeonPulseBars(accent = accent)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val haptics = rememberMdHaptics()
            voices.forEachIndexed { index, (ru, color, en) ->
                val selected = index == safeIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightInMinTouch()
                        .clip(ChipShape)
                        .background(
                            if (selected) color.toComposeColor().copy(alpha = 0.22f)
                            else ColorTokens.OledTrueBlack.toComposeColor(),
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) {
                                color.toComposeColor()
                            } else {
                                ColorTokens.SurfaceBorder.toComposeColor()
                            },
                            shape = ChipShape,
                        )
                        .interactiveSpringPress(
                            pressedScale = 0.95f,
                            hapticEvent = HapticEvent.Selection,
                            haptics = haptics,
                            onClick = { onVoiceSelected(index) },
                        )
                        .semantics { contentDescription = "Voice $en" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ru,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            color.toComposeColor()
                        } else {
                            ColorTokens.TextSecondary.toComposeColor()
                        },
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        NeonActionButton(
            label = speakLabel.ifBlank { "Speak · ${voices[safeIndex].first}" },
            accent = accent,
            onClick = onSpeak,
            filled = true,
            contentDescription = speakLabel.asPremiumLabel().ifBlank { "Speak" },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    accent: Long,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NeonCard(accent = accent, onClick = onToggle, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = ColorTokens.TextPrimary.toComposeColor(),
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ColorTokens.TextSecondary.toComposeColor(),
                )
            }
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .clip(ChipShape)
                    .background(
                        if (enabled) {
                            accent.toComposeColor().copy(alpha = 0.35f)
                        } else {
                            ColorTokens.SurfaceBorder.toComposeColor()
                        },
                    )
                    .border(
                        1.dp,
                        if (enabled) accent.toComposeColor() else ColorTokens.SurfaceBorder.toComposeColor(),
                        ChipShape,
                    )
                    .padding(3.dp),
                contentAlignment = if (enabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (enabled) accent.toComposeColor() else ColorTokens.TextSecondary.toComposeColor(),
                        ),
                )
            }
        }
    }
}

@Composable
fun LanguageChipRow(
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        "ru" to "Русский",
        "en" to "English",
        "es" to "Español",
    )
    val haptics = rememberMdHaptics()
    // C-61: all Settings CTAs = solid Flame + white label (no outline-only chips).
    val flame = ColorTokens.NeonOrange.toComposeColor()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (code, label) ->
            val selected = code == selectedCode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightInMinTouch()
                    .clip(ChipShape)
                    .background(flame)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) Color.White.copy(alpha = 0.55f) else flame,
                        shape = ChipShape,
                    )
                    .interactiveSpringPress(
                        pressedScale = 0.95f,
                        hapticEvent = HapticEvent.Selection,
                        haptics = haptics,
                        onClick = { onSelect(code) },
                    )
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
            }
        }
    }
}
