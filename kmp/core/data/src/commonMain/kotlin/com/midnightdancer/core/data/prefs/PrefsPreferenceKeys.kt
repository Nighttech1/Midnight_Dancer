package com.midnightdancer.core.data.prefs

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.midnightdancer.core.domain.settings.SettingsKeys

/**
 * DataStore preference keys — names identical to Flutter [SettingsKeys] / AppData.settings.
 * Product is full-only (`com.midnightdancer.app`); no lite/standard/english flavor keys.
 */
internal object PrefsPreferenceKeys {
    val uiLanguage = stringPreferencesKey(SettingsKeys.UI_LANGUAGE)
    val legalTermsAccepted = booleanPreferencesKey(SettingsKeys.LEGAL_TERMS_ACCEPTED)
    val danceReminderEnabled = booleanPreferencesKey(SettingsKeys.DANCE_REMINDER_ENABLED)
    val danceReminderHour = intPreferencesKey(SettingsKeys.DANCE_REMINDER_HOUR)
    val danceReminderMinute = intPreferencesKey(SettingsKeys.DANCE_REMINDER_MINUTE)
    val danceReminderMode = stringPreferencesKey(SettingsKeys.DANCE_REMINDER_MODE)
    val danceReminderWeekday = intPreferencesKey(SettingsKeys.DANCE_REMINDER_WEEKDAY)
    /** Nested Flutter object stored as JSON string (Preferences has no object type). */
    val lastImportManifest = stringPreferencesKey(SettingsKeys.LAST_IMPORT_MANIFEST)

    /** JSON array of element category names (C-57). */
    val elementCategories = stringPreferencesKey(SettingsKeys.ELEMENT_CATEGORIES)
    val danceStyles = stringPreferencesKey(SettingsKeys.DANCE_STYLES)
    val lowDiskWarnBytes = longPreferencesKey(SettingsKeys.LOW_DISK_WARN_BYTES)
    val lowDiskWarnEnabled = booleanPreferencesKey(SettingsKeys.LOW_DISK_WARN_ENABLED)

    /** One-shot Flutter → DataStore migration marker (idempotency). */
    val prefsMigratedFromFlutterV1 = booleanPreferencesKey("prefs_migrated_from_flutter_v1")
}
