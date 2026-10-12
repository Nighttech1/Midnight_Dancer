package com.midnightdancer.app.ui.exchange

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.exchange.logic.ExchangeIntent
import com.midnightdancer.feature.exchange.logic.ExchangeUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CardShape = RoundedCornerShape(18.dp)
private val DisabledGray = Color(0xFF5A5A5A)

/**
 * Stitch kinetic sync deck (C-94) — MdPageTitle + Flame hologram action cards.
 * C-109: disk free/required + gray disabled «Загрузить» when free < required×1.20.
 */
@Composable
fun KineticSyncDeckScreen(
    state: ExchangeUiState,
    onIntent: (ExchangeIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val softFling = rememberSoftGridFlingBehavior()
    val importEnabled = state.importEnabled && !state.busy
    val raiseHard = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current

    LaunchedEffect(Unit) {
        while (isActive) {
            onIntent(ExchangeIntent.RefreshDiskGate)
            delay(10_000L)
        }
    }

    if (state.showExportDialog) {
        ExportPackageDialog(
            limitsText = state.exportLimitsText,
            styles = state.exportCatalog.styles,
            styleId = state.exportStyleId,
            styleName = state.exportStyleName,
            selectedMoves = state.exportSelectedMoveIds.size,
            selectedSongs = state.exportSelectedSongIds.size,
            selectedChoreos = state.exportSelectedChoreoIds.size,
            onStyleSelect = { onIntent(ExchangeIntent.SelectExportStyle(it)) },
            onOpenPick = { onIntent(ExchangeIntent.OpenExportPick(it)) },
            onConfirm = { onIntent(ExchangeIntent.ConfirmExport) },
            onDismiss = { onIntent(ExchangeIntent.DismissExportDialog) },
        )
    }

    if (state.showImportParams) {
        ImportParamsDialog(
            localStyles = state.importLocalStyles,
            packageStyles = state.importPackageStyles,
            targetStyleId = state.importTargetStyleId,
            onTargetStyleChange = { onIntent(ExchangeIntent.SetImportTargetStyle(it)) },
            onConfirm = {
                if (!importEnabled) {
                    raiseHard?.invoke(
                        com.midnightdancer.app.ui.components.HARD_DISK_BANNER_TEXT,
                    )
                    onIntent(ExchangeIntent.DismissImportParams)
                } else {
                    onIntent(ExchangeIntent.ConfirmImport)
                }
            },
            onDismiss = { onIntent(ExchangeIntent.DismissImportParams) },
        )
    }

    // Full-screen section picker (Elements / Music / Choreography)
    val pickKind = state.exportPickKind
    if (pickKind != null) {
        val items =
            when (pickKind) {
                com.midnightdancer.core.domain.exchange.ExportPickKind.Elements ->
                    state.exportCatalog.elementsForStyle(state.exportStyleId)
                com.midnightdancer.core.domain.exchange.ExportPickKind.Songs ->
                    state.exportCatalog.songsForStyle(state.exportStyleId)
                com.midnightdancer.core.domain.exchange.ExportPickKind.Choreographies ->
                    state.exportCatalog.choreosForStyle(state.exportStyleId)
            }
        ExportSectionPickerScreen(
            kind = pickKind,
            styleName = state.exportStyleName,
            items = items,
            selectedIds = state.exportPickDraftIds,
            onToggle = { onIntent(ExchangeIntent.ToggleExportPickItem(it)) },
            onConfirm = { onIntent(ExchangeIntent.ConfirmExportPick) },
            onDismiss = { onIntent(ExchangeIntent.DismissExportPick) },
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(oled),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(220.dp)
                .graphicsLayer { alpha = 0.20f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(AtelierColors.Flame.copy(alpha = 0.35f), Color.Transparent),
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
            item(key = "ex-header") {
                MdPageTitle(text = state.title)
            }
            item(key = "ex-share") {
                HologramCard(
                    title = "Поделиться",
                    subtitle = "Отправь другу элементы, музыку, хореографии (.mdance)",
                    accent = AtelierColors.Flame,
                    icon = Icons.Filled.Share,
                    haptics = haptics,
                    enabled = !state.busy,
                    onClick = { onIntent(ExchangeIntent.ExportStub) },
                )
            }
            item(key = "ex-import") {
                HologramCard(
                    title = "Загрузить",
                    subtitle =
                        if (importEnabled) {
                            "Добавить элементы, музыку, хореографии"
                        } else {
                            "Мало места · нужно ${state.requiredLabel} (+20%), свободно ${state.freeLabel}"
                        },
                    accent = if (importEnabled) AtelierColors.Flame else DisabledGray,
                    icon = Icons.Filled.Upload,
                    haptics = haptics,
                    enabled = importEnabled,
                    onClick = {
                        if (!importEnabled) {
                            raiseHard?.invoke(
                                com.midnightdancer.app.ui.components.HARD_DISK_BANNER_TEXT,
                            )
                        } else {
                            onIntent(ExchangeIntent.ImportStub)
                        }
                    },
                )
            }
            item(key = "ex-rollback") {
                HologramCard(
                    title = "Откатить",
                    subtitle = "Удалить последний загруженный пакет",
                    detail = state.lastImportName.takeIf { it.isNotBlank() },
                    accent = AtelierColors.Flame,
                    icon = Icons.Filled.Refresh,
                    haptics = haptics,
                    enabled = !state.busy,
                    onClick = { onIntent(ExchangeIntent.RollbackStub) },
                )
            }
            if (state.lastActionLabel != null) {
                item(key = "ex-last") {
                    Text(
                        text = "LAST · ${state.lastActionLabel}",
                        color = AtelierColors.Soft,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HologramCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector,
    haptics: HapticFeedback,
    onClick: () -> Unit,
    detail: String? = null,
    enabled: Boolean = true,
) {
    val titleColor = if (enabled) Color.White else DisabledGray
    val subColor = if (enabled) Color.White.copy(alpha = 0.55f) else DisabledGray.copy(alpha = 0.85f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = if (enabled) 0.16f else 0.06f),
                        Color(0xCC0A0B10),
                    ),
                ),
            )
            .border(1.dp, accent.copy(alpha = if (enabled) 0.35f else 0.20f), CardShape)
            .then(
                if (enabled) {
                    Modifier.interactiveSpringPress(
                        hapticEvent = HapticEvent.MediumImpact,
                        haptics = haptics,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = if (enabled) 0.18f else 0.08f))
                .border(1.dp, accent.copy(alpha = if (enabled) 0.40f else 0.18f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.uppercase(),
                color = titleColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = subColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )
            if (!detail.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = detail,
                    color = accent.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(accent, CircleShape),
        )
    }
}
