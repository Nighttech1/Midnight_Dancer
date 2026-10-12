package com.midnightdancer.core.data.migrate

import com.midnightdancer.core.data.prefs.DataStoreReadWriteUserPrefs
import com.midnightdancer.core.data.prefs.InMemoryPreferencesDataStore
import com.midnightdancer.core.domain.settings.DanceReminderMode
import com.midnightdancer.core.domain.settings.UiLanguage
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrefsMigratorTest {
    /** Same payload as `commonTest/resources/fixtures/legacy_metadata_with_prefs.json`. */
    private val fixtureJson: String =
        """
        {
          "danceStyles": [],
          "songs": [],
          "choreographies": [],
          "settings": {
            "ui_language": "es",
            "legalTermsAccepted": true,
            "danceReminderEnabled": true,
            "danceReminderHour": 20,
            "danceReminderMinute": 30,
            "danceReminderMode": "weekly",
            "danceReminderWeekday": 5,
            "last_import_manifest": {
              "wholeStyleIds": ["style-a"],
              "moveIds": ["move-1"],
              "songIds": [],
              "choreographyIds": ["ch-1"]
            }
          }
        }
        """.trimIndent()

    private fun newPrefs(): DataStoreReadWriteUserPrefs =
        DataStoreReadWriteUserPrefs(InMemoryPreferencesDataStore())

    @Test
    fun migratesHotPrefs_fromFilesystemFixture_prefersFsOverHive() =
        runTest {
            val prefs = newPrefs()
            val reader =
                FixtureLegacyMetadataReader(
                    filesystemJson = fixtureJson,
                    hiveJson = """{"settings":{"ui_language":"en"}}""",
                )
            val result = PrefsMigrator(reader, prefs).migrateOnce()

            assertTrue(result.prefsApplied)
            assertFalse(result.skippedAlreadyMigrated)
            assertEquals(LegacyPrefsSource.FilesystemMetadataJson, result.source)
            assertNotNull(result.catalogHandOff)
            assertEquals(LegacyPrefsSource.FilesystemMetadataJson, result.catalogHandOff!!.source)

            assertEquals(UiLanguage.ES, prefs.readUiLanguage())
            assertTrue(prefs.readLegalTermsAccepted())
            val reminder = prefs.readDanceReminder()
            assertTrue(reminder.enabled)
            assertEquals(20, reminder.hour)
            assertEquals(30, reminder.minute)
            assertEquals(DanceReminderMode.weekly, reminder.mode)
            assertEquals(5, reminder.weekday)
            val manifest = prefs.readLastImportManifest()
            assertEquals(setOf("style-a"), manifest.wholeStyleIds)
            assertEquals(setOf("move-1"), manifest.moveIds)
            assertEquals(setOf("ch-1"), manifest.choreographyIds)
        }

    @Test
    fun fallsBackToHive_whenFilesystemMissing() =
        runTest {
            val prefs = newPrefs()
            val reader =
                FixtureLegacyMetadataReader(
                    filesystemJson = null,
                    hiveJson = fixtureJson,
                )
            val result = PrefsMigrator(reader, prefs).migrateOnce()

            assertTrue(result.prefsApplied)
            assertEquals(LegacyPrefsSource.HiveBoxMetadata, result.source)
            assertEquals(UiLanguage.ES, prefs.readUiLanguage())
        }

    @Test
    fun secondRun_isIdempotent_skipsOverwrite() =
        runTest {
            val prefs = newPrefs()
            val reader = FixtureLegacyMetadataReader(filesystemJson = fixtureJson)
            val migrator = PrefsMigrator(reader, prefs)

            val first = migrator.migrateOnce()
            assertTrue(first.prefsApplied)

            prefs.writeUiLanguage(UiLanguage.EN)
            val second = migrator.migrateOnce()
            assertTrue(second.skippedAlreadyMigrated)
            assertFalse(second.prefsApplied)
            assertNull(second.catalogHandOff)
            // User change after first migration must not be clobbered
            assertEquals(UiLanguage.EN, prefs.readUiLanguage())
        }

    @Test
    fun constants_matchFlutterStorage() {
        assertEquals("MidnightDancer", LegacyMetadataReader.FOLDER_NAME)
        assertEquals("metadata.json", LegacyMetadataReader.METADATA_FILE_NAME)
        assertEquals("midnight_dancer", LegacyMetadataReader.HIVE_BOX_NAME)
        assertEquals("metadata", LegacyMetadataReader.HIVE_METADATA_KEY)
        assertEquals("com.midnightdancer.app", LegacyMetadataReader.FULL_APPLICATION_ID)
    }
}
