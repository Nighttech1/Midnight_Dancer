package com.midnightdancer.core.domain.usecase

/**
 * What to merge from an archive (C-113 «Параметры импорта»).
 */
data class ImportContentFilter(
    val importElements: Boolean = true,
    val importMusic: Boolean = true,
    val importChoreography: Boolean = true,
) {
    val choreographyOnly: Boolean
        get() = importChoreography && !importElements && !importMusic

    companion object {
        val All = ImportContentFilter()
    }
}
