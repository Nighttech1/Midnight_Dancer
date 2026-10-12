package com.midnightdancer.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.midnightdancer.app.R

/**
 * Elite display face — Noto Serif with Cyrillic (C-68).
 * Prefer [Display] over [FontFamily.Serif] (system serif falls back to sans for RU).
 * [Understrukk] — live session track clock (C-98).
 */
object MdFonts {
    val Display: FontFamily = FontFamily(
        Font(R.font.noto_serif_regular, FontWeight.Normal),
        Font(R.font.noto_serif_regular, FontWeight.Medium),
        Font(R.font.noto_serif_bold, FontWeight.SemiBold),
        Font(R.font.noto_serif_bold, FontWeight.Bold),
    )

    val Understrukk: FontFamily = FontFamily(
        Font(R.font.understrukk, FontWeight.Normal),
        Font(R.font.understrukk, FontWeight.Bold),
    )
}
