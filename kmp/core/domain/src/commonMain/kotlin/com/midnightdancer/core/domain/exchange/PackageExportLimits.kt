package com.midnightdancer.core.domain.exchange

/**
 * Системные лимиты одного сета обмена (.mdance) — C-219 / product 2026-10-16.
 *
 * Единственный видимый UX-лимит — вес **300 МБ**. Count-caps подняты высоко,
 * чтобы не блокировать выбор; вес сета — единственный gate.
 */
object PackageExportLimits {
    /** Soft count caps — не показывать в UI; вес сета — gate. */
    const val MAX_ELEMENTS: Int = 999
    const val MAX_SONGS: Int = 999
    const val MAX_CHOREOGRAPHIES: Int = 999

    /** Один элемент с видео (каталог / импорт). */
    const val ELEMENT_VIDEO_MEGABYTES: Int = 120

    /** Два трека суммарно (ориентир внутри сета, не UX-текст). */
    const val TWO_SONGS_MEGABYTES: Int = 20

    /**
     * Обрезанные клипы шагов внутри одной хореографии
     * (могут отсутствовать среди выбранных элементов каталога).
     */
    const val CHOREO_CLIPPED_MEDIA_MEGABYTES: Int = 200

    /** Жёсткий потолок размера одного .mdance (C-219). */
    const val MAX_PACKAGE_MEGABYTES: Int = 300

    val MAX_PACKAGE_BYTES: Long = MAX_PACKAGE_MEGABYTES.toLong() * 1024L * 1024L

    val MAX_ELEMENT_VIDEO_BYTES: Long =
        ELEMENT_VIDEO_MEGABYTES.toLong() * 1024L * 1024L

    /** Единственная пользовательская строка лимита в UI. */
    const val HUMAN_LIMITS_RU: String = "Максимальный размер сета — 300 МБ."
}
