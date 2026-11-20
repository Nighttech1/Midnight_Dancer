package com.midnightdancer.core.data.files

import com.midnightdancer.core.data.migrate.LegacyMetadataReader
import com.midnightdancer.core.domain.serialization.DomainJson
import kotlinx.serialization.json.JsonObject

/** Read-only legacy source. Corruption is an error, never the absence of a user's old library. */
class IosLegacyMetadataReader : LegacyMetadataReader {
    override suspend fun readFilesystemMetadataJson(): String? {
        val raw = IosDurableTextFile("MidnightDancer/metadata.json").readOrNull() ?: return null
        val root = DomainJson.format.parseToJsonElement(raw) as? JsonObject ?: error("invalid_ios_legacy_metadata")
        require(root.keys.any { it in setOf("danceStyles", "songs", "choreographies", "settings") }) { "invalid_ios_legacy_metadata" }
        DomainJson.decodeAppData(raw)
        return raw
    }

    // The catalog migration explicitly detects unsupported Hive before preferences are prepared.
    override suspend fun readHiveMetadataJson(): String? = null
}
