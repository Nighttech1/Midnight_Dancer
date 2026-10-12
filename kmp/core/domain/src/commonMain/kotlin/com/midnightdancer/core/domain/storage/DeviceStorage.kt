package com.midnightdancer.core.domain.storage

/**
 * Platform free/total bytes on the volume used for app media / exchange (C-109 / C-110).
 */
interface DeviceStorage {
    /** Usable free bytes; 0 if unknown. */
    fun freeBytes(): Long

    /** Total volume bytes; 0 if unknown. */
    fun totalBytes(): Long
}

object DiskSpaceGate {
    /** Import allowed only if free ≥ required × this factor (user: +20%). */
    const val IMPORT_BUFFER_FACTOR: Double = 1.20

    fun requiredWithBuffer(requiredBytes: Long): Long {
        if (requiredBytes <= 0L) return 0L
        return ((requiredBytes.toDouble() * IMPORT_BUFFER_FACTOR).toLong()).coerceAtLeast(requiredBytes)
    }

    fun canImport(freeBytes: Long, requiredBytes: Long): Boolean {
        if (requiredBytes <= 0L) return freeBytes > 0L
        return freeBytes >= requiredWithBuffer(requiredBytes)
    }
}

object LowDiskDefaults {
    /** Default warn when free space ≤ 5 GiB. */
    const val DEFAULT_WARN_BYTES: Long = 5L * 1024L * 1024L * 1024L

    /** Soft plaque on by default (C-111). */
    const val DEFAULT_WARN_ENABLED: Boolean = true

    /**
     * Minimum free bytes to allow media write / create (element, music, choreo).
     * Hard gate — independent of soft-warn toggle (C-112).
     */
    const val MEDIA_OP_MIN_FREE_BYTES: Long = 64L * 1024L * 1024L

    val PRESET_GIB: List<Int> = listOf(1, 2, 5, 10)

    fun bytesForGib(gib: Int): Long = gib.toLong() * 1024L * 1024L * 1024L

    fun shouldWarn(freeBytes: Long, thresholdBytes: Long): Boolean =
        thresholdBytes > 0L && freeBytes in 1L..thresholdBytes

    fun canWriteMedia(freeBytes: Long): Boolean =
        freeBytes >= MEDIA_OP_MIN_FREE_BYTES
}
