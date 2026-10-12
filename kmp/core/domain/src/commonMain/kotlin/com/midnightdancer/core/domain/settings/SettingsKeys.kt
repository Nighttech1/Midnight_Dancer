package com.midnightdancer.core.domain.settings

/**
 * Flutter settings key names locked for metadata.json / DataStore parity.
 * Product is full-only — no lite/standard/english flavor keys.
 */
object SettingsKeys {
    const val UI_LANGUAGE: String = "ui_language"
    const val LEGAL_TERMS_ACCEPTED: String = "legalTermsAccepted"
    const val DANCE_REMINDER_ENABLED: String = "danceReminderEnabled"
    const val DANCE_REMINDER_HOUR: String = "danceReminderHour"
    const val DANCE_REMINDER_MINUTE: String = "danceReminderMinute"
    const val DANCE_REMINDER_MODE: String = "danceReminderMode"
    const val DANCE_REMINDER_WEEKDAY: String = "danceReminderWeekday"
    const val LAST_IMPORT_MANIFEST: String = "last_import_manifest"
    /** JSON array of element category names (C-57). */
    const val ELEMENT_CATEGORIES: String = "element_categories"
    /** JSON array of dance style names (C-70). */
    const val DANCE_STYLES: String = "dance_styles"
    /** C-110: warn banner when free bytes ≤ this (default 5 GiB). */
    const val LOW_DISK_WARN_BYTES: String = "low_disk_warn_bytes"
    /** C-111: soft low-disk plaque enabled (hard fail always on). */
    const val LOW_DISK_WARN_ENABLED: String = "low_disk_warn_enabled"
}
