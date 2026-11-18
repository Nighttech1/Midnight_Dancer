package com.midnightdancer.app.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.midnightdancer.app.ui.components.MdSelectAllTextField
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.app.ui.theme.toComposeColor
import com.midnightdancer.core.designsystem.ColorTokens
import com.midnightdancer.core.domain.exchange.ExportPickKind
import com.midnightdancer.core.domain.exchange.PackageExportLimits
import com.midnightdancer.core.domain.exchange.StyleRef

/**
 * Окно «Создать сет» / «Редактировать сет» — Stitch storage + dock icons (C-287 / C-288).
 */
@Composable
fun ExportPackageDialog(
    limitsText: String,
    styles: List<StyleRef>,
    styleId: String,
    styleName: String,
    selectedMoves: Int,
    selectedSongs: Int,
    selectedChoreos: Int,
    weightMb: Float,
    overweight: Boolean,
    onStyleSelect: (String) -> Unit,
    onOpenPick: (ExportPickKind) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isEditing: Boolean = false,
    validationError: String? = null,
    isBusy: Boolean = false,
    catalogLoading: Boolean = false,
    readOnly: Boolean = false,
    styleLocked: Boolean = false,
    archiveLoading: Boolean = false,
    archiveError: String? = null,
    archiveReady: Boolean = false,
    onRetryArchive: () -> Unit = {},
    invalidSelectionCount: Int = 0,
    onRemoveInvalidSelection: () -> Unit = {},
    setName: String = "",
    onSetNameChange: (String) -> Unit = {},
) {
    val flame = ColorTokens.NeonOrange.toComposeColor()
    val shape = RoundedCornerShape(22.dp)
    var styleExpanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val menuMaxHeight = (screenH * 0.45f).coerceAtLeast(180.dp).coerceAtMost(420.dp)
    val displayName = styleName.ifBlank { "Выберите стиль" }
    // C-288: create title «Создать сет»; body same as edit.
    val title = when {
        readOnly -> "Просмотр сета"
        isEditing -> "Редактировать сет"
        else -> "Создать сет"
    }
    val contentOverweight = overweight && !readOnly
    val saveBlocked = contentOverweight || invalidSelectionCount > 0 || isBusy || catalogLoading
    val confirmLabel = when {
        isBusy -> "Подождите…"
        catalogLoading -> "Загрузка материалов…"
        isEditing -> "Сохранить изменения"
        else -> "Создать сет"
    }
    val maxMb = PackageExportLimits.MAX_PACKAGE_MEGABYTES.toFloat()
    val usedMb = weightMb.coerceAtLeast(0f)
    val availMb = (maxMb - usedMb).coerceAtLeast(0f)
    val quotaFrac = (usedMb / maxMb).coerceIn(0f, 1f)
    val pct = ((quotaFrac * 100f).toInt()).coerceIn(0, 100)

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0xFF0A0B10))
                .border(1.dp, flame.copy(alpha = 0.45f), shape)
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(22.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(flame),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = MdFonts.Display,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White.copy(alpha = 0.75f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ИМЯ СЕТА",
                    color = flame,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                )
                if (!readOnly) Text(
                    text = "ОБЯЗАТЕЛЬНО",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    letterSpacing = 0.6.sp,
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (readOnly) {
                    Text(
                        text = setName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontFamily = MdFonts.Display,
                        modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                    )
                } else {
                    MdSelectAllTextField(
                        value = setName,
                        onValueChange = onSetNameChange,
                        // Returning from a content picker must not reopen the IME and move the CTA.
                        selectAllOnOpen = false,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = MdFonts.Display,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = flame,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "СТИЛЬ СЕТА",
                color = flame,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (readOnly || styleLocked) {
                Text(
                    text = styleName.ifBlank { styleId.ifBlank { "Стиль не указан" } },
                    color = Color.White,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.Black)
                        .border(1.dp, flame.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                )
            } else {
                FlameStyleDropdownField(
                    displayName = displayName,
                    styles = styles,
                    expanded = styleExpanded,
                    onExpandedChange = { styleExpanded = it },
                    onSelect = { onStyleSelect(it.id) },
                    flame = flame,
                    widthFraction = 1f,
                    menuMaxHeight = menuMaxHeight,
                )
            }

            if (readOnly && (archiveLoading || archiveError != null)) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (archiveLoading) "Читаем состав архива…" else archiveError.orEmpty(),
                    color = if (archiveError != null) Color(0xFFFF8B80) else Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                )
                if (archiveError != null) TextButton(onClick = onRetryArchive) {
                    Text("Повторить", color = flame)
                }
            }
            if (!readOnly && invalidSelectionCount > 0) {
                Text(
                    text = "В сете есть материалы другого стиля или недоступные материалы: $invalidSelectionCount. Уберите их перед сохранением.",
                    color = Color(0xFFFF8B80), fontSize = 13.sp,
                )
                TextButton(onClick = onRemoveInvalidSelection) {
                    Text("Убрать недоступные материалы", color = flame)
                }
            }
            Column {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "СОДЕРЖИМОЕ СЕТА",
                        color = Color.White.copy(alpha = 0.55f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.7.sp,
                    )

                }
                Spacer(modifier = Modifier.height(8.dp))

                // Dock section icons (CyberAcousticDock): List / MusicNote / Groups
                SetContentCard(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "Элементы",
                    subtitle = if (readOnly && !archiveReady) (if (archiveLoading) "Загрузка…" else "Состав недоступен") else movesSubtitle(selectedMoves),
                    flame = flame,
                    onClick = { onOpenPick(ExportPickKind.Elements) },
                    readOnly = readOnly,
                    enabled = !readOnly || archiveReady,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SetContentCard(
                    icon = Icons.Filled.MusicNote,
                    title = "Музыка",
                    subtitle = if (readOnly && !archiveReady) (if (archiveLoading) "Загрузка…" else "Состав недоступен") else songsSubtitle(selectedSongs),
                    flame = flame,
                    onClick = { onOpenPick(ExportPickKind.Songs) },
                    readOnly = readOnly,
                    enabled = !readOnly || archiveReady,
                )
                Spacer(modifier = Modifier.height(8.dp))
                SetContentCard(
                    icon = Icons.Filled.Groups,
                    title = "Хореографии",
                    subtitle = if (readOnly && !archiveReady) (if (archiveLoading) "Загрузка…" else "Состав недоступен") else choreosSubtitle(selectedChoreos),
                    flame = flame,
                    onClick = { onOpenPick(ExportPickKind.Choreographies) },
                    readOnly = readOnly,
                    enabled = !readOnly || archiveReady,
                )

                if (!readOnly) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF14151A))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Хранилище сета",
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            Row {
                                Text(
                                    text = "${formatWeightMb(usedMb)} МБ",
                                    color = if (overweight) Color(0xFFFF6B6B) else flame,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                )
                                Text(
                                    text = " / ${PackageExportLimits.MAX_PACKAGE_MEGABYTES} МБ",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { quotaFrac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = flame,
                            trackColor = Color.White.copy(alpha = 0.12f),
                            strokeCap = StrokeCap.Round,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Занято $pct% квоты",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4ADE80)),
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Доступно ${formatWeightMb(availMb)} МБ",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        if (limitsText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = limitsText,
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            if (!readOnly) {
                if (validationError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = validationError,
                        color = Color(0xFFFF8B80),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (saveBlocked) {
                                Brush.linearGradient(
                                    listOf(Color(0xFF3A3A40), Color(0xFF2A2A30)),
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(Color(0xFFFF5500), Color(0xFFFF1A00)),
                                )
                            },
                        )
                        .clickable(enabled = !saveBlocked) {
                            styleExpanded = false
                            focusManager.clearFocus()
                            keyboard?.hide()
                            onConfirm()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = if (saveBlocked) Color.White.copy(alpha = 0.35f) else Color.Black,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = confirmLabel,
                            color = if (saveBlocked) Color.White.copy(alpha = 0.35f) else Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(
                    if (readOnly) "ЗАКРЫТЬ" else "ОТМЕНА",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun SetContentCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    flame: Color,
    onClick: () -> Unit,
    readOnly: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14151A))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(2.dp, flame.copy(alpha = 0.85f), CircleShape)
                .background(flame.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = flame,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(flame.copy(alpha = 0.18f))
                .border(1.dp, flame.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (readOnly) Icons.Filled.ChevronRight else Icons.Filled.Add,
                contentDescription = if (readOnly) "Смотреть" else "Добавить",
                tint = flame,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun movesSubtitle(n: Int): String =
    when (n) {
        0 -> "0 движений"
        1 -> "1 движение"
        in 2..4 -> "$n движения"
        else -> "$n движений"
    }

private fun songsSubtitle(n: Int): String =
    when (n) {
        0 -> "0 треков"
        1 -> "1 трек"
        in 2..4 -> "$n трека"
        else -> "$n треков"
    }

private fun choreosSubtitle(n: Int): String =
    when (n) {
        0 -> "0 связок"
        1 -> "1 связка"
        in 2..4 -> "$n связки"
        else -> "$n связок"
    }

private fun formatWeightMb(weightMb: Float): String {
    val tenths = ((weightMb * 10f).toInt().coerceAtLeast(0)) / 10f
    return if (tenths == tenths.toInt().toFloat()) {
        tenths.toInt().toString() + ".0"
    } else {
        tenths.toString()
    }
}
