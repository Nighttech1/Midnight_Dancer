package com.midnightdancer.core.data.fixtures

import com.midnightdancer.core.data.backup.FullBackupImportMerge
import com.midnightdancer.core.data.zip.DefaultValidateSecureZip
import com.midnightdancer.core.domain.serialization.DomainJson
import com.midnightdancer.core.domain.settings.LastImportManifest
import com.midnightdancer.core.domain.settings.UiLanguage
import com.midnightdancer.core.domain.usecase.SecureZipValidationResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * C-17 — prove golden fixtures under `commonTest/resources/fixtures/` are loaded and exercised.
 * Does not duplicate the full C-15 suite; mirrors merge / zip path-list cases from JSON goldens.
 * Plain JUnit (no Robolectric) — only ClassLoader + domain/data pure logic.
 */
class FixtureCatalogTest {
    private val pathListJson = Json { ignoreUnknownKeys = true }

    @Test
    fun appdata_minimal_roundTrips() {
        val raw = TestFixtureResources.text("appdata_minimal.json")
        val data = DomainJson.decodeAppData(raw)
        assertTrue(data.danceStyles.isEmpty())
        assertTrue(data.songs.isEmpty())
        assertTrue(data.choreographies.isEmpty())
        val again = DomainJson.decodeAppData(DomainJson.encodeAppData(data))
        assertEquals(data, again)
    }

    @Test
    fun appdata_realistic_roundTrips_and_manifest() {
        val raw = TestFixtureResources.text("appdata_realistic.json")
        val data = DomainJson.decodeAppData(raw)
        assertEquals(1, data.danceStyles.size)
        assertEquals("style-salsa", data.danceStyles.single().id)
        assertEquals(2, data.danceStyles.single().moves.size)
        assertEquals(1, data.songs.size)
        assertEquals(1, data.choreographies.size)
        assertEquals("style-salsa::move-basic", data.choreographies.single().timeline[0.0])
        assertEquals("move-turn", data.choreographies.single().timeline[1.5])
        assertEquals(UiLanguage.RU, UiLanguage.fromSettings(data.settings))
        val manifest = LastImportManifest.fromSettings(data.settings)
        assertEquals(setOf("move-basic"), manifest.moveIds)
        assertEquals(setOf("song-1"), manifest.songIds)
        assertEquals(setOf("ch-1"), manifest.choreographyIds)
        val again = DomainJson.decodeAppData(DomainJson.encodeAppData(data))
        assertEquals(data.danceStyles, again.danceStyles)
        assertEquals(data.songs, again.songs)
        assertEquals(data.choreographies, again.choreographies)
    }

    @Test
    fun merge_fixtures_match_full_backup_merge_semantics() {
        val local = DomainJson.decodeAppData(TestFixtureResources.text("merge_local.json"))
        val imported = DomainJson.decodeAppData(TestFixtureResources.text("merge_imported.json"))
        val plan =
            FullBackupImportMerge.mergeInto(
                local = local,
                imported = imported,
                choreographyIdFactory = { "fixed" },
            )
        assertEquals(2, plan.merged.danceStyles.size)
        assertEquals(2, plan.merged.danceStyles.first { it.id == "st1" }.moves.size)
        assertEquals(2, plan.merged.songs.size)
        assertEquals(3, plan.merged.choreographies.size)
        assertTrue(plan.merged.choreographies.any { it.id == "c1" })
        assertTrue(plan.merged.choreographies.any { it.id.startsWith("c1-import-") })
        assertTrue(plan.merged.choreographies.any { it.id == "c2" })
        assertEquals("ru", (plan.merged.settings["ui_language"] as JsonPrimitive).content)
        assertEquals("1", (plan.merged.settings["extra"] as JsonPrimitive).content)
        assertTrue("m2" in plan.videoMoveIdsToWrite)
        assertTrue("s2" in plan.musicSongIdsToWrite)
        assertTrue("st2" in plan.manifest.wholeStyleIds)
        assertTrue("m2" in plan.manifest.moveIds)
    }

    private fun loadPathList(name: String): List<String> =
        pathListJson.decodeFromString(
            ListSerializer(String.serializer()),
            TestFixtureResources.text(name),
        )

    @Test
    fun zip_entries_valid_fixture_accepted() {
        val r = DefaultValidateSecureZip.validateEntryPaths(loadPathList("zip_entries_valid.json"))
        assertIs<SecureZipValidationResult.Ok>(r)
    }

    @Test
    fun zip_entries_rejected_ext_fixture() {
        val r = DefaultValidateSecureZip.validateEntryPaths(loadPathList("zip_entries_rejected_ext.json"))
        val rejected = assertIs<SecureZipValidationResult.Rejected>(r)
        assertTrue(rejected.reasons.any { it.startsWith("not_whitelisted:") })
    }

    @Test
    fun zip_entries_zipslip_names_fixture_rejected() {
        val r = DefaultValidateSecureZip.validateEntryPaths(loadPathList("zip_entries_zipslip_names.json"))
        val rejected = assertIs<SecureZipValidationResult.Rejected>(r)
        assertTrue(rejected.reasons.any { it.startsWith("zip_slip:") })
    }
}
