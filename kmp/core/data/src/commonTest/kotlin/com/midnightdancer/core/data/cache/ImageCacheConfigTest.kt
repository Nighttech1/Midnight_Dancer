package com.midnightdancer.core.data.cache

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ImageCacheConfigTest {
    @Test
    fun default_diskMax_isPositive_andWithinSaneBound() {
        val config = ImageCacheConfig.Default
        assertTrue(config.diskMaxSizeBytes > 0L)
        assertTrue(config.diskMaxSizeBytes <= ImageCacheDefaults.MAX_SANE_DISK_BYTES)
        assertEquals(128L * 1024L * 1024L, config.diskMaxSizeBytes)
    }

    @Test
    fun default_memoryPercent_isSane() {
        val config = ImageCacheConfig.Default
        assertTrue(config.memoryMaxSizePercent > 0.0)
        assertTrue(config.memoryMaxSizePercent <= ImageCacheDefaults.MAX_MEMORY_PERCENT)
        assertEquals(0.25, config.memoryMaxSizePercent)
    }

    @Test
    fun defaults_constants_match_product_limits() {
        assertEquals(128L, ImageCacheDefaults.DISK_MAX_SIZE_MIB)
        assertEquals(512L, ImageCacheDefaults.MAX_SANE_DISK_MIB)
        assertEquals("image_cache", ImageCacheDefaults.DISK_CACHE_DIRECTORY_NAME)
    }

    @Test
    fun rejects_zero_or_oversized_disk() {
        assertFailsWith<IllegalArgumentException> {
            ImageCacheConfig(diskMaxSizeBytes = 0L)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageCacheConfig(diskMaxSizeBytes = ImageCacheDefaults.MAX_SANE_DISK_BYTES + 1L)
        }
    }

    @Test
    fun rejects_invalid_memory_percent() {
        assertFailsWith<IllegalArgumentException> {
            ImageCacheConfig(memoryMaxSizePercent = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ImageCacheConfig(memoryMaxSizePercent = 0.51)
        }
    }
}
