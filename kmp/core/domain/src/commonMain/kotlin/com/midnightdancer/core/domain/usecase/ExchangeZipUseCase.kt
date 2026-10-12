package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.exchange.ExportCatalogSnapshot
import com.midnightdancer.core.domain.exchange.ExportPackageSelection
import com.midnightdancer.core.domain.exchange.ImportPeek
import com.midnightdancer.core.domain.exchange.StyleImportPlan

/**
 * Debug-scale exchange ZIP export/import under app files (C-107 / C-114 / C-115).
 */
interface ExchangeZipUseCase {
    suspend fun export(
        selection: ExportPackageSelection? = null,
    ): ExchangeZipResult

    /** Import last export, or [preferredPath] / inbox zip if present. */
    suspend fun import(
        preferredPath: String? = null,
        contentFilter: ImportContentFilter = ImportContentFilter.All,
        stylePlan: StyleImportPlan = StyleImportPlan.Empty,
    ): ExchangeZipResult

    /** Peek package + local styles for mapping dialog (no merge). */
    suspend fun peekImport(preferredPath: String? = null): ImportPeek?

    /** Catalog rows for export picker. */
    suspend fun loadExportCatalog(): ExportCatalogSnapshot
}

sealed class ExchangeZipResult {
    data class Ok(
        val message: String,
        val archivePath: String = "",
        val sizeBytes: Long = 0L,
        val mediaFilesPacked: Int = 0,
    ) : ExchangeZipResult()

    data class Failed(
        val message: String,
        val reasons: List<String> = emptyList(),
    ) : ExchangeZipResult()
}
