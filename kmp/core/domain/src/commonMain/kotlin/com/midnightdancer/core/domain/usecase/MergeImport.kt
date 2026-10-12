package com.midnightdancer.core.domain.usecase

import com.midnightdancer.core.domain.entity.AppData
import com.midnightdancer.core.domain.settings.LastImportManifest

/**
 * Merge imported catalogs into local AppData.
 * Full backup + choreography package mapping — implementation in C-15 / `:core:data`.
 */
interface MergeImport {
    /**
     * Merge a full backup payload into [local].
     * Returns merged catalog and the import delta for [LastImportManifest].
     */
    suspend fun mergeFullBackup(
        local: AppData,
        imported: AppData,
    ): MergeImportResult

    /**
     * Merge a choreography package slice (styles/moves/song/choreography) into [local].
     */
    suspend fun mergeChoreographyPackage(
        local: AppData,
        packageData: AppData,
    ): MergeImportResult
}

data class MergeImportResult(
    val merged: AppData,
    val manifest: LastImportManifest,
)
