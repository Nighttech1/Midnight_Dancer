package com.midnightdancer.core.domain.media

import com.midnightdancer.core.domain.exchange.PackageExportLimits

/**
 * Лимиты медиафайлов каталога (C-122).
 * Видео элемента до 120 МБ допускается; UI не держит все файлы в RAM.
 */
object MediaContentLimits {
    const val MAX_ELEMENT_VIDEO_MEGABYTES: Int = PackageExportLimits.ELEMENT_VIDEO_MEGABYTES

    val MAX_ELEMENT_VIDEO_BYTES: Long = PackageExportLimits.MAX_ELEMENT_VIDEO_BYTES

    fun acceptsElementVideoBytes(sizeBytes: Long): Boolean =
        sizeBytes in 0L..MAX_ELEMENT_VIDEO_BYTES
}
