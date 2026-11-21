package com.midnightdancer.core.data.exchange

import com.midnightdancer.core.domain.entity.AppData

/**
 * Mandatory integration boundary. Its exclusive owner MUST also serialize every other library writer.
 * Implement in Kotlin beside the real Room repository; a per-import Mutex alone is insufficient.
 * Do not implement rollback by save(snapshot.data) alone: restore canonical style metadata too.
 */
interface IosImportLibraryAccess {
    suspend fun acquireExclusiveWrite(): IosImportLibrarySession
}

/** Implementation privately retains raw canonical-style/catalog metadata needed for exact rollback. */
interface IosImportCatalogSnapshot { val data: AppData }

interface IosImportLibrarySession {
    suspend fun snapshot(): IosImportCatalogSnapshot
    /** Receipts are private DB metadata, never keys imported from AppData.settings. */
    suspend fun receipt(key: String): String?
    /** Atomic catalog + pending receipt commit; publish style StateFlow only AFTER actual DB commit. */
    suspend fun commitCatalogWithPendingReceipt(data: AppData, key: String, pendingReceipt: String)
    /** Atomic receipt upsert used for completed or recovery-required status. No library rows changed. */
    suspend fun writeReceipt(key: String, receipt: String)
    /** Atomic exact catalog/style-metadata restore + removal of ONLY this operation's receipt. */
    suspend fun rollback(snapshot: IosImportCatalogSnapshot, key: String)
    /** Idempotent, nonthrowing release of the exclusive writer; must not close the application database. */
    fun close()
}
