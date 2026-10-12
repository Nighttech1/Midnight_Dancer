package com.midnightdancer.core.domain.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiskSpaceGateTest {

    @Test
    fun requiredWithBuffer_addsTwentyPercent() {
        val required = 100L
        assertEquals(120L, DiskSpaceGate.requiredWithBuffer(required))
    }

    @Test
    fun canImport_falseWhenFreeBelowBufferedNeed() {
        assertFalse(DiskSpaceGate.canImport(freeBytes = 119L, requiredBytes = 100L))
        assertTrue(DiskSpaceGate.canImport(freeBytes = 120L, requiredBytes = 100L))
    }

    @Test
    fun lowDiskDefaults_shouldWarnAtOrBelowThreshold() {
        val thr = LowDiskDefaults.DEFAULT_WARN_BYTES
        assertTrue(LowDiskDefaults.shouldWarn(thr, thr))
        assertTrue(LowDiskDefaults.shouldWarn(1L, thr))
        assertFalse(LowDiskDefaults.shouldWarn(thr + 1L, thr))
        assertFalse(LowDiskDefaults.shouldWarn(0L, thr))
    }
}
