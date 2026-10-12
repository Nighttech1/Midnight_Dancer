package com.midnightdancer.core.domain.exchange

/**
 * Системные лимиты одного пакета обмена (C-115 / C-120).
 */
object PackageExportLimits {
    const val MAX_ELEMENTS: Int = 5
    const val MAX_SONGS: Int = 2
    const val MAX_CHOREOGRAPHIES: Int = 1

    /** Один элемент с видео (каталог / импорт). */
    const val ELEMENT_VIDEO_MEGABYTES: Int = 120

    /** Два трека суммарно (ориентир внутри пакета). */
    const val TWO_SONGS_MEGABYTES: Int = 20

    /**
     * Обрезанные клипы шагов внутри одной хореографии
     * (могут отсутствовать среди выбранных элементов каталога).
     */
    const val CHOREO_CLIPPED_MEDIA_MEGABYTES: Int = 200

    /** Жёсткий потолок размера одного .mdance (C-120). */
    const val MAX_PACKAGE_MEGABYTES: Int = 500

    val MAX_PACKAGE_BYTES: Long = MAX_PACKAGE_MEGABYTES.toLong() * 1024L * 1024L

    val MAX_ELEMENT_VIDEO_BYTES: Long =
        ELEMENT_VIDEO_MEGABYTES.toLong() * 1024L * 1024L

    /** Краткий текст для окна «Поделиться пакетом». */
    const val HUMAN_LIMITS_RU: String =
        "Один стиль. Не больше пяти элементов, двух треков и одной хореографии. " +
            "Жёсткий лимит размера пакета — пятьсот мегабайт."
}
