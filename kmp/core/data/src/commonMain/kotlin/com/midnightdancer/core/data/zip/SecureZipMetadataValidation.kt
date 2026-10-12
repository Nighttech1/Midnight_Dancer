package com.midnightdancer.core.data.zip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Metadata.json trust limits (S-04 / S-08).
 * Cap + structural pre-check before [com.midnightdancer.core.domain.serialization.DomainJson.decodeAppData].
 */
object SecureZipMetadataLimits {
    /** Fail-closed max UTF-8 byte size for metadata.json (2 MiB). */
    const val MAX_METADATA_JSON_BYTES: Int = 2 * 1024 * 1024

    val REQUIRED_TOP_LEVEL_KEYS: List<String> =
        listOf("danceStyles", "songs", "choreographies", "settings")
}

private val metadataStructureJson: Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

/**
 * Validates size + Flutter STEP-4 shape. Returns rejection reason codes (empty = OK).
 * Does not decode into [com.midnightdancer.core.domain.entity.AppData].
 */
fun validateMetadataJsonBeforeDecode(raw: String): List<String> {
    val bytes = raw.encodeToByteArray()
    if (bytes.isEmpty()) return listOf("empty_metadata")
    if (bytes.size > SecureZipMetadataLimits.MAX_METADATA_JSON_BYTES) {
        return listOf("metadata_too_large:${bytes.size}")
    }
    val element =
        runCatching { metadataStructureJson.parseToJsonElement(raw) }.getOrElse {
            return listOf("metadata_invalid_json")
        }
    val obj =
        element as? JsonObject
            ?: return listOf("metadata_not_object")
    for (key in SecureZipMetadataLimits.REQUIRED_TOP_LEVEL_KEYS) {
        if (key !in obj) return listOf("metadata_missing_field:$key")
    }
    if (obj["danceStyles"] !is JsonArray) return listOf("metadata_bad_array_types:danceStyles")
    if (obj["songs"] !is JsonArray) return listOf("metadata_bad_array_types:songs")
    if (obj["choreographies"] !is JsonArray) return listOf("metadata_bad_array_types:choreographies")
    if (obj["settings"] !is JsonObject) return listOf("metadata_bad_settings")
    return emptyList()
}