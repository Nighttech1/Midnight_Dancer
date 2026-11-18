package com.midnightdancer.core.domain.exchange

import com.midnightdancer.core.domain.entity.AppData

/** Opaque editor document keeps exact trim/speed/lane data without a data -> feature dependency. */
data class EditorExchangeMedia(val archivePath: String, val sourceUri: String)

data class EditorExchangePayload(
    val payloadJson: String,
    val media: List<EditorExchangeMedia> = emptyList(),
)

interface PreparedChoreographyImport {
    fun commit()
    fun rollback()
}

interface ChoreographyExchangeBridge {
    fun catalog(): List<CatalogPickItem>
    fun export(ids: Set<String>): EditorExchangePayload
    /** Validate and resolve every media ref before committing any catalog or vault changes. */
    fun prepareImport(
        payloadJson: String,
        resolvedMedia: Map<String, String>,
        styleRedirect: Map<String, String> = emptyMap(),
        legacyData: AppData = AppData(),
    ): PreparedChoreographyImport

    companion object {
        const val METADATA_KEY = "editorVaultV1"
    }
}
