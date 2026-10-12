package com.midnightdancer.core.domain.settings

/**
 * User-defined dance style names (Settings → Elements StyleSplit).
 * Same mechanics as [ElementCategoryConfig] tags (C-70).
 */
data class DanceStyleConfig(
    val names: List<String> = DEFAULT_NAMES,
) {
    companion object {
        const val MAX_NAME_LENGTH: Int = 24
        const val MAX_STYLES: Int = 24
        /** Visible rows in Settings styles list before scroll (C-75). */
        const val VISIBLE_STYLES_IN_SETTINGS: Int = 5

        val DEFAULT_NAMES: List<String> = listOf(
            "Sensual",
            "Bachata",
            "Kizomba",
            "Salsa",
            "Dominican",
            "Modern",
            "Zouk",
            "Танго",
            "Бачата",
            "Сальса",
        )

        fun sanitizeName(raw: String): String =
            raw.trim().take(MAX_NAME_LENGTH)

        fun sanitizeList(raw: List<String>): List<String> =
            raw.map(::sanitizeName)
                .filter { it.isNotEmpty() }
                .distinct()
                .take(MAX_STYLES)
    }
}
