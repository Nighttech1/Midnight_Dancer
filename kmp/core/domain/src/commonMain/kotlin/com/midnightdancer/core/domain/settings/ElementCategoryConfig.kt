package com.midnightdancer.core.domain.settings

/**
 * User-defined element category names for Elements filter (C-57).
 * Each name is capped at [MAX_NAME_LENGTH] characters.
 */
data class ElementCategoryConfig(
    val names: List<String> = DEFAULT_NAMES,
) {
    companion object {
        /** Product 2026-10-09: tag names capped at 12 characters. */
        const val MAX_NAME_LENGTH: Int = 12
        const val MAX_CATEGORIES: Int = 24
        /** Max tags per element / track / choreography — product rule 2026-10-08: one tag. */
        const val MAX_TAGS_PER_ELEMENT: Int = 1

        val DEFAULT_NAMES: List<String> = listOf(
            "Начинающий",
            "Средний",
            "Про",
            "Базовый",
            "Соло",
            "В паре",
            "Фигура",
            "Волна",
            "Поворот",
            "Шейны",
            "Кросс",
            "Дип",
        )

        fun sanitizeName(raw: String): String =
            raw.trim().take(MAX_NAME_LENGTH)

        fun sanitizeList(raw: List<String>): List<String> =
            raw.map(::sanitizeName)
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_CATEGORIES)

        /** Tags on one element/track — distinct, capped at [MAX_TAGS_PER_ELEMENT]. */
        fun sanitizeTagsForItem(raw: List<String>): List<String> =
            raw.map(::sanitizeName)
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_TAGS_PER_ELEMENT)
    }
}
