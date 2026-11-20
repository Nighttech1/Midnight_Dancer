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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent
import com.midnightdancer.core.designsystem.HapticFeedback
import com.midnightdancer.feature.exchange.logic.SavedSet
import com.midnightdancer.feature.exchange.logic.SetHubTab

private val CardShape = RoundedCornerShape(16.dp)
private val Hairline = Color.White.copy(alpha = 0.10f)

/**
 * Sets hub list body — Vault-like cards with edit / share / delete (C-227).
 */
@Composable
fun SetsListBody(
    sets: List<SavedSet>,
    hubTab: SetHubTab,
    onEdit: (String) -> Unit,
    onShare: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp),
) {
    val haptics = rememberMdHaptics()
    val softFling = rememberSoftGridFlingBehavior()
    var pendingDelete by remember { mutableStateOf<SavedSet?>(null) }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Color(0xFF12131A),
            title = {
                Text(
                    text = "Удалить сет?",
                    color = Color.White,
                    fontFamily = MdFonts.Display,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "Сет «${item.name}» будет удалён без возможности восстановления.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontFamily = MdFonts.Display,
                    fontSize = 14.sp,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDelete(item.id)
                    },
                ) {
                    Text("Удалить", color = AtelierColors.Flame, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(
                        "Отмена",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                    )
                }
            },
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        flingBehavior = softFling,
    ) {
        items(sets, key = { it.id }) { item ->
            SavedSetCard(
                item = item,
                onEdit = { onEdit(item.id) },
                onShare = { onShare(item.id) },
                onDelete = { pendingDelete = item },
                haptics = haptics,
            )
        }
        if (sets.isEmpty()) {
            item(key = "sets-empty") {
                val emptyText =
                    when (hubTab) {
                        SetHubTab.Mine ->
                            "Пока нет своих сетов. Нажмите «Создать сет»."
                        SetHubTab.Imported ->
                            "Пока нет добавленных сетов. Нажмите «Загрузить сет»."
                    }
                Text(
                    text = emptyText,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 14.sp,
                    fontFamily = MdFonts.Display,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }
    }
}

/** @deprecated Use [SetsListBody] inside the Sets hub (C-227). */
@Composable
fun MySetsScreen(
    sets: List<SavedSet>,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onCopy: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onShare: (String) -> Unit = onCopy,
) {
    // Kept for residual callers; hub no longer uses full-screen My Sets. onBack unused.
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        SetsListBody(
            sets = sets,
            hubTab = SetHubTab.Mine,
            onEdit = onEdit,
            onShare = onShare,
            onDelete = onDelete,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
        )
    }
}

@Composable
private fun SavedSetCard(
    item: SavedSet,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    haptics: HapticFeedback,
) {
    val weightMb = item.estimatedBytes / (1024f * 1024f)
    val weightLabel =
        if (weightMb < 0.1f && item.estimatedBytes > 0L) {
            "<0.1 МБ"
        } else {
            val tenths = ((weightMb * 10f).toInt().coerceAtLeast(0)) / 10f
            val s =
                if (tenths == tenths.toInt().toFloat()) {
                    tenths.toInt().toString() + ".0"
                } else {
                    tenths.toString()
                }
            "$s МБ"
        }
    val counts =
        if (item.origin == com.midnightdancer.feature.exchange.logic.SavedSetOrigin.Imported &&
            item.moveIds.isEmpty() && item.songIds.isEmpty() && item.choreoIds.isEmpty()
        ) {
            "Архив сета · $weightLabel"
        } else buildString {
            append(item.moveIds.size)
            append(" эл. · ")
            append(item.songIds.size)
            append(" муз. · ")
            append(item.choreoIds.size)
            append(" хор. · ")
            append(weightLabel)
        }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(Color(0xCC0A0B10))
            .border(1.dp, Hairline, CardShape)
            .interactiveSpringPress(
                pressedScale = 0.985f,
                hapticEvent = HapticEvent.LightImpact,
                haptics = haptics,
                onClick = onEdit,
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
                    text = item.name,
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
                        if (item.origin == com.midnightdancer.feature.exchange.logic.SavedSetOrigin.Imported) Icons.Filled.ChevronRight else Icons.Filled.Edit,
                        contentDescription = if (item.origin == com.midnightdancer.feature.exchange.logic.SavedSetOrigin.Imported) "Смотреть состав" else "Редактировать",
                        tint = AtelierColors.Flame,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Поделиться",
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
            // C-244: meta+tag row like VaultCard (same height / placement).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = buildString {
                        append((item.styleName.ifBlank { item.styleId }).ifBlank { "—" })
                        append(" · ")
                        append(counts)
                    },
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
