package com.midnightdancer.core.domain.exchange

import com.midnightdancer.core.domain.media.MediaContentLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageExportLimitsTest {
    @Test
    fun packageHardCapIs500Mb() {
        assertEquals(500, PackageExportLimits.MAX_PACKAGE_MEGABYTES)
        assertEquals(500L * 1024L * 1024L, PackageExportLimits.MAX_PACKAGE_BYTES)
        assertTrue(PackageExportLimits.HUMAN_LIMITS_RU.contains("пятьсот"))
    }

    @Test
    fun elementVideoUpTo120MbAccepted() {
        assertEquals(120, MediaContentLimits.MAX_ELEMENT_VIDEO_MEGABYTES)
        assertTrue(MediaContentLimits.acceptsElementVideoBytes(120L * 1024L * 1024L))
        assertFalse(MediaContentLimits.acceptsElementVideoBytes(120L * 1024L * 1024L + 1L))
    }
}
