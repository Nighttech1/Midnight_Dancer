package com.midnightdancer.core.domain.exchange

/**
 * Сопоставление «стиль из пакета → стиль в приложении».
 * Ключ — id стиля в архиве, значение — id локального стиля.
 */
data class StyleImportPlan(
    val archiveStyleToLocalId: Map<String, String> = emptyMap(),
) {
    companion object {
        val Empty = StyleImportPlan()

        /** Весь пакет → один локальный стиль (C-117). */
        fun allInto(localStyleId: String, packageStyleIds: Collection<String>): StyleImportPlan {
            if (localStyleId.isBlank()) return Empty
            return StyleImportPlan(
                archiveStyleToLocalId = packageStyleIds.associateWith { localStyleId },
            )
        }
    }
}

data class StyleRef(
    val id: String,
    val name: String,
)

data class ImportPeek(
    val packageStyles: List<StyleRef>,
    val localStyles: List<StyleRef>,
)

data class CatalogPickItem(
    val id: String,
    val title: String,
    val subtitle: String = "",
    /** Style id for single-style export filter (C-116). */
    val styleId: String = "",
    /** Approximate on-disk media size for set weight (C-219). */
    val approximateBytes: Long = 0L,
)

data class ExportCatalogSnapshot(
    val styles: List<StyleRef> = emptyList(),
    val elements: List<CatalogPickItem> = emptyList(),
    val songs: List<CatalogPickItem> = emptyList(),
    val choreographies: List<CatalogPickItem> = emptyList(),
) {
    /** Blank/unknown style is never a request for the global catalog. IDs take priority over display aliases. */
    fun canonicalStyleId(value: String): String? {
        if (value.isBlank()) return null
        return (styles.firstOrNull { it.id == value }
            ?: styles.firstOrNull { it.id.equals(value, true) }
            ?: styles.firstOrNull { it.name.equals(value, true) })?.id
    }

    private fun scoped(rows: List<CatalogPickItem>, styleId: String): List<CatalogPickItem> {
        val canonical = canonicalStyleId(styleId) ?: return emptyList()
        return rows.filter { canonicalStyleId(it.styleId) == canonical }
    }

    fun elementsForStyle(styleId: String): List<CatalogPickItem> = scoped(elements, styleId)
    fun songsForStyle(styleId: String): List<CatalogPickItem> = scoped(songs, styleId)
    fun choreosForStyle(styleId: String): List<CatalogPickItem> = scoped(choreographies, styleId)

}

enum class ExportPickKind {
    Elements,
    Songs,
    Choreographies,
}

/**
 * Выбор содержимого для экспорта — жёстко ограничен [PackageExportLimits].
 */
data class ExportPackageSelection(
    val moveIds: List<String> = emptyList(),
    val songIds: List<String> = emptyList(),
    val choreographyIds: List<String> = emptyList(),
) {
    fun validate(): List<String> {
        val reasons = mutableListOf<String>()
        if (moveIds.size > PackageExportLimits.MAX_ELEMENTS) {
            reasons += "too_many_elements:${moveIds.size}"
        }
        if (songIds.size > PackageExportLimits.MAX_SONGS) {
            reasons += "too_many_songs:${songIds.size}"
        }
        if (choreographyIds.size > PackageExportLimits.MAX_CHOREOGRAPHIES) {
            reasons += "too_many_choreographies:${choreographyIds.size}"
        }
        if (moveIds.isEmpty() && songIds.isEmpty() && choreographyIds.isEmpty()) {
            reasons += "empty_selection"
        }
        return reasons
    }

    companion object {
        fun autoCap(snapshot: ExportCatalogSnapshot): ExportPackageSelection =
            ExportPackageSelection(
                moveIds = snapshot.elements.take(PackageExportLimits.MAX_ELEMENTS).map { it.id },
                songIds = snapshot.songs.take(PackageExportLimits.MAX_SONGS).map { it.id },
                choreographyIds =
                    snapshot.choreographies
                        .take(PackageExportLimits.MAX_CHOREOGRAPHIES)
                        .map { it.id },
            )
    }
}
