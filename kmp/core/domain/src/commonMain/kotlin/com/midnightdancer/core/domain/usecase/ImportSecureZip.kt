package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.exchange.StyleImportPlan

/**
 * Import a validated secure ZIP into the library.
 * Contract only — implementation in C-15.
 */
interface ImportSecureZip {
    suspend fun import(
        archivePath: String,
        into: AppData,
        contentFilter: ImportContentFilter = ImportContentFilter.All,
        stylePlan: StyleImportPlan = StyleImportPlan.Empty,
    ): ImportSecureZipResult
}

sealed class ImportSecureZipResult {
    data class Success(
        val data: AppData,
        val merge: MergeImportResult,
    ) : ImportSecureZipResult()

    data class Failed(
        val reasons: List<String>,
    ) : ImportSecureZipResult()
}
