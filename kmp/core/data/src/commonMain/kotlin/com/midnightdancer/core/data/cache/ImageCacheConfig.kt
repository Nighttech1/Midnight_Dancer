package com.midnightdancer.core.data.cache

/**
 * C-18 — Coil 3 disk + memory (LRU) cache limits for Midnight Dancer.
 *
 * Pure config (no UI). Android wires these into Coil [coil3.ImageLoader] via
 * [com.midnightdancer.core.data.cache.buildCoilImageLoader].
 * iOS: stub until Stage 3 UI (see [ImageCacheFactoryNotes]).
 *
 * TZ §медиа-кэш mentions a disk cap figure that was not recoverable from the
 * source document (embedded image illegible); we document product defaults here
 * and in `memory/tickets/C-18-coil-disk-cache-config/decisions.md`.
 */
data class ImageCacheConfig(
    /** Max bytes for Coil DiskCache (LRU eviction on disk). */
    val diskMaxSizeBytes: Long = ImageCacheDefaults.DISK_MAX_SIZE_BYTES,
    /**
     * Fraction of application memory for Coil MemoryCache (LRU).
     * Coil interprets this via [coil3.memory.MemoryCache.Builder.maxSizePercent].
     */
    val memoryMaxSizePercent: Double = ImageCacheDefaults.MEMORY_MAX_SIZE_PERCENT,
    /** Subdirectory name under the platform cache root (Android: `context.cacheDir`). */
    val diskCacheDirectoryName: String = ImageCacheDefaults.DISK_CACHE_DIRECTORY_NAME,
) {
    init {
        require(diskMaxSizeBytes > 0L) {
            "diskMaxSizeBytes must be > 0 (got $diskMaxSizeBytes)"
        }
        require(diskMaxSizeBytes <= ImageCacheDefaults.MAX_SANE_DISK_BYTES) {
            "diskMaxSizeBytes must be ≤ ${ImageCacheDefaults.MAX_SANE_DISK_BYTES} " +
                "(got $diskMaxSizeBytes)"
        }
        require(memoryMaxSizePercent > 0.0 && memoryMaxSizePercent <= ImageCacheDefaults.MAX_MEMORY_PERCENT) {
            "memoryMaxSizePercent must be in (0, ${ImageCacheDefaults.MAX_MEMORY_PERCENT}] " +
                "(got $memoryMaxSizePercent)"
        }
        require(diskCacheDirectoryName.isNotBlank()) {
            "diskCacheDirectoryName must not be blank"
        }
        require('/' !in diskCacheDirectoryName && '\\' !in diskCacheDirectoryName) {
            "diskCacheDirectoryName must be a single path segment"
        }
    }

    companion object {
        /** Product default used by factories and smoke tests. */
        val Default: ImageCacheConfig = ImageCacheConfig()
    }
}

/**
 * Named constants for Version Catalog consumers and decisions.md.
 *
 * Disk: 128 MiB — within Stage 2 ticket band 64–128 MiB; enough for catalog
 * poster thumbs without competing with music/video under `MidnightDancer/`.
 * Memory: 0.25 — Coil's documented default fraction (LRU soft limit).
 */
object ImageCacheDefaults {
    const val BYTES_PER_MIB: Long = 1024L * 1024L

    /** 128 MiB disk cache. */
    const val DISK_MAX_SIZE_MIB: Long = 128L
    const val DISK_MAX_SIZE_BYTES: Long = DISK_MAX_SIZE_MIB * BYTES_PER_MIB

    /** Upper bound for config validation / commonTest (512 MiB). */
    const val MAX_SANE_DISK_MIB: Long = 512L
    const val MAX_SANE_DISK_BYTES: Long = MAX_SANE_DISK_MIB * BYTES_PER_MIB

    /** Coil default: 25% of available memory for the in-process LRU. */
    const val MEMORY_MAX_SIZE_PERCENT: Double = 0.25

    /** Hard ceiling for memory percent (avoid OOM on low-RAM devices). */
    const val MAX_MEMORY_PERCENT: Double = 0.5

    const val DISK_CACHE_DIRECTORY_NAME: String = "image_cache"
}
