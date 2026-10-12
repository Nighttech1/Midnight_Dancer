package com.midnightdancer.core.domain.settings

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * UI language — Flutter `AppUiLanguage` / key [SettingsKeys.UI_LANGUAGE].
 * Codes: `ru` | `en` | `es`. Full product default is [RU] (no english-flavor fallback).
 */
enum class UiLanguage(val code: String) {
    RU("ru"),
    EN("en"),
    ES("es"),
    ;

    fun toSettingsEntry(): Map<String, String> = mapOf(SettingsKeys.UI_LANGUAGE to code)

    companion object {
        fun fromCode(raw: String?): UiLanguage =
            when (raw) {
                "en" -> EN
                "es" -> ES
                "ru" -> RU
                else -> RU
            }

        fun fromSettings(settings: JsonObject): UiLanguage {
            val v = settings[SettingsKeys.UI_LANGUAGE]?.jsonPrimitive?.contentOrNull
            return fromCode(v)
        }

        fun embed(settings: JsonObject, language: UiLanguage): JsonObject {
            val mutable = settings.toMutableMap()
            mutable[SettingsKeys.UI_LANGUAGE] = JsonPrimitive(language.code)
            return JsonObject(mutable)
        }
    }
}
