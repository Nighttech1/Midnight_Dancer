package com.midnightdancer.feature.choreography.logic

/**
 * C-304: CapCut-style sticky label inset for a timeline clip.
 * Never consumes the last [minTextPx] of the clip (avoids name sliding to the right edge).
 */
fun timelineStickyPadPx(
    clipStartPx: Float,
    blockWidthPx: Float,
    scrollPx: Float,
    viewportPx: Float,
    minTextPx: Float = 72f,
): Float {
    val visibleLeft = scrollPx
    val visibleRight = scrollPx + viewportPx.coerceAtLeast(1f)
    val clipEndPx = clipStartPx + blockWidthPx
    val intersect = (minOf(clipEndPx, visibleRight) - maxOf(clipStartPx, visibleLeft))
        .coerceAtLeast(0f)
    if (intersect <= minTextPx) return 0f
    val maxPad = (blockWidthPx - minTextPx).coerceAtLeast(0f)
    return (visibleLeft - clipStartPx).coerceIn(0f, maxPad)
}
