package com.midnightdancer.app.ui.choreography

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.R
import com.midnightdancer.app.ui.components.CatalogToolbar
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.choreography.logic.ChoreographyIntent
import com.midnightdancer.feature.choreography.logic.ChoreographySortMode
import com.midnightdancer.feature.choreography.logic.ChoreographyUiState
import com.midnightdancer.feature.choreography.logic.ChoreographyVaultItem
import com.midnightdancer.feature.choreography.logic.VaultStatus

private val CardShape = RoundedCornerShape(16.dp)
private val Hairline = Color.White.copy(alpha = 0.10f)

/**
 * Stitch `midnight_dancer_kinetic_vault` — OLED choreography vault (C-63 / C-72 / C-77).
 */
@Composable
fun KineticVaultScreen(
    state: ChoreographyUiState,
    onIntent: (ChoreographyIntent) -> Unit,
    modifier: Modifier = Modifier,
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
            item(key = "kv-header") {
                com.midnightdancer.app.ui.components.MdPageTitle(
                    text = state.title,
                )
            }
            item(key = "kv-toolbar") {
                CatalogToolbar(
                    filterOptions = state.tagCatalog,
                    selectedFilter = state.selectedTag.takeIf { it != "Все теги" },
                    onFilterSelect = { tag ->
                        onIntent(ChoreographyIntent.SetTagFilter(tag ?: "Все теги"))
                    },
                    sortOptions = listOf(
                        stringResource(R.string.catalog_sort_tag) to {
                            onIntent(ChoreographyIntent.SetSortMode(ChoreographySortMode.ByTag))
                        },
                        stringResource(R.string.catalog_sort_alpha) to {
                            onIntent(ChoreographyIntent.SetSortMode(ChoreographySortMode.Alphabetical))
                        },
                    ),
                    sortActive = state.sortMode != ChoreographySortMode.ByTag,
                    searchQuery = state.searchQuery,
                    onSearchChange = { onIntent(ChoreographyIntent.SetSearchQuery(it)) },
                    searchPlaceholder = stringResource(R.string.choreo_search_placeholder),
                    haptics = haptics,
                    allFiltersLabel = "Все теги",
                )
            }
            item(key = "kv-cta") {
                val raiseHardDisk = com.midnightdancer.app.ui.components.LocalRaiseHardDisk.current
                CreateChoreographyCta(
                    haptics = haptics,
                    onClick = {
                        if (com.midnightdancer.app.ui.components.guardMediaDiskOrRaise(raiseHardDisk)) {
                            onIntent(ChoreographyIntent.CreateChoreography)
                        }
                    },
                )
            }
            items(
                items = state.filteredItems,
                key = { it.id },
            ) { item ->
                VaultCard(
                    item = item,
                    onOpen = { onIntent(ChoreographyIntent.OpenEditor(item.id)) },
                    onEdit = { onIntent(ChoreographyIntent.EditItem(item.id)) },
                    onDelete = { onIntent(ChoreographyIntent.DeleteItem(item.id)) },
                    haptics = haptics,
                )
            }
            if (state.filteredItems.isEmpty()) {
                item(key = "kv-empty") {
                    Text(
                        text = "Нет хореографий по фильтру",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 14.sp,
                        fontFamily = MdFonts.Display,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateChoreographyCta(
    haptics: HapticFeedback,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AtelierColors.Flame, AtelierColors.Glow, AtelierColors.Gold),
                ),
            )
            .interactiveSpringPress(
                pressedScale = 0.96f,
                hapticEvent = HapticEvent.MediumImpact,
                haptics = haptics,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.Black)
            Text(
                text = "Создать хореографию",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontFamily = MdFonts.Display,
            )
        }
    }
}

@Composable
private fun VaultCard(
    item: ChoreographyVaultItem,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    haptics: HapticFeedback,
) {
    val isDraft = item.status == VaultStatus.Draft

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(0xCC0A0B10))
            .border(
                1.dp,
                if (isDraft) AtelierColors.Flame.copy(alpha = 0.28f) else Hairline,
                CardShape,
            )
            .interactiveSpringPress(
                pressedScale = 0.985f,
                hapticEvent = HapticEvent.LightImpact,
                haptics = haptics,
                onClick = onOpen,
            )
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = MdFonts.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Редактировать",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${item.style} · ${item.trackName} · ${item.durationLabel} · " +
                        String.format("%.1f×", item.songPlaybackSpeed),
                    color = Color.White.copy(alpha = 0.50f),
                    fontSize = 12.sp,
                    fontFamily = MdFonts.Display,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                item.tags.take(1).forEach { tag ->
                    Text(
                        text = tag,
                        color = AtelierColors.Soft,
                        fontSize = 10.sp,
                        fontFamily = MdFonts.Display,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(AtelierColors.Flame.copy(alpha = 0.12f))
                            .border(
                                1.dp,
                                AtelierColors.Flame.copy(alpha = 0.30f),
                                RoundedCornerShape(50),
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}
