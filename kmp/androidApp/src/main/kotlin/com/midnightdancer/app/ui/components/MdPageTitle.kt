package com.midnightdancer.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.midnightdancer.app.ui.theme.MdFonts

/**
 * Canonical screen title — matches Elements «Элементы» (C-81).
 * Disk plaques live dock-flush (C-111), not under the title.
 */
@Composable
fun MdPageTitle(
    text: String,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") attachLowDiskBanner: Boolean = false,
) {
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontFamily = MdFonts.Display,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp,
            fontSize = 26.sp,
        ),
        color = Color.White,
        modifier = modifier,
    )
}
