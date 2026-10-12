package com.midnightdancer.core.domain.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Flutter `DanceReminderMode`. */
@Serializable
enum class DanceReminderMode {
    daily,
    weekdays,
    weekly,
}

/**
 * Dance reminder prefs — Flutter `DanceReminderConfig`.
 * Weekday uses ISO-style 1=Monday … 7=Sunday (Dart `DateTime.monday`…`sunday`).
 */
@Serializable
data class DanceReminderConfig(
    val enabled: Boolean = false,
    val hour: Int = 19,
    val minute: Int = 0,
    val mode: DanceReminderMode = DanceReminderMode.daily,
    val weekday: Int = 6,
) {
    fun toSettingsEntries(): Map<String, Any> =
        mapOf(
            SettingsKeys.DANCE_REMINDER_ENABLED to enabled,
            SettingsKeys.DANCE_REMINDER_HOUR to hour,
            SettingsKeys.DANCE_REMINDER_MINUTE to minute,
            SettingsKeys.DANCE_REMINDER_MODE to mode.name,
            SettingsKeys.DANCE_REMINDER_WEEKDAY to weekday,
        )

    companion object {
        fun fromSettings(settings: JsonObject): DanceReminderConfig {
            val modeStr =
                settings[SettingsKeys.DANCE_REMINDER_MODE]?.jsonPrimitive?.contentOrNull ?: "daily"
            val mode =
                when (modeStr) {
                    "weekdays" -> DanceReminderMode.weekdays
                    "weekly" -> DanceReminderMode.weekly
                    else -> DanceReminderMode.daily
                }
            val hour =
                settings[SettingsKeys.DANCE_REMINDER_HOUR]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(0, 23) ?: 19
            val minute =
                settings[SettingsKeys.DANCE_REMINDER_MINUTE]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(0, 59) ?: 0
            val weekday =
                settings[SettingsKeys.DANCE_REMINDER_WEEKDAY]?.jsonPrimitive?.intOrNull
                    ?.coerceIn(1, 7) ?: 6
            return DanceReminderConfig(
                enabled =
                    settings[SettingsKeys.DANCE_REMINDER_ENABLED]?.jsonPrimitive?.booleanOrNull == true,
                hour = hour,
                minute = minute,
                mode = mode,
                weekday = weekday,
            )
        }

        fun embed(settings: JsonObject, config: DanceReminderConfig): JsonObject {
            val mutable = settings.toMutableMap()
            mutable[SettingsKeys.DANCE_REMINDER_ENABLED] = JsonPrimitive(config.enabled)
            mutable[SettingsKeys.DANCE_REMINDER_HOUR] = JsonPrimitive(config.hour)
            mutable[SettingsKeys.DANCE_REMINDER_MINUTE] = JsonPrimitive(config.minute)
            mutable[SettingsKeys.DANCE_REMINDER_MODE] = JsonPrimitive(config.mode.name)
            mutable[SettingsKeys.DANCE_REMINDER_WEEKDAY] = JsonPrimitive(config.weekday)
            return JsonObject(mutable)
        }
    }
}
