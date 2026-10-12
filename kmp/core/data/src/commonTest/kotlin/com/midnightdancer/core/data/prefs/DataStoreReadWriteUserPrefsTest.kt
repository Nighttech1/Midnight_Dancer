package com.midnightdancer.core.data.prefs

import com.midnightdancer.core.domain.settings.DanceReminderConfig
import com.midnightdancer.core.domain.settings.DanceReminderMode
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.storage.LowDiskDefaults
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DataStoreReadWriteUserPrefsTest {
    private fun newPrefs(): DataStoreReadWriteUserPrefs =
        DataStoreReadWriteUserPrefs(InMemoryPreferencesDataStore())

    @Test
    fun prefs_roundTrip_languageLegalReminderManifest() =
        runTest {
            val prefs = newPrefs()
            prefs.writeUiLanguage(UiLanguage.ES)
            prefs.writeLegalTermsAccepted(true)
            prefs.writeDanceReminder(
                DanceReminderConfig(
                    enabled = true,
                    hour = 8,
                    minute = 15,
                    mode = DanceReminderMode.weekdays,
                    weekday = 2,
                ),
            )
            prefs.writeLastImportManifest(
                LastImportManifest(
                    wholeStyleIds = setOf("s1"),
                    moveIds = setOf("m1", "m2"),
                    songIds = emptySet(),
                    choreographyIds = setOf("c1"),
                ),
            )

            assertEquals(UiLanguage.ES, prefs.readUiLanguage())
            assertTrue(prefs.readLegalTermsAccepted())
            val reminder = prefs.readDanceReminder()
            assertTrue(reminder.enabled)
            assertEquals(8, reminder.hour)
            assertEquals(15, reminder.minute)
            assertEquals(DanceReminderMode.weekdays, reminder.mode)
            assertEquals(2, reminder.weekday)
            val manifest = prefs.readLastImportManifest()
            assertEquals(setOf("s1"), manifest.wholeStyleIds)
            assertEquals(setOf("m1", "m2"), manifest.moveIds)
            assertEquals(setOf("c1"), manifest.choreographyIds)
            assertFalse(manifest.isEmpty)
        }

    @Test
    fun defaults_matchFlutterFullProduct() =
        runTest {
            val prefs = newPrefs()
            assertEquals(UiLanguage.RU, prefs.readUiLanguage())
            assertFalse(prefs.readLegalTermsAccepted())
            val reminder = prefs.readDanceReminder()
            assertFalse(reminder.enabled)
            assertEquals(19, reminder.hour)
            assertEquals(0, reminder.minute)
            assertEquals(DanceReminderMode.daily, reminder.mode)
            assertEquals(6, reminder.weekday)
            assertTrue(prefs.readLastImportManifest().isEmpty)
            assertEquals(LowDiskDefaults.DEFAULT_WARN_BYTES, prefs.readLowDiskWarnBytes())
        }

    @Test
    fun lowDiskWarnBytes_roundTrip() =
        runTest {
            val prefs = newPrefs()
            val twoGib = LowDiskDefaults.bytesForGib(2)
            prefs.writeLowDiskWarnBytes(twoGib)
            assertEquals(twoGib, prefs.readLowDiskWarnBytes())
        }

    @Test
    fun preferenceKeyNames_matchFlutterSettingsKeys() {
        assertEquals("ui_language", PrefsPreferenceKeys.uiLanguage.name)
        assertEquals("legalTermsAccepted", PrefsPreferenceKeys.legalTermsAccepted.name)
        assertEquals("danceReminderEnabled", PrefsPreferenceKeys.danceReminderEnabled.name)
        assertEquals("danceReminderHour", PrefsPreferenceKeys.danceReminderHour.name)
        assertEquals("danceReminderMinute", PrefsPreferenceKeys.danceReminderMinute.name)
        assertEquals("danceReminderMode", PrefsPreferenceKeys.danceReminderMode.name)
        assertEquals("danceReminderWeekday", PrefsPreferenceKeys.danceReminderWeekday.name)
        assertEquals("last_import_manifest", PrefsPreferenceKeys.lastImportManifest.name)
    }
}
