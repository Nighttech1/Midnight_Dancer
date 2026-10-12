package com.midnightdancer.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.elements.AtelierColors
import com.midnightdancer.app.ui.motion.interactiveSpringPress
import com.midnightdancer.app.ui.motion.rememberMdHaptics
import com.midnightdancer.app.ui.motion.rememberSoftGridFlingBehavior
import com.midnightdancer.app.ui.theme.MdFonts
import com.midnightdancer.core.designsystem.HapticEvent

private val PanelShape = RoundedCornerShape(12.dp)
private val Hairline = Color.White.copy(alpha = 0.12f)

/**
 * In-bounds searchable dropdown — expands downward inside parent (dialog / accordion),
 * never escapes window edges. Soft fling list + search field on top.
 */
@Composable
fun SearchablePicker(
    label: String,
    value: String,
    options: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    panelMaxHeight: Dp = 200.dp,
) {
    val haptics = rememberMdHaptics()
    val softFling = rememberSoftGridFlingBehavior(frictionMultiplier = 0.32f)
    var query by remember(expanded) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val filtered = remember(options, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            options
        } else {
            options.filter { it.contains(q, ignoreCase = true) }
        }
    }

    LaunchedEffect(expanded) {
        if (expanded) {
            query = ""
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (label.isNotBlank()) {
            MdFieldLabel(text = label)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(PanelShape)
                .background(Color(0xE6121319))
                .border(
                    1.dp,
                    if (expanded) AtelierColors.Flame.copy(alpha = 0.45f) else Hairline,
                    PanelShape,
                )
                .interactiveSpringPress(
                    pressedScale = 0.98f,
                    hapticEvent = HapticEvent.Selection,
                    haptics = haptics,
                    onClick = { onExpandedChange(!expanded) },
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = value.ifBlank { "Выбрать…" },
                color = if (value.isBlank()) Color.White.copy(alpha = 0.40f) else Color.White,
                fontFamily = MdFonts.Display,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = AtelierColors.Gold,
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
                    .heightIn(max = panelMaxHeight)
                    .clip(PanelShape)
                    .background(Color(0xF014151C))
                    .border(1.dp, AtelierColors.Flame.copy(alpha = 0.28f), PanelShape)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Hairline, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Поиск по буквам…",
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 13.sp,
                            fontFamily = MdFonts.Display,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = MdFonts.Display,
                        ),
                        cursorBrush = SolidColor(AtelierColors.Flame),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = panelMaxHeight - 56.dp),
                    flingBehavior = softFling,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(filtered, key = { it }) { option ->
                        val selected = option == value
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) {
                                        AtelierColors.Flame.copy(alpha = 0.18f)
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .interactiveSpringPress(
                                    pressedScale = 0.98f,
                                    hapticEvent = HapticEvent.Selection,
                                    haptics = haptics,
                                    onClick = {
                                        onSelect(option)
                                        onExpandedChange(false)
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = option,
                                color = if (selected) AtelierColors.Flame else Color.White,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = MdFonts.Display,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = "Ничего не найдено",
                                color = Color.White.copy(alpha = 0.40f),
                                fontSize = 13.sp,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
