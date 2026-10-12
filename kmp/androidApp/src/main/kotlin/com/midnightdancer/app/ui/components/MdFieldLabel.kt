package com.midnightdancer.app.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.elements.AtelierColors

/**
 * Canonical field caption — same style as Storyline «Элементы» / «Интервал»:
 * Soft flame tint · 11sp · SemiBold.
 */
@Composable
fun MdFieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AtelierColors.Soft,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
