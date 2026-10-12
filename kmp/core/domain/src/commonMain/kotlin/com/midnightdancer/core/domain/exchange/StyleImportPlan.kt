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
)

data class ExportCatalogSnapshot(
    val styles: List<StyleRef> = emptyList(),
    val elements: List<CatalogPickItem> = emptyList(),
    val songs: List<CatalogPickItem> = emptyList(),
    val choreographies: List<CatalogPickItem> = emptyList(),
) {
    fun elementsForStyle(styleId: String): List<CatalogPickItem> =
        elements.filter { it.styleId == styleId || styleId.isBlank() }

    fun songsForStyle(styleId: String): List<CatalogPickItem> =
        songs.filter {
            it.styleId == styleId ||
                it.styleId.equals(styleId, ignoreCase = true) ||
                styleId.isBlank()
        }

    fun choreosForStyle(styleId: String): List<CatalogPickItem> =
        choreographies.filter { it.styleId == styleId || styleId.isBlank() }
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
