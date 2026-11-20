package com.midnightdancer.feature.choreography.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimelineStickyPadTest {

    @Test
    fun stickyPad_followsScrollButLeavesMinText() {
        val pad = timelineStickyPadPx(
            clipStartPx = 0f,
            blockWidthPx = 400f,
            scrollPx = 100f,
            viewportPx = 400f,
        )
        assertEquals(100f, pad)
        assertTrue(400f - pad >= 72f)
    }

    @Test
    fun stickyPad_capsBeforeMinTextWidth() {
        val pad = timelineStickyPadPx(
            clipStartPx = 0f,
            blockWidthPx = 200f,
            scrollPx = 180f,
            viewportPx = 400f,
        )
        // intersect with clip is only 20px → below minText → no sticky shove
        assertEquals(0f, pad)
    }

    @Test
    fun stickyPad_zeroWhenClipFullyVisible() {
        val pad = timelineStickyPadPx(
            clipStartPx = 100f,
            blockWidthPx = 200f,
            scrollPx = 0f,
            viewportPx = 800f,
        )
        assertEquals(0f, pad)
    }
}
