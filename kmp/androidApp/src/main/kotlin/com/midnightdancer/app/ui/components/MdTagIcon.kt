package com.midnightdancer.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.midnightdancer.app.ui.elements.AtelierColors

/**
 * Diagonal price-tag glyph (user PNG 2026-10-12) — solid Flame, hole near tip.
 * Used for Elements / Music / Vault tag filter buttons.
 */
@Composable
fun MdTagIcon(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = AtelierColors.Flame,
    contentDescription: String? = null,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
    ) {
        val w = this.size.width
        val h = this.size.height
        rotate(degrees = -38f, pivot = Offset(w / 2f, h / 2f)) {
            val tagW = w * 0.72f
            val tagH = h * 0.42f
            val left = (w - tagW) / 2f
            val top = (h - tagH) / 2f
            val tipCut = tagH * 0.38f
            val path = Path().apply {
                // Rounded rectangle with pointed right end (price tag).
                moveTo(left + tipCut * 0.15f, top)
                lineTo(left + tagW - tipCut, top)
                lineTo(left + tagW, top + tagH / 2f)
                lineTo(left + tagW - tipCut, top + tagH)
                lineTo(left + tipCut * 0.15f, top + tagH)
                close()
                // Soft left round via overlay round rect fill below.
            }
            // Body with rounded left edge
            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = left + tagW - tipCut * 0.55f,
                        bottom = top + tagH,
                        cornerRadius = CornerRadius(tagH * 0.28f, tagH * 0.28f),
                    ),
                )
            }
            val tip = Path().apply {
                moveTo(left + tagW - tipCut * 0.85f, top)
                lineTo(left + tagW, top + tagH / 2f)
                lineTo(left + tagW - tipCut * 0.85f, top + tagH)
                close()
            }
            drawPath(body, tint)
            drawPath(tip, tint)
            // Hole near tip
            val holeCenter = Offset(left + tagW - tipCut * 0.42f, top + tagH / 2f)
            drawCircle(
                color = Color.Black,
                radius = tagH * 0.16f,
                center = holeCenter,
            )
            // Silence unused path warning in some compilers
            @Suppress("UNUSED_VARIABLE")
            val _p = path
            @Suppress("UNUSED_VARIABLE")
            val _s = Size(tagW, tagH)
        }
    }
}
