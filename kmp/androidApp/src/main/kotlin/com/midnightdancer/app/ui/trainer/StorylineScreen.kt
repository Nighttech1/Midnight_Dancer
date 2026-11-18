package com.midnightdancer.app.ui.trainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.components.MdFieldLabel
import com.midnightdancer.app.ui.components.MdPageTitle
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
import com.midnightdancer.core.designsystem.NoOpHapticFeedback
import com.midnightdancer.core.tts.TrainerVoice
import com.midnightdancer.feature.trainer.logic.StorylineStep
import com.midnightdancer.feature.trainer.logic.TrainerIntent
import com.midnightdancer.feature.trainer.logic.TrainerMode
import com.midnightdancer.feature.trainer.logic.TrainerUiState

private val CardShape = RoundedCornerShape(16.dp)
private val Hairline = Color.White.copy(alpha = 0.12f)
private val GreyDance = Color.White.copy(alpha = 0.18f)
/** Small gap between Dance CTA and CyberAcousticDock (C-99). */
private val DanceCtaClearance = 12.dp
private val DanceCtaHeightPad = 92.dp

/**
 * Stitch storyline — OLED accordion deck (C-94 / C-192).
 * Filled steps: bright Flame perimeter glow; Dance CTA flush above dock.
 * Pickers use real Room catalogs from [TrainerUiState] — no mock lists.
 */
@Composable
fun StorylineScreen(
    state: TrainerUiState,
    onIntent: (TrainerIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    isTabSelected: Boolean = true,
) {
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val softFling = rememberSoftGridFlingBehavior()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oled),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(280.dp)
                .graphicsLayer { alpha = 0.20f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AtelierColors.Flame.copy(alpha = 0.40f), Color.Transparent),
                    ),
                ),
        )

        // C-229: sticky title + gear (parity with Elements/Music); accordion scrolls under.
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MdPageTitle(
                    text = state.title,
                    modifier = Modifier.weight(1f),
                )
                MdSettingsGear(
                    onClick = onOpenSettings,
                    haptics = haptics,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = DanceCtaHeightPad,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                flingBehavior = softFling,
            ) {
                items(
                    items = state.storylineSteps,
                    key = { it.index },
                ) { step ->
                    AccordionCard(
                        step = step,
                        isTabSelected = isTabSelected,
                        expanded = state.expandedStep == step.index,
                        state = state,
                        onExpand = { onIntent(TrainerIntent.SetExpandedStep(step.index)) },
                        onIntent = onIntent,
                        haptics = haptics,
                    )
                }
            }
        }

        DanceButton(
            ready = state.readyPercent >= 100,
            onClick = { onIntent(TrainerIntent.StartDance) },
            haptics = haptics,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = DanceCtaClearance)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun ModeToggle(
    mode: TrainerMode,
    onMode: (TrainerMode) -> Unit,
    haptics: HapticFeedback,
) {
    // C-265: restore dual Freestyle/Choreography chips (not SearchablePicker).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6121319))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModeChip(
            label = "Фристайл",
            active = mode == TrainerMode.Freestyle,
            modifier = Modifier.weight(1f),
            haptics = haptics,
            onClick = { onMode(TrainerMode.Freestyle) },
        )
        ModeChip(
            label = "Хореография",
            active = mode == TrainerMode.Choreography,
            modifier = Modifier.weight(1f),
            haptics = haptics,
            onClick = { onMode(TrainerMode.Choreography) },
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    active: Boolean,
    modifier: Modifier,
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (active) {
                    Modifier.background(
                        Brush.horizontalGradient(
                            listOf(AtelierColors.Flame, AtelierColors.Glow),
                        ),
                    )
                } else {
                    Modifier.background(Color.Transparent)
                },
            )
            .interactiveSpringPress(
                hapticEvent = HapticEvent.Selection,
                haptics = NoOpHapticFeedback,
                onClick = onClick,
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontFamily = MdFonts.Display,
            fontSize = 14.sp,
        )
    }
    @Suppress("UNUSED_PARAMETER")
    val unused = haptics
}

@Composable
private fun AccordionCard(
    step: StorylineStep,
    isTabSelected: Boolean,
    expanded: Boolean,
    state: TrainerUiState,
    onExpand: () -> Unit,
    onIntent: (TrainerIntent) -> Unit,
    haptics: HapticFeedback,
) {
    val isChoreo = state.mode == TrainerMode.Choreography
    // C-273: choreography mode = 01 mode · 02 tag+choreo · 03 voice
    val voiceStepIndex = if (isChoreo) 3 else 4

    val summary = when {
        step.index == 1 ->
            if (state.mode == TrainerMode.Freestyle) "«Фристайл»" else "«Хореография»"
        isChoreo && step.index == 2 ->
            listOf(state.choreographyTag, state.selectedChoreographyTitle)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { step.subtitle }
        !isChoreo && step.index == 2 ->
            listOf(state.styleName, state.trackTitle)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { step.subtitle }
        !isChoreo && step.index == 3 -> {
            val tag = state.elementTagLabel.ifBlank { state.levelLabel }
            val interval = if (state.intervalSec > 0f) {
                "${state.intervalSec.toInt()} с"
            } else {
                ""
            }
            listOf(tag, interval)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { step.subtitle }
        }
        step.index == voiceStepIndex ->
            if (state.voiceName.isNotBlank()) "«${state.voiceName}»" else step.subtitle
        else -> step.subtitle
    }

    val filled = when {
        step.index == 1 -> true
        isChoreo && step.index == 2 ->
            state.selectedChoreographyId.isNotBlank()
        !isChoreo && step.index == 2 ->
            state.styleName.isNotBlank() && state.trackTitle.isNotBlank()
        !isChoreo && step.index == 3 ->
            state.elementTagLabel.isNotBlank() && state.intervalSec > 0f
        step.index == voiceStepIndex -> state.voiceName.isNotBlank()
        else -> false
    }

    val indexLabel = step.index.toString().padStart(2, '0')

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (filled) {
                    Modifier.shadow(
                        elevation = 12.dp,
                        shape = CardShape,
                        ambientColor = AtelierColors.Flame,
                        spotColor = AtelierColors.Flame,
                    )
                } else {
                    Modifier
                },
            )
            .clip(CardShape)
            // C-253: same plate as SearchablePicker (0xE6121319) — not near-OLED 4% white slab.
            .background(Color(0xE6121319))
            .border(
                width = if (filled) 2.dp else 1.dp,
                color = if (filled) AtelierColors.Flame else Hairline,
                shape = CardShape,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .interactiveSpringPress(
                    hapticEvent = HapticEvent.LightImpact,
                    // Accordion header: silent (no LightImpact buzz on expand/collapse).
                    haptics = NoOpHapticFeedback,
                    onClick = onExpand,
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = indexLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = MdFonts.Display,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (expanded) AtelierColors.Flame else Color.White.copy(alpha = 0.10f),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Column {
                    Text(
                        text = step.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp,
                    )
                    Text(
                        text = summary,
                        color = Color.White.copy(alpha = 0.50f),
                        fontSize = 10.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
            }
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .size(20.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.06f)),
                )
                when {
                    step.index == 1 -> ModeToggle(
                        mode = state.mode,
                        onMode = { onIntent(TrainerIntent.SetMode(it)) },
                        haptics = haptics,
                    )
                    isChoreo && step.index == 2 -> ChoreographyPickSection(
                        state = state,
                        isTabSelected = isTabSelected,
                        onIntent = onIntent,
                    )
                    !isChoreo && step.index == 2 -> StyleTrackSection(
                        state = state,
                        onIntent = onIntent,
                        haptics = haptics,
                    )
                    !isChoreo && step.index == 3 -> ElementsIntervalSection(
                        state = state,
                        onIntent = onIntent,
                        haptics = haptics,
                    )
                    step.index == voiceStepIndex -> VoiceSection(
                        voiceName = state.voiceName,
                        selectedIndex = state.selectedVoiceIndex,
                        onVoice = { onIntent(TrainerIntent.SelectVoice(it)) },
                        haptics = haptics,
                    )
                }
            }
        }
    }
}

/** C-273: choreography mode step 2 — tag then choreography SearchablePickers. */
@Composable
private fun ChoreographyPickSection(
    state: TrainerUiState,
    isTabSelected: Boolean,
    onIntent: (TrainerIntent) -> Unit,
) {
    var openPicker by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchablePicker(
            label = "Тег",
            value = state.choreographyTag.ifBlank { "Все теги" },
            options = state.tagOptions.ifEmpty {
                listOf(com.midnightdancer.feature.trainer.logic.TrainerSessionFactory.TAG_ALL)
            },
            expanded = openPicker == "tag",
            onExpandedChange = { open ->
                openPicker = if (open) "tag" else null
            },
            onSelect = {
                onIntent(TrainerIntent.SetChoreographyTag(it))
                openPicker = null
            },
            panelMaxHeight = 200.dp,
        )
        SearchablePicker(
            label = "Хореография",
            value = state.selectedChoreographyTitle,
            options = state.choreographyOptions,
            expanded = openPicker == "choreo",
            onExpandedChange = { open ->
                // Refresh titles from vault when opening.
                if (open) onIntent(TrainerIntent.SetChoreographyTag(state.choreographyTag.ifBlank { "Все теги" }))
                openPicker = if (open) "choreo" else null
            },
            onSelect = {
                onIntent(TrainerIntent.SetSelectedChoreography(it))
                openPicker = null
            },
            panelMaxHeight = 240.dp,
        )
        RehearsalEntry(state, isTabSelected)
    }
}

@Composable
private fun StyleTrackSection(
    state: TrainerUiState,
    onIntent: (TrainerIntent) -> Unit,
    haptics: HapticFeedback,
) {
    var openPicker by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchablePicker(
            label = "Стиль",
            value = state.styleName,
            options = state.styleOptions,
            expanded = openPicker == "style",
            onExpandedChange = { open ->
                openPicker = if (open) "style" else null
            },
            onSelect = { onIntent(TrainerIntent.SetStyle(it)) },
            panelMaxHeight = 220.dp,
        )
        SearchablePicker(
            label = "Трек",
            value = state.trackTitle,
            options = state.trackOptions,
            expanded = openPicker == "track",
            onExpandedChange = { open ->
                openPicker = if (open) "track" else null
            },
            onSelect = { onIntent(TrainerIntent.SetTrack(it)) },
            panelMaxHeight = 220.dp,
        )
    }
    @Suppress("UNUSED_PARAMETER")
    val unusedHaptics = haptics
}

@Composable
private fun ElementsIntervalSection(
    state: TrainerUiState,
    onIntent: (TrainerIntent) -> Unit,
    haptics: HapticFeedback,
) {
    var tagsExpanded by remember { mutableStateOf(false) }
    val interval = state.intervalSec.coerceIn(2f, 15f).let { if (it <= 0f) 8f else it }
    val useTiming = state.useElementTiming
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SearchablePicker(
            label = "Тег",
            value = state.elementTagLabel.ifBlank { "Выбрать…" },
            options = state.tagOptions.ifEmpty {
                listOf(com.midnightdancer.feature.trainer.logic.TrainerSessionFactory.TAG_ALL)
            },
            expanded = tagsExpanded,
            onExpandedChange = { tagsExpanded = it },
            onSelect = { onIntent(TrainerIntent.SetElementTag(it)) },
            panelMaxHeight = 200.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptics.perform(HapticEvent.Selection)
                    onIntent(TrainerIntent.SetUseElementTiming(!useTiming))
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Single toggle path — Checkbox must not also fire (double-toggle).
            Checkbox(
                checked = useTiming,
                onCheckedChange = null,
                colors = CheckboxDefaults.colors(
                    checkedColor = AtelierColors.Flame,
                    uncheckedColor = Color.White.copy(alpha = 0.35f),
                    checkmarkColor = Color.Black,
                ),
            )
            Text(
                text = "Учесть продолжительность элементов",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
        }
        // C-229: interval always editable — fallback duration for elements without video
        // (and sole spacing when checkbox is OFF).
        MdFieldLabel(text = "Интервал между элементами")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Slider(
                value = interval,
                onValueChange = {
                    onIntent(TrainerIntent.SetInterval(it.toInt().coerceIn(2, 15).toFloat()))
                },
                enabled = true,
                valueRange = 2f..15f,
                steps = 12,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = AtelierColors.Flame,
                    activeTrackColor = AtelierColors.Flame,
                    inactiveTrackColor = Color.White.copy(alpha = 0.15f),
                ),
            )
            Text(
                text = "${interval.toInt()} с",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(48.dp),
            )
        }
        Text(
            text = "Если в карточке элемента нет видео, его продолжительность принимается по интервалу между элементами",
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            fontFamily = MdFonts.Display,
        )
    }
}

@Composable
private fun VoiceSection(
    voiceName: String,
    selectedIndex: Int,
    onVoice: (TrainerVoice) -> Unit,
    haptics: HapticFeedback,
) {
    val voices = TrainerVoice.fullSet
    // C-265: restore multi voice chips (Руслан / Ирина / Камила).
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MdFieldLabel(text = "Голос")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            voices.forEachIndexed { index, voice ->
                val active = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (active) {
                                Brush.horizontalGradient(
                                    listOf(AtelierColors.Flame, AtelierColors.Glow),
                                )
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color(0xE6121319), Color(0xE6121319)),
                                )
                            },
                        )
                        .border(
                            1.dp,
                            if (active) AtelierColors.Flame.copy(alpha = 0.55f) else Hairline,
                            RoundedCornerShape(12.dp),
                        )
                        .interactiveSpringPress(
                            hapticEvent = HapticEvent.Selection,
                            haptics = NoOpHapticFeedback,
                            onClick = { onVoice(voice) },
                        )
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = voice.displayNameRu,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = MdFonts.Display,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
        @Suppress("UNUSED_PARAMETER")
        val unusedName = voiceName
        @Suppress("UNUSED_PARAMETER")
        val unusedHaptics = haptics
    }
}

@Composable
private fun DanceButton(
    ready: Boolean,
    onClick: () -> Unit,
    haptics: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val shimmer = rememberInfiniteTransition(label = "dance-shimmer")
    val shineX by shimmer.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dance-shine-x",
    )

    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (ready) {
                    Modifier
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
                            hapticEvent = HapticEvent.MediumImpact,
                            haptics = haptics,
                            onClick = onClick,
                        )
                } else {
                    Modifier.background(GreyDance)
                },
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = if (ready) Color.White else Color.White.copy(alpha = 0.40f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Танцуем!",
                color = if (ready) Color.White else Color.White.copy(alpha = 0.40f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )
        }
    }
}
