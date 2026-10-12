package com.midnightdancer.core.domain.serialization

import com.midnightdancer.core.domain.entity.AppData
import kotlinx.serialization.json.Json

/** Shared Json config for Flutter metadata.json parity (lenient unknowns, pretty optional). */
object DomainJson {
    val format: Json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            explicitNulls = false
        }

    fun encodeAppData(data: AppData): String = format.encodeToString(AppData.serializer(), data)

    fun decodeAppData(raw: String): AppData = format.decodeFromString(AppData.serializer(), raw)
}
