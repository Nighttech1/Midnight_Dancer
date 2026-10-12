package com.midnightdancer.core.domain.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Flutter json_serializable encodes `Map<double, String>` with string keys.
 */
object DoubleKeyedStringMapSerializer : KSerializer<Map<Double, String>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("DoubleKeyedStringMap")

    override fun serialize(encoder: Encoder, value: Map<Double, String>) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("DoubleKeyedStringMapSerializer supports JSON only")
        val content = value.entries.associate { (k, v) ->
            k.toString() to JsonPrimitive(v)
        }
        jsonEncoder.encodeJsonElement(JsonObject(content))
    }

    override fun deserialize(decoder: Decoder): Map<Double, String> {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("DoubleKeyedStringMapSerializer supports JSON only")
        val element = jsonDecoder.decodeJsonElement()
        if (element is JsonNull) return emptyMap()
        val obj = element as? JsonObject ?: return emptyMap()
        val out = linkedMapOf<Double, String>()
        for ((rawKey, rawValue) in obj) {
            val key = rawKey.toDoubleOrNull() ?: continue
            val value = rawValue.jsonPrimitive.contentOrNull ?: continue
            out[key] = value
        }
        return out
    }
}
