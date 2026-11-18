package com.midnightdancer.core.domain.exchange

import com.midnightdancer.core.domain.media.MediaContentLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PackageExportLimitsTest {
    @Test
    fun packageHardCapIs300Mb() {
        assertEquals(300, PackageExportLimits.MAX_PACKAGE_MEGABYTES)
        assertEquals(300L * 1024L * 1024L, PackageExportLimits.MAX_PACKAGE_BYTES)
        assertEquals("Максимальный размер сета — 300 МБ.", PackageExportLimits.HUMAN_LIMITS_RU)
        assertFalse(PackageExportLimits.HUMAN_LIMITS_RU.contains("пять"))
        assertFalse(PackageExportLimits.HUMAN_LIMITS_RU.contains("двух"))
        assertFalse(PackageExportLimits.HUMAN_LIMITS_RU.contains("одной"))
    }

    @Test
    fun countCapsDoNotBlockUx() {
        assertTrue(PackageExportLimits.MAX_ELEMENTS >= 999)
        assertTrue(PackageExportLimits.MAX_SONGS >= 999)
        assertTrue(PackageExportLimits.MAX_CHOREOGRAPHIES >= 999)
    }

    @Test
    fun elementVideoUpTo120MbAccepted() {
        assertEquals(120, MediaContentLimits.MAX_ELEMENT_VIDEO_MEGABYTES)
        assertTrue(MediaContentLimits.acceptsElementVideoBytes(120L * 1024L * 1024L))
        assertFalse(MediaContentLimits.acceptsElementVideoBytes(120L * 1024L * 1024L + 1L))
    }
}
