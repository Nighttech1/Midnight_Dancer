package com.midnightdancer.core.domain.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Snapshot of entities added by the last ZIP import — Flutter `LastImportManifest`.
 * Stored under [SettingsKeys.LAST_IMPORT_MANIFEST].
 */
@Serializable
data class LastImportManifest(
    val wholeStyleIds: Set<String> = emptySet(),
    val moveIds: Set<String> = emptySet(),
    val songIds: Set<String> = emptySet(),
    val choreographyIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() =
            wholeStyleIds.isEmpty() &&
                moveIds.isEmpty() &&
                songIds.isEmpty() &&
                choreographyIds.isEmpty()

    fun toJsonObject(): JsonObject =
        JsonObject(
            mapOf(
                "wholeStyleIds" to JsonArray(wholeStyleIds.map { JsonPrimitive(it) }),
                "moveIds" to JsonArray(moveIds.map { JsonPrimitive(it) }),
                "songIds" to JsonArray(songIds.map { JsonPrimitive(it) }),
                "choreographyIds" to JsonArray(choreographyIds.map { JsonPrimitive(it) }),
            ),
        )

    companion object {
        fun empty(): LastImportManifest = LastImportManifest()

        fun fromJsonObject(json: JsonObject?): LastImportManifest {
            if (json == null || json.isEmpty()) return empty()
            fun ids(key: String): Set<String> {
                val v = json[key] ?: return emptySet()
                val arr = runCatching { v.jsonArray }.getOrNull() ?: return emptySet()
                return arr.mapNotNull { el ->
                    runCatching { el.jsonPrimitive.contentOrNull }.getOrNull()
                }.toSet()
            }
            return LastImportManifest(
                wholeStyleIds = ids("wholeStyleIds"),
                moveIds = ids("moveIds"),
                songIds = ids("songIds"),
                choreographyIds = ids("choreographyIds"),
            )
        }

        fun fromSettings(settings: JsonObject): LastImportManifest {
            val raw = settings[SettingsKeys.LAST_IMPORT_MANIFEST] ?: return empty()
            val obj = runCatching { raw.jsonObject }.getOrNull() ?: return empty()
            return fromJsonObject(obj)
        }

        fun embed(settings: JsonObject, manifest: LastImportManifest): JsonObject {
            val mutable = settings.toMutableMap()
            if (manifest.isEmpty) {
                mutable.remove(SettingsKeys.LAST_IMPORT_MANIFEST)
            } else {
                mutable[SettingsKeys.LAST_IMPORT_MANIFEST] = manifest.toJsonObject()
            }
            return JsonObject(mutable)
        }
    }
}
