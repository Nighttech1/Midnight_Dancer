package com.midnightdancer.core.domain.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Legal consent flag — Flutter `legalTermsAccepted`. */
object LegalTermsConsent {
    fun isAccepted(settings: JsonObject): Boolean =
        settings[SettingsKeys.LEGAL_TERMS_ACCEPTED]?.jsonPrimitive?.booleanOrNull == true

    fun acceptedEntry(): Map<String, Boolean> =
        mapOf(SettingsKeys.LEGAL_TERMS_ACCEPTED to true)

    fun embedAccepted(settings: JsonObject): JsonObject {
        val mutable = settings.toMutableMap()
        mutable[SettingsKeys.LEGAL_TERMS_ACCEPTED] = JsonPrimitive(true)
        return JsonObject(mutable)
    }
}
