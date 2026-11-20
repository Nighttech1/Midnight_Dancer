package com.midnightdancer.app.ui.exchange

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.components.FlameShimmerCta
import com.midnightdancer.app.ui.components.MdPageTitle
import com.midnightdancer.app.ui.components.MdSettingsGear
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.exchange.logic.ExchangeIntent
import com.midnightdancer.feature.exchange.logic.ExchangeUiState
import com.midnightdancer.feature.exchange.logic.SetHubTab
import com.midnightdancer.feature.exchange.logic.SetsSortMode
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val Hairline = Color.White.copy(alpha = 0.10f)

/**
 * Sets hub — Мои | Добавленные, catalog chrome, Vault cards, system share (C-227).
 */
@Composable
fun KineticSyncDeckScreen(
    state: ExchangeUiState,
    onIntent: (ExchangeIntent) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptics = rememberMdHaptics()
    val oled = ColorTokens.OledTrueBlack.toComposeColor()
    val importEnabled = state.importEnabled && !state.busy
    val raiseHard = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current
    val latestOnIntent by rememberUpdatedState(onIntent)

    val pickImport = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull().orEmpty()
        val lower = name.lowercase()
        if (!lower.endsWith(".dance") && !lower.endsWith(".mdance") && lower.isNotBlank()) {
            raiseHard?.invoke("Выберите файл сета .dance")
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        latestOnIntent(
            ExchangeIntent.ImportPackagePicked(
                contentUri = uri.toString(),
                displayName = name.ifBlank { "import.dance" },
            ),
        )
    }

    LaunchedEffect(state.operationError) {
        state.operationError?.let { raiseHard?.invoke(it) }
    }

    LaunchedEffect(state.lastActionLabel) {
        val msg = state.lastActionLabel ?: return@LaunchedEffect
        if (msg.contains("успешно импортирован", ignoreCase = true)) {
            raiseHard?.invoke(msg)
        }
        // C-289: empty-set share → bottom warn, never open chooser.
        if (msg == com.midnightdancer.feature.exchange.logic.EMPTY_SET_SHARE_WARN_RU) {
            raiseHard?.invoke(msg)
        }
    }

    LaunchedEffect(state.pendingSharePath) {
        val path = state.pendingSharePath ?: return@LaunchedEffect
        val src = File(path)
        if (!src.isFile) {
            latestOnIntent(ExchangeIntent.ClearPendingShare)
            raiseHard?.invoke("Файл сета не найден")
            return@LaunchedEffect
        }
        val shareName =
            state.pendingShareFileName.ifBlank { src.name }.let { n ->
                when {
                    n.endsWith(".dance", ignoreCase = true) -> n
                    n.endsWith(".mdance", ignoreCase = true) -> n.dropLast(7) + ".dance"
                    else -> "$n.dance"
                }
            }
        val shareDir = File(context.cacheDir, "sets_share").apply { mkdirs() }
        val shareFile = File(shareDir, shareName)
        runCatching {
            src.copyTo(shareFile, overwrite = true)
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    shareFile,
                )
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, shareName)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = android.content.ClipData.newUri(
                        context.contentResolver,
                        shareName,
                        uri,
                    )
                }
            context.startActivity(Intent.createChooser(send, "Поделиться сетом"))
        }.onFailure {
            raiseHard?.invoke("Не удалось открыть «Поделиться»")
        }
        latestOnIntent(ExchangeIntent.ClearPendingShare)
    }

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
            selectedMoves = if (state.viewingImportedSet) state.archiveInspectionCatalog?.elements?.size ?: 0 else state.exportSelectedMoveIds.size,
            selectedSongs = if (state.viewingImportedSet) state.archiveInspectionCatalog?.songs?.size ?: 0 else state.exportSelectedSongIds.size,
            selectedChoreos = if (state.viewingImportedSet) state.archiveInspectionCatalog?.choreographies?.size ?: 0 else state.exportSelectedChoreoIds.size,
            weightMb = state.exportWeightMb,
            overweight = state.exportOverWeight,
            onStyleSelect = { onIntent(ExchangeIntent.SelectExportStyle(it)) },
            onOpenPick = { onIntent(ExchangeIntent.OpenExportPick(it)) },
            onConfirm = { onIntent(ExchangeIntent.ConfirmExport) },
            onDismiss = { onIntent(ExchangeIntent.DismissExportDialog) },
            isEditing = state.isEditingSet,
            validationError = state.exportValidationError,
            isBusy = state.busy,
            catalogLoading = state.exportCatalogLoading,
            readOnly = state.viewingImportedSet,
            styleLocked = state.exportStyleLocked,
            archiveLoading = state.archiveInspectionLoading,
            archiveError = state.archiveInspectionError,
            archiveReady = state.archiveInspectionCatalog != null && !state.archiveInspectionLoading && state.archiveInspectionError == null,
            onRetryArchive = { onIntent(ExchangeIntent.RetryArchiveInspection) },
            invalidSelectionCount = state.invalidExportSelectionCount,
            onRemoveInvalidSelection = { onIntent(ExchangeIntent.RemoveInvalidExportSelection) },
            setName = state.exportSetName,
            onSetNameChange = { onIntent(ExchangeIntent.SetExportSetName(it)) },
        )
    }

    if (state.showImportParams) {
        ImportParamsDialog(
            localStyles = state.importLocalStyles,
            packageStyles = state.importPackageStyles,
            targetStyleId = state.importTargetStyleId,
            onTargetStyleChange = { onIntent(ExchangeIntent.SetImportTargetStyle(it)) },
            onCreateNewStyle = { onIntent(ExchangeIntent.CreateImportTargetStyle) },
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

    if (state.showShareSetPicker) {
        AlertDialog(
            onDismissRequest = { onIntent(ExchangeIntent.DismissShareSetPicker) },
            containerColor = Color(0xFF12131A),
            title = {
                Text(
                    text = "Выберите сет",
                    color = Color.White,
                    fontFamily = MdFonts.Display,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.height(240.dp),
                ) {
                    items(state.visibleSets.ifEmpty { state.savedSets }, key = { it.id }) { set ->
                        TextButton(
                            onClick = { onIntent(ExchangeIntent.ShareSet(set.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = set.name,
                                color = AtelierColors.Flame,
                                fontFamily = MdFonts.Display,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onIntent(ExchangeIntent.DismissShareSetPicker) }) {
                    Text("Отмена", color = Color.White.copy(alpha = 0.7f))
                }
            },
        )
    }

    // Full-screen section picker (Elements / Music / Choreography)
    val pickKind = state.exportPickKind
    if (pickKind != null) {
        BackHandler { onIntent(ExchangeIntent.DismissExportPick) }
        val items = if (state.viewingImportedSet) {
            when (pickKind) {
                com.midnightdancer.core.domain.exchange.ExportPickKind.Elements -> state.archiveInspectionCatalog?.elements.orEmpty()
                com.midnightdancer.core.domain.exchange.ExportPickKind.Songs -> state.archiveInspectionCatalog?.songs.orEmpty()
                com.midnightdancer.core.domain.exchange.ExportPickKind.Choreographies -> state.archiveInspectionCatalog?.choreographies.orEmpty()
            }
        } else when (pickKind) {
                com.midnightdancer.core.domain.exchange.ExportPickKind.Elements ->
                    state.exportCatalog.elementsForStyle(state.exportStyleId)
                com.midnightdancer.core.domain.exchange.ExportPickKind.Songs ->
                    state.exportCatalog.songsForStyle(state.exportStyleId)
                com.midnightdancer.core.domain.exchange.ExportPickKind.Choreographies ->
                    state.exportCatalog.choreosForStyle(state.exportStyleId)
            }
        ExportSectionPickerScreen(
            kind = pickKind,
            styleName = if (state.viewingImportedSet) "Состав архива" else state.exportStyleName,
            items = items,
            selectedIds = state.exportPickDraftIds,
            onToggle = { onIntent(ExchangeIntent.ToggleExportPickItem(it)) },
            onConfirm = { onIntent(ExchangeIntent.ConfirmExportPick) },
            onDismiss = { onIntent(ExchangeIntent.DismissExportPick) },
            modifier = modifier.fillMaxSize(),
            highlightSelected = state.isEditingSet && !state.viewingImportedSet,
            weightMb = if (state.isEditingSet && !state.viewingImportedSet) state.exportWeightMb else null,
            readOnly = state.viewingImportedSet,
            overweight = state.exportOverWeight,
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

        // C-229/C-230: sticky title + tabs + CTA + toolbar (parity Elements/Music).
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
                    MdPageTitle(
                        text = state.title.ifBlank { "Сеты" },
                        modifier = Modifier.weight(1f),
                    )
                    MdSettingsGear(onClick = onOpenSettings, haptics = haptics)
                }

                SetsHubModeToggle(
                    tab = state.hubTab,
                    onTab = { onIntent(ExchangeIntent.SelectSetHubTab(it)) },
                )

                val ctaLabel =
                    when (state.hubTab) {
                        SetHubTab.Mine -> "Создать сет"
                        SetHubTab.Imported -> "Загрузить сет"
                    }
                FlameShimmerCta(
                    label = ctaLabel,
                    enabled = !state.busy && (state.hubTab == SetHubTab.Mine || importEnabled),
                    haptics = haptics,
                    onClick = {
                        when (state.hubTab) {
                            SetHubTab.Mine -> onIntent(ExchangeIntent.ExportStub)
                            SetHubTab.Imported -> {
                                if (!importEnabled) {
                                    raiseHard?.invoke(
                                        com.midnightdancer.app.ui.components.HARD_DISK_BANNER_TEXT,
                                    )
                                } else {
                                    pickImport.launch(arrayOf("*/*"))
                                }
                            }
                        }
                    },
                )

                CatalogToolbar(
                    filterOptions = state.tagCatalog,
                    selectedFilter = state.selectedFilter.takeIf {
                        !it.equals("Все", ignoreCase = true) &&
                            !it.equals("Все теги", ignoreCase = true)
                    },
                    onFilterSelect = { tag ->
                        onIntent(ExchangeIntent.SetSetsFilter(tag))
                    },
                    sortOptions = listOf(
                        stringResource(R.string.catalog_sort_tag) to {
                            onIntent(ExchangeIntent.SetSetsSortMode(SetsSortMode.ByStyle))
                        },
                        stringResource(R.string.catalog_sort_alpha) to {
                            onIntent(ExchangeIntent.SetSetsSortMode(SetsSortMode.Alphabetical))
                        },
                    ),
                    sortActive = state.sortMode != SetsSortMode.ByStyle,
                    searchQuery = state.searchQuery,
                    onSearchChange = { onIntent(ExchangeIntent.SetSetsSearchQuery(it)) },
                    searchPlaceholder = stringResource(R.string.sets_search_placeholder),
                    haptics = haptics,
                    allFiltersLabel = "Все теги",
                )
            }

            SetsListBody(
                sets = state.visibleSets,
                hubTab = state.hubTab,
                onEdit = { onIntent(ExchangeIntent.EditSavedSet(it)) },
                onShare = { onIntent(ExchangeIntent.ShareSet(it)) },
                onDelete = { onIntent(ExchangeIntent.DeleteSavedSet(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 0.dp,
                    bottom = 100.dp,
                ),
            )

            // C-244: no bottom «Export OK…» / lastActionLabel footer under the list.
        }
    }
}

@Composable
private fun SetsHubModeToggle(
    tab: SetHubTab,
    onTab: (SetHubTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6090B10))
            .border(1.dp, Hairline, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SetsModeChip(
            label = "Мои",
            active = tab == SetHubTab.Mine,
            modifier = Modifier.weight(1f),
            onClick = { onTab(SetHubTab.Mine) },
        )
        SetsModeChip(
            label = "Добавленные",
            active = tab == SetHubTab.Imported,
            modifier = Modifier.weight(1f),
            onClick = { onTab(SetHubTab.Imported) },
        )
    }
}

@Composable
private fun SetsModeChip(
    label: String,
    active: Boolean,
    modifier: Modifier,
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
            // C-230: no haptic on Мои/Добавленные.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            // Same face as Create set CTA.
            fontWeight = FontWeight.Bold,
            fontFamily = MdFonts.Display,
        )
    }
}
